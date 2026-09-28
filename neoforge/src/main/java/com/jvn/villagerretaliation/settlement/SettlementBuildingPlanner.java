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
        builders.sort(Comparator.comparing(SettlementBuildingPreviewPayload.Builder::name).thenComparing(SettlementBuildingPreviewPayload.Builder::id));
        var selected = request.builder() == null ? null : SettlementConstructionService.resolveBuilder(level, town, request.builder());
        var inventory = selected == null ? null : HiredJobInventory.getJobInventory(selected);
        var worker = selected == null ? null : town.workers().get(selected.getUUID());
        var carried = worker == null ? List.<ItemStack>of() : worker.items(level.registryAccess());
        var depots = SettlementStock.supplies(town).stream().filter(SettlementDepot::rulesObserved)
                .filter(depot -> BuilderWorker.builderMaterialStorageFilter(plan, origin).test(depot.position())).toList();
        var materials = new ArrayList<SettlementBuildingPreviewPayload.Material>();
        for (var material : plan.materials()) {
            int available = SettlementStock.count(depots, stack -> BuilderStructureScanner.sameMaterial(stack, material.item()));
            if (worker != null) {
                for (int slot : worker.supplySlots()) {
                    var stack = carried.get(slot);
                    if (BuilderStructureScanner.sameMaterial(stack, material.item())) available += stack.getCount();
                }
            } else if (inventory != null) {
                for (int slot : inventory.supplySlots()) {
                    var stack = inventory.getItem(slot);
                    if (BuilderStructureScanner.sameMaterial(stack, material.item())) available += stack.getCount();
                }
            }
            materials.add(new SettlementBuildingPreviewPayload.Material(material.item(), material.count(), available));
        }
        var blocks = plan.blocks().stream().map(block -> new SettlementBuildingPreviewPayload.PreviewBlock(block.localPos(), block.state())).toList();
        String failure = SettlementConstructionService.placementFailure(player, town, plan, origin);
        if (failure.isEmpty() && request.builder() != null && builders.stream().noneMatch(builder -> builder.id().equals(request.builder()))) failure = "builder";
        return new SettlementBuildingPreviewPayload(request.town(), request.requestId(), request.structure(), request.anchor(), request.rotation(),
                origin, plan.worldMin(origin), plan.worldMax(origin), plan.price(), town.economy().constructionFunds(),
                failure, blocks, materials,
                builders.stream().limit(SettlementBuildingPreviewPayload.MAX_BUILDERS).toList());
    }

    private static SettlementBuildingPreviewPayload empty(SettlementBuildingPreviewRequestPayload request, String failure) {
        return new SettlementBuildingPreviewPayload(request.town(), request.requestId(), request.structure(), request.anchor(), request.rotation(),
                request.anchor(), BlockPos.ZERO, BlockPos.ZERO, 0, CurrencyAmount.ZERO, failure, List.of(), List.of(), List.of());
    }
}
