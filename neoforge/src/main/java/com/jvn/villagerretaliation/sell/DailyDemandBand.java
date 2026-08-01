package com.jvn.villagerretaliation.sell;

public enum DailyDemandBand {
    VERY_HIGH(150, 100),
    HIGH(125, 100),
    NORMAL(100, 100),
    LOW(85, 100),
    VERY_LOW(70, 100);

    private final CurrencyAmount multiplier;

    DailyDemandBand(long numerator, long denominator) {
        this.multiplier = CurrencyAmount.of(numerator, denominator);
    }

    public CurrencyAmount multiplier() {
        return this.multiplier;
    }
}
