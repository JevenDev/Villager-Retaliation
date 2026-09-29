package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundTransfers {
    private SettlementBackgroundTransfers() {
    }

    static boolean tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var queue = SettlementTransferService.pending(town, worker.id());
        if (queue == null) queue = town.transferQueues().get(worker.area());
        if (queue == null) return false;
        AssignedStorageService.releaseCourierClaims(worker.id());
        queue.releasePlanned(worker.id());
        var allocation = queue.allocation(worker.id());
        if (allocation != null) {
            deliver(level, town, worker, queue, allocation, elapsed, multiplier);
            return true;
        }
        var items = worker.items(level.registryAccess());
        if (worker.outputSlots().stream().anyMatch(slot -> !items.get(slot).isEmpty())) return false;
        for (var order : queue.orders()) {
            if (queue.available(order.id()) <= 0) continue;
            if (!SettlementTransferService.endpoints(town, worker.area(), order)) {
                queue.blocker(order.id(), "transfer_storage_changed");
                continue;
            }
            var source = town.depots().get(order.source().depot());
            var destination = town.depots().get(order.destination().depot());
            if (!source.rulesObserved() || !destination.rulesObserved()) {
                queue.blocker(order.id(), "transfer_storage_unobserved");
                continue;
            }
            int claimed = AssignedStorageService.reservedItemCount(level, source.position(), worker.id(),
                    VillagerFilterPolicy.TransferOperation.PROVIDE, order.sample());
            int count = Math.min(queue.available(order.id()), HiredVillagerRoles.courierTransferLimit(worker.aptitude()));
            count = Math.min(count, Math.max(0, source.count(order::matches) - claimed));
            count = Math.min(count, SettlementLogisticsService.framedAllowance(level, source, order.sample(),
                    VillagerFilterPolicy.TransferOperation.PROVIDE, true));
            count = Math.min(count, capacity(level, destination, order.sample()));
            if (count <= 0) {
                queue.blocker(order.id(), "transfer_supply_or_capacity");
                continue;
            }
            queue.blocker(order.id(), "");
            worker.work(elapsed, multiplier);
            if (!worker.spend(SettlementBackgroundHauling.travelCost(worker.position(), source.position(), destination.position()))) {
                worker.status("hauling");
                return true;
            }
            allocation = queue.reserve(worker.id(), order.id(), count);
            if (allocation == null) return true;
            int moved = 0;
            for (int slot = 0; slot < source.getContainerSize() && moved < allocation.amount(); slot++) {
                var stored = source.getItem(slot);
                if (!order.matches(stored)) continue;
                int requested = Math.min(stored.getCount(), allocation.amount() - moved);
                int accepted = requested - destination.deposit(stored.copyWithCount(requested)).getCount();
                source.removeItem(slot, accepted);
                moved += accepted;
            }
            if (moved > 0) {
                queue.picked(allocation.id(), moved);
                queue.delivered(allocation.id(), moved);
                worker.practice(level, HiredWorkPractice.courier(moved, Math.sqrt(source.position().distSqr(destination.position()))));
            } else queue.releasePlanned(worker.id());
            worker.status(moved > 0 ? "delivered" : "transfer_destination_full");
            return true;
        }
        return false;
    }

    private static void deliver(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
                                HiredTransferQueue queue, HiredTransferQueue.Allocation allocation, int elapsed, double multiplier) {
        var order = queue.order(allocation.order());
        var destination = SettlementTransferService.depot(town, order.destination());
        if (destination == null || !SettlementTransferService.destination(town, worker.area(), order)
                || !SettlementTransferService.destination(town, queue.owner(), order)) {
            block(worker, queue, order, "transfer_storage_changed");
            return;
        }
        if (!destination.rulesObserved()) {
            block(worker, queue, order, "transfer_storage_unobserved");
            return;
        }
        var items = worker.items(level.registryAccess());
        int carried = worker.outputSlots().stream().map(items::get).filter(order::matches).mapToInt(ItemStack::getCount).sum();
        if (carried < allocation.amount()) {
            block(worker, queue, order, "transfer_cargo_missing");
            return;
        }
        int count = Math.min(allocation.amount(), capacity(level, destination, order.sample()));
        if (count <= 0) {
            block(worker, queue, order, "transfer_destination_full");
            return;
        }
        worker.work(elapsed, multiplier);
        if (!worker.spend(SettlementBackgroundHauling.travelCost(worker.position(), worker.position(), destination.position()))) {
            worker.status("hauling");
            return;
        }
        int moved = 0;
        for (int slot : worker.outputSlots()) {
            var stack = items.get(slot);
            if (!order.matches(stack) || moved >= count) continue;
            int requested = Math.min(count - moved, stack.getCount());
            int accepted = requested - destination.deposit(stack.copyWithCount(requested)).getCount();
            stack.shrink(accepted);
            moved += accepted;
        }
        if (moved > 0) {
            worker.updateInventory(level.registryAccess(), items);
            queue.delivered(allocation.id(), moved);
            com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService.onBackgroundPractice(level, worker.id(),
                    com.jvn.villagerretaliation.interaction.HiredVillagerRole.COURIER,
                    HiredWorkPractice.courier(moved, Math.sqrt(order.source().position().distSqr(destination.position()))));
        }
        worker.status(moved > 0 ? "delivered" : "transfer_destination_full");
    }

    private static int capacity(ServerLevel level, SettlementDepot depot, ItemStack sample) {
        int loaded = AssignedStorageService.unreservedOutputCapacity(level, depot.position(), sample);
        return Math.min(loaded >= 0 ? loaded : SettlementStock.capacity(List.of(depot), sample),
                SettlementLogisticsService.framedAllowance(level, depot, sample, VillagerFilterPolicy.TransferOperation.RECEIVE, true));
    }

    private static void block(SettlementWorkerState worker, HiredTransferQueue queue, HiredTransferQueue.Order order, String reason) {
        queue.blocker(order.id(), reason);
        worker.status(reason);
    }
}
