package com.jvn.villagerretaliation.villager;

import net.minecraft.core.BlockPos;

/**
 * Transient navigation state attached to villagers by the hired-navigation mixin.
 */
public interface HiredNavigationState {
    BlockPos villagerretaliation$getHiredWalkTarget();

    void villagerretaliation$setHiredWalkTarget(BlockPos target);
}
