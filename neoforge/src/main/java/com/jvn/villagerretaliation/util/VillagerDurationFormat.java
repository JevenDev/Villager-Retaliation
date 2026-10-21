package com.jvn.villagerretaliation.util;

import java.util.ArrayList;
import java.util.List;

public final class VillagerDurationFormat {
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final List<UnitDefinition> DISPLAY_UNITS_DESC = List.of(
            new UnitDefinition("Y", 365L * SECONDS_PER_DAY),
            new UnitDefinition("MO", 30L * SECONDS_PER_DAY),
            new UnitDefinition("W", 7L * SECONDS_PER_DAY),
            new UnitDefinition("D", SECONDS_PER_DAY),
            new UnitDefinition("H", 3_600L),
            new UnitDefinition("M", 60L),
            new UnitDefinition("S", 1L));

    private VillagerDurationFormat() { }

    public static String formatGameDays(double days) {
        if (Double.isNaN(days) || days <= 0) return "0D";
        if (Double.isInfinite(days)) return "∞";
        // use calendar units within a game day rather than real tick durations
        long seconds = (long) (days * SECONDS_PER_DAY);
        List<String> parts = new ArrayList<>(2);
        for (UnitDefinition unit : DISPLAY_UNITS_DESC) {
            if (seconds < unit.secondsPerUnit()) continue;
            long unitCount = seconds / unit.secondsPerUnit();
            seconds %= unit.secondsPerUnit();
            parts.add(unitCount + unit.compactSuffix());
            if (parts.size() == 2) break;
        }
        return parts.isEmpty() ? "<1S" : String.join(", ", parts);
    }

    private record UnitDefinition(String compactSuffix, long secondsPerUnit) { }
}
