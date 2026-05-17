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
        return new InteractionState(
                !entry.hasTalked(),
                entry.recentDialogueIds(),
                entry.lastPositiveDialogueReputationGameTime(),
                entry.lastPositiveDialogueReputationDay(),
                entry.lastNegativeDialogueReputationGameTime(),
                entry.lastJokeReputationGameTime(),
                entry.badFirstImpression(),
                entry.consecutiveRequestCount(DialogueRequestType.QUESTION)
        );
    }

    public static void rememberDialogue(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType, String dialogueId) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.markTalked();
        entry.rememberDialogueId(requestType, dialogueId, level.getGameTime());
        data.setDirty();
    }

    public static long lastReputationGameTime(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType) {
        return VillagerInteractionSavedData.get(level)
                .getOrCreate(villager.getUUID(), player.getUUID())
                .lastReputationGameTime(requestType);
    }

    public static long lastDialogueGameTime(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType) {
        return VillagerInteractionSavedData.get(level)
                .getOrCreate(villager.getUUID(), player.getUUID())
                .lastDialogueGameTime(requestType);
    }

    public static void rememberDialogueReputation(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType, int delta, boolean badFirstImpression) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.rememberDialogueReputation(requestType, delta, level.getGameTime(), level.getDayTime() / 24000L, badFirstImpression);
        data.setDirty();
    }

    public static boolean hasDisturbedSleepThisNight(ServerLevel level, Villager villager, ServerPlayer player, long night) {
        return VillagerInteractionSavedData.get(level)
                .getOrCreate(villager.getUUID(), player.getUUID())
                .hasDisturbedSleepThisNight(night);
    }

    public static void rememberSleepDisturbance(ServerLevel level, Villager villager, ServerPlayer player, long night) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.rememberSleepDisturbance(night);
        data.setDirty();
    }

    public record InteractionState(
            boolean firstConversation,
            List<String> recentDialogueIds,
            long lastPositiveDialogueReputationGameTime,
            long lastPositiveDialogueReputationDay,
            long lastNegativeDialogueReputationGameTime,
            long lastJokeReputationGameTime,
            boolean badFirstImpression,
            int consecutiveQuestionCount
    ) {
    }
}
