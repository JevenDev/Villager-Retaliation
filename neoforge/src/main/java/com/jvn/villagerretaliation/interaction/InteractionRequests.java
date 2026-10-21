package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class InteractionRequests {
    private InteractionRequests() { }

    public static Optional<ActorInteractionContext> require(ServerPlayer player, InteractionMenu.SessionRef reference) {
        var entity = player.serverLevel().getEntity(reference.entityId());
        if (!(entity instanceof LivingEntity actor) || !reference.actorId().equals(actor.getUUID())
                || !InteractionSessions.validate(player, actor, reference.id())) return Optional.empty();
        return InteractionSessions.current(player).map(session -> new ActorInteractionContext(player.serverLevel(), player, actor, session));
    }

}
