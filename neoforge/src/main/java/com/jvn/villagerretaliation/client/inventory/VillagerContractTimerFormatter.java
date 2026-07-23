package com.jvn.villagerretaliation.client.inventory;

import java.util.ArrayList;
import java.util.List;

/** Formats contract ticks as real elapsed time for the inventory countdown. */
public final class VillagerContractTimerFormatter {
    private static final long TICKS_PER_SECOND = 20L;
    private static final long TICKS_PER_MINUTE = 60L * TICKS_PER_SECOND;
    private static final long TICKS_PER_HOUR = 60L * TICKS_PER_MINUTE;
    private static final long TICKS_PER_DAY = 24L * TICKS_PER_HOUR;
    private static final List<Unit> UNITS = List.of(
            new Unit(TICKS_PER_DAY, "d"),
            new Unit(TICKS_PER_HOUR, "h"),
            new Unit(TICKS_PER_MINUTE, "m"),
            new Unit(TICKS_PER_SECOND, "s"));

    private VillagerContractTimerFormatter() {
    }

    /** Returns only the largest unit, for the small value beside the timer icon. */
    public static String compact(long remainingTicks) {
        long ticks = roundedToSeconds(remainingTicks);
        for (Unit unit : UNITS) {
            if (ticks >= unit.ticks()) {
                return ticks / unit.ticks() + unit.suffix();
            }
        }
        return "0s";
    }

    /** Returns every non-zero unit, for the timer hover tooltip. */
    public static String full(long remainingTicks) {
        long ticks = roundedToSeconds(remainingTicks);
        List<String> parts = new ArrayList<>();
        for (Unit unit : UNITS) {
            if (ticks < unit.ticks()) {
                continue;
            }
            long count = ticks / unit.ticks();
            ticks %= unit.ticks();
            parts.add(count + unit.suffix());
        }
        return parts.isEmpty() ? "0s" : String.join(", ", parts);
    }

    private static long roundedToSeconds(long remainingTicks) {
        long ticks = Math.max(0L, remainingTicks);
        if (ticks == 0L) {
            return 0L;
        }
        return Math.max(1L, (ticks + TICKS_PER_SECOND - 1L) / TICKS_PER_SECOND) * TICKS_PER_SECOND;
    }

    private record Unit(long ticks, String suffix) {
    }
}