package com.jvn.villagerretaliation.sell;

public final class VillageMarketPolicy {
    public static final long DAY_TICKS = 24_000L;
    public static final CurrencyAmount PRESSURE_RECOVERY_PER_DAY = CurrencyAmount.of(16, 1);
    public static final CurrencyAmount MAX_SAVED_PRESSURE = CurrencyAmount.of(1_000_000, 1);
    public static final CurrencyAmount MIN_EFFECTIVE_MULTIPLIER = CurrencyAmount.of(25, 100);
    public static final CurrencyAmount MAX_EFFECTIVE_MULTIPLIER = CurrencyAmount.of(150, 100);

    private VillageMarketPolicy() {
    }

    public static CurrencyAmount effectiveMultiplier(DailyDemandBand demand, SupplyBand supply) {
        CurrencyAmount multiplied = demand.multiplier().multiply(supply.multiplier());
        return multiplied.max(MIN_EFFECTIVE_MULTIPLIER).min(MAX_EFFECTIVE_MULTIPLIER);
    }

    public static CurrencyAmount sanitizePressure(CurrencyAmount pressure) {
        if (pressure == null) {
            return CurrencyAmount.ZERO;
        }
        return pressure.min(MAX_SAVED_PRESSURE);
    }
}
