package com.jvn.villagerretaliation.item;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Dispatches every supported physical filter item through one matching entry point. */
public final class VillagerFilterMatcher {
    private static final List<VillagerFilterType> TYPES = List.of(
            new RegisteredFilterType(
                    VillagerRetaliationItems::isItemFilter,
                    VillagerItemFilterData::matches),
            new RegisteredFilterType(
                    VillagerRetaliationItems::isAttributeFilter,
                    VillagerAttributeFilterData::matches));

    private VillagerFilterMatcher() {
    }

    public static boolean matches(Level level, ItemStack filter, ItemStack candidate) {
        if (filter == null) {
            return false;
        }
        for (VillagerFilterType type : TYPES) {
            if (type.supports(filter)) {
                return type.matches(level, filter, candidate);
            }
        }
        return false;
    }

    private record RegisteredFilterType(
            Predicate<ItemStack> supported,
            MatchFunction matcher) implements VillagerFilterType {
        @Override
        public boolean supports(ItemStack filter) {
            return this.supported.test(filter);
        }

        @Override
        public boolean matches(
                Level level,
                ItemStack filter,
                ItemStack candidate) {
            return this.matcher.matches(level, filter, candidate);
        }
    }

    @FunctionalInterface
    private interface MatchFunction {
        boolean matches(Level level, ItemStack filter, ItemStack candidate);
    }
}
