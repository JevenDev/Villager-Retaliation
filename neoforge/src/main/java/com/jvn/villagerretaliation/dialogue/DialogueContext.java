package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
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
        WeatherState weather,
        TimeOfDay timeOfDay,
        long lastPositiveDialogueReputationGameTime,
        long lastNegativeDialogueReputationGameTime,
        long lastJokeReputationGameTime,
        long lastApologyDialogueGameTime,
        boolean badFirstImpression,
        long lastBrokenBedGameTime,
        long lastDirectHitGameTime,
        String lastDirectHitWeapon,
        VillagerInteractionTracker.CartographerMapReport cartographerMapReport,
        VillagerInteractionTracker.CombatSurvivalReport combatSurvivalReport,
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

    public boolean hasRecentPlayerEvent(VillageEventMemory.EventTag... tags) {
        UUID playerId = this.player.getUUID();
        return this.recentEvents.stream().anyMatch(event ->
                event.playerId() != null
                        && event.playerId().equals(playerId)
                        && java.util.List.of(tags).contains(event.tag()));
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

    public Optional<VillagerInteractionTracker.CombatSurvivalReport> unreportedCombatSurvivalReport() {
        return Optional.ofNullable(this.combatSurvivalReport);
    }

    public boolean hasUnreportedCombatSurvivalReport() {
        return this.combatSurvivalReport != null;
    }

    public boolean hasUnapologizedRememberedHarm() {
        long latestHarm = latestRememberedHarmGameTime();
        return latestHarm != Long.MIN_VALUE && latestHarm > this.lastApologyDialogueGameTime;
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
