package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
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
        long lastDirectHitGameTime,
        String lastDirectHitWeapon,
        List<VillageEventMemory.MemoryEvent> recentEvents,
        RandomSource random
) {
    private static final long DIRECT_HIT_MEMORY_TICKS = 20L * 60L * 20L;

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

    public boolean hasRecentDirectHitMemory() {
        return this.lastDirectHitGameTime != Long.MIN_VALUE
                && this.level.getGameTime() - this.lastDirectHitGameTime <= DIRECT_HIT_MEMORY_TICKS;
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
