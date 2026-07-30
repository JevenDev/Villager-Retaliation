package com.jvn.villagerretaliation.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Lets assigned-storage code use a NeoForge item handler without assuming a vanilla container. */
final class ItemHandlerContainerAdapter implements Container {
    private final IItemHandler handler;

    ItemHandlerContainerAdapter(IItemHandler handler) {
        this.handler = handler;
    }

    ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack remainder = stack == null ? ItemStack.EMPTY : stack.copy();
        for (int slot = 0; slot < this.handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = this.handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }

    ItemStack extract(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= this.handler.getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        return this.handler.extractItem(slot, amount, simulate);
    }

    int insertionCapacity(ItemStack stack, int maximum) {
        if (stack == null || stack.isEmpty() || maximum <= 0) {
            return 0;
        }
        int capacity = 0;
        for (int slot = 0; slot < this.handler.getSlots() && capacity < maximum; slot++) {
            int requested = Math.min(maximum - capacity, stack.getMaxStackSize());
            ItemStack offered = stack.copyWithCount(requested);
            ItemStack remainder = this.handler.insertItem(slot, offered, true);
            capacity += Math.clamp(requested - remainder.getCount(), 0, requested);
        }
        return capacity;
    }

    @Override
    public int getContainerSize() {
        return this.handler.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < this.handler.getSlots(); slot++) {
            if (!this.handler.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= this.handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return this.handler.getStackInSlot(slot).copy();
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return extract(slot, amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return extract(slot, Integer.MAX_VALUE, false);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.handler.getSlots()) {
            return;
        }
        ItemStack safeStack = stack == null ? ItemStack.EMPTY : stack.copy();
        if (this.handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, safeStack);
            return;
        }

        ItemStack current = this.handler.getStackInSlot(slot);
        if (safeStack.isEmpty()) {
            this.handler.extractItem(slot, Integer.MAX_VALUE, false);
        } else if (ItemStack.isSameItemSameComponents(current, safeStack)) {
            int delta = safeStack.getCount() - current.getCount();
            if (delta > 0) {
                this.handler.insertItem(slot, safeStack.copyWithCount(delta), false);
            } else if (delta < 0) {
                this.handler.extractItem(slot, -delta, false);
            }
        }
    }

    @Override
    public void setChanged() {
        // Capability implementations own their change notification.
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < this.handler.getSlots(); slot++) {
            this.handler.extractItem(slot, Integer.MAX_VALUE, false);
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.handler.getSlots() || stack == null || stack.isEmpty()) {
            return false;
        }
        ItemStack one = stack.copyWithCount(1);
        return this.handler.insertItem(slot, one, true).getCount() < one.getCount();
    }

    @Override
    public int getMaxStackSize() {
        int maximum = 1;
        for (int slot = 0; slot < this.handler.getSlots(); slot++) {
            maximum = Math.max(maximum, this.handler.getSlotLimit(slot));
        }
        return maximum;
    }
}
