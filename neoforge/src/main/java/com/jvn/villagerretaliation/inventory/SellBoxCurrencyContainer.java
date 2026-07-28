package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A courier-only view of a sell box. It exposes minted whole currency units and never exposes the
 * pending sale stack through the assigned-input-storage APIs.
 */
final class SellBoxCurrencyContainer implements Container {
    private final SellBoxBlockEntity sellBox;

    SellBoxCurrencyContainer(SellBoxBlockEntity sellBox) {
        this.sellBox = sellBox;
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getItem(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        checkSlot(slot);
        return this.sellBox.extractCurrencyForCourier(Integer.MAX_VALUE, true);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        checkSlot(slot);
        return this.sellBox.extractCurrencyForCourier(amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        checkSlot(slot);
        return this.sellBox.extractCurrencyForCourier(Integer.MAX_VALUE, false);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        checkSlot(slot);
        ItemStack visible = getItem(slot);
        int desired = isPrimaryCurrency(stack) ? stack.getCount() : 0;
        int difference = desired - visible.getCount();
        if (difference > 0) {
            this.sellBox.restoreCurrency(stack.copyWithCount(difference));
        } else if (difference < 0) {
            this.sellBox.extractCurrencyForCourier(-difference, false);
        }
    }

    @Override
    public void setChanged() {
        this.sellBox.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return this.sellBox.stillValid(player);
    }

    @Override
    public void clearContent() {
        this.sellBox.extractCurrencyForCourier(Integer.MAX_VALUE, false);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        checkSlot(slot);
        return isPrimaryCurrency(stack);
    }

    ItemStack restore(ItemStack stack) {
        return isPrimaryCurrency(stack) ? this.sellBox.restoreCurrency(stack) : stack;
    }

    private boolean isPrimaryCurrency(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && this.sellBox.getLevel() != null
                && stack.is(VillagerCurrencyResources.primaryItem(this.sellBox.getLevel().getServer()));
    }

    private static void checkSlot(int slot) {
        if (slot != 0) {
            throw new IllegalArgumentException("Sell box currency view has no slot " + slot);
        }
    }
}
