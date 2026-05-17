package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.List;
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
        List<VillageEventMemory.MemoryEvent> recentEvents,
        RandomSource random
) {
    public enum WeatherState {
        CLEAR,
        RAIN,
        THUNDER
    }
}
