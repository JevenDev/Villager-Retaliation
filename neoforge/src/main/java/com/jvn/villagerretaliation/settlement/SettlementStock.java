package com.jvn.villagerretaliation.settlement;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

public final class SettlementStock {
    private SettlementStock() {
    }

    public static List<SettlementDepot> supplies(SettlementRecord town) {
        return depots(town).stream().filter(depot -> depot.purpose().providesSupplies()).toList();
    }

    public static List<SettlementDepot> outputs(SettlementRecord town) {
        return depots(town).stream().filter(depot -> depot.purpose().acceptsOutputs()).toList();
    }

    private static List<SettlementDepot> depots(SettlementRecord town) {
        return town.depots().values().stream()
                .filter(depot -> town.areas().get(depot.area()) != null && town.areas().get(depot.area()).enabled())
                .sorted(Comparator.comparingInt(SettlementDepot::priority).reversed().thenComparing(SettlementDepot::id))
                .toList();
    }

    public static int count(SettlementRecord town, Predicate<ItemStack> predicate) {
        return supplies(town).stream().mapToInt(depot -> depot.count(predicate)).sum();
    }

    public static ItemStack take(SettlementRecord town, Predicate<ItemStack> predicate, int maximum) {
        for (SettlementDepot depot : supplies(town)) {
            ItemStack taken = depot.withdraw(predicate, maximum);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack deposit(SettlementRecord town, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (SettlementDepot depot : outputs(town)) {
            remaining = depot.deposit(remaining);
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    public static int capacity(SettlementRecord town, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int capacity = 0;
        for (SettlementDepot depot : outputs(town)) {
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                if (!depot.canPlaceItem(slot, stack)) {
                    continue;
                }
                ItemStack existing = depot.getItem(slot);
                if (existing.isEmpty()) {
                    capacity += Math.min(depot.getMaxStackSize(), stack.getMaxStackSize());
                } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    capacity += Math.max(0, Math.min(depot.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount());
                }
            }
        }
        return capacity;
    }
}
