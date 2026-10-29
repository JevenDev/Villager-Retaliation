package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementBuildingPlanner {
    private SettlementBuildingPlanner() {
    }

    public static void request(ServerPlayer player, SettlementBuildingPreviewRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementBuildingPreviewRequestPayload.TYPE.id(), 4L)) return;
        PacketDistributor.sendToPlayer(player, preview(player, request));
    }

    public static SettlementBuildingPreviewPayload preview(ServerPlayer player, SettlementBuildingPreviewRequestPayload request) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).get(new VillageAllegianceId(request.town())).orElse(null);
        if (!SettlementConstructionService.canBuild(player, town)) return empty(request, "permission");
        var plan = SettlementConstructionService.scan(level, request.structure(), request.rotation());
        if (plan == null || plan.blocks().size() > SettlementBuildingPreviewPayload.MAX_BLOCKS
                || plan.materials().size() > SettlementBuildingPreviewPayload.MAX_MATERIALS) return empty(request, "prefab");
        var origin = SettlementConstructionService.origin(plan, request.anchor());
        var builders = new ArrayList<SettlementBuildingPreviewPayload.Builder>();
        for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
            var builder = SettlementConstructionService.resolveBuilder(level, town, member.id());
            if (SettlementConstructionService.eligible(level, town, builder)) {
                var name = VillagerPresetNameRegistry.resolveDisplayName(builder).getString();
                builders.add(new SettlementBuildingPreviewPayload.Builder(member.id(), name.substring(0, Math.min(128, name.length()))));
            }
        }
        builders.add(new SettlementBuildingPreviewPayload.Builder(SettlementProject.AUTOMATIC_BUILDER, "Next available assigned builder"));
        builders.sort(Comparator.comparing(SettlementBuildingPreviewPayload.Builder::name).thenComparing(SettlementBuildingPreviewPayload.Builder::id));
        var selected = request.builder() == null || request.builder().equals(SettlementProject.AUTOMATIC_BUILDER) ? null : request.builder();
        var storage = selected == null ? SettlementStorageLinks.AUTOMATIC : SettlementStorageService.effective(town, selected);
        var duty = selected == null ? null : town.duties().get(selected);
        var links = duty == null ? SettlementWorksiteLinks.AUTOMATIC : SettlementWorksiteService.links(town, duty, selected);
        var depots = SettlementStock.supplies(town, com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER).stream()
                .filter(SettlementDepot::rulesObserved)
                .filter(depot -> storage.automatic() ? links.permitsSupply(depot.id())
                        : storage.entries().stream().anyMatch(link -> link.position().equals(depot.position()) && link.purpose().supplies()))
                .filter(depot -> !level.hasChunkAt(depot.position()) || SettlementStorageService.permitted(level, town,
                        com.jvn.villagerretaliation.interaction.HiredVillagerRole.BUILDER,
                        new SettlementStorageLinks.Link(depot.position(), SettlementStorageLinks.Purpose.SUPPLY, 0)))
                .filter(depot -> BuilderWorker.builderMaterialStorageFilter(plan, origin).test(depot.position())).toList();
        var materials = new ArrayList<SettlementBuildingPreviewPayload.Material>();
        for (var material : com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost.requirements(plan)) {
            int available = SettlementStock.count(depots, material::matches);
            var item = material.tag() ? net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, material.id()))
                    .flatMap(tag -> tag.stream().findFirst()).map(holder -> new ItemStack(holder.value()))
                    .orElseGet(() -> new ItemStack(net.minecraft.world.item.Items.BARRIER))
                    : new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(material.id()));
            String label = material.displayName();
            materials.add(new SettlementBuildingPreviewPayload.Material(item, material.count(), available,
                    label.substring(0, Math.min(256, label.length()))));
        }
        var blocks = plan.blocks().stream().map(block -> new SettlementBuildingPreviewPayload.PreviewBlock(block.localPos(), block.state())).toList();
        String failure = SettlementConstructionService.placementFailure(player, town, plan, origin);

        return new SettlementBuildingPreviewPayload(request.town(), request.requestId(), request.structure(), request.anchor(), request.rotation(),
                origin, plan.worldMin(origin), plan.worldMax(origin), plan.price(), town.economy().constructionFunds(),
                failure, blocks, materials,
                builders.stream().limit(SettlementBuildingPreviewPayload.MAX_BUILDERS).toList(), blockers(level, player, town, plan, origin, failure));
    }

    private static List<BlockPos> blockers(net.minecraft.server.level.ServerLevel level, ServerPlayer player, SettlementRecord town,
            BuilderStructureScanner.StructurePlan plan, BlockPos origin, String failure) {
        if (failure.isEmpty() || !(failure.equals("bounds") || failure.equals("blocked_existing") || failure.equals("site_unloaded"))) return List.of();
        var positions = new ArrayList<BlockPos>();
        for (var block : plan.siteBlocks(level, origin)) {
            var pos = plan.worldPos(origin, block);
            boolean blocked = failure.equals("bounds") ? !town.contains(pos, pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    : !level.hasChunkAt(pos) || level.getBlockEntity(pos) != null || !level.mayInteract(player, pos)
                    || !com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.placementCheck(level, null, pos, block, false).valid();
            if (blocked) positions.add(pos);
            if (positions.size() == 128) break;
        }
        return positions;
    }

    static SettlementBuildingPreviewPayload projectPreview(net.minecraft.server.level.ServerLevel level, SettlementRecord town, SettlementProject project, int requestId) {
        var plan = SettlementConstructionService.scan(level, project);
        if (plan == null || !project.matchesPlan(plan) || plan.blocks().size() > SettlementBuildingPreviewPayload.MAX_BLOCKS
                || !plan.worldMin(project.origin()).equals(project.min()) || !plan.worldMax(project.origin()).equals(project.max())) return null;
        var blocks = plan.blocks().stream().map(block -> new SettlementBuildingPreviewPayload.PreviewBlock(block.localPos(), block.state())).toList();
        return new SettlementBuildingPreviewPayload(town.id().value(), requestId, project.structure(), project.origin(), project.rotation(),
                project.origin(), project.min(), project.max(), 0, CurrencyAmount.ZERO, "", blocks, List.of(), List.of());
    }

    private static SettlementBuildingPreviewPayload empty(SettlementBuildingPreviewRequestPayload request, String failure) {
        return new SettlementBuildingPreviewPayload(request.town(), request.requestId(), request.structure(), request.anchor(), request.rotation(),
                request.anchor(), BlockPos.ZERO, BlockPos.ZERO, 0, CurrencyAmount.ZERO, failure, List.of(), List.of(), List.of());
    }
}
