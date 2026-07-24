package com.jvn.villagerretaliation.interaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Package-local bridge for courier lifecycle checks. */
final class VillagerAggressionPolicy {
    private VillagerAggressionPolicy() {
    }

    static boolean shouldAttackOnSight(Villager villager, ServerPlayer player) {
        return com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy
                .shouldAttackOnSight(villager, player);
    }
}
