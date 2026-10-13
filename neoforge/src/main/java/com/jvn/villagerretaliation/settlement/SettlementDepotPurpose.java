package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public enum SettlementDepotPurpose {
    GENERAL,
    FOOD,
    MATERIALS,
    OUTPUT,
    BUILDER_ONLY;

    /** Couriers may replenish and move construction stock; other jobs cannot consume it. */
    public boolean permitsRole(HiredVillagerRole role) {
        return this != BUILDER_ONLY || role == HiredVillagerRole.BUILDER || role == HiredVillagerRole.COURIER;
    }

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
