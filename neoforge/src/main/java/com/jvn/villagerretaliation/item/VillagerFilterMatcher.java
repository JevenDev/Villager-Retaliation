package com.jvn.villagerretaliation.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Dispatches every supported physical filter item through one matching entry point. */
public final class VillagerFilterMatcher {
    private static final List<VillagerFilterType> TYPES = List.of(
            new RegisteredFilterType(
                    VillagerRetaliationItems::isItemFilter,
                    VillagerItemFilterData::rawMatches),
            new RegisteredFilterType(
                    VillagerRetaliationItems::isAttributeFilter,
                    VillagerAttributeFilterData::rawMatches),
            new RegisteredFilterType(
                    VillagerRetaliationItems::isRecipeFilter,
                    VillagerFilterMatcher::recipeRawMatches));

    private VillagerFilterMatcher() {
    }

    public static boolean matches(Level level, ItemStack filter, ItemStack candidate) {
        if (filter == null || candidate == null || candidate.isEmpty()) {
            return false;
        }
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        if (!policy.valid()) {
            return false;
        }
        MatchContext context = new MatchContext();
        boolean rawMatch = rawMatches(level, filter, candidate, context);
        if (!context.valid()) {
            return false;
        }
        return policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                ? rawMatch
                : !rawMatch;
    }

    /** Matches only the configured predicate graph, without direction, stock, or allow/deny policy. */
    public static boolean rawMatches(Level level, ItemStack filter, ItemStack candidate) {
        return rawMatchResult(level, filter, candidate).matched();
    }

    /** Reports malformed predicate graphs separately from valid non-matches. */
    public static RawMatchResult rawMatchResult(Level level, ItemStack filter, ItemStack candidate) {
        MatchContext context = new MatchContext();
        boolean matched = rawMatches(level, filter, candidate, context);
        return new RawMatchResult(context.valid(), context.valid() && matched);
    }

    private static boolean rawMatches(
            Level level,
            ItemStack filter,
            ItemStack candidate,
            MatchContext context) {
        if (filter == null || candidate == null || candidate.isEmpty() || !context.enter(filter)) {
            return false;
        }
        try {
            for (VillagerFilterType type : TYPES) {
                if (type.supports(filter)) {
                    return type.rawMatches(level, filter, candidate, context);
                }
            }
            if (VillagerRetaliationItems.isFilter(filter)) {
                context.invalidate();
            }
            return false;
        } catch (RuntimeException exception) {
            context.invalidate();
            return false;
        } finally {
            context.exit();
        }
    }

    private static boolean recipeRawMatches(
            Level level,
            ItemStack filter,
            ItemStack candidate,
            MatchContext context) {
        if (!VillagerFilterPolicy.read(filter).valid()) {
            context.invalidate();
            return false;
        }
        VillagerRecipeFilterData.Resolution resolution = VillagerRecipeFilterData.resolve(level, filter);
        if (resolution.state() == VillagerRecipeFilterData.ResolutionState.EMPTY) {
            return false;
        }
        if (!resolution.valid()) {
            context.invalidate();
            return false;
        }
        ItemStack result = resolution.recipe().value().getResultItem(level.registryAccess());
        return !result.isEmpty() && ItemStack.isSameItemSameComponents(
                result.copyWithCount(1), candidate.copyWithCount(1));
    }

    private record RegisteredFilterType(
            Predicate<ItemStack> supported,
            MatchFunction matcher) implements VillagerFilterType {
        @Override
        public boolean supports(ItemStack filter) {
            return this.supported.test(filter);
        }

        @Override
        public boolean rawMatches(
                Level level,
                ItemStack filter,
                ItemStack candidate,
                MatchContext context) {
            return this.matcher.matches(level, filter, candidate, context);
        }
    }

    @FunctionalInterface
    private interface MatchFunction {
        boolean matches(Level level, ItemStack filter, ItemStack candidate, MatchContext context);
    }

    public record RawMatchResult(boolean valid, boolean matched) {
    }

    /** One predicate-graph traversal. Repeated configurations and excessive depth invalidate it. */
    public static final class MatchContext {
        private final List<ItemStack> path = new ArrayList<>();
        private boolean valid = true;

        public boolean nestedMatches(Level level, ItemStack filter, ItemStack candidate) {
            return rawMatches(level, filter, candidate, this);
        }

        public void invalidate() {
            this.valid = false;
        }

        public boolean valid() {
            return this.valid;
        }

        private boolean enter(ItemStack filter) {
            if (!this.valid || this.path.size() > VillagerItemFilterData.MAX_NESTING_DEPTH) {
                this.valid = false;
                return false;
            }
            for (ItemStack ancestor : this.path) {
                if (ItemStack.isSameItemSameComponents(ancestor, filter)) {
                    this.valid = false;
                    return false;
                }
            }
            this.path.add(filter.copyWithCount(1));
            return true;
        }

        private void exit() {
            if (!this.path.isEmpty()) {
                this.path.removeLast();
            }
        }
    }
}
