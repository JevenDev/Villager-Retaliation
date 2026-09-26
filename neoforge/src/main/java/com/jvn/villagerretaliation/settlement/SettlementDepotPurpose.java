package com.jvn.villagerretaliation.settlement;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public enum SettlementDepotPurpose {
    GENERAL,
    FOOD,
    MATERIALS,
    OUTPUT;

    public boolean accepts(ItemStack stack) {
        return stack.isEmpty() || this != FOOD || stack.has(DataComponents.FOOD);
    }

    public boolean providesSupplies() {
        return this != OUTPUT;
    }

    public boolean acceptsOutputs() {
        return this != MATERIALS;
    }
}
