package com.jvn.villagerretaliation.combat;

import net.minecraft.world.item.ItemStack;

public record PacifyPaymentOffer(
        ItemStack stack,
        int count,
        String itemName,
        String pluralItemName) {
    public String itemNameForCount() {
        return this.count == 1 ? this.itemName : this.pluralItemName;
    }
}
