package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class VillagerInventoryContainer implements Container {
    static final int ARMOR_SLOT_COUNT = 4;
    static final int INVENTORY_SLOT_COUNT = 36;
    static final int HELD_SLOT = ARMOR_SLOT_COUNT + INVENTORY_SLOT_COUNT;
    static final int OFFHAND_SLOT = HELD_SLOT + 1;
    static final int SLOT_COUNT = OFFHAND_SLOT + 1;

    private static final String EXTRA_INVENTORY_TAG = "VillagerRetaliationExtraInventory";
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Villager villager;
    private final NonNullList<ItemStack> extraInventory;

    VillagerInventoryContainer(Villager villager) {
        this.villager = villager;
        this.extraInventory = NonNullList.withSize(Math.max(0, INVENTORY_SLOT_COUNT - vanillaInventorySlots()), ItemStack.EMPTY);
        loadExtraInventory();
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
            VillagerRetaliationVillagerWeapons.clearTrackedPickup(this.villager);
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
        }
    }

    @Override
    public void setChanged() {
        this.villager.getInventory().setChanged();
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

    static void dropExtraInventory(Villager villager) {
        NonNullList<ItemStack> extraInventory = loadExtraInventory(villager, Math.max(0, INVENTORY_SLOT_COUNT - vanillaInventorySlots(villager)));
        for (ItemStack stack : extraInventory) {
            if (!stack.isEmpty()) {
                villager.spawnAtLocation(stack.copy());
            }
        }
        villager.getPersistentData().remove(EXTRA_INVENTORY_TAG);
    }

    private ItemStack getInventoryItem(int inventorySlot) {
        int vanillaSlots = vanillaInventorySlots();
        if (inventorySlot < vanillaSlots) {
            return this.villager.getInventory().getItem(inventorySlot);
        }

        int extraSlot = inventorySlot - vanillaSlots;
        return extraSlot >= 0 && extraSlot < this.extraInventory.size() ? this.extraInventory.get(extraSlot) : ItemStack.EMPTY;
    }

    private void setInventoryItem(int inventorySlot, ItemStack stack) {
        int vanillaSlots = vanillaInventorySlots();
        if (inventorySlot < vanillaSlots) {
            this.villager.getInventory().setItem(inventorySlot, stack);
            return;
        }

        int extraSlot = inventorySlot - vanillaSlots;
        if (extraSlot >= 0 && extraSlot < this.extraInventory.size()) {
            this.extraInventory.set(extraSlot, stack);
        }
    }

    private void setEquipment(EquipmentSlot slot, ItemStack stack) {
        this.villager.setItemSlot(slot, stack);
        if (!stack.isEmpty()) {
            this.villager.setGuaranteedDrop(slot);
        }
        setChanged();
    }

    private int vanillaInventorySlots() {
        return vanillaInventorySlots(this.villager);
    }

    private static int vanillaInventorySlots(Villager villager) {
        return Math.min(INVENTORY_SLOT_COUNT, villager.getInventory().getContainerSize());
    }

    private void loadExtraInventory() {
        NonNullList<ItemStack> loaded = loadExtraInventory(this.villager, this.extraInventory.size());
        for (int slot = 0; slot < Math.min(this.extraInventory.size(), loaded.size()); slot++) {
            this.extraInventory.set(slot, loaded.get(slot));
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

    private void saveExtraInventory() {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), this.extraInventory, true, this.villager.level().registryAccess());
        this.villager.getPersistentData().put(EXTRA_INVENTORY_TAG, tag);
    }
}
