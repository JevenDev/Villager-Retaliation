package com.jvn.villagerretaliation.raid;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Tracks the short, server-side confirmation window for deliberate player raid declarations. */
final class PlayerRaidConfirmationTracker {
    static final long CONFIRMATION_WINDOW_TICKS = 30L * 20L;

    private final Map<ConfirmationKey, Long> armedUntil = new HashMap<>();

    boolean consumeOrArm(UUID playerId, VillageAllegianceId villageId, long now) {
        pruneExpired(now);
        ConfirmationKey key = new ConfirmationKey(playerId, villageId);
        Long expiresAt = armedUntil.remove(key);
        if (expiresAt != null) {
            return true;
        }
        armedUntil.put(key, now + CONFIRMATION_WINDOW_TICKS);
        return false;
    }

    void pruneExpired(long now) {
        armedUntil.values().removeIf(expiresAt -> now >= expiresAt);
    }

    private record ConfirmationKey(UUID playerId, VillageAllegianceId villageId) {
    }
}
