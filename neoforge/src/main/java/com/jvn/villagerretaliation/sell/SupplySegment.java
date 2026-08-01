package com.jvn.villagerretaliation.sell;

public record SupplySegment(
        SupplyBand supplyBand,
        CurrencyAmount baseValue,
        CurrencyAmount effectiveMultiplier,
        CurrencyAmount payout) {

    public SupplySegment {
        if (supplyBand == null || baseValue == null || effectiveMultiplier == null || payout == null) {
            throw new IllegalArgumentException("Supply segments require a band and exact values");
        }
    }
}
