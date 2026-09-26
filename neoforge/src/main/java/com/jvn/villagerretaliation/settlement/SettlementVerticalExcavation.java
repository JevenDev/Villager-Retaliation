package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningExcavationShaft;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHazardManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;

final class SettlementVerticalExcavation {
    private SettlementVerticalExcavation() {
    }

    static SettlementMiningRoutes.Target target(SettlementWorkerState worker, SettlementMine mine, SettlementMiningView view,
                                                Villager owner, BlockPos origin, HiredWorkContext context, Integer layer,
                                                SettlementMiningRoutes routes, SettlementMiningRoutes.Budget budget) {
        if (layer == null) {
            HiredWorkPlan.clear(context);
            worker.status(remaining(mine, context) ? "excavation_blocked" : "excavation_complete");
            return null;
        }
        var shaft = MiningExcavationShaft.stored(context);
        if (shaft != null && MiningExcavationShaft.isNeededTarget(view, context, layer, shaft.at(layer))) {
            var position = shaft.at(layer);
            if (valid(mine, view, context, layer, position)) {
                var target = routes.target(owner, position, budget);
                if (target != null || routes.pending(position)) {
                    HiredWorkPlan.replaceWithObjective(context, "ladder_shaft", position, List.of(position), 20);
                    if (target == null) worker.status("searching_excavation_route");
                    return target;
                }
            }
        }
        HiredWorkPlan.retainMatching(context, position -> valid(mine, view, context, layer, position), 20);
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
                position -> valid(mine, view, context, layer, position));
        var ordered = MiningExcavationPlan.lineOrder(origin, context, scan.candidates(), scan.candidates().size());
        for (int index = 0; index < ordered.size(); index++) {
            var position = ordered.get(index);
            var target = routes.target(owner, position, budget);
            if (target != null || routes.pending(position)) {
                HiredWorkPlan.replaceWithObjective(context, "excavation", position,
                        ordered.subList(index, Math.min(ordered.size(), index + 20)), 20);
                if (target == null) worker.status("searching_excavation_route");
                return target;
            }
        }
        var landing = worker.position().below(2);
        worker.status(!scan.completedFullPass() ? "searching_excavation"
                : mine.contains(landing) && landing.getY() == layer && routes.protectsNativeLanding(landing)
                ? "awaiting_worker_movement" : "excavation_blocked");
        return null;
    }

    private static boolean valid(SettlementMine mine, SettlementMiningView view, HiredWorkContext context, int layer, BlockPos position) {
        return mine.contains(position) && position.getY() == layer
                && (MiningExcavationShaft.isNeededTarget(view, context, layer, position)
                || !MiningHazardManager.isProtectedBarrier(context, position)
                && MiningBlockRules.isMineableExcavationBlock(mine, mine::observed, position))
                && MiningExcavationShaft.canMineCurrentLayerTarget(view, context, layer, position)
                && !MiningBlockRules.hasAdjacentExcavationFluid(mine, mine::observed, position);
    }

    private static boolean remaining(SettlementMine mine, HiredWorkContext context) {
        for (BlockPos position : BlockPos.betweenClosed(context.workMin(), context.workMax())) {
            if (!MiningHazardManager.isProtectedBarrier(context, position)
                    && (MiningBlockRules.isExcavationMaterial(mine, mine::observed, position)
                    || !mine.getFluidState(position).isEmpty())) return true;
        }
        return false;
    }
}
