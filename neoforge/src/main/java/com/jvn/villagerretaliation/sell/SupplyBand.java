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

    public CurrencyAmount lowerBound() {
        return this.lowerBound;
    }

    public Optional<CurrencyAmount> upperBound() {
        return Optional.ofNullable(this.upperBound);
    }

    public CurrencyAmount multiplier() {
        return this.multiplier;
    }

    public static SupplyBand forPressure(CurrencyAmount pressure) {
        CurrencyAmount safe = pressure == null ? CurrencyAmount.ZERO : pressure;
        if (safe.compareTo(CurrencyAmount.of(16, 1)) < 0) {
            return FRESH;
        }
        if (safe.compareTo(CurrencyAmount.of(32, 1)) < 0) {
            return ACTIVE;
        }
        if (safe.compareTo(CurrencyAmount.of(64, 1)) < 0) {
            return SATURATED;
        }
        return GLUTTED;
    }
}
