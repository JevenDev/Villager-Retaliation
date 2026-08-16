package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Reserves a mined-in-place staircase while a tall horizontal excavation is opened. */
final class MiningHorizontalStairPlan {
    private static final String AXIS_TAG = "HorizontalExcavationStairAxis";
    private static final String FROM_MIN_TAG = "HorizontalExcavationStairFromMin";
    private static final String LANE_TAG = "HorizontalExcavationStairLane";
    private static final String CLEANUP_TAG = "HorizontalExcavationStairCleanup";
    private static final int GROUND_REACHABLE_HEIGHT = 4;

    private MiningHorizontalStairPlan() {
    }

    static void reset(HiredWorkContext context) {
        context.state().remove(AXIS_TAG);
        context.state().remove(FROM_MIN_TAG);
        context.state().remove(LANE_TAG);
        context.state().remove(CLEANUP_TAG);
    }

    static boolean isReservedSupport(HiredWorkContext context, Villager villager, BlockPos pos) {
        ensureInitialized(context, villager);
        return !cleanup(context) && isSupport(context, pos);
    }

    static boolean isSupport(HiredWorkContext context, BlockPos pos) {
        StairPlan plan = storedPlan(context);
        if (pos == null || plan == null) {
            return false;
        }
        if ((plan.xAxis() ? pos.getZ() : pos.getX()) != plan.lane()) {
            return false;
        }
        int coordinate = plan.xAxis() ? pos.getX() : pos.getZ();
        int min = plan.xAxis() ? context.workMin().getX() : context.workMin().getZ();
        int max = plan.xAxis() ? context.workMax().getX() : context.workMax().getZ();
        int depth = plan.fromMin() ? coordinate - min : max - coordinate;
        if (depth < 1) {
            return false;
        }
        int requiredRise = Math.max(0,
                context.workMax().getY() - context.workMin().getY() - GROUND_REACHABLE_HEIGHT + 1);
        int supportY = context.workMin().getY() - 1 + Math.min(depth, requiredRise);
        return requiredRise > 0 && pos.getY() == supportY;
    }

    static boolean hasRemainingSupport(ServerLevel level, HiredWorkContext context) {
        StairPlan plan = storedPlan(context);
        if (plan == null) {
            return false;
        }
        LongSet protectedBarriers = MiningHazardManager.protectedBarrierPositions(context);
        int min = plan.xAxis() ? context.workMin().getX() : context.workMin().getZ();
        int max = plan.xAxis() ? context.workMax().getX() : context.workMax().getZ();
        int requiredRise = Math.max(0,
                context.workMax().getY() - context.workMin().getY() - GROUND_REACHABLE_HEIGHT + 1);
        for (int coordinate = min; coordinate <= max; coordinate++) {
            int depth = plan.fromMin() ? coordinate - min : max - coordinate;
            if (depth < 1) {
                continue;
            }
            int supportY = context.workMin().getY() - 1 + Math.min(depth, requiredRise);
            BlockPos pos = plan.xAxis()
                    ? new BlockPos(coordinate, supportY, plan.lane())
                    : new BlockPos(plan.lane(), supportY, coordinate);
            if (MiningBlockRules.isMineableExcavationBlock(level, pos, protectedBarriers)) {
                return true;
            }
        }
        return false;
    }

    static void beginCleanup(HiredWorkContext context) {
        context.state().putBoolean(CLEANUP_TAG, true);
    }

    static boolean cleanup(HiredWorkContext context) {
        return context.state().getBoolean(CLEANUP_TAG);
    }

    private static boolean needsStairs(HiredWorkContext context) {
        return context.workMax().getY() - context.workMin().getY() + 1 > GROUND_REACHABLE_HEIGHT;
    }

    private static void ensureInitialized(HiredWorkContext context, Villager villager) {
        if (!needsStairs(context)) {
            reset(context);
            return;
        }
        if (storedPlan(context) != null) {
            return;
        }
        int sizeX = context.workMax().getX() - context.workMin().getX();
        int sizeZ = context.workMax().getZ() - context.workMin().getZ();
        boolean xAxis = sizeX >= sizeZ;
        int value = xAxis ? villager.blockPosition().getX() : villager.blockPosition().getZ();
        int min = xAxis ? context.workMin().getX() : context.workMin().getZ();
        int max = xAxis ? context.workMax().getX() : context.workMax().getZ();
        boolean fromMin = Math.abs(value - min) <= Math.abs(value - max);
        int lineValue = xAxis ? villager.blockPosition().getZ() : villager.blockPosition().getX();
        int lineMin = xAxis ? context.workMin().getZ() : context.workMin().getX();
        int lineMax = xAxis ? context.workMax().getZ() : context.workMax().getX();
        context.state().putString(AXIS_TAG, xAxis ? "x" : "z");
        context.state().putBoolean(FROM_MIN_TAG, fromMin);
        context.state().putInt(LANE_TAG, Math.clamp(lineValue, lineMin, lineMax));
    }

    private static StairPlan storedPlan(HiredWorkContext context) {
        if (!needsStairs(context)) {
            return null;
        }
        if (!context.state().contains(AXIS_TAG, Tag.TAG_STRING)
                || !context.state().contains(FROM_MIN_TAG, Tag.TAG_BYTE)
                || !context.state().contains(LANE_TAG, Tag.TAG_INT)) {
            reset(context);
            return null;
        }
        String axis = context.state().getString(AXIS_TAG);
        if (!axis.equals("x") && !axis.equals("z")) {
            reset(context);
            return null;
        }
        boolean xAxis = axis.equals("x");
        int lane = context.state().getInt(LANE_TAG);
        int laneMin = xAxis ? context.workMin().getZ() : context.workMin().getX();
        int laneMax = xAxis ? context.workMax().getZ() : context.workMax().getX();
        if (lane < laneMin || lane > laneMax) {
            reset(context);
            return null;
        }
        return new StairPlan(xAxis, context.state().getBoolean(FROM_MIN_TAG), lane);
    }

    private record StairPlan(boolean xAxis, boolean fromMin, int lane) {
    }
}
