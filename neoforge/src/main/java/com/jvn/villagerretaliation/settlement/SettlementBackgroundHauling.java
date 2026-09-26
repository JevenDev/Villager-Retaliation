package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundHauling {
    private SettlementBackgroundHauling() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        List<SettlementDepot> depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        if (depositCarried(level, town, worker, elapsed, multiplier, depots, true)) {
            return;
        }
        SettlementHaulTask task = SettlementLogisticsService.selectBackground(level, town, depots, worker);
        if (task == null) {
            worker.status("no_demand");
            return;
        }
        SettlementDepot source = town.depots().get(task.source());
        SettlementDepot destination = town.depots().get(task.destination());
        ItemStack sample = task.sample();
        int reservedSource = AssignedStorageService.reservedItemCount(level, source.position(), null,
                VillagerFilterPolicy.TransferOperation.PROVIDE, sample);
        int available = source.count(task::matches) - Math.max(0, SettlementLogisticsService.requestedTarget(level, town, source, sample))
                - reservedSource;
        int reservedDestination = AssignedStorageService.reservedItemCount(level, destination.position(), null,
                VillagerFilterPolicy.TransferOperation.RECEIVE, sample);
        int needed = task.target() - destination.count(task::matches) - reservedDestination;
        int count = Math.min(Math.min(available, needed), HiredVillagerRoles.courierTransferLimit(worker.aptitude()));
        count = Math.min(count, Math.min(receiveAllowance(level, destination, sample),
                SettlementLogisticsService.framedAllowance(level, source, sample, VillagerFilterPolicy.TransferOperation.PROVIDE, true)));
        if (count <= 0) {
            worker.status("no_demand");
            return;
        }
        worker.work(elapsed, multiplier);
        if (!worker.spend(travelCost(worker.position(), source.position(), destination.position()))) {
            worker.status("hauling");
            return;
        }
        int moved = 0;
        for (int slot = 0; slot < source.getContainerSize() && moved < count; slot++) {
            ItemStack stored = source.getItem(slot);
            if (!task.matches(stored)) {
                continue;
            }
            int requested = Math.min(stored.getCount(), count - moved);
            ItemStack remainder = destination.deposit(stored.copyWithCount(requested));
            int accepted = requested - remainder.getCount();
            source.removeItem(slot, accepted);
            moved += accepted;
        }
        worker.status(moved > 0 ? "delivered" : "storage_full");
    }

    static boolean depositCarried(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
                                  int elapsed, double multiplier, List<SettlementDepot> depots, boolean courier) {
        var items = worker.items(level.registryAccess());
        for (int slot : worker.outputSlots()) {
            ItemStack cargo = items.get(slot);
            if (cargo.isEmpty()) {
                continue;
            }
            for (SettlementDepot destination : depots) {
                if (courier ? !destination.purpose().providesSupplies() : !destination.purpose().acceptsOutputs()) {
                    continue;
                }
                int count = Math.min(cargo.getCount(), receiveAllowance(level, destination, cargo));
                int target = courier ? SettlementLogisticsService.requestedTarget(level, town, destination, cargo) : -1;
                if (target >= 0) {
                    int reserved = AssignedStorageService.reservedItemCount(level, destination.position(), null,
                            VillagerFilterPolicy.TransferOperation.RECEIVE, cargo);
                    count = Math.min(count, target - destination.count(stack -> ItemStack.isSameItemSameComponents(stack, cargo)) - reserved);
                }
                if (count <= 0) {
                    continue;
                }
                worker.work(elapsed, multiplier);
                if (!worker.spend(travelCost(worker.position(), worker.position(), destination.position()))) {
                    worker.status("hauling");
                    return true;
                }
                ItemStack remainder = destination.deposit(cargo.copyWithCount(count));
                int moved = count - remainder.getCount();
                cargo.shrink(moved);
                worker.updateInventory(level.registryAccess(), items);
                worker.status(moved > 0 ? "delivered" : "storage_full");
                return true;
            }
            worker.status("storage_full");
            return true;
        }
        return false;
    }

    private static int receiveAllowance(ServerLevel level, SettlementDepot depot, ItemStack sample) {
        return Math.min(SettlementStock.capacity(List.of(depot), sample),
                SettlementLogisticsService.framedAllowance(level, depot, sample, VillagerFilterPolicy.TransferOperation.RECEIVE, true));
    }

    static double travelCost(BlockPos origin, BlockPos source, BlockPos destination) {
        return 40.0D + 8.0D * (Math.sqrt(origin.distSqr(source)) + Math.sqrt(source.distSqr(destination))
                + Math.sqrt(destination.distSqr(origin)));
    }
}
