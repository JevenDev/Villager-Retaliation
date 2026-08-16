package com.jvn.villagerretaliation.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A reusable, serialization-friendly position in a specific dimension. */
public record WorldLocation(ResourceLocation dimension, BlockPos position) {
    public WorldLocation {
        if (dimension == null || position == null) {
            throw new IllegalArgumentException("world location requires a dimension and position");
        }
        position = position.immutable();
    }

    public static WorldLocation of(ResourceKey<Level> dimension, BlockPos position) {
        return dimension == null || position == null ? null : new WorldLocation(dimension.location(), position);
    }
}
