package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class NarrativeActorReservations {
    private NarrativeActorReservations() { }

    public static boolean scene(ServerLevel level, UUID actorId) {
        return SceneSavedData.get(level).hasActiveActor(actorId);
    }

    public static boolean conversation(Villager actor) {
        return ConversationService.isCharacterReserved(actor);
    }
}
