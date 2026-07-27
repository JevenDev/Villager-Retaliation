package com.jvn.villagerretaliation.item;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Dispatches every supported physical filter item through one matching entry point. */
public final class VillagerFilterMatcher {
    private VillagerFilterMatcher() {
    }

    public static boolean matches(Level level, ItemStack filter, ItemStack candidate) {
        if (VillagerRetaliationItems.isItemFilter(filter)) {
            return VillagerItemFilterData.matches(level, filter, candidate);
        }
        if (VillagerRetaliationItems.isAttributeFilter(filter)) {
            return VillagerAttributeFilterData.matches(level, filter, candidate);
        }
        return false;
    }
}
