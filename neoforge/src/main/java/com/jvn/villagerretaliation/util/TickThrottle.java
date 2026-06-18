package com.jvn.villagerretaliation.util;

import java.util.Map;
import java.util.UUID;

public final class TickThrottle {
    private TickThrottle() {
    }

    public static boolean consume(UUID id, Map<UUID, Long> nextTicks, long gameTime, long intervalTicks) {
        Long nextTick = nextTicks.get(id);
        if (nextTick == null) {
            long firstTick = gameTime + stagger(id, intervalTicks);
            if (firstTick > gameTime) {
                nextTicks.put(id, firstTick);
                return false;
            }
        } else if (gameTime < nextTick) {
            return false;
        }

        nextTicks.put(id, gameTime + Math.max(1L, intervalTicks));
        return true;
    }

    public static boolean isSpreadTick(long seed, long gameTime, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return true;
        }
        return Math.floorMod(gameTime, intervalTicks) == spreadOffset(seed, intervalTicks);
    }

    public static boolean isSpreadTick(UUID id, long gameTime, long intervalTicks) {
        return isSpreadTick(id.getLeastSignificantBits(), gameTime, intervalTicks);
    }

    public static long spreadOffset(long seed, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return 0L;
        }
        return Math.floorMod(seed, intervalTicks);
    }

    public static long spreadOffset(UUID id, long intervalTicks) {
        return spreadOffset(id.getLeastSignificantBits(), intervalTicks);
    }

    public static long stagger(UUID id, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return 0L;
        }
        return Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), intervalTicks);
    }
}
