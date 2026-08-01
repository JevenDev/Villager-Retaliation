package com.jvn.villagerretaliation.sell;

import java.math.BigInteger;

public record CommodityMarketState(CurrencyAmount pressure, long lastUpdatedDay) {
    public CommodityMarketState {
        pressure = VillageMarketPolicy.sanitizePressure(pressure);
    }

    public CommodityMarketState recover(long currentDay) {
        if (currentDay <= this.lastUpdatedDay) {
            return this;
        }
        BigInteger elapsedDays = BigInteger.valueOf(currentDay).subtract(BigInteger.valueOf(this.lastUpdatedDay));
        BigInteger daysToFullRecovery = this.pressure.numerator()
                .multiply(VillageMarketPolicy.PRESSURE_RECOVERY_PER_DAY.denominator())
                .divide(this.pressure.denominator()
                        .multiply(VillageMarketPolicy.PRESSURE_RECOVERY_PER_DAY.numerator()))
                .add(BigInteger.ONE);
        if (elapsedDays.compareTo(daysToFullRecovery) >= 0) {
            return new CommodityMarketState(CurrencyAmount.ZERO, currentDay);
        }
        CurrencyAmount recovery =
                VillageMarketPolicy.PRESSURE_RECOVERY_PER_DAY.multiply(elapsedDays.longValueExact());
        return new CommodityMarketState(this.pressure.subtractClamped(recovery), currentDay);
    }

    public CommodityMarketState add(CurrencyAmount added, long currentDay) {
        CommodityMarketState recovered = this.recover(currentDay);
        CurrencyAmount safeAdded = VillageMarketPolicy.sanitizePressure(added);
        return new CommodityMarketState(
                VillageMarketPolicy.sanitizePressure(recovered.pressure.add(safeAdded)),
                recovered.lastUpdatedDay);
    }

    public CommodityMarketState merge(CommodityMarketState other, long currentDay) {
        CommodityMarketState left = this.recover(currentDay);
        CommodityMarketState right = other == null
                ? new CommodityMarketState(CurrencyAmount.ZERO, currentDay)
                : other.recover(currentDay);
        return new CommodityMarketState(
                VillageMarketPolicy.sanitizePressure(left.pressure.add(right.pressure)),
                Math.max(left.lastUpdatedDay, right.lastUpdatedDay));
    }
}
