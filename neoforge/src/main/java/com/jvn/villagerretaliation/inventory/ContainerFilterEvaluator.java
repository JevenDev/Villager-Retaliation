package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Deterministically composes every framed transfer rule attached to one logical container. */
public final class ContainerFilterEvaluator {
    private ContainerFilterEvaluator() {
    }

    public static Evaluation evaluate(
            Level level,
            List<ItemStack> rules,
            ItemStack candidate,
            VillagerFilterPolicy.TransferOperation operation,
            StockCounter stockCounter) {
        if (candidate == null || candidate.isEmpty() || operation == null) {
            return Evaluation.invalid();
        }

        List<ItemStack> safeRules = rules == null ? List.of() : rules;
        StockCounter safeStockCounter = stockCounter == null ? StockCounter.EMPTY : stockCounter;
        boolean hasApplicableAllow = false;
        boolean hasMatchingAllow = false;
        int applicableAllowRules = 0;
        int matchingAllowRules = 0;
        int matchingDenyRules = 0;
        int allowance = VillagerFilterPolicy.UNLIMITED_ALLOWANCE;

        for (ItemStack rule : safeRules) {
            if (rule == null || rule.isEmpty()) {
                continue;
            }

            if (!VillagerRetaliationItems.isFilter(rule)) {
                hasApplicableAllow = true;
                applicableAllowRules++;
                if (candidate.is(rule.getItem())) {
                    hasMatchingAllow = true;
                    matchingAllowRules++;
                }
                continue;
            }

            VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(rule);
            if (!policy.valid()) {
                return Evaluation.invalid();
            }
            if (!policy.direction().permits(operation)) {
                continue;
            }

            VillagerFilterMatcher.RawMatchResult match =
                    VillagerFilterMatcher.rawMatchResult(level, rule, candidate);
            if (!match.valid()) {
                return Evaluation.invalid();
            }

            if (policy.listMode() == VillagerFilterPolicy.ListMode.DENY_MATCHING) {
                if (match.matched()) {
                    matchingDenyRules++;
                    return Evaluation.denied(
                            applicableAllowRules, matchingAllowRules, matchingDenyRules, true);
                }
                continue;
            }

            hasApplicableAllow = true;
            applicableAllowRules++;
            if (!match.matched()) {
                continue;
            }

            hasMatchingAllow = true;
            matchingAllowRules++;
            if (policy.stockTarget().isPresent()) {
                StockState stock;
                try {
                    stock = safeStockCounter.stockFor(rule, policy, candidate, operation);
                } catch (RuntimeException exception) {
                    return Evaluation.invalid();
                }
                if (stock == null) {
                    return Evaluation.invalid();
                }
                allowance = Math.min(allowance, VillagerFilterPolicy.allowance(
                        policy,
                        operation,
                        stock.currentStock(),
                        stock.reservationsOrClaims()));
            }
        }

        if (hasApplicableAllow && !hasMatchingAllow) {
            return Evaluation.denied(applicableAllowRules, matchingAllowRules, matchingDenyRules, true);
        }
        return new Evaluation(
                true,
                allowance,
                applicableAllowRules,
                matchingAllowRules,
                matchingDenyRules,
                true);
    }

    @FunctionalInterface
    public interface StockCounter {
        StockCounter EMPTY = (rule, policy, candidate, operation) -> StockState.EMPTY;

        StockState stockFor(
                ItemStack rule,
                VillagerFilterPolicy.Policy policy,
                ItemStack candidate,
                VillagerFilterPolicy.TransferOperation operation);
    }

    public record StockState(int currentStock, int reservationsOrClaims) {
        public static final StockState EMPTY = new StockState(0, 0);

        public StockState {
            currentStock = Math.max(0, currentStock);
            reservationsOrClaims = Math.max(0, reservationsOrClaims);
        }
    }

    public record Evaluation(
            boolean permitted,
            int allowance,
            int applicableAllowRules,
            int matchingAllowRules,
            int matchingDenyRules,
            boolean valid) {
        private static Evaluation denied(
                int applicableAllowRules,
                int matchingAllowRules,
                int matchingDenyRules,
                boolean valid) {
            return new Evaluation(
                    false, 0, applicableAllowRules, matchingAllowRules, matchingDenyRules, valid);
        }

        private static Evaluation invalid() {
            return denied(0, 0, 0, false);
        }
    }
}
