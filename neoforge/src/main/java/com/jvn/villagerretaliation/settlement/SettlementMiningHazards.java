package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

final class SettlementMiningHazards {
    private SettlementMiningHazards() {
    }

    static Result tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementMine mine, SettlementMiningView view,
                       Villager owner, HiredWorkContext context, BlockPos origin, List<SettlementDepot> depots,
                       SettlementWorkBudget budget, SettlementMiningRoutes.Budget routeBudget) {
        var plan = MiningHazardManager.assess(mine, mine::observed, context, origin.getBottomCenter());
        if (plan == null) return Result.NONE;
        if (plan.kind() == MiningHazardManager.HazardKind.BLOCKED_TARGET || plan.index() >= plan.positions().size()) {
            MiningHazardManager.completePlan(context, plan);
            clearWork(context);
            worker.status(plan.permanent() ? "hazard_sealed" : "hazard_drained");
            return Result.PROGRESSED;
        }
        var position = plan.positions().get(plan.index());
        if (!mine.observed(position)) {
            worker.status("excavation_needs_observation");
            return Result.WAITING;
        }
        if (!MiningHazardManager.canReplaceWithFill(mine, mine::observed, position)) {
            MiningHazardManager.advancePlan(context, plan);
            clearWork(context);
            worker.status("checking_hazard");
            return Result.PROGRESSED;
        }
        var updated = SettlementBackgroundTools.items(context.inventory());
        var slots = new ArrayList<>(worker.outputSlots());
        slots.addAll(worker.supplySlots());
        int selected = -1;
        for (int slot : slots) {
            if (safeFill(view, position, updated.get(slot))) {
                selected = slot;
                break;
            }
        }
        if (selected < 0) return fetch(level, town, worker, context, origin, position, plan.positions().size() - plan.index(),
                view, depots, budget);
        var routes = SettlementMiningRoutes.forWorker(worker, mine, origin, true);
        var target = routes.target(owner, position, routeBudget);
        if (target == null) {
            worker.status(routes.pending(position) ? "searching_hazard_route" : "hazard_unreachable");
            return Result.WAITING;
        }
        double cost = 20.0D + 16.0D * target.steps();
        if (!SettlementBackgroundMining.ready(worker, target, budget, cost, "approaching_hazard", "containing_hazard")) return Result.WAITING;
        var fill = ((BlockItem) updated.get(selected).getItem()).getBlock().defaultBlockState();
        if (!mine.changeHazard(position, fill)) {
            worker.status("mine_conflict");
            return Result.WAITING;
        }
        budget.spend(cost);
        updated.get(selected).shrink(1);
        SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
        MiningHazardManager.advancePlan(context, plan);
        clearWork(context);
        context.state().putLong(SettlementBackgroundMining.POSITION, target.stance().asLong());
        worker.status(plan.kind() == MiningHazardManager.HazardKind.FALL ? "placed_fall_guard" : "placed_hazard_fill");
        return Result.PROGRESSED;
    }

    private static void clearWork(HiredWorkContext context) {
        HiredWorkPlan.clear(context);
        AbstractBlockWorker.clearStoredTarget(context.state());
        MiningWorkerState.clearExcavationLayerCache(context);
    }

    private static Result fetch(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredWorkContext context, BlockPos origin,
                                BlockPos position, int remaining, SettlementMiningView view,
                                List<SettlementDepot> depots, SettlementWorkBudget budget) {
        var supplies = SettlementProductionInventory.supplies(level, town, worker, SettlementBackgroundTools.items(context.inventory()), depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !safeFill(view, position, source.available())) continue;
            int count = Math.min(32, Math.min(remaining, source.available().getCount()));
            var updated = SettlementBackgroundTools.items(context.inventory());
            while (count > 0 && !SettlementProductionInventory.insertSupplies(worker, updated, List.of(source.available().copyWithCount(count)))) {
                count--;
                updated = SettlementBackgroundTools.items(context.inventory());
            }
            if (count == 0) {
                worker.status("fill_supply_full");
                return Result.WAITING;
            }
            var consumed = new ArrayList<>(Collections.nCopies(supplies.size(), 0));
            consumed.set(index, count);
            if (!SettlementProductionInventory.allowed(level, supplies, consumed)) continue;
            double cost = 40.0D + 16.0D * Math.sqrt(origin.distSqr(source.depot().position()));
            if (!budget.spend(cost)) {
                worker.status("fetching_hazard_fill");
                return Result.WAITING;
            }
            source.depot().removeItem(source.slot(), count);
            SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
            worker.status("fetched_hazard_fill");
            return Result.PROGRESSED;
        }
        worker.status("missing_safe_fill");
        return Result.WAITING;
    }

    private static boolean safeFill(SettlementMiningView view, BlockPos position, ItemStack stack) {
        try {
            return !stack.isEmpty() && MiningHazardManager.isSafeFillStack(view, position, stack);
        } catch (SettlementMiningView.Unavailable unavailable) {
            return false;
        }
    }

    enum Result {
        NONE,
        PROGRESSED,
        WAITING
    }
}
