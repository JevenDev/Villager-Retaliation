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

    static ItemStack equipTool(SettlementWorkerState worker, HiredJobInventory inventory,
                               Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score) {
        if (inventory.equipmentBlocker(net.minecraft.world.entity.EquipmentSlot.MAINHAND) != HiredJobInventory.EquipmentBlocker.NONE
                || inventory.findTool(predicate).isEmpty()) return ItemStack.EMPTY;
        var owner = inventory.villager();
        var current = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        if (com.jvn.villagerretaliation.util.VillagerEquipmentDurability.isUsable(current) && predicate.test(current)
                && current.getCount() == owner.getMainHandItem().getCount()
                && ItemStack.isSameItemSameComponents(current, owner.getMainHandItem())
                && com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(owner)
                && !com.jvn.villagerretaliation.party.PartyQuickCommandService.hasWeaponsUnequipped(owner))
            return inventory.equipBestTool(predicate, score);
        var before = owner.saveWithoutId(new CompoundTag());
        var selected = inventory.equipBestTool(predicate, score);
        worker.rememberEquipment(inventory.villager(), before);
        return selected;
    }

    static void fetchTool(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
                          List<SettlementDepot> depots, Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score,
                          String kind, SettlementWorkBudget budget) {
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var inventory = HiredJobInventory.getJobInventory(owner);
        fetchTool(level, town, worker, inventory, depots, predicate, score, kind, budget);
        var snapshot = inventory.captureSnapshot();
        if (!snapshot.equals(worker.inventory())) worker.changed();
        worker.inventorySnapshot(snapshot);
    }

    static boolean fetchTool(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                             List<SettlementDepot> depots, Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score, String kind, SettlementWorkBudget budget) {
        return fetchEquipment(level, town, worker, inventory, depots, predicate, score, kind,
                net.minecraft.world.entity.EquipmentSlot.MAINHAND, budget);
    }

    static boolean fetchEquipment(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory,
                                  List<SettlementDepot> depots, Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score,
                                  String kind, net.minecraft.world.entity.EquipmentSlot slot, SettlementWorkBudget budget) {
        var blocker = inventory.equipmentBlocker(slot);
        if (blocker != HiredJobInventory.EquipmentBlocker.NONE) {
            worker.status("equipment_" + blocker.name().toLowerCase(java.util.Locale.ROOT));
            return true;
        }
        var updated = items(inventory);
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        int selected = -1;
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (com.jvn.villagerretaliation.util.VillagerEquipmentDurability.isUsable(source.available())
                    && predicate.test(source.available()) && (source.depot() == null
                    || com.jvn.villagerretaliation.inventory.VillagerItemFilterService.mayWithdraw(inventory.villager(), source.available()))
                    && score.applyAsDouble(source.available()) > best && allowed(level, supplies, index)) {
                selected = index;
                best = score.applyAsDouble(source.available());
            }
        }
        if (selected < 0) {
            worker.status("missing_" + kind);
            return false;
        }
        var source = supplies.get(selected);
        var replacement = source.available().copyWithCount(1);
        if (source.depot() != null && !inventory.canEquipReplacement(slot, replacement)) {
            worker.status("supply_full");
            return true;
        }
        double cost = 40.0D + (source.depot() == null ? 0.0D : 16.0D * Math.sqrt(worker.position().distSqr(source.depot().position())));
        if (!budget.spend(cost)) {
            worker.status("fetching_" + kind);
            return true;
        }
        if (source.depot() == null) {
            var before = inventory.villager().saveWithoutId(new CompoundTag());
            if (inventory.equipBestEquipment(slot, predicate, score).isEmpty()) {
                worker.status("supply_full");
                return true;
            }
            worker.rememberEquipment(inventory.villager(), before);
        } else {
            var before = inventory.villager().saveWithoutId(new CompoundTag());
            if (!inventory.equipReplacement(slot, replacement)) {
                worker.status("supply_full");
                return true;
            }
            source.depot().removeItem(source.slot(), 1);
            worker.rememberEquipment(inventory.villager(), before);
        }
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
