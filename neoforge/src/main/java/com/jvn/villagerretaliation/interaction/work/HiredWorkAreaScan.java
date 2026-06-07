package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

final class HiredWorkAreaScan {
    private HiredWorkAreaScan() {
    }

    static Result collect(
            HiredWorkContext context,
            String cursorTag,
            int maxPositions,
            Predicate<BlockPos> candidateFilter) {
        int safeMaxPositions = Math.max(1, maxPositions);
        int sizeX = Math.max(1, context.workMax().getX() - context.workMin().getX() + 1);
        int sizeY = Math.max(1, context.workMax().getY() - context.workMin().getY() + 1);
        int sizeZ = Math.max(1, context.workMax().getZ() - context.workMin().getZ() + 1);
        long totalPositions = (long) sizeX * sizeY * sizeZ;
        long index = Math.floorMod(context.state().getLong(cursorTag), totalPositions);
        long visited = 0L;
        List<BlockPos> candidates = new ArrayList<>();

        while (visited < totalPositions && visited < safeMaxPositions) {
            BlockPos pos = positionAt(context.workMin(), context.workMax(), sizeX, sizeZ, index);
            if (candidateFilter.test(pos)) {
                candidates.add(pos);
            }
            index = (index + 1L) % totalPositions;
            visited++;
        }

        boolean completedFullPass = visited >= totalPositions || index == 0L;
        if (completedFullPass) {
            clearCursor(context, cursorTag);
        } else {
            context.state().putLong(cursorTag, index);
        }
        return new Result(candidates, visited, completedFullPass);
    }

    static boolean isInProgress(HiredWorkContext context, String cursorTag) {
        return context.state().contains(cursorTag);
    }

    static void clearCursor(HiredWorkContext context, String cursorTag) {
        context.state().remove(cursorTag);
    }

    private static BlockPos positionAt(BlockPos min, BlockPos max, int sizeX, int sizeZ, long index) {
        int xOffset = (int) (index % sizeX);
        long zyIndex = index / sizeX;
        int zOffset = (int) (zyIndex % sizeZ);
        int yOffset = (int) (zyIndex / sizeZ);
        return new BlockPos(min.getX() + xOffset, max.getY() - yOffset, min.getZ() + zOffset);
    }

    record Result(List<BlockPos> candidates, long visitedPositions, boolean completedFullPass) {
    }
}
