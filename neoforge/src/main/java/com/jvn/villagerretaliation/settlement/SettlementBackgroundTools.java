package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;

final class SettlementBackgroundTools {
    private SettlementBackgroundTools() {
    }

    static boolean fetchTool(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                             List<SettlementDepot> depots, Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score, String kind, SettlementWorkBudget budget) {
        var updated = items(inventory);
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        int selected = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (predicate.test(source.available()) && score.applyAsDouble(source.available()) > best && allowed(level, supplies, index)) {
                selected = index;
                best = score.applyAsDouble(source.available());
            }
        }
        if (selected < 0) {
            worker.status("missing_" + kind);
            return false;
        }
        var source = supplies.get(selected);
        if (source.depot() == null) updated.get(source.slot()).shrink(1);
        var previous = updated.get(HiredJobInventory.MAINHAND_SLOT);
        updated.set(HiredJobInventory.MAINHAND_SLOT, ItemStack.EMPTY);
        if (!SettlementProductionInventory.insertSupplies(worker, updated, List.of(previous))) {
            worker.status("supply_full");
            return true;
        }
        double cost = 40.0D + (source.depot() == null ? 0.0D : 16.0D * Math.sqrt(worker.position().distSqr(source.depot().position())));
        if (!budget.spend(cost)) {
            worker.status("fetching_" + kind);
            return true;
        }
        if (source.depot() != null) source.depot().removeItem(source.slot(), 1);
        updated.set(HiredJobInventory.MAINHAND_SLOT, source.available().copyWithCount(1));
        replaceItems(level, inventory, updated);
        worker.status("equipped_" + kind);
        return true;
    }

    static boolean allowed(ServerLevel level, List<SettlementProductionInventory.Supply> supplies, int selected) {
        var consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
        consumed.set(selected, 1);
        return SettlementProductionInventory.allowed(level, supplies, consumed);
    }

    static NonNullList<ItemStack> items(HiredJobInventory inventory) {
        var result = NonNullList.withSize(HiredJobInventory.SLOT_COUNT, ItemStack.EMPTY);
        for (int slot = 0; slot < result.size(); slot++) result.set(slot, inventory.getItem(slot).copy());
        return result;
    }

    static void replaceItems(ServerLevel level, HiredJobInventory inventory, NonNullList<ItemStack> items) {
        CompoundTag snapshot = inventory.captureSnapshot();
        ContainerHelper.saveAllItems(snapshot, items, true, level.registryAccess());
        inventory.restoreSnapshot(snapshot);
    }
}
