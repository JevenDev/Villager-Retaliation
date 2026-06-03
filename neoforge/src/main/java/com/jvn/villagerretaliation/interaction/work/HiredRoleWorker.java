package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public interface HiredRoleWorker {
    HiredVillagerRole role();

    WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context);

    default void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
    }

    default void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        context.setProgressTicks(0);
    }

    default String status(ServerLevel level, Villager villager, HiredWorkContext context) {
        return context.status();
    }

    default boolean requiresFood() {
        return true;
    }
}
