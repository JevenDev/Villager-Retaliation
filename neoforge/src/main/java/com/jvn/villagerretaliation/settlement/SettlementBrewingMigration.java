package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

final class SettlementBrewingMigration {
    private SettlementBrewingMigration() {
    }

    static String append(ServerPlayer player, SettlementRecord town, UUID areaId, HiredProductionOrder requested) {
        var level = player.serverLevel();
        var stations = HiredProductionStations.get(level);
        if (stations.readOnly()) return "Production station data requires recovery";
        var sources = new LinkedHashMap<UUID, Legacy>();
        for (var resident : town.duties().entrySet().stream().filter(entry -> areaId.equals(entry.getValue())).map(Map.Entry::getKey).sorted().toList()) {
            if (level.getEntity(resident) instanceof Villager villager) {
                if (SettlementWorkforceService.hasPersonalCommitment(villager)
                        || SettlementWorkforceService.duty(villager).filter(duty -> duty.town() == town && duty.area().id().equals(areaId)).isEmpty()) {
                    return "Staff assignments changed before brewing conversion";
                }
                SettlementBackgroundService.onActive(villager);
                SettlementBackgroundService.onUnloaded(villager);
            }
            var worker = town.workers().get(resident);
            if (worker == null || worker.role() != HiredVillagerRole.BREWING || !worker.area().equals(areaId)) continue;
            var order = SettlementBrewingOrder.read(worker.settings());
            if (order == null) {
                if (BrewingWorker.hasOrder(worker.settings())) return "A legacy brewing order requires recovery";
                continue;
            }
            var route = HiredBrewingRecipeCatalog.find(level, order.item(), order.potion()).orElse(null);
            if (route == null) return "A legacy brewing recipe is unavailable";
            sources.put(resident, new Legacy(worker, order, route));
        }
        if (sources.isEmpty()) {
            var queue = town.beginProduction(player.getUUID(), areaId);
            return queue != null && queue.append(requested) ? "" : "The production queue could not be created";
        }
        if (sources.size() >= HiredProductionQueue.MAX_ORDERS) return "Converting the legacy orders would leave no room for the new order";
        var area = town.areas().get(areaId);
        for (var source : sources.values()) {
            var state = source.worker().settings();
            if (!state.contains("BrewingCachedStandPos", Tag.TAG_LONG)) continue;
            var position = BlockPos.of(state.getLong("BrewingCachedStandPos"));
            if (!area.contains(position)) return "Restore access to the legacy brewer's active stand before conversion";
            var recorded = town.brewingStands().get(position);
            if (recorded != null && !recorded.area().equals(areaId)) return "The active stand belongs to another worksite";
            if (recorded == null) {
                if (!level.hasChunkAt(position)) return "Load the active brewing stand before converting its order";
                if (level.getBlockEntity(position) instanceof BrewingStandBlockEntity stand) {
                    town.recordBrewingStand(SettlementBrewingStandState.capture(level, areaId, stand, level.getGameTime()));
                }
            }
        }
        var assigned = new LinkedHashMap<UUID, Integer>();
        var batches = new ArrayList<Batch>();
        var construction = new SettlementConstructionMachines(level, town, null);
        for (var machine : town.brewingStands().values().stream().filter(value -> value.area().equals(areaId))
                .sorted(Comparator.comparing(SettlementBrewingStandState::position)).toList()) {
            if (!stations.available(level, machine.position())) continue;
            if (!SettlementBrewingStandService.available(level, machine, construction)) {
                if (machine.pending() || machine.conflict()) return "Resolve the active brewing stand conflict before conversion";
                continue;
            }
            var items = machine.items(level.registryAccess());
            int count = (int) items.subList(0, 3).stream().filter(stack -> !stack.isEmpty()).count();
            if (count == 0) continue;
            if (items.subList(0, 3).stream().anyMatch(stack -> stack.getCount() > 1)) return "An active brewing batch requires recovery";
            var source = sources.values().stream().filter(legacy -> compatible(level, items, legacy.route()))
                    .min(Comparator.comparingInt((Legacy legacy) -> cached(legacy.worker(), machine.position()) ? 0 : 1)
                            .thenComparingInt(legacy -> legacy.order().continuous() || assigned.getOrDefault(legacy.worker().id(), 0) < legacy.order().amount() ? 0 : 1)
                            .thenComparingDouble(legacy -> legacy.worker().position().distSqr(machine.position()))).orElse(null);
            if (source == null) return "An active brewing batch cannot be matched to a legacy order";
            assigned.merge(source.worker().id(), count, Integer::sum);
            batches.add(new Batch(source.worker().id(), machine.position(), count));
        }
        if (batches.size() > HiredProductionQueue.MAX_ALLOCATIONS) return "Too many active brewing batches to convert safely";
        var converted = SettlementRecord.load(town.save(level.registryAccess()), level.registryAccess(), () -> {});
        var queue = converted.beginProduction(player.getUUID(), areaId);
        if (queue == null) return "The worksite could not adopt shared production";
        var orders = new LinkedHashMap<UUID, HiredProductionOrder>();
        for (var entry : sources.entrySet()) {
            var old = entry.getValue().order();
            int amount = old.continuous() ? 1 : Math.max(old.amount(), assigned.getOrDefault(entry.getKey(), 0));
            var order = HiredProductionOrder.create(level, HiredProductionRecipe.brewing(old.item(), old.potion()),
                    old.continuous() ? HiredProductionOrder.Mode.CONTINUOUS : HiredProductionOrder.Mode.BATCH, amount, null);
            if (!queue.append(order)) return "The legacy brewing orders do not fit the shared queue";
            orders.put(entry.getKey(), order);
            converted.workers().get(entry.getKey()).brewingOrder(null);
        }
        for (var batch : batches) {
            UUID worker = queue.allocation(batch.resident()) == null ? batch.resident() : UUID.randomUUID();
            var allocation = queue.reserve(worker, orders.get(batch.resident()).id(), batch.count(), 1, 0L, batch.position());
            if (allocation == null || !queue.bindMachine(allocation.id(), town.brewingStands().get(batch.position()).id())
                    || !queue.start(allocation.id())) return "An active brewing batch could not be preserved";
            if (!worker.equals(batch.resident())) queue.detach(worker);
        }
        if (!queue.append(requested)) return "The production order already exists";
        var installedQueue = HiredProductionQueue.load(queue.save(level.registryAccess()), level.registryAccess(), ignored -> town.changed());
        var workers = new LinkedHashMap<UUID, SettlementWorkerState>();
        sources.keySet().forEach(resident -> workers.put(resident, converted.workers().get(resident)));
        try {
            SettlementSavedData.get(level).persistProductionMigration(level, town, converted);
        } catch (java.io.IOException exception) {
            com.mojang.logging.LogUtils.getLogger().error("Could not persist brewing order conversion", exception);
            return "The brewing conversion could not be saved. Existing orders were kept";
        }
        town.installProductionMigration(areaId, installedQueue, workers);
        for (var batch : batches) stations.claim(level, batch.position());
        workers.forEach((resident, worker) -> {
            if (level.getEntity(resident) instanceof Villager villager) SettlementBrewingService.restore(worker, villager);
        });
        return "";
    }

    private static boolean cached(SettlementWorkerState worker, BlockPos position) {
        return worker.settings().contains("BrewingCachedStandPos", Tag.TAG_LONG)
                && worker.settings().getLong("BrewingCachedStandPos") == position.asLong();
    }

    private static boolean compatible(ServerLevel level, net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> items,
            HiredBrewingRecipeCatalog.BrewingRoute route) {
        int step = SettlementBackgroundBrewing.currentStep(level, items, route);
        return step >= 0 && (items.get(4).isEmpty() || items.get(4).is(Items.BLAZE_POWDER))
                && (items.get(3).isEmpty() || step < route.ingredients().size() && items.get(3).is(route.ingredients().get(step))
                    || SettlementBackgroundBrewing.remainder(route, items.get(3)));
    }

    private record Legacy(SettlementWorkerState worker, SettlementBrewingOrder order, HiredBrewingRecipeCatalog.BrewingRoute route) {
    }

    private record Batch(UUID resident, BlockPos position, int count) {
    }
}
