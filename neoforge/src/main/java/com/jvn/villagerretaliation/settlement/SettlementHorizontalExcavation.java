package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalStairPlan;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;

final class SettlementHorizontalExcavation {
    private SettlementHorizontalExcavation() {
    }

    static SettlementMiningRoutes.Target target(SettlementWorkerState worker, SettlementMine mine, Villager owner, BlockPos origin,
                                                HiredWorkContext context, SettlementMiningRoutes routes, SettlementMiningRoutes.Budget budget) {
        HiredWorkPlan.retainMatching(context, position -> valid(mine, context, origin, position), 20);
        for (var position : HiredWorkPlan.targets(context)) {
            var target = routes.target(owner, position, budget);
            if (target != null) return target;
            if (routes.pending(position)) {
                worker.status("searching_excavation_route");
                return null;
            }
        }
        HiredWorkPlan.clear(context);
        var scan = HiredWorkAreaScan.collect(context, "SettlementExcavationScanCursor", 768,
                position -> valid(mine, context, origin, position));
        var ordered = MiningExcavationPlan.lineOrder(origin, context, scan.candidates(), scan.candidates().size());
        for (int index = 0; index < ordered.size(); index++) {
            var position = ordered.get(index);
            var target = routes.target(owner, position, budget);
            if (target != null || routes.pending(position)) {
                List<BlockPos> plan = List.copyOf(ordered.subList(index, Math.min(ordered.size(), index + 20)));
                HiredWorkPlan.replaceWithObjective(context, plan.size() > 1 ? "excavation" : "excavation_block", position, plan, 20);
                if (target == null) worker.status("searching_excavation_route");
                return target;
            }
        }
        if (!scan.completedFullPass()) {
            worker.status("searching_excavation");
        } else if (!MiningHorizontalStairPlan.cleanup(context) && !remaining(mine, context, origin, false)
                && remaining(mine, context, origin, true)) {
            MiningHorizontalStairPlan.beginCleanup(context);
            worker.status("clearing_excavation_stairs");
        } else {
            worker.status(remaining(mine, context, origin, true) ? "excavation_blocked" : "excavation_complete");
        }
        return null;
    }

    private static boolean valid(SettlementMine mine, HiredWorkContext context, BlockPos origin, BlockPos position) {
        return mine.contains(position) && !MiningHorizontalStairPlan.isReservedSupport(context, origin, position)
                && !MiningHazardManager.isProtectedBarrier(context, position)
                && MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, position)
                && !MiningBlockRules.hasAdjacentExcavationFluid(mine, mine::observed, position);
    }

    private static boolean remaining(SettlementMine mine, HiredWorkContext context, BlockPos origin, boolean includeStairs) {
        var barriers = MiningHazardManager.protectedBarrierPositions(context);
        for (BlockPos position : BlockPos.betweenClosed(context.workMin(), context.workMax())) {
            if (!barriers.contains(position.asLong()) && MiningBlockRules.isExcavationMaterial(mine, mine::observed, position)
                    && (includeStairs || !MiningHorizontalStairPlan.isReservedSupport(context, origin, position))) return true;
        }
        return false;
    }
}
