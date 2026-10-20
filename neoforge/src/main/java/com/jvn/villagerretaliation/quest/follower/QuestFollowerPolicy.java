package com.jvn.villagerretaliation.quest.follower;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

@FunctionalInterface
public interface QuestFollowerPolicy {
    String issue(PathfinderMob actor, ServerPlayer player);
}
