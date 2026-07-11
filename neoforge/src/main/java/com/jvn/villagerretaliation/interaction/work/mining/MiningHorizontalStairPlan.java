package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
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
        if (pos == null || !needsStairs(context) || !context.state().contains(AXIS_TAG, Tag.TAG_STRING)) {
            return false;
        }
        boolean xAxis = "x".equals(context.state().getString(AXIS_TAG));
        if ((xAxis ? pos.getZ() : pos.getX()) != context.state().getInt(LANE_TAG)) {
            return false;
        }
        int coordinate = xAxis ? pos.getX() : pos.getZ();
        int min = xAxis ? context.workMin().getX() : context.workMin().getZ();
        int max = xAxis ? context.workMax().getX() : context.workMax().getZ();
        int depth = context.state().getBoolean(FROM_MIN_TAG) ? coordinate - min : max - coordinate;
        if (depth < 1) {
            return false;
        }
        int requiredRise = Math.max(0,
                context.workMax().getY() - context.workMin().getY() - GROUND_REACHABLE_HEIGHT + 1);
        int supportY = context.workMin().getY() - 1 + Math.min(depth, requiredRise);
        return requiredRise > 0 && pos.getY() == supportY;
    }

    static boolean hasRemainingSupport(ServerLevel level, HiredWorkContext context) {
        for (BlockPos raw : BlockPos.betweenClosed(context.workMin(), context.workMax())) {
            BlockPos pos = raw.immutable();
            if (isSupport(context, pos) && MiningBlockRules.isMineableExcavationBlock(level, context, pos)) {
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
        if (!needsStairs(context) || context.state().contains(AXIS_TAG, Tag.TAG_STRING)) {
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
}
