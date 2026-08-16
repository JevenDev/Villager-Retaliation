package com.jvn.villagerretaliation.interaction.work;

import net.minecraft.world.level.pathfinder.Path;

public record HiredPathResult(HiredPathTarget target, Path path, boolean reachesDestination, double score) {
    public static HiredPathResult blocked() {
        return new HiredPathResult(null, null, false, Double.POSITIVE_INFINITY);
    }
}
