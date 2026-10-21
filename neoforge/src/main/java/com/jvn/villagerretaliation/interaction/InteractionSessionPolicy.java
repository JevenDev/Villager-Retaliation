package com.jvn.villagerretaliation.interaction;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public interface InteractionSessionPolicy {
    boolean canStart(ServerPlayer player, LivingEntity actor, InteractionSessions.StartMode mode);
    boolean valid(ServerPlayer player, LivingEntity actor, InteractionSession session);
    long idleTimeout(ServerPlayer player, LivingEntity actor, InteractionSession session);

    default void prepare(ServerPlayer player, LivingEntity actor) { }
    default void hold(ServerPlayer player, LivingEntity actor, InteractionSession session) { }
    default void tick(ServerPlayer player, LivingEntity actor, InteractionSession session) { }
    default void ended(ServerPlayer player, InteractionSession session, boolean notifyClient, boolean finishExchanges) { }
}
