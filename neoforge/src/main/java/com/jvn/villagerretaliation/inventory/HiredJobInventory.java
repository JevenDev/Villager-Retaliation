package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class HiredJobInventory implements Container {
    public static final int SLOT_COUNT = 27;
    private static final String TAG = "VillagerRetaliationJobInventory";
    private static final String SLOT_TYPES_TAG = "SlotTypes";
    private static final String SLOT_TAG = "Slot";
    private static final String TYPE_TAG = "Type";

    private final Villager villager;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final HiredJobInventorySlotType[] slotTypes = new HiredJobInventorySlotType[SLOT_COUNT];

    public HiredJobInventory(Villager villager) {
        this.villager = villager;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.slotTypes[slot] = defaultType(slot);
        }
        load();
    }

    public static boolean isJobInventoryAvailable(Villager villager) {
        return villager != null && !villager.isBaby();
    }

    public static HiredJobInventory getJobInventory(Villager villager) {
        return new HiredJobInventory(villager);
    }

    public static boolean isProtectedVillagerProperty(ItemStack stack) {
        return ProtectedVillagerProperty.isProtected(stack);
    }

    public static boolean canHirerRemoveFromJobInventory(ItemStack stack) {
        return !isProtectedVillagerProperty(stack);
    }

    public static ItemStack markAsProtectedVillagerProperty(ItemStack stack, Villager owner, String reason) {
        return ProtectedVillagerProperty.mark(stack, owner, reason);
    }

    public static void dropAll(Villager villager, LivingDropsEvent event) {
        HiredJobInventory inventory = new HiredJobInventory(villager);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops.addDrop(event, stack.copy());
            }
        }
        inventory.clearContent();
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return isValidSlot(slot) ? this.items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (!isValidSlot(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (stack.isEmpty() || !canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (!isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = this.items.get(slot);
        if (!canHirerRemoveFromJobInventory(stack)) {
            return ItemStack.EMPTY;
        }
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!isValidSlot(slot)) {
            return;
        }
        this.items.set(slot, stack);
        if (!stack.isEmpty() && ProtectedVillagerProperty.isProtected(stack)) {
            this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
        } else if (this.slotTypes[slot] == HiredJobInventorySlotType.PROTECTED_PROPERTY) {
            this.slotTypes[slot] = defaultType(slot);
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        save();
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
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            this.items.set(slot, ItemStack.EMPTY);
            this.slotTypes[slot] = defaultType(slot);
        }
        setChanged();
    }

    public HiredJobInventorySlotType slotType(int slot) {
        if (!isValidSlot(slot)) {
            return HiredJobInventorySlotType.NORMAL;
        }
        ItemStack stack = this.items.get(slot);
        return ProtectedVillagerProperty.isProtected(stack) ? HiredJobInventorySlotType.PROTECTED_PROPERTY : this.slotTypes[slot];
    }

    public boolean isOutputSlot(int slot) {
        return slotType(slot) == HiredJobInventorySlotType.OUTPUT;
    }

    public boolean hasOutputSpace() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (isOutputSlot(slot) && this.items.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public List<OutputStack> collectOutputItems() {
        List<OutputStack> output = new ArrayList<>();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = this.items.get(slot);
            if (stack.isEmpty()
                    || !isOutputSlot(slot)
                    || ProtectedVillagerProperty.isProtected(stack)
                    || slotType(slot) == HiredJobInventorySlotType.GEAR
                    || slotType(slot) == HiredJobInventorySlotType.SUPPLY) {
                continue;
            }
            output.add(new OutputStack(slot, stack.copy()));
        }
        return output;
    }

    public boolean depositOutputToAssignedStorage() {
        if (this.collectOutputItems().isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (OutputStack output : collectOutputItems()) {
            ItemStack remainder = AssignedStorageService.depositStack(this.villager, output.stack());
            int moved = output.stack().getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack current = this.items.get(output.slot());
            current.shrink(moved);
            if (current.isEmpty()) {
                this.items.set(output.slot(), ItemStack.EMPTY);
            }
            changed = true;
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private void load() {
        CompoundTag tag = this.villager.getPersistentData().getCompound(TAG);
        if (tag.isEmpty()) {
            return;
        }
        ContainerHelper.loadAllItems(tag, this.items, this.villager.level().registryAccess());
        ListTag slotTypesTag = tag.getList(SLOT_TYPES_TAG, Tag.TAG_COMPOUND);
        for (Tag rawType : slotTypesTag) {
            if (!(rawType instanceof CompoundTag typeTag)) {
                continue;
            }
            int slot = typeTag.getInt(SLOT_TAG);
            if (isValidSlot(slot)) {
                this.slotTypes[slot] = HiredJobInventorySlotType.byId(typeTag.getString(TYPE_TAG));
            }
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (ProtectedVillagerProperty.isProtected(this.items.get(slot))) {
                this.slotTypes[slot] = HiredJobInventorySlotType.PROTECTED_PROPERTY;
            }
        }
    }

    private void save() {
        CompoundTag tag = ContainerHelper.saveAllItems(new CompoundTag(), this.items, true, this.villager.level().registryAccess());
        ListTag slotTypesTag = new ListTag();
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            HiredJobInventorySlotType type = slotType(slot);
            if (type == defaultType(slot) && type != HiredJobInventorySlotType.PROTECTED_PROPERTY) {
                continue;
            }
            CompoundTag typeTag = new CompoundTag();
            typeTag.putInt(SLOT_TAG, slot);
            typeTag.putString(TYPE_TAG, type.id());
            slotTypesTag.add(typeTag);
        }
        tag.put(SLOT_TYPES_TAG, slotTypesTag);
        this.villager.getPersistentData().put(TAG, tag);
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private static HiredJobInventorySlotType defaultType(int slot) {
        if (slot < 9) {
            return HiredJobInventorySlotType.GEAR;
        }
        if (slot < 18) {
            return HiredJobInventorySlotType.SUPPLY;
        }
        return HiredJobInventorySlotType.OUTPUT;
    }

    public record OutputStack(int slot, ItemStack stack) {
    }
}
