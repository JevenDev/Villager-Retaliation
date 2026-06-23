package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;

final class MiningExcavationPlan {
    private MiningExcavationPlan() {
    }

    static List<BlockPos> lineOrder(
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            int maxTargets) {
        List<BlockPos> ordered = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            if (candidate != null) {
                ordered.add(candidate.immutable());
            }
        }
        HorizontalAxis depthAxis = excavationDepthAxis(villager.blockPosition(), context);
        HorizontalAxis lineAxis = depthAxis == HorizontalAxis.X ? HorizontalAxis.Z : HorizontalAxis.X;
        boolean depthFromMin = startsFromMin(villager.blockPosition(), context, depthAxis);
        boolean lineFromMin = startsFromMin(villager.blockPosition(), context, lineAxis);
        ordered.sort((left, right) -> compareLineOrder(
                left,
                right,
                context,
                depthAxis,
                lineAxis,
                depthFromMin,
                lineFromMin,
                villager.blockPosition()));
        if (ordered.size() > maxTargets) {
            return new ArrayList<>(ordered.subList(0, maxTargets));
        }
        return ordered;
    }

    private static int compareLineOrder(
            BlockPos left,
            BlockPos right,
            HiredWorkContext context,
            HorizontalAxis depthAxis,
            HorizontalAxis lineAxis,
            boolean depthFromMin,
            boolean lineFromMin,
            BlockPos villagerPos) {
        int result = Integer.compare(context.workMax().getY() - left.getY(), context.workMax().getY() - right.getY());
        if (result != 0) {
            return result;
        }

        int leftDepth = orderedAxisCoordinate(left, context, depthAxis, depthFromMin);
        int rightDepth = orderedAxisCoordinate(right, context, depthAxis, depthFromMin);
        result = Integer.compare(leftDepth, rightDepth);
        if (result != 0) {
            return result;
        }

        boolean lineDirection = (leftDepth & 1) == 0 ? lineFromMin : !lineFromMin;
        int leftLine = orderedAxisCoordinate(left, context, lineAxis, lineDirection);
        int rightLine = orderedAxisCoordinate(right, context, lineAxis, lineDirection);
        result = Integer.compare(leftLine, rightLine);
        if (result != 0) {
            return result;
        }

        return Double.compare(left.distSqr(villagerPos), right.distSqr(villagerPos));
    }

    private static HorizontalAxis excavationDepthAxis(BlockPos villagerPos, HiredWorkContext context) {
        int xOutside = distanceOutside(villagerPos.getX(), context.workMin().getX(), context.workMax().getX());
        int zOutside = distanceOutside(villagerPos.getZ(), context.workMin().getZ(), context.workMax().getZ());
        if (xOutside != zOutside) {
            return xOutside > zOutside ? HorizontalAxis.X : HorizontalAxis.Z;
        }
        int sizeX = context.workMax().getX() - context.workMin().getX();
        int sizeZ = context.workMax().getZ() - context.workMin().getZ();
        return sizeZ >= sizeX ? HorizontalAxis.Z : HorizontalAxis.X;
    }

    private static boolean startsFromMin(BlockPos villagerPos, HiredWorkContext context, HorizontalAxis axis) {
        int min = axis == HorizontalAxis.X ? context.workMin().getX() : context.workMin().getZ();
        int max = axis == HorizontalAxis.X ? context.workMax().getX() : context.workMax().getZ();
        int value = axis == HorizontalAxis.X ? villagerPos.getX() : villagerPos.getZ();
        return value <= min + (max - min) / 2;
    }

    private static int orderedAxisCoordinate(BlockPos pos, HiredWorkContext context, HorizontalAxis axis, boolean fromMin) {
        int min = axis == HorizontalAxis.X ? context.workMin().getX() : context.workMin().getZ();
        int max = axis == HorizontalAxis.X ? context.workMax().getX() : context.workMax().getZ();
        int value = axis == HorizontalAxis.X ? pos.getX() : pos.getZ();
        return fromMin ? value - min : max - value;
    }

    private static int distanceOutside(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private enum HorizontalAxis {
        X,
        Z
    }
}
