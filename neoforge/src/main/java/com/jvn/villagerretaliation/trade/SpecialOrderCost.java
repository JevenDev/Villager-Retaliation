package com.jvn.villagerretaliation.trade;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public record SpecialOrderCost(Item item, int count) {
    public static final SpecialOrderCost EMPTY = new SpecialOrderCost(Items.AIR, 0);

    public SpecialOrderCost {
        item = item == null ? Items.AIR : item;
        count = Math.clamp(count, 0, 64);
    }

    public boolean isEmpty() {
        return this.item == Items.AIR || this.count <= 0;
    }
}
