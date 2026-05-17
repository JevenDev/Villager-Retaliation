package com.jvn.villagerretaliation.dialogue;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerInteractionTracker {
    private VillagerInteractionTracker() {
    }

    public static InteractionState getState(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData.InteractionEntry entry = VillagerInteractionSavedData.get(level)
                .getOrCreate(villager.getUUID(), player.getUUID());
        return new InteractionState(!entry.hasTalked(), entry.recentDialogueIds());
    }

    public static void rememberDialogue(ServerLevel level, Villager villager, ServerPlayer player, String dialogueId) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.markTalked();
        entry.rememberDialogueId(dialogueId);
        data.setDirty();
    }

    public record InteractionState(boolean firstConversation, List<String> recentDialogueIds) {
    }
}
