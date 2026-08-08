package com.jvn.villagerretaliation.sell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record SellRateDefinition(
        SellPriceDefinition.IntRange itemCount,
        SellPriceDefinition.IntRange currencyCount) {

    public SellRateDefinition {
        if (itemCount == null || currencyCount == null) {
            throw new IllegalArgumentException("Sell rates require item_count and currency_count");
        }
        if (itemCount.max() > SellPriceDefinition.MAX_ITEM_COUNT) {
            throw new IllegalArgumentException(
                    "item_count must not exceed " + SellPriceDefinition.MAX_ITEM_COUNT);
        }
    }

    long combinationCount() {
        return (long) itemCount.size() * currencyCount.size();
    }

    List<CurrencyAmount> candidatePrices() {
        LinkedHashSet<CurrencyAmount> unique = new LinkedHashSet<>();
        for (long items = itemCount.min(); items <= itemCount.max(); items++) {
            for (long currency = currencyCount.min(); currency <= currencyCount.max(); currency++) {
                unique.add(CurrencyAmount.of(currency, items));
            }
        }
        return new ArrayList<>(unique);
    }
}
