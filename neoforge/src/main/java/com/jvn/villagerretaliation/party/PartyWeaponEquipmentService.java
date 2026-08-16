package com.jvn.villagerretaliation.party;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerDefensiveLoadoutService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Moves explicitly stowed party weapons between equipment and party inventory slots. */
final class PartyWeaponEquipmentService {
    private static final String STOWED_TAG = "VillagerRetaliationStowedPartyWeapon";
    private static final String VILLAGER_TAG = "Villager";
    private static final String SLOT_TAG = "Slot";
    private static final String MAINHAND = "mainhand";
    private static final String OFFHAND = "offhand";

    private PartyWeaponEquipmentService() {
    }

    static int unequip(Villager villager) {
        if (!canManage(villager)) {
            return 0;
        }
        prepareEquipment(villager);
        List<StowCandidate> candidates = new ArrayList<>(2);
        collectCandidate(villager, EquipmentSlot.MAINHAND, candidates);
        collectCandidate(villager, EquipmentSlot.OFFHAND, candidates);
        if (candidates.isEmpty()) {
            return 0;
        }

        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        List<Integer> destinations = emptyStorageSlots(inventory, candidates.size());
        if (destinations.size() < candidates.size()) {
            return 0;
        }

        villager.stopUsingItem();
        for (int index = 0; index < candidates.size(); index++) {
            StowCandidate candidate = candidates.get(index);
            int equipmentSlot = jobEquipmentSlot(candidate.slot());
            if (!inventory.getItem(equipmentSlot).isEmpty()) {
                inventory.setItem(equipmentSlot, ItemStack.EMPTY);
            } else {
                VillagerRetaliationVillagerEquipment.setInventoryEquipment(
                        villager, candidate.slot(), ItemStack.EMPTY);
            }
            inventory.setItem(
                    destinations.get(index),
                    markStowed(candidate.stack(), villager.getUUID(), candidate.slot()));
        }
        return candidates.size();
    }

    static int reequip(Villager villager) {
        if (!canManage(villager)) {
            return 0;
        }
        prepareEquipment(villager);
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        int equipped = 0;
        equipped += reequip(villager, inventory, EquipmentSlot.MAINHAND);
        equipped += reequip(villager, inventory, EquipmentSlot.OFFHAND);
        return equipped;
    }

    static boolean hasStowedWeapons(Villager villager) {
        if (!canManage(villager)) {
            return false;
        }
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(villager);
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (stowedSlot(inventory.getItem(slot), villager.getUUID()) != null) {
                return true;
            }
        }
        return false;
    }

    private static int reequip(
            Villager villager,
            HiredJobInventory inventory,
            EquipmentSlot equipmentSlot) {
        if (!villager.getItemBySlot(equipmentSlot).isEmpty()) {
            return 0;
        }
        int sourceSlot = findStowedSlot(inventory, villager.getUUID(), equipmentSlot);
        if (sourceSlot < 0) {
            return 0;
        }
        ItemStack stack = removeStowedMarker(inventory.getItem(sourceSlot).copy());
        inventory.setItem(sourceSlot, ItemStack.EMPTY);
        inventory.setItem(jobEquipmentSlot(equipmentSlot), stack);
        return 1;
    }

    private static void collectCandidate(
            Villager villager,
            EquipmentSlot slot,
            List<StowCandidate> candidates) {
        ItemStack stack = villager.getItemBySlot(slot);
        if (isWeaponOrShield(stack)) {
            candidates.add(new StowCandidate(slot, stack.copy()));
        }
    }

    private static List<Integer> emptyStorageSlots(HiredJobInventory inventory, int count) {
        List<Integer> slots = new ArrayList<>(count);
        collectEmptySlots(inventory, HiredJobInventory.HOTBAR_START, HiredJobInventory.FILTER_SLOT, count, slots);
        collectEmptySlots(inventory, HiredJobInventory.MAIN_GRID_START, HiredJobInventory.HOTBAR_START, count, slots);
        return slots;
    }

    private static void collectEmptySlots(
            HiredJobInventory inventory,
            int start,
            int end,
            int count,
            List<Integer> slots) {
        for (int slot = start; slot < end && slots.size() < count; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                slots.add(slot);
            }
        }
    }

    private static int findStowedSlot(
            HiredJobInventory inventory,
            UUID villagerId,
            EquipmentSlot equipmentSlot) {
        for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
            if (equipmentSlot == stowedSlot(inventory.getItem(slot), villagerId)) {
                return slot;
            }
        }
        return -1;
    }

    private static ItemStack markStowed(ItemStack stack, UUID villagerId, EquipmentSlot equipmentSlot) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag stowed = new CompoundTag();
            stowed.putUUID(VILLAGER_TAG, villagerId);
            stowed.putString(SLOT_TAG, equipmentSlot == EquipmentSlot.MAINHAND ? MAINHAND : OFFHAND);
            tag.put(STOWED_TAG, stowed);
        });
        return stack;
    }

    private static EquipmentSlot stowedSlot(ItemStack stack, UUID villagerId) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag stowed = data.copyTag().getCompound(STOWED_TAG);
        if (!stowed.hasUUID(VILLAGER_TAG) || !villagerId.equals(stowed.getUUID(VILLAGER_TAG))) {
            return null;
        }
        return MAINHAND.equals(stowed.getString(SLOT_TAG))
                ? EquipmentSlot.MAINHAND
                : OFFHAND.equals(stowed.getString(SLOT_TAG)) ? EquipmentSlot.OFFHAND : null;
    }

    private static ItemStack removeStowedMarker(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.remove(STOWED_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return stack;
    }

    private static boolean isWeaponOrShield(ItemStack stack) {
        return stack.is(Items.SHIELD) || VillagerRetaliationVillagerWeapons.isUsableWeapon(stack);
    }

    private static int jobEquipmentSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND
                ? HiredJobInventory.MAINHAND_SLOT
                : HiredJobInventory.OFFHAND_SLOT;
    }

    private static boolean canManage(Villager villager) {
        return villager != null && villager.isAlive() && HiredJobInventory.isJobInventoryAvailable(villager);
    }

    private static void prepareEquipment(Villager villager) {
        VillagerDefensiveLoadoutService.prepareForInventoryAccess(villager);
        VillagerRetaliationHandler.releaseTemporaryWeaponForInventory(villager);
        VillagerRetaliationVillagerWeapons.prepareTrackedPickupForInventory(villager);
        if (VillagerInventoryAccess.hasBorrowedCombatWeapon(villager)) {
            VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        }
    }

    private record StowCandidate(EquipmentSlot slot, ItemStack stack) {
    }
}
