package com.jvn.villagerretaliation.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public record ClipboardWorkAreaEntry(ResourceLocation dimension, BlockPos min, BlockPos max) {
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
    }
}
