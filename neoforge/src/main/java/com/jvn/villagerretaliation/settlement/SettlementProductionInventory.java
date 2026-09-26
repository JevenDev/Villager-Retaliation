package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementProductionInventory {
    private SettlementProductionInventory() {
    }

    static List<Supply> supplies(ServerLevel level, SettlementWorkerState worker,
                                        NonNullList<ItemStack> items, List<SettlementDepot> depots) {
        List<Supply> result = new ArrayList<>();
        for (int slot : worker.supplySlots()) {
            if (!items.get(slot).isEmpty()) {
                result.add(new Supply(null, slot, items.get(slot).copy()));
            }
        }
        if (worker.settings().contains("UseAssignedStorageForSupplies")
                && !worker.settings().getBoolean("UseAssignedStorageForSupplies")) {
            return result;
        }
        for (SettlementDepot depot : depots) {
            if (!depot.purpose().providesSupplies()) {
                continue;
            }
            int depotStart = result.size();
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                ItemStack stored = depot.getItem(slot);
                if (stored.isEmpty()) {
                    continue;
                }
                int alreadyListed = result.subList(depotStart, result.size()).stream()
                        .filter(supply -> ItemStack.isSameItemSameComponents(supply.available(), stored))
                        .mapToInt(supply -> supply.available().getCount()).sum();
                int unclaimed = depot.count(stack -> ItemStack.isSameItemSameComponents(stack, stored))
                        - AssignedStorageService.reservedItemCount(level, depot.position(), null,
                                VillagerFilterPolicy.TransferOperation.PROVIDE, stored) - alreadyListed;
                int count = Math.min(stored.getCount(), Math.min(unclaimed,
                        SettlementLogisticsService.framedAllowance(level, depot, stored,
                                VillagerFilterPolicy.TransferOperation.PROVIDE, true)));
                if (count > 0) {
                    result.add(new Supply(depot, slot, stored.copyWithCount(count)));
                }
            }
        }
        return result;
    }

    static boolean allowed(ServerLevel level, List<Supply> supplies, List<Integer> consumed) {
        Map<SettlementDepot, List<ItemStack>> reservations = new HashMap<>();
        for (int index = 0; index < supplies.size(); index++) {
            Supply supply = supplies.get(index);
            int count = consumed.get(index);
            if (count <= 0 || supply.depot() == null) {
                continue;
            }
            var depot = supply.depot();
            var reserved = reservations.computeIfAbsent(depot, ignored -> new ArrayList<>());
            if (AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                    supply.available(), VillagerFilterPolicy.TransferOperation.PROVIDE, reserved) < count) {
                return false;
            }
            reserved.add(supply.available().copyWithCount(count));
        }
        return true;
    }

    static boolean insertOutputs(SettlementWorkerState worker, NonNullList<ItemStack> items, List<ItemStack> outputs) {
        return insert(worker.outputSlots(), items, outputs);
    }

    static boolean insertSupplies(SettlementWorkerState worker, NonNullList<ItemStack> items, List<ItemStack> supplies) {
        return insert(worker.supplySlots(), items, supplies);
    }

    private static boolean insert(List<Integer> slots, NonNullList<ItemStack> items, List<ItemStack> outputs) {
        for (ItemStack output : outputs) {
            ItemStack remaining = output.copy();
            for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
                for (int slot : slots) {
                    ItemStack existing = items.get(slot);
                    if (pass == 0 ? existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining) : !existing.isEmpty()) {
                        continue;
                    }
                    int count = Math.min(remaining.getCount(), Math.min(64, remaining.getMaxStackSize()) - existing.getCount());
                    if (count > 0) {
                        items.set(slot, remaining.copyWithCount(existing.getCount() + count));
                        remaining.shrink(count);
                    }
                    if (remaining.isEmpty()) {
                        break;
                    }
                }
            }
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    record Supply(SettlementDepot depot, int slot, ItemStack available) {
    }
}
