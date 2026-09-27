package com.jvn.villagerretaliation.settlement;

final class SettlementSimulationClock {
    private static final ThreadLocal<Long> RESUMED_AT = ThreadLocal.withInitial(() -> 0L);

    private SettlementSimulationClock() {
    }

    static int elapsed(long checkedAt, long now) {
        return (int) Math.clamp(now - Math.max(checkedAt, RESUMED_AT.get()), 0L, 200L);
    }

    static void run(SettlementRecord town, Runnable work) {
        long previous = RESUMED_AT.get();
        RESUMED_AT.set(town.simulationResumedAt());
        try {
            work.run();
        } finally {
            RESUMED_AT.set(previous);
        }
    }
}
