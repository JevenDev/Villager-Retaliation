package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class VillagerInteractionTracker {
    private VillagerInteractionTracker() {
    }

    public static InteractionState getState(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData.InteractionEntry entry = VillagerInteractionSavedData.get(level)
                .getOrEmptyForRead(villager.getUUID(), player.getUUID());
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
                entry.requestUseCount(DialogueRequestType.GIFT_ADVICE_FOLLOWUP, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.MAP_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.STORY_HINT_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.COMBAT_SURVIVAL_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.GEAR_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.RECRUITMENT_FOLLOWUP, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.CURED_RECOGNITION, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.VILLAGE_EVENT_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.APOLOGY, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.VILLAGE_DEFENSE_REPORT, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.STORY, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.SHARE_STORY, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.JOKE, gameTime, day, optionResetTicks),
                entry.requestUseCount(DialogueRequestType.INSULT, gameTime, day, optionResetTicks),
                entry.lastDialogueGameTime(DialogueRequestType.APOLOGY),
                entry.lastDialogueGameTime(DialogueRequestType.VILLAGE_DEFENSE_REPORT),
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
        Map<ResourceLocation, Boolean> structuresAtPlayer = new HashMap<>();
        List<CartographerMapReport> discoveries = data.markCartographerMapDiscoveriesNear(
                player.getUUID(),
                level.dimension().location(),
                structureId -> structuresAtPlayer.computeIfAbsent(
                        structureId,
                        id -> isPlayerInsideStructure(level, player.blockPosition(), id)
                ),
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

    public static void rememberStoryHint(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime) {
        rememberStoryHint(level, villager, player, kind, targetId, targetName, targetPos, expiresAtGameTime, 0);
    }

    public static void rememberStoryHint(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime,
            int discoveryRadius) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        if (data.rememberStoryHint(
                villager.getUUID(),
                player.getUUID(),
                level.dimension().location(),
                kind,
                targetId,
                targetName,
                targetPos,
                expiresAtGameTime,
                discoveryRadius,
                level.getGameTime()
        )) {
            data.setDirty();
        }
    }

    public static List<StoryHintReport> markStoryHintDiscoveriesNear(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation currentBiomeId,
            double radius) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        Map<ResourceLocation, Boolean> structuresAtPlayer = new HashMap<>();
        List<StoryHintReport> discoveries = data.markStoryHintDiscoveriesNear(
                player.getUUID(),
                level.dimension().location(),
                currentBiomeId,
                structureId -> structuresAtPlayer.computeIfAbsent(
                        structureId,
                        id -> isPlayerInsideStructure(level, player.blockPosition(), id)
                ),
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

    public static DiscoveryReports markDiscoveriesNear(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation currentBiomeId,
            double mapRadius,
            double storyRadius) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        Map<ResourceLocation, Boolean> structuresAtPlayer = new HashMap<>();
        DiscoveryReports reports = data.markDiscoveriesNear(
                player.getUUID(),
                level.dimension().location(),
                currentBiomeId,
                structureId -> structuresAtPlayer.computeIfAbsent(
                        structureId,
                        id -> isPlayerInsideStructure(level, player.blockPosition(), id)
                ),
                player.getX(),
                player.getZ(),
                mapRadius * mapRadius,
                storyRadius * storyRadius,
                level.getGameTime()
        );
        if (!reports.cartographerMapReports().isEmpty() || !reports.storyHintReports().isEmpty()) {
            data.setDirty();
        }
        return reports;
    }

    private static boolean isPlayerInsideStructure(ServerLevel level, BlockPos playerPos, ResourceLocation structureId) {
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureId);
        return level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE)
                .getHolder(structureKey)
                .map(holder -> level.structureManager()
                        .getStructureWithPieceAt(playerPos, HolderSet.direct(holder))
                        .isValid())
                .orElse(false);
    }

    public static Optional<StoryHintReport> unreportedStoryHintDiscovery(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedStoryHintDiscovery(villager.getUUID(), player.getUUID()));
    }

    public static Optional<StoryHintReport> claimUnreportedStoryHintDiscovery(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        StoryHintReport report = data.claimUnreportedStoryHintDiscovery(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberShareableStory(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos,
            long expiresAtGameTime) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        if (data.rememberShareableStory(
                villager.getUUID(),
                player.getUUID(),
                level.dimension().location(),
                kind,
                targetId,
                targetName,
                targetPos,
                expiresAtGameTime,
                level.getGameTime()
        )) {
            data.setDirty();
        }
    }

    public static Optional<StoryHintReport> shareableStory(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .shareableStory(villager.getUUID(), player.getUUID(), level.getGameTime()));
    }

    public static Optional<StoryHintReport> claimShareableStory(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        StoryHintReport report = data.claimShareableStory(villager.getUUID(), player.getUUID(), level.getGameTime());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberCombatSurvivalReport(ServerLevel level, Villager villager, ServerPlayer player, String eventKind) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberCombatSurvivalReport(villager.getUUID(), player.getUUID(), eventKind, level.getGameTime());
        data.setDirty();
    }

    public static Optional<CombatSurvivalReport> unreportedCombatSurvivalReport(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedCombatSurvivalReport(villager.getUUID(), player.getUUID()));
    }

    public static Optional<CombatSurvivalReport> claimUnreportedCombatSurvivalReport(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        CombatSurvivalReport report = data.claimUnreportedCombatSurvivalReport(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberGearReport(ServerLevel level, Villager villager, ServerPlayer player, String gearKind) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberGearReport(villager.getUUID(), player.getUUID(), gearKind, level.getGameTime());
        data.setDirty();
    }

    public static Optional<GearReport> unreportedGearReport(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedGearReport(villager.getUUID(), player.getUUID()));
    }

    public static Optional<GearReport> claimUnreportedGearReport(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        GearReport report = data.claimUnreportedGearReport(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void markGearReportsUsedInCombat(ServerLevel level, Villager villager, boolean weaponEquipped, boolean armorEquipped) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        if (data.markGearReportsUsedInCombat(villager.getUUID(), weaponEquipped, armorEquipped)) {
            data.setDirty();
        }
    }

    public static void rememberRecruitmentFollowup(ServerLevel level, Villager villager, ServerPlayer player, String scenario) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberRecruitmentFollowup(villager.getUUID(), player.getUUID(), scenario, level.getGameTime());
        data.setDirty();
    }

    public static Optional<RecruitmentFollowupReport> unreportedRecruitmentFollowup(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedRecruitmentFollowup(villager.getUUID(), player.getUUID()));
    }

    public static Optional<RecruitmentFollowupReport> claimUnreportedRecruitmentFollowup(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        RecruitmentFollowupReport report = data.claimUnreportedRecruitmentFollowup(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberCuredRecognition(ServerLevel level, Villager villager, UUID playerId) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberCuredRecognition(villager.getUUID(), playerId, level.getGameTime());
        data.setDirty();
    }

    public static Optional<CuredRecognitionReport> unreportedCuredRecognition(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedCuredRecognition(villager.getUUID(), player.getUUID()));
    }

    public static Optional<CuredRecognitionReport> claimUnreportedCuredRecognition(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        CuredRecognitionReport report = data.claimUnreportedCuredRecognition(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberRecruitmentMemory(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String scenario,
            String biomeName,
            int distanceBlocks,
            boolean boatTrip,
            boolean oceanCrossing) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberRecruitmentMemory(
                villager.getUUID(),
                player.getUUID(),
                scenario,
                biomeName,
                distanceBlocks,
                boatTrip,
                oceanCrossing,
                level.getGameTime()
        );
        data.setDirty();
    }

    public static Optional<RecruitmentMemory> recruitmentMemory(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .recruitmentMemory(villager.getUUID(), player.getUUID()));
    }

    public static Optional<GiftAdviceResultReport> unreportedGiftAdviceResult(ServerLevel level, Villager villager, ServerPlayer player) {
        return Optional.ofNullable(VillagerInteractionSavedData.get(level)
                .unreportedGiftAdviceResult(villager.getUUID(), player.getUUID()));
    }

    public static Optional<GiftAdviceResultReport> claimUnreportedGiftAdviceResult(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        GiftAdviceResultReport report = data.claimUnreportedGiftAdviceResult(villager.getUUID(), player.getUUID());
        if (report != null) {
            data.setDirty();
        }
        return Optional.ofNullable(report);
    }

    public static void rememberGiftAdvice(
            ServerLevel level,
            Villager villager,
            ServerPlayer player,
            String itemId,
            String itemName,
            String targetProfessionKey) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        data.rememberGiftAdvice(villager.getUUID(), player.getUUID(), itemId, itemName, targetProfessionKey);
        data.setDirty();
    }

    public static void markGiftAdviceResult(
            ServerLevel level,
            Villager testedVillager,
            ServerPlayer player,
            String itemId,
            String itemName,
            String testedProfessionKey,
            String testedProfessionName,
            String testedVillagerName,
            boolean liked) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        if (data.markGiftAdviceResult(
                player.getUUID(),
                testedVillager.getUUID(),
                itemId,
                itemName,
                testedProfessionKey,
                testedProfessionName,
                testedVillagerName,
                liked,
                level.getGameTime())) {
            data.setDirty();
        }
    }

    public static long lastReputationGameTime(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType) {
        return VillagerInteractionSavedData.get(level)
                .getOrEmptyForRead(villager.getUUID(), player.getUUID())
                .lastReputationGameTime(requestType);
    }

    public static long lastDialogueGameTime(ServerLevel level, Villager villager, ServerPlayer player, DialogueRequestType requestType) {
        return VillagerInteractionSavedData.get(level)
                .getOrEmptyForRead(villager.getUUID(), player.getUUID())
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
                .getOrEmptyForRead(villager.getUUID(), player.getUUID())
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

    public static ContextReports contextReports(ServerLevel level, Villager villager, ServerPlayer player) {
        VillagerInteractionSavedData data = VillagerInteractionSavedData.get(level);
        return data.contextReports(villager.getUUID(), player.getUUID(), level.getGameTime());
    }

    public record ContextReports(
            CartographerMapReport cartographerMapReport,
            StoryHintReport storyHintReport,
            StoryHintReport shareableStoryReport,
            CombatSurvivalReport combatSurvivalReport,
            GearReport gearReport,
            RecruitmentFollowupReport recruitmentFollowupReport,
            CuredRecognitionReport curedRecognitionReport,
            RecruitmentMemory recruitmentMemory,
            GiftAdviceResultReport giftAdviceResultReport
    ) {
    }

    public record DiscoveryReports(
            List<CartographerMapReport> cartographerMapReports,
            List<StoryHintReport> storyHintReports
    ) {
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
            int giftAdviceFollowupUseCount,
            int mapReportUseCount,
            int storyHintReportUseCount,
            int combatSurvivalReportUseCount,
            int gearReportUseCount,
            int recruitmentFollowupUseCount,
            int curedRecognitionUseCount,
            int villageEventReportUseCount,
            int apologyUseCount,
            int villageDefenseReportUseCount,
            int storyUseCount,
            int shareStoryUseCount,
            int jokeUseCount,
            int insultUseCount,
            long lastApologyDialogueGameTime,
            long lastVillageDefenseReportGameTime,
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
                case GIFT_ADVICE_FOLLOWUP -> this.giftAdviceFollowupUseCount;
                case MAP_REPORT -> this.mapReportUseCount;
                case STORY_HINT_REPORT -> this.storyHintReportUseCount;
                case COMBAT_SURVIVAL_REPORT -> this.combatSurvivalReportUseCount;
                case GEAR_REPORT -> this.gearReportUseCount;
                case RECRUITMENT_FOLLOWUP -> this.recruitmentFollowupUseCount;
                case CURED_RECOGNITION -> this.curedRecognitionUseCount;
                case VILLAGE_EVENT_REPORT -> this.villageEventReportUseCount;
                case APOLOGY -> this.apologyUseCount;
                case VILLAGE_DEFENSE_REPORT -> this.villageDefenseReportUseCount;
                case STORY -> this.storyUseCount;
                case SHARE_STORY -> this.shareStoryUseCount;
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

    public enum StoryHintKind {
        BIOME,
        STRUCTURE
    }

    public record StoryHintReport(
            UUID villagerId,
            ResourceLocation dimension,
            StoryHintKind kind,
            ResourceLocation targetId,
            String targetName,
            BlockPos targetPos
    ) {
    }

    public record CombatSurvivalReport(
            UUID villagerId,
            String eventKind,
            long gameTime
    ) {
    }

    public record GearReport(
            UUID villagerId,
            String gearKind,
            boolean usedInCombat,
            long gameTime
    ) {
    }

    public record RecruitmentFollowupReport(
            UUID villagerId,
            String scenario,
            long gameTime
    ) {
    }

    public record CuredRecognitionReport(
            UUID villagerId,
            long gameTime
    ) {
    }

    public record RecruitmentMemory(
            UUID villagerId,
            String scenario,
            String biomeName,
            int distanceBlocks,
            boolean boatTrip,
            boolean oceanCrossing,
            long gameTime
    ) {
    }

    public record GiftAdviceResultReport(
            UUID villagerId,
            String itemId,
            String itemName,
            String testedProfessionKey,
            String testedProfessionName,
            String testedVillagerName,
            boolean liked,
            long gameTime
    ) {
    }
}
