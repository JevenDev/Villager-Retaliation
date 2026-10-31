package com.jvn.villagerretaliation.util;

import com.jvn.toucanlib.util.ToucanTickThrottle;
import java.util.Map;
import java.util.UUID;

public final class TickThrottle {
    private TickThrottle() {
    }

    public static boolean consume(UUID id, Map<UUID, Long> nextTicks, long gameTime, long intervalTicks) {
        return ToucanTickThrottle.consume(id, nextTicks, gameTime, intervalTicks);
    }

    public static boolean isSpreadTick(long seed, long gameTime, long intervalTicks) {
        return ToucanTickThrottle.isSpreadTick(seed, gameTime, intervalTicks);
    }

    public static boolean isSpreadTick(UUID id, long gameTime, long intervalTicks) {
        return ToucanTickThrottle.isSpreadTick(id, gameTime, intervalTicks);
    }

    public static long spreadOffset(long seed, long intervalTicks) {
        return ToucanTickThrottle.spreadOffset(seed, intervalTicks);
    }

    public static long spreadOffset(UUID id, long intervalTicks) {
        return ToucanTickThrottle.spreadOffset(id, intervalTicks);
    }

    public static long stagger(UUID id, long intervalTicks) {
        return ToucanTickThrottle.stagger(id, intervalTicks);
    }
}
