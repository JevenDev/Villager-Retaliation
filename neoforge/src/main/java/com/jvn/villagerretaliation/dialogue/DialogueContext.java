package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.social.VillagerFamilyTreeSnapshot;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public record DialogueContext(
        ServerLevel level,
        ServerPlayer player,
        Villager villager,
        VillagerProfession profession,
        int reputation,
        VillagerReputationLevel reputationLevel,
        boolean firstConversation,
        boolean firstVillageInteraction,
        WeatherState weather,
        TimeOfDay timeOfDay,
        long lastPositiveDialogueReputationGameTime,
        long lastNegativeDialogueReputationGameTime,
        long lastJokeReputationGameTime,
        long lastApologyDialogueGameTime,
        long lastVillageDefenseReportGameTime,
        boolean badFirstImpression,
        long lastBrokenBedGameTime,
        long lastDirectHitGameTime,
        String lastDirectHitWeapon,
        VillagerInteractionTracker.CartographerMapReport cartographerMapReport,
        VillagerInteractionTracker.StoryHintReport storyHintReport,
        VillagerInteractionTracker.StoryHintReport shareableStoryReport,
        VillagerInteractionTracker.CombatSurvivalReport combatSurvivalReport,
        VillagerInteractionTracker.GearReport gearReport,
        VillagerInteractionTracker.RecruitmentFollowupReport recruitmentFollowupReport,
        VillagerInteractionTracker.CuredRecognitionReport curedRecognitionReport,
        VillagerInteractionTracker.RecruitmentMemory recruitmentMemory,
        VillagerInteractionTracker.GiftAdviceResultReport giftAdviceResultReport,
        VillagerFamilyTreeSnapshot familyTree,
        List<VillageEventMemory.MemoryEvent> recentEvents,
        RandomSource random,
        String locale
) {
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 60L * 20L;
    private static final long BROKEN_BED_MEMORY_TICKS = 20L * 60L * 20L;
    private static final long DIALOGUE_MOOD_MEMORY_TICKS = 20L * 60L * 5L;

    public boolean hasRecentEvent(VillageEventMemory.EventTag... tags) {
        return VillageEventMemory.hasAny(this.recentEvents, tags);
    }

    public boolean hasKnownFamily() {
        return this.familyTree.hasFamily();
    }

    public boolean hasKnownParent() {
        return this.familyTree.hasParent();
    }

    public boolean hasKnownSibling() {
        return this.familyTree.hasSibling();
    }

    public boolean hasKnownSpouse() {
        return this.familyTree.hasSpouse();
    }

    public boolean hasKnownChild() {
        return this.familyTree.hasChild();
    }

    public boolean hasRecentPlayerEvent(VillageEventMemory.EventTag... tags) {
        return VillageEventMemory.hasAnyForPlayer(this.recentEvents, this.player.getUUID(), tags);
    }

    public Optional<VillageEventMemory.MemoryEvent> recentGiftToThisVillager() {
        UUID playerId = this.player.getUUID();
        UUID villagerId = this.villager.getUUID();
        return this.recentEvents.stream()
                .filter(event -> event.gift() != null)
                .filter(event -> playerId.equals(event.playerId()))
                .filter(event -> villagerId.equals(event.sourceId()))
                .max(Comparator.comparingLong(VillageEventMemory.MemoryEvent::gameTime));
    }

    public Optional<VillageEventMemory.MemoryEvent> recentGiftToAnotherVillager() {
        UUID playerId = this.player.getUUID();
        UUID villagerId = this.villager.getUUID();
        return this.recentEvents.stream()
                .filter(event -> event.gift() != null)
                .filter(event -> playerId.equals(event.playerId()))
                .filter(event -> !villagerId.equals(event.sourceId()))
                .max(Comparator.comparingLong(VillageEventMemory.MemoryEvent::gameTime));
    }

    public boolean hasRecentDirectHitMemory() {
        return this.lastDirectHitGameTime != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastDirectHitGameTime <= DIRECT_HIT_MEMORY_TICKS;
    }

    public boolean hasRecentBrokenBedMemory() {
        return this.lastBrokenBedGameTime != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastBrokenBedGameTime <= BROKEN_BED_MEMORY_TICKS;
    }

    public Optional<VillagerInteractionTracker.CartographerMapReport> unreportedCartographerMapDiscovery() {
        return Optional.ofNullable(this.cartographerMapReport);
    }

    public boolean hasUnreportedCartographerMapDiscovery() {
        return this.cartographerMapReport != null;
    }

    public Optional<VillagerInteractionTracker.StoryHintReport> unreportedStoryHintDiscovery() {
        return Optional.ofNullable(this.storyHintReport);
    }

    public boolean hasUnreportedStoryHintDiscovery() {
        return this.storyHintReport != null;
    }

    public Optional<VillagerInteractionTracker.StoryHintReport> shareableStory() {
        return Optional.ofNullable(this.shareableStoryReport);
    }

    public boolean hasShareableStory() {
        return this.shareableStoryReport != null;
    }

    public Optional<VillagerInteractionTracker.CombatSurvivalReport> unreportedCombatSurvivalReport() {
        return Optional.ofNullable(this.combatSurvivalReport);
    }

    public boolean hasUnreportedCombatSurvivalReport() {
        return this.combatSurvivalReport != null;
    }

    public Optional<VillagerInteractionTracker.GearReport> unreportedGearReport() {
        return Optional.ofNullable(this.gearReport);
    }

    public boolean hasUnreportedGearReport() {
        return this.gearReport != null;
    }

    public boolean hasUnreportedGearReportUsedInCombat() {
        return this.gearReport != null && this.gearReport.usedInCombat();
    }

    public boolean hasUnreportedGearReportUnusedInCombat() {
        return this.gearReport != null && !this.gearReport.usedInCombat();
    }

    public String gearReportKind() {
        return this.gearReport == null || this.gearReport.gearKind() == null || this.gearReport.gearKind().isBlank()
                ? "gear"
                : this.gearReport.gearKind();
    }

    public Optional<VillagerInteractionTracker.RecruitmentFollowupReport> unreportedRecruitmentFollowup() {
        return Optional.ofNullable(this.recruitmentFollowupReport);
    }

    public boolean hasUnreportedRecruitmentFollowup() {
        return this.recruitmentFollowupReport != null;
    }

    public boolean hasRecruitmentFollowupScenario(String scenario) {
        return this.recruitmentFollowupReport != null
                && scenario != null
                && this.recruitmentFollowupReport.scenario().equalsIgnoreCase(scenario);
    }

    public String recruitmentFollowupScenario() {
        return this.recruitmentFollowupReport == null
                || this.recruitmentFollowupReport.scenario() == null
                || this.recruitmentFollowupReport.scenario().isBlank()
                ? "safe"
                : this.recruitmentFollowupReport.scenario();
    }

    public Optional<VillagerInteractionTracker.CuredRecognitionReport> unreportedCuredRecognition() {
        return Optional.ofNullable(this.curedRecognitionReport);
    }

    public boolean hasUnreportedCuredRecognition() {
        return this.curedRecognitionReport != null;
    }

    public boolean hasRecentVillageEventConcern() {
        return hasRecentEvent(
                VillageEventMemory.EventTag.THUNDERSTORM,
                VillageEventMemory.EventTag.SANDSTORM,
                VillageEventMemory.EventTag.SNOWSTORM,
                VillageEventMemory.EventTag.VILLAGE_FIRE,
                VillageEventMemory.EventTag.RAID,
                VillageEventMemory.EventTag.PLAYER_DEFENDED_RAID,
                VillageEventMemory.EventTag.NIGHT_ATTACK
        );
    }

    public Optional<VillagerInteractionTracker.RecruitmentMemory> recruitmentMemoryOptional() {
        return Optional.ofNullable(this.recruitmentMemory);
    }

    public boolean hasRecruitmentMemory() {
        return this.recruitmentMemory != null;
    }

    public boolean hasRecruitmentMemoryScenario(String scenario) {
        return this.recruitmentMemory != null
                && scenario != null
                && this.recruitmentMemory.scenario().equalsIgnoreCase(scenario);
    }

    public int recruitmentMemoryDistanceBlocks() {
        return this.recruitmentMemory == null ? 0 : this.recruitmentMemory.distanceBlocks();
    }

    public boolean hasRecruitmentMemoryBoatTrip() {
        return this.recruitmentMemory != null && this.recruitmentMemory.boatTrip();
    }

    public boolean hasRecruitmentMemoryOceanCrossing() {
        return this.recruitmentMemory != null && this.recruitmentMemory.oceanCrossing();
    }

    public boolean hasRecruitmentMemorySwimTrip() {
        return this.recruitmentMemory != null && this.recruitmentMemory.oceanCrossing() && !this.recruitmentMemory.boatTrip();
    }

    public String recruitmentMemoryBiome() {
        return this.recruitmentMemory == null
                || this.recruitmentMemory.biomeName() == null
                || this.recruitmentMemory.biomeName().isBlank()
                ? "the wilds"
                : this.recruitmentMemory.biomeName();
    }

    public Optional<VillagerInteractionTracker.GiftAdviceResultReport> unreportedGiftAdviceResult() {
        return Optional.ofNullable(this.giftAdviceResultReport);
    }

    public boolean hasUnreportedGiftAdviceResult() {
        return this.giftAdviceResultReport != null;
    }

    public boolean hasUnapologizedRememberedHarm() {
        long latestHarm = latestRememberedHarmGameTime();
        return latestHarm != Long.MIN_VALUE && latestHarm > this.lastApologyDialogueGameTime;
    }

    public boolean hasUnreportedVillageDefense() {
        long latestDefense = latestVillageDefenseGameTime();
        return latestDefense != Long.MIN_VALUE && latestDefense > this.lastVillageDefenseReportGameTime;
    }

    public long latestVillageDefenseGameTime() {
        long latest = Long.MIN_VALUE;
        UUID playerId = this.player.getUUID();
        for (VillageEventMemory.MemoryEvent event : this.recentEvents) {
            if (event.tag() == VillageEventMemory.EventTag.PLAYER_DEFENDED_RAID
                    && playerId.equals(event.playerId())) {
                latest = Math.max(latest, event.gameTime());
            }
        }
        return latest;
    }

    public long latestRememberedHarmGameTime() {
        long latest = Long.MIN_VALUE;
        if (hasRecentDirectHitMemory()) {
            latest = Math.max(latest, this.lastDirectHitGameTime);
        }
        if (hasRecentBrokenBedMemory()) {
            latest = Math.max(latest, this.lastBrokenBedGameTime);
        }
        UUID playerId = this.player.getUUID();
        for (VillageEventMemory.MemoryEvent event : this.recentEvents) {
            if (event.tag() == VillageEventMemory.EventTag.PLAYER_ATTACKED_VILLAGER
                    && playerId.equals(event.playerId())) {
                latest = Math.max(latest, event.gameTime());
            }
        }
        return latest;
    }

    public boolean hasRecentPositiveDialogueMoodMemory() {
        return this.lastPositiveDialogueReputationGameTime != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastPositiveDialogueReputationGameTime <= DIALOGUE_MOOD_MEMORY_TICKS;
    }

    public boolean hasRecentNegativeDialogueMoodMemory() {
        return this.lastNegativeDialogueReputationGameTime != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastNegativeDialogueReputationGameTime <= DIALOGUE_MOOD_MEMORY_TICKS;
    }

    public String rememberedAttackWeapon() {
        if (this.lastDirectHitWeapon == null || this.lastDirectHitWeapon.isBlank()) {
            return "fists";
        }
        return this.lastDirectHitWeapon;
    }

    public enum WeatherState {
        CLEAR,
        RAIN,
        THUNDER
    }

    public enum TimeOfDay {
        MORNING,
        AFTERNOON,
        EVENING,
        NIGHT
    }
}
