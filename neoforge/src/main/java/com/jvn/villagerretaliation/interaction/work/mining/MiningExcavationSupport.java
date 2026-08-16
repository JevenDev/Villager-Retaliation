package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Stable mining-worker facade over shaft topology, support placement, and supply logistics. */
public final class MiningExcavationSupport {
    private MiningExcavationSupport() {
    }

    public static BlockPos entryTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.entryTarget(level, context);
    }

    public static BlockPos returnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        return MiningExcavationShaft.returnTarget(level, villager, context);
    }

    public static BlockPos currentLayerDescentTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.currentLayerDescentTarget(level, context);
    }

    public static WorkResult maintain(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractBlockWorker worker) {
        return MiningSupportManager.maintain(level, villager, context, worker);
    }

    public static WorkResult requireLadder(ServerLevel level, Villager villager, HiredWorkContext context) {
        return MiningSupportSupplies.requireLadder(level, villager, context);
    }

    public static WorkResult gatherSupplies(ServerLevel level, Villager villager, HiredWorkContext context) {
        return MiningSupportSupplies.gather(level, villager, context);
    }

    public static boolean shouldUseLadderFallback(
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target) {
        return !com.jvn.villagerretaliation.interaction.HiredMiningMode.fromState(context.state()).excavatesHorizontally()
                && MiningExcavationShaft.shouldUseFallback(context, villager, target);
    }

    public static boolean hasCompleteLadderRouteToLayer(
            ServerLevel level,
            HiredWorkContext context,
            int layerY) {
        return MiningExcavationShaft.hasCompleteRouteToLayer(level, context, layerY);
    }

    public static boolean canMineCurrentLayerTarget(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos target) {
        return MiningExcavationShaft.canMineCurrentLayerTarget(level, context, target);
    }

    public static boolean isNeededLadderShaftTarget(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos target) {
        return MiningExcavationShaft.isNeededTarget(level, context, target);
    }

    public static BlockPos nextNeededLadderShaftTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.nextNeededTarget(level, context);
    }

    public static boolean needsLadderRouteOutputReserve(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos target) {
        return MiningExcavationShaft.needsOutputReserve(level, context, target);
    }
}
