package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.VillagerFamilyAccessor;
import java.util.HashSet;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class SettlementFamilyInventory {
    private static final String VERSION_TAG = "VillagerRetaliationSettlementFamilyVersion";
    private final long version;
    private final int food;
    private final CompoundTag inventory;

    private SettlementFamilyInventory(long version, int food, CompoundTag inventory) {
        if (version <= 0L) throw new IllegalArgumentException("Family inventory has no revision");
        this.version = version;
        this.food = food;
        this.inventory = inventory.copy();
    }

    static SettlementFamilyInventory capture(Villager villager, SettlementFamilyInventory previous) {
        var items = NonNullList.withSize(villager.getInventory().getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, villager.getInventory().getItem(slot).copy());
        var inventory = ContainerHelper.saveAllItems(new CompoundTag(), items, villager.registryAccess());
        int food = ((VillagerFamilyAccessor) villager).villagerretaliation$getFamilyFood();
        long version = Math.max(previous == null ? 0L : previous.version, villager.getPersistentData().getLong(VERSION_TAG));
        if (previous == null || previous.food != food || !previous.inventory.equals(inventory)) version = Math.incrementExact(version);
        villager.getPersistentData().putLong(VERSION_TAG, version);
        return new SettlementFamilyInventory(version, food, inventory);
    }

    boolean restore(Villager villager) {
        if (villager.getPersistentData().getLong(VERSION_TAG) >= this.version) return false;
        copyTo(villager);
        return true;
    }

    void copyTo(Villager villager) {
        var items = NonNullList.withSize(villager.getInventory().getContainerSize(), ItemStack.EMPTY);
        for (Tag raw : this.inventory.getList("Items", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            int slot = entry.getByte("Slot") & 255;
            if (slot >= items.size()) throw new IllegalArgumentException("Family inventory exceeds the native container");
            items.set(slot, ItemStack.parse(villager.registryAccess(), entry)
                    .orElseThrow(() -> new IllegalArgumentException("Family inventory contains an invalid item")));
        }
        for (int slot = 0; slot < items.size(); slot++) villager.getInventory().setItem(slot, items.get(slot));
        ((VillagerFamilyAccessor) villager).villagerretaliation$setFamilyFood(this.food);
        villager.getPersistentData().putLong(VERSION_TAG, this.version);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putLong("Version", this.version);
        tag.putInt("Food", this.food);
        tag.put("Inventory", this.inventory.copy());
        return tag;
    }

    static SettlementFamilyInventory load(CompoundTag tag) {
        if (!tag.contains("Food", Tag.TAG_INT) || !tag.contains("Inventory", Tag.TAG_COMPOUND)
                || !tag.getCompound("Inventory").contains("Items", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Family inventory is incomplete");
        }
        var inventory = tag.getCompound("Inventory");
        var slots = new HashSet<Integer>();
        var items = (net.minecraft.nbt.ListTag) inventory.get("Items");
        if (!items.isEmpty() && items.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Family inventory entries must be compounds");
        }
        for (Tag raw : items) {
            var entry = (CompoundTag) raw;
            int slot = entry.getByte("Slot") & 255;
            if (!entry.contains("Slot", Tag.TAG_BYTE) || slot >= 8 || !slots.add(slot)) {
                throw new IllegalArgumentException("Family inventory has an invalid or duplicate slot");
            }
        }
        return new SettlementFamilyInventory(tag.getLong("Version"), tag.getInt("Food"), inventory);
    }
}
