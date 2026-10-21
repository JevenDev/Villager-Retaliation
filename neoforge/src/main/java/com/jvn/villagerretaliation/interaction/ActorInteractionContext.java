package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.NarrativeActors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public record ActorInteractionContext(ServerLevel level, ServerPlayer player, LivingEntity actor, InteractionSession session) {
    public ActorInteractionContext {
        if (player.serverLevel() != level || actor.level() != level || !session.playerId().equals(player.getUUID())
                || !session.actorId().equals(actor.getUUID()) || session.entityId() != actor.getId())
            throw new IllegalArgumentException("Interaction participants do not match their session");
    }

    public DialogueRuntimeContext dialogue() {
        return NarrativeActors.context(this.level, this.player, this.actor)
                .orElseThrow(() -> new IllegalStateException("Interaction dialogue adapter is unavailable"));
    }
}
