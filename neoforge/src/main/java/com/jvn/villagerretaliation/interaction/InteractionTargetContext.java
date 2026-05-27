package com.jvn.villagerretaliation.interaction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public record InteractionTargetContext(
        ServerLevel level,
        ServerPlayer player,
        Villager villager) {
}
