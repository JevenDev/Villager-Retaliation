package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

final class VillagerInventoryContainer implements Container {
    static final int ARMOR_SLOT_COUNT = 4;
    static final int INVENTORY_SLOT_COUNT = 27;
    private static final int LEGACY_INVENTORY_SLOT_COUNT = 36;
    static final int HELD_SLOT = ARMOR_SLOT_COUNT + INVENTORY_SLOT_COUNT;
    static final int OFFHAND_SLOT = HELD_SLOT + 1;
    static final int SLOT_COUNT = OFFHAND_SLOT + 1;

    private static final String EXTRA_INVENTORY_TAG = "VillagerRetaliationExtraInventory";
    private static final String BORROWED_COMBAT_WEAPON_TAG = "VillagerRetaliationBorrowedCombatWeapon";
    private static final String BORROWED_COMBAT_WEAPON_SLOT_TAG = "Slot";
    private static final String BORROWED_COMBAT_WEAPON_STACK_TAG = "Stack";
    private static final Map<UUID, Integer> OPEN_INVENTORIES = new HashMap<>();
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Villager villager;
    private final NonNullList<ItemStack> inventory;

    VillagerInventoryContainer(Villager villager) {
        this.villager = villager;
        this.inventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        VillagerRetaliationHandler.releaseTemporaryWeaponForInventory(villager);
        VillagerRetaliationVillagerWeapons.prepareTrackedPickupForInventory(villager);
        loadInventory();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (isArmorSlot(slot)) {
            return this.villager.getItemBySlot(ARMOR_SLOTS[slot]);
        }
        if (slot == HELD_SLOT) {
            return this.villager.getMainHandItem();
        }
        if (slot == OFFHAND_SLOT) {
            return this.villager.getOffhandItem();
        }
        int inventorySlot = slot - ARMOR_SLOT_COUNT;
        if (isInventorySlot(inventorySlot)) {
            return getInventoryItem(inventorySlot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed;
        if (stack.getCount() <= amount) {
            removed = stack.copy();
            setItem(slot, ItemStack.EMPTY);
        } else {
            removed = stack.split(amount);
            setItem(slot, stack);
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty()) {
            setItem(slot, ItemStack.EMPTY);
        }
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (isArmorSlot(slot)) {
            setEquipment(ARMOR_SLOTS[slot], stack);
            return;
        }
        if (slot == HELD_SLOT) {
            setEquipment(EquipmentSlot.MAINHAND, stack);
            return;
        }
        if (slot == OFFHAND_SLOT) {
            setEquipment(EquipmentSlot.OFFHAND, stack);
            return;
        }

        int inventorySlot = slot - ARMOR_SLOT_COUNT;
        if (isInventorySlot(inventorySlot)) {
            setInventoryItem(inventorySlot, stack);
            setChanged();
        }
    }

    @Override
    public void setChanged() {
        saveInventory();
        saveExtraInventory();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.villager.isAlive()
                && player.isAlive()
                && !player.isSpectator()
                && player.distanceToSqr(this.villager) <= 64.0D;
    }

    @Override
    public void startOpen(Player player) {
        OPEN_INVENTORIES.merge(this.villager.getUUID(), 1, Integer::sum);
    }

    @Override
    public void stopOpen(Player player) {
        setChanged();
        OPEN_INVENTORIES.computeIfPresent(this.villager.getUUID(), (uuid, openCount) -> openCount <= 1 ? null : openCount - 1);
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            setItem(slot, ItemStack.EMPTY);
        }
        setChanged();
    }

    static boolean isArmorSlot(int slot) {
        return slot >= 0 && slot < ARMOR_SLOT_COUNT;
    }

    static boolean isVillagerInventorySlot(int slot) {
        return slot >= ARMOR_SLOT_COUNT && slot < HELD_SLOT;
    }

    static boolean isInventorySlot(int inventorySlot) {
        return inventorySlot >= 0 && inventorySlot < INVENTORY_SLOT_COUNT;
    }

    static EquipmentSlot armorEquipmentSlot(int slot) {
        return ARMOR_SLOTS[slot];
    }

    static boolean hasOpenInventory(Villager villager) {
        return OPEN_INVENTORIES.getOrDefault(villager.getUUID(), 0) > 0;
    }

    static void dropExtraInventory(Villager villager) {
        NonNullList<ItemStack> extraInventory = loadExtraInventory(villager, Math.max(0, LEGACY_INVENTORY_SLOT_COUNT - vanillaInventorySlots(villager)));
        for (ItemStack stack : extraInventory) {
            if (!stack.isEmpty()) {
                villager.spawnAtLocation(stack.copy());
            }
        }
        villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
    }

    static ItemStack addItem(Villager villager, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        ItemStack remainder = stack.copy();
        mergeIntoExistingStacks(inventory, remainder);
        fillEmptySlots(inventory, remainder);
        saveFullInventory(villager, inventory);
        return remainder;
    }

    static boolean hasUsableWeapon(Villager villager) {
        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            if (VillagerRetaliationVillagerWeapons.isUsableWeapon(stack)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasBorrowedCombatWeapon(Villager villager) {
        return villager.getPersistentData().contains(BORROWED_COMBAT_WEAPON_TAG, CompoundTag.TAG_COMPOUND);
    }

    static boolean maintainBorrowedCombatWeapon(Villager villager) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        if (borrowedWeapon == null) {
            return false;
        }

        ItemStack mainHand = villager.getMainHandItem();
        if (ItemStack.isSameItem(mainHand, borrowedWeapon.stack())) {
            persistBorrowedCombatWeapon(villager, borrowedWeapon.slot(), mainHand.copy());
            return true;
        }

        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, borrowedWeapon.stack(), 0.0F);
        return true;
    }

    static boolean tryBorrowCombatWeapon(Villager villager) {
        if (hasBorrowedCombatWeapon(villager)) {
            return maintainBorrowedCombatWeapon(villager);
        }
        if (!villager.getMainHandItem().isEmpty()
                || VillagerRetaliationVillagerEquipment.isPlayerManagedMainHand(villager)) {
            return false;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        int selectedSlot = selectBestWeaponSlot(inventory);
        if (selectedSlot < 0) {
            return false;
        }

        ItemStack borrowedStack = inventory.get(selectedSlot).copy();
        inventory.set(selectedSlot, ItemStack.EMPTY);
        saveFullInventory(villager, inventory);
        persistBorrowedCombatWeapon(villager, selectedSlot, borrowedStack);
        VillagerRetaliationVillagerEquipment.setTemporaryMainHand(villager, borrowedStack, 0.0F);
        return true;
    }

    static void returnBorrowedCombatWeapon(Villager villager) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        if (borrowedWeapon == null) {
            return;
        }

        ItemStack mainHand = villager.getMainHandItem();
        ItemStack returnedStack = mainHand;
        if (returnedStack.isEmpty() || !ItemStack.isSameItem(returnedStack, borrowedWeapon.stack())) {
            if (!mainHand.isEmpty()) {
                addItem(villager, mainHand.copy());
            }
            returnedStack = borrowedWeapon.stack();
        } else {
            returnedStack = returnedStack.copy();
        }

        villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
        VillagerRetaliationVillagerEquipment.restoreMainHand(villager, ItemStack.EMPTY);
        addItemToPreferredSlot(villager, returnedStack, borrowedWeapon.slot());
    }

    static void clearBorrowedCombatWeapon(Villager villager) {
        villager.getPersistentData().remove(BORROWED_COMBAT_WEAPON_TAG);
    }

    static void dropAllInventoryAndEquipment(Villager villager, LivingDropsEvent event) {
        BorrowedCombatWeapon borrowedWeapon = borrowedCombatWeapon(villager);
        boolean borrowedWeaponInMainHand = borrowedWeapon != null
                && ItemStack.isSameItem(villager.getMainHandItem(), borrowedWeapon.stack());
        dropEquipment(villager, event);
        if (borrowedWeapon != null && !borrowedWeaponInMainHand) {
            com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, borrowedWeapon.stack().copy());
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
        dropLegacyOverflowInventory(villager, event);

        clearFullInventory(villager);
        clearBorrowedCombatWeapon(villager);
    }

    private ItemStack getInventoryItem(int inventorySlot) {
        return this.inventory.get(inventorySlot);
    }

    private void setInventoryItem(int inventorySlot, ItemStack stack) {
        this.inventory.set(inventorySlot, stack);
    }

    private void setEquipment(EquipmentSlot slot, ItemStack stack) {
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(this.villager, slot, stack);
        setChanged();
    }

    private int vanillaInventorySlots() {
        return vanillaInventorySlots(this.villager);
    }

    private static int vanillaInventorySlots(Villager villager) {
        return Math.min(INVENTORY_SLOT_COUNT, villager.getInventory().getContainerSize());
    }

    private void loadInventory() {
        int vanillaSlots = vanillaInventorySlots();
        for (int slot = 0; slot < vanillaSlots; slot++) {
            this.inventory.set(slot, this.villager.getInventory().getItem(slot).copy());
        }

        int currentExtraSlots = Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots);
        int legacyExtraSlots = Math.max(currentExtraSlots, LEGACY_INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> loaded = loadExtraInventory(this.villager, legacyExtraSlots);
        for (int slot = 0; slot < Math.min(loaded.size(), currentExtraSlots); slot++) {
            this.inventory.set(vanillaSlots + slot, loaded.get(slot));
        }
        for (int slot = currentExtraSlots; slot < loaded.size(); slot++) {
            ItemStack overflow = loaded.get(slot);
            if (!overflow.isEmpty()) {
                this.villager.spawnAtLocation(overflow.copy());
            }
        }
    }

    private static NonNullList<ItemStack> loadExtraInventory(Villager villager, int size) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(size, ItemStack.EMPTY);
        CompoundTag tag = villager.getPersistentData().getCompound(EXTRA_INVENTORY_TAG);
        if (!tag.isEmpty()) {
            ContainerHelper.loadAllItems(tag, inventory, villager.level().registryAccess());
        }
        return inventory;
    }

    private static NonNullList<ItemStack> loadFullInventory(Villager villager) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        int vanillaSlots = vanillaInventorySlots(villager);
        for (int slot = 0; slot < vanillaSlots; slot++) {
            inventory.set(slot, villager.getInventory().getItem(slot).copy());
        }

        NonNullList<ItemStack> extraInventory = loadExtraInventory(villager, Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots));
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            inventory.set(vanillaSlots + slot, extraInventory.get(slot).copy());
        }
        return inventory;
    }

    private static void saveFullInventory(Villager villager, NonNullList<ItemStack> inventory) {
        int vanillaSlots = vanillaInventorySlots(villager);
        for (int slot = 0; slot < vanillaSlots; slot++) {
            villager.getInventory().setItem(slot, inventory.get(slot).copy());
        }
        villager.getInventory().setChanged();

        NonNullList<ItemStack> extraInventory = NonNullList.withSize(Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots), ItemStack.EMPTY);
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            extraInventory.set(slot, inventory.get(vanillaSlots + slot).copy());
        }
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), extraInventory, true, villager.level().registryAccess());
        villager.getPersistentData().put(EXTRA_INVENTORY_TAG, tag);
    }

    private static void clearFullInventory(Villager villager) {
        NonNullList<ItemStack> emptyInventory = NonNullList.withSize(INVENTORY_SLOT_COUNT, ItemStack.EMPTY);
        saveFullInventory(villager, emptyInventory);
        villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
    }

    private static void addItemToPreferredSlot(Villager villager, ItemStack stack, int preferredSlot) {
        if (stack.isEmpty()) {
            return;
        }

        NonNullList<ItemStack> inventory = loadFullInventory(villager);
        ItemStack remainder = stack.copy();
        if (preferredSlot >= 0 && preferredSlot < inventory.size()) {
            ItemStack preferredStack = inventory.get(preferredSlot);
            if (preferredStack.isEmpty()) {
                inventory.set(preferredSlot, remainder.copy());
                remainder = ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(preferredStack, remainder)) {
                int moveCount = Math.min(remainder.getCount(), preferredStack.getMaxStackSize() - preferredStack.getCount());
                if (moveCount > 0) {
                    preferredStack.grow(moveCount);
                    remainder.shrink(moveCount);
                }
            }
        }
        mergeIntoExistingStacks(inventory, remainder);
        fillEmptySlots(inventory, remainder);
        saveFullInventory(villager, inventory);
        if (!remainder.isEmpty()) {
            villager.spawnAtLocation(remainder);
        }
    }

    private static void dropLegacyOverflowInventory(Villager villager, LivingDropsEvent event) {
        int vanillaSlots = vanillaInventorySlots(villager);
        int currentExtraSlots = Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots);
        int legacyExtraSlots = Math.max(currentExtraSlots, LEGACY_INVENTORY_SLOT_COUNT - vanillaSlots);
        NonNullList<ItemStack> legacyExtraInventory = loadExtraInventory(villager, legacyExtraSlots);
        for (int slot = currentExtraSlots; slot < legacyExtraInventory.size(); slot++) {
            ItemStack stack = legacyExtraInventory.get(slot);
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
    }

    private static void mergeIntoExistingStacks(NonNullList<ItemStack> inventory, ItemStack remainder) {
        for (ItemStack existingStack : inventory) {
            if (remainder.isEmpty()) {
                return;
            }
            if (existingStack.isEmpty() || !ItemStack.isSameItemSameComponents(existingStack, remainder)) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), existingStack.getMaxStackSize() - existingStack.getCount());
            if (moveCount > 0) {
                existingStack.grow(moveCount);
                remainder.shrink(moveCount);
            }
        }
    }

    private static void fillEmptySlots(NonNullList<ItemStack> inventory, ItemStack remainder) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (remainder.isEmpty()) {
                return;
            }
            if (!inventory.get(slot).isEmpty()) {
                continue;
            }

            int moveCount = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            inventory.set(slot, remainder.copyWithCount(moveCount));
            remainder.shrink(moveCount);
        }
    }

    private static int selectBestWeaponSlot(NonNullList<ItemStack> inventory) {
        int bestSlot = -1;
        ItemStack bestWeapon = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack candidate = inventory.get(slot);
            if (VillagerRetaliationVillagerWeapons.isBetterWeaponChoice(candidate, bestWeapon)) {
                bestSlot = slot;
                bestWeapon = candidate;
            }
        }
        return bestSlot;
    }

    private static void persistBorrowedCombatWeapon(Villager villager, int slot, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(BORROWED_COMBAT_WEAPON_SLOT_TAG, slot);
        tag.put(BORROWED_COMBAT_WEAPON_STACK_TAG, stack.saveOptional(villager.level().registryAccess()));
        villager.getPersistentData().put(BORROWED_COMBAT_WEAPON_TAG, tag);
    }

    private static BorrowedCombatWeapon borrowedCombatWeapon(Villager villager) {
        CompoundTag tag = villager.getPersistentData().getCompound(BORROWED_COMBAT_WEAPON_TAG);
        if (tag.isEmpty() || !tag.contains(BORROWED_COMBAT_WEAPON_STACK_TAG, CompoundTag.TAG_COMPOUND)) {
            clearBorrowedCombatWeapon(villager);
            return null;
        }

        ItemStack stack = ItemStack.parseOptional(
                villager.level().registryAccess(),
                tag.getCompound(BORROWED_COMBAT_WEAPON_STACK_TAG)
        );
        if (stack.isEmpty()) {
            clearBorrowedCombatWeapon(villager);
            return null;
        }
        return new BorrowedCombatWeapon(tag.getInt(BORROWED_COMBAT_WEAPON_SLOT_TAG), stack);
    }

    private static void dropEquipment(Villager villager, LivingDropsEvent event) {
        dropEquipmentSlot(villager, event, EquipmentSlot.HEAD);
        dropEquipmentSlot(villager, event, EquipmentSlot.CHEST);
        dropEquipmentSlot(villager, event, EquipmentSlot.LEGS);
        dropEquipmentSlot(villager, event, EquipmentSlot.FEET);
        dropEquipmentSlot(villager, event, EquipmentSlot.MAINHAND);
        dropEquipmentSlot(villager, event, EquipmentSlot.OFFHAND);
    }

    private static void dropEquipmentSlot(Villager villager, LivingDropsEvent event, EquipmentSlot slot) {
        ItemStack stack = villager.getItemBySlot(slot);
        if (stack.isEmpty()) {
            return;
        }

        removeOneMatchingDrop(event, stack);
        com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, ItemStack.EMPTY);
    }

    private static void removeOneMatchingDrop(LivingDropsEvent event, ItemStack stack) {
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            if (ItemStack.isSameItemSameComponents(drops.next().getItem(), stack)) {
                drops.remove();
                return;
            }
        }
    }

    private record BorrowedCombatWeapon(int slot, ItemStack stack) {
    }

    private void saveExtraInventory() {
        int vanillaSlots = vanillaInventorySlots();
        NonNullList<ItemStack> extraInventory = NonNullList.withSize(Math.max(0, INVENTORY_SLOT_COUNT - vanillaSlots), ItemStack.EMPTY);
        for (int slot = 0; slot < extraInventory.size(); slot++) {
            extraInventory.set(slot, this.inventory.get(vanillaSlots + slot).copy());
        }

        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), extraInventory, true, this.villager.level().registryAccess());
        this.villager.getPersistentData().put(EXTRA_INVENTORY_TAG, tag);
    }

    private void saveInventory() {
        int vanillaSlots = vanillaInventorySlots();
        for (int slot = 0; slot < vanillaSlots; slot++) {
            this.villager.getInventory().setItem(slot, this.inventory.get(slot).copy());
        }
        this.villager.getInventory().setChanged();
    }
}
