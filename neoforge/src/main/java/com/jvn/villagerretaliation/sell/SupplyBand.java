package com.jvn.villagerretaliation.sell;

import java.util.Optional;

public enum SupplyBand {
    FRESH(0, 16, 100, 100),
    ACTIVE(16, 32, 75, 100),
    SATURATED(32, 64, 50, 100),
    GLUTTED(64, null, 25, 100);

    private final CurrencyAmount lowerBound;
    private final CurrencyAmount upperBound;
    private final CurrencyAmount multiplier;

    SupplyBand(long lowerBound, Integer upperBound, long multiplierNumerator, long multiplierDenominator) {
        this.lowerBound = CurrencyAmount.of(lowerBound, 1);
        this.upperBound = upperBound == null ? null : CurrencyAmount.of(upperBound, 1);
        this.multiplier = CurrencyAmount.of(multiplierNumerator, multiplierDenominator);
    }

    public Optional<CurrencyAmount> upperBound() {
        return Optional.ofNullable(this.upperBound);
    }

    public CurrencyAmount multiplier() {
        return this.multiplier;
    }

    public static SupplyBand forPressure(CurrencyAmount pressure) {
        CurrencyAmount safe = pressure == null ? CurrencyAmount.ZERO : pressure;
        if (safe.compareTo(ACTIVE.lowerBound) < 0) {
            return FRESH;
        }
        if (safe.compareTo(SATURATED.lowerBound) < 0) {
            return ACTIVE;
        }
        if (safe.compareTo(GLUTTED.lowerBound) < 0) {
            return SATURATED;
        }
        return GLUTTED;
    }
}
