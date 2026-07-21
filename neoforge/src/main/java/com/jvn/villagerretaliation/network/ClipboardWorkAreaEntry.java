package com.jvn.villagerretaliation.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record ClipboardWorkAreaEntry(
        ResourceLocation dimension,
        BlockPos min,
        BlockPos max,
        BlockPos center,
        boolean showCenter,
        BlockPos firstCorner,
        boolean showFirstCorner,
        BlockPos secondCorner,
        boolean showSecondCorner,
        String ownerName,
        String jobName) {
    public ClipboardWorkAreaEntry {
        BlockPos normalizedMin = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ()));
        BlockPos normalizedMax = new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ()));
        min = normalizedMin;
        max = normalizedMax;
        center = center == null ? new BlockPos(
                Math.floorDiv(min.getX() + max.getX(), 2),
                Math.floorDiv(min.getY() + max.getY(), 2),
                Math.floorDiv(min.getZ() + max.getZ(), 2)) : center.immutable();
        firstCorner = firstCorner == null ? min : firstCorner.immutable();
        secondCorner = secondCorner == null ? max : secondCorner.immutable();
        ownerName = ownerName == null ? "" : ownerName;
        jobName = jobName == null ? "" : jobName;
    }

    public ClipboardWorkAreaEntry(ResourceLocation dimension, BlockPos min, BlockPos max) {
        this(dimension, min, max, null, false, null, false, null, false, "", "");
    }

    public ClipboardWorkAreaEntry(
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            boolean showCenter,
            BlockPos firstCorner,
            boolean showFirstCorner,
            BlockPos secondCorner,
            boolean showSecondCorner) {
        this(dimension, min, max, center, showCenter, firstCorner, showFirstCorner,
                secondCorner, showSecondCorner, "", "");
    }
}
