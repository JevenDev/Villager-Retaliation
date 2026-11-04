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
        var basePlan = SettlementConstructionService.scan(level, request.structure(), request.rotation());
        if (basePlan == null || basePlan.blocks().size() > SettlementBuildingPreviewPayload.MAX_BLOCKS
                || basePlan.materials().size() > SettlementBuildingPreviewPayload.MAX_MATERIALS) return empty(request, "prefab");
        var origin = SettlementConstructionService.origin(basePlan, request.anchor());
        var preparation = SettlementBuildingPreparation.preview(player, town, basePlan, origin, request.preparation());
        var plan = preparation.failure().isEmpty() ? preparation.preparation().apply(basePlan) : basePlan;
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
        String failure = preparation.failure().isEmpty() ? SettlementConstructionService.placementFailure(player, town, plan, origin) : preparation.failure();
        var entrances = entrances(level, basePlan, origin);
        var diagnostics = diagnostics(player, town, plan, origin, failure, entrances, request.builder(), basePlan.worldMin(origin).getY());
        if (preparation.problem() != null && diagnostics.size() < 128) diagnostics.add(new SettlementBuildingPreviewPayload.Diagnostic(preparation.problem(), preparation.failure(), true));
        var terrain = preparation.preparation() == null ? List.<SettlementBuildingPreviewPayload.TerrainCell>of() : preparation.preparation().cells().stream()
                .map(cell -> new SettlementBuildingPreviewPayload.TerrainCell(origin.offset(cell.local()), cell.before(), cell.after())).toList();

        return new SettlementBuildingPreviewPayload(request.town(), request.requestId(), request.structure(), request.anchor(), request.rotation(),
                origin, plan.worldMin(origin), plan.worldMax(origin), plan.price(), town.economy().constructionFunds(),
                failure, blocks, materials,
                builders.stream().limit(SettlementBuildingPreviewPayload.MAX_BUILDERS).toList(),
                diagnostics.stream().filter(SettlementBuildingPreviewPayload.Diagnostic::blocking).map(SettlementBuildingPreviewPayload.Diagnostic::position).distinct().toList(),
                diagnostics, terrain, entrances, request.preparation(), preparation.failure().isEmpty() ? BuilderStructureScanner.fingerprint(plan) : "");
    }

    private static List<BlockPos> entrances(net.minecraft.server.level.ServerLevel level, BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        var result = new ArrayList<BlockPos>();
        var states = plan.blocks().stream().collect(java.util.stream.Collectors.toMap(BuilderStructureScanner.BuildBlock::localPos, BuilderStructureScanner.BuildBlock::state));
        for (var block : plan.blocks()) {
            if (!(block.state().getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                    || block.state().getValue(net.minecraft.world.level.block.DoorBlock.HALF) != net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) continue;
            var facing = block.state().getValue(net.minecraft.world.level.block.DoorBlock.FACING);
            BlockPos entrance = null;
            int shortest = 9;
            for (var direction : List.of(facing, facing.getOpposite())) {
                for (int distance = 1; distance < shortest; distance++) {
                    var local = block.localPos().relative(direction, distance);
                    if (local.getX() < plan.localMin().getX() || local.getX() > plan.localMax().getX()
                            || local.getZ() < plan.localMin().getZ() || local.getZ() > plan.localMax().getZ()) {
                        entrance = origin.offset(block.localPos().relative(direction));
                        shortest = distance;
                        break;
                    }
                    var foot = states.get(local);
                    var head = states.get(local.above());
                    if (foot != null && !foot.getCollisionShape(level, origin.offset(local)).isEmpty()
                            || head != null && !head.getCollisionShape(level, origin.offset(local.above())).isEmpty()) break;
                }
            }
            if (entrance != null) result.add(entrance);
            if (result.size() == 16) break;
        }
        return List.copyOf(result);
    }

    private static ArrayList<SettlementBuildingPreviewPayload.Diagnostic> diagnostics(ServerPlayer player, SettlementRecord town,
            BuilderStructureScanner.StructurePlan plan, BlockPos origin, String failure, List<BlockPos> entrances, java.util.UUID selected, int groundY) {
        var level = player.serverLevel();
        var result = new ArrayList<SettlementBuildingPreviewPayload.Diagnostic>();
        for (var block : plan.siteBlocks(level, origin)) {
            var pos = plan.worldPos(origin, block);
            String reason = "";
            boolean blocking = true;
            if (!town.contains(pos, pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) reason = "bounds";
            else if (!level.hasChunkAt(pos)) reason = "site_unloaded";
            else if (!level.mayInteract(player, pos)) reason = "protected";
            else if (level.getBlockEntity(pos) != null) reason = "container";
            else {
                var check = com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.placementCheck(level, null, pos, block, false);
                if (!check.valid()) reason = check.statusKey().replace("interaction.work.builder.", "");
                else if (!block.state().getCollisionShape(level, pos).isEmpty()
                        && !level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(pos),
                                entity -> entity.isAlive() && !entity.isSpectator()).isEmpty()) {
                    reason = "blocked_entity";
                    blocking = false;
                }
            }
            if (!reason.isEmpty()) result.add(new SettlementBuildingPreviewPayload.Diagnostic(pos, reason, blocking));
            if (result.size() == 96) break;
        }
        if (!failure.isEmpty() && result.stream().noneMatch(SettlementBuildingPreviewPayload.Diagnostic::blocking))
            result.add(new SettlementBuildingPreviewPayload.Diagnostic(plan.worldMin(origin), failure, true));
        var predicted = plan.blocks().stream().collect(java.util.stream.Collectors.toMap(block -> plan.worldPos(origin, block), BuilderStructureScanner.BuildBlock::state));
        for (var entrance : entrances) {
            boolean clear = true;
            for (var pos : List.of(entrance, entrance.above())) {
                if (!level.hasChunkAt(pos) || !predicted.getOrDefault(pos, level.getBlockState(pos)).getCollisionShape(level, pos).isEmpty()) clear = false;
            }
            if (!clear) result.add(new SettlementBuildingPreviewPayload.Diagnostic(entrance, "access_blocked", false));
        }
        var builder = selected == null || selected.equals(SettlementProject.AUTOMATIC_BUILDER) ? null : SettlementConstructionService.resolveBuilder(level, town, selected);
        if (builder != null && level.getEntity(builder.getUUID()) == builder && !entrances.isEmpty() && failure.isEmpty()) {
            var path = builder.getNavigation().createPath(entrances.getFirst(), 1);
            if (path == null || !path.canReach()) result.add(new SettlementBuildingPreviewPayload.Diagnostic(entrances.getFirst(), "builder_access", false));
        }
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        for (int x = min.getX(); x <= max.getX() && result.size() < 128; x++) {
            for (int z = min.getZ(); z <= max.getZ() && result.size() < 128; z++) {
                var support = new BlockPos(x, groundY - 1, z);
                if (level.hasChunkAt(support) && !predicted.getOrDefault(support, level.getBlockState(support)).isFaceSturdy(level, support, net.minecraft.core.Direction.UP))
                    result.add(new SettlementBuildingPreviewPayload.Diagnostic(support, "foundation_gap", false));
            }
        }
        return result;
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
                request.anchor(), BlockPos.ZERO, BlockPos.ZERO, 0, CurrencyAmount.ZERO, failure, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), request.preparation(), "");
    }
}
