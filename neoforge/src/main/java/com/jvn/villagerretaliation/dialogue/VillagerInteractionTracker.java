package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class VillagerInteractionTracker {
    private VillagerInteractionTracker() {
    }

    public static InteractionState getState(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData.InteractionEntry entry = VillagerInteractionSavedData.get(level)
                .getOrCreate(villager.getUUID(), player.getUUID());
        long gameTime = level.getGameTime();
        long day = level.getDayTime() / 24000L;
        long optionResetTicks = VillagerRetaliationConfig.REPEATED_DIALOGUE_OPTION_RESET_TICKS.get();
        return new InteractionState(
                !entry.hasTalked(),
                entry.recentDialogueIds(),
                entry.lastPositiveDialogueReputationGameTime(),
                entry.lastPositiveDialogueReputationDay(),
                entry.lastNegativeDialogueReputationGameTime(),
                entry.lastJokeReputationGameTime(),
                entry.badFirstImpression(),
                entry.requestUseCount(DialogueRequestType.CHAT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.GREETING, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.QUESTION, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.GIFT_PREFERENCES, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.MAP_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.STORY, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.JOKE, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.INSULT, gameTime, day, optionResetTicks),
                entry.lastBrokenBedGameTime(),
                entry.lastDirectHitGameTime(),
                entry.lastDirectHitWeapon()
        );
    }

    public static void rememberDialogue(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType, String dialogueId) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.markTalked();
        entry.rememberDialogueId(
                requestType,
                dialogueId,
                level.getGameTime(),
                level.getDayTime() / 24000L,
                VillagerRetaliationConfig.REPEATED_DIALOGUE_OPTION_RESET_TICKS.get()
        );
        data.setDirty();
    }

    public static void rememberCartographerMap(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            ResourceLocation structureId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberCartographerMap(
                villager.getUUID(),
                player.getUUID(),
                level.dimension().location(),
                structureId,
                targetName,
                targetPos,
                expiresAtGameTime
        );
        data.setDirty();
    }

    public static List<CartographerMapReport> markCartographerMapDiscoveriesNear(ServerLevel level, ServerPlayer player, double radius) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        List<CartographerMapReport> discoveries = data.markCartographerMapDiscoveriesNear(
                player.getUUID(),
                level.dimension().location(),
                player.getX(),
                player.getZ(),
                radius * radius,
                level.getGameTime()
        );
        if (!discoveries.isEmpty()) {
            data.setDirty();
        }
        return discoveries;
    }

    public static Optional<CartographerMapReport> unreportedCartographerMapDiscovery(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedCartographerMapDiscovery(villager.getUUID(), player.getUUID()));
    }

    public static Optional<CartographerMapReport> claimUnreportedCartographerMapDiscovery(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        CartographerMapReport report = data.claimUnreportedCartographerMapDiscovery(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
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

    public static void reduceRepeatedDialogueUseCounts(ServerLevel level, Villager villager, ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.reduceRepeatedDialogueUseCounts(
                amount,
                level.getGameTime(),
                level.getDayTime() / 24000L,
                VillagerRetaliationConfig.REPEATED_DIALOGUE_OPTION_RESET_TICKS.get()
        );
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

    public static void rememberBrokenBed(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.rememberBrokenBed(level.getGameTime());
        data.setDirty();
    }

    public static void rememberDirectHit(ServerLevel level, Villager villager, ServerPlayer player, String weapon) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        VillagerInteractionSavedData.InteractionEntry entry = data.getOrCreate(villager.getUUID(), player.getUUID());
        entry.rememberDirectHit(level.getGameTime(), weapon);
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
            int chatUseCount,
            int greetingUseCount,
            int questionUseCount,
            int giftPreferenceUseCount,
            int mapReportUseCount,
            int storyUseCount,
            int jokeUseCount,
            int insultUseCount,
            long lastBrokenBedGameTime,
            long lastDirectHitGameTime,
            String lastDirectHitWeapon
    ) {
        public int requestUseCount(DialogueRequestType requestType) {
            return switch (requestType) {
                case CHAT -> this.chatUseCount;
                case GREETING -> this.greetingUseCount;
                case QUESTION -> this.questionUseCount;
                case GIFT_PREFERENCES -> this.giftPreferenceUseCount;
                case MAP_REPORT -> this.mapReportUseCount;
                case STORY -> this.storyUseCount;
                case JOKE -> this.jokeUseCount;
                case INSULT -> this.insultUseCount;
            };
        }
    }

    public record CartographerMapReport(
            UUID villagerId,
            ResourceLocation dimension,
            ResourceLocation structureId,
            String targetName,
            BlockPos targetPos
    ) {
    }
}
