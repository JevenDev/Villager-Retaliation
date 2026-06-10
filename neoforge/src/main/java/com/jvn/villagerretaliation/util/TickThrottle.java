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

    public static long stagger(UUID id, long intervalTicks) {
        if (intervalTicks <= 1L) {
            return 0L;
        }
        return Math.floorMod(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), intervalTicks);
    }
}
