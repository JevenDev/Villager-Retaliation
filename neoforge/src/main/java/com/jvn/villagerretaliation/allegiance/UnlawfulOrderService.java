package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public final class UnlawfulOrderService {
    private static final int REPUTATION_PENALTY = -2;
    private static final long DEDUPLICATION_TICKS = 5L;
    private static final Map<IncidentKey, Long> LAST_INCIDENTS = new HashMap<>();

    private UnlawfulOrderService() {
    }

    public static void record(ServerLevel level, Villager recruit, UUID responsiblePlayerId, UUID targetId) {
        if (level == null || recruit == null || responsiblePlayerId == null || targetId == null) {
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        IncidentKey key = new IncidentKey(recruit.getUUID(), responsiblePlayerId, targetId);
        Long previous = LAST_INCIDENTS.get(key);
        if (previous != null && now - previous < DEDUPLICATION_TICKS) {
            return;
        }
        LAST_INCIDENTS.put(key, now);
        VillagerReputationManager.addUnlawfulOrderReputation(level, recruit, responsiblePlayerId, REPUTATION_PENALTY);
        LAST_INCIDENTS.entrySet().removeIf(entry -> now - entry.getValue() > 200L);
    }

    public static void clearRuntimeState() {
        LAST_INCIDENTS.clear();
    }

    private record IncidentKey(UUID villagerId, UUID playerId, UUID targetId) {
    }
}
