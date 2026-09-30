package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

final class SettlementMiningFloor {
    private SettlementMiningFloor() {
    }

    static Result tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementMine mine, Villager owner,
                       HiredWorkContext context, BlockPos origin, List<SettlementDepot> depots,
                       SettlementWorkBudget budget, SettlementMiningRoutes.Budget routeBudget) {
        var position = MiningHazardManager.horizontalFloorTarget(mine, mine::observed, context, origin.getBottomCenter());
        if (position == null) return Result.NONE;
        var routes = SettlementMiningRoutes.forWorker(worker, mine, origin, true);
        var target = routes.target(owner, position, routeBudget);
        if (target == null) {
            worker.status(routes.pending(position) ? "searching_floor_route" : "floor_unreachable");
            return routes.pending(position) ? Result.WAITING : Result.DEFERRED;
        }
        var view = new SettlementMiningView(level, mine);
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
        if (selected < 0) return fetch(level, town, worker, context, origin, position, view, depots, budget);
        double cost = 20.0D + 16.0D * target.steps();
        if (!SettlementBackgroundMining.ready(worker, target, budget, cost, "approaching_floor", "patching_floor")) return Result.WAITING;
        var fill = ((BlockItem) updated.get(selected).getItem()).getBlock().defaultBlockState();
        if (!mine.changeFloor(position, fill)) {
            worker.status("mine_conflict");
            return Result.WAITING;
        }
        budget.spend(cost);
        updated.get(selected).shrink(1);
        SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
        MiningHazardManager.floorPatched(context, position);
        HiredWorkPlan.clear(context);
        AbstractBlockWorker.clearStoredTarget(context.state());
        context.state().putLong(SettlementBackgroundMining.POSITION, target.stance().asLong());
        worker.status("patched_floor");
        return Result.PROGRESSED;
    }

    private static Result fetch(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredWorkContext context, BlockPos origin,
                                BlockPos position, SettlementMiningView view, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        var updated = SettlementBackgroundTools.items(context.inventory());
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (source.depot() == null || !safeFill(view, position, source.available())
                    || !SettlementBackgroundTools.allowed(level, supplies, index)) continue;
            if (!SettlementProductionInventory.insertSupplies(worker, updated, List.of(source.available().copyWithCount(1)))) {
                worker.status("fill_supply_full");
                return Result.WAITING;
            }
            double cost = 40.0D + 16.0D * Math.sqrt(origin.distSqr(source.depot().position()));
            if (!budget.spend(cost)) {
                worker.status("fetching_fill");
                return Result.WAITING;
            }
            source.depot().removeItem(source.slot(), 1);
            SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
            worker.status("fetched_fill");
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
        DEFERRED,
        PROGRESSED,
        WAITING
    }
}
