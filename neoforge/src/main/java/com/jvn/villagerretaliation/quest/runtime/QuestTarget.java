package com.jvn.villagerretaliation.quest.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record QuestTarget(ResourceKey<Level> dimension, BlockPos position, String objective) {
    public QuestTarget {
        position = position == null ? null : position.immutable();
        objective = objective == null ? "" : objective;
    }
}
