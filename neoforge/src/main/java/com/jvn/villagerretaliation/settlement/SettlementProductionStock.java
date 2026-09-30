package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementProductionStock {
    private SettlementProductionStock() {
    }

    public static long count(ServerLevel level, SettlementRecord town, HiredProductionQueue queue, HiredProductionOrder order) {
        if (order.mode() != HiredProductionOrder.Mode.MAINTAIN_STOCK) return 0L;
        var links = SettlementWorksiteService.links(town, queue.owner());
        var depot = SettlementStock.outputs(town).stream().filter(candidate -> candidate.position().equals(order.storage())
                && links.permitsOutput(candidate.id()) && candidate.rulesObserved()).findFirst().orElse(null);
        if (depot == null) return -1L;
        long incoming = incoming(level, town, order.storage(), order.output(), queue);
        return incoming < 0L ? -1L : depot.count(stack -> HiredProductionStock.matches(order.output(), stack)) + incoming;
    }

    static long incoming(ServerLevel level, SettlementRecord town, net.minecraft.core.BlockPos destination,
                         net.minecraft.world.item.ItemStack sample, HiredProductionQueue excluded) {
        long count = 0L;
        var commitments = com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(level);
        commitments.refresh(level, destination);
        long incoming = commitments.count(destination, sample, null);
        if (incoming < 0L) return -1L;
        count += incoming;
        var transferWorkers = new java.util.HashSet<>(commitments.contributors(destination, sample));
        for (var transfers : town.transferQueues().values()) {
            count += transfers.pending(sample, destination);
            for (var allocation : transfers.allocations()) transferWorkers.add(allocation.worker());
        }
        count += AssignedStorageService.reservedItemsExcluding(level, destination, transferWorkers,
                VillagerFilterPolicy.TransferOperation.RECEIVE, stack -> HiredProductionStock.matches(sample, stack));
        for (var other : town.productionQueues().values()) {
            if (other != excluded) count += other.pending(sample, destination);
        }
        for (var worker : town.workers().values()) {
            if (!town.duties().containsKey(worker.id())) continue;
            if (level.getEntity(worker.id()) instanceof Villager loaded) {
                if (!destination.equals(HiredProductionStock.destination(HiredWorkStateStore.state(loaded)))) continue;
                var inventory = HiredJobInventory.getJobInventory(loaded);
                for (int slot : inventory.outputSlots()) {
                    var stack = inventory.getItem(slot);
                    if (HiredProductionStock.matches(sample, stack)) count += stack.getCount();
                }
            } else if (destination.equals(HiredProductionStock.destination(worker.settings()))) {
                var inventory = worker.items(level.registryAccess());
                for (int slot : worker.outputSlots()) {
                    var stack = inventory.get(slot);
                    if (HiredProductionStock.matches(sample, stack)) count += stack.getCount();
                }
            }
        }
        return count;
    }
}
