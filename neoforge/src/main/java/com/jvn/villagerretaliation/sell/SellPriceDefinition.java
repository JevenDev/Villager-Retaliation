package com.jvn.villagerretaliation.sell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public record SellPriceDefinition(
        ResourceLocation id,
        Item item,
        List<SellRateDefinition> rates,
        ResourceLocation marketGroup) {

    public static final int MAX_ITEM_COUNT = 256;
    public static final int MAX_RATES = 256;
    public static final long MAX_CANDIDATE_COMBINATIONS = 65_536L;

    public SellPriceDefinition(ResourceLocation id, Item item, IntRange itemCount, IntRange currencyCount) {
        this(id, item, List.of(new SellRateDefinition(itemCount, currencyCount)));
    }

    public SellPriceDefinition(
            ResourceLocation id,
            Item item,
            IntRange itemCount,
            IntRange currencyCount,
            ResourceLocation marketGroup) {
        this(id, item, List.of(new SellRateDefinition(itemCount, currencyCount)), marketGroup);
    }

    public SellPriceDefinition(ResourceLocation id, Item item, List<SellRateDefinition> rates) {
        this(id, item, rates, item == null ? null : BuiltInRegistries.ITEM.getKey(item));
    }

    public SellPriceDefinition {
        rates = rates == null ? List.of() : List.copyOf(rates);
        if (id == null || item == null || rates.isEmpty() || marketGroup == null) {
            throw new IllegalArgumentException(
                    "Sell price definitions require an id, item, at least one rate, and a market group");
        }
        if (rates.size() > MAX_RATES) {
            throw new IllegalArgumentException(
                    "rates must contain at most " + MAX_RATES + " entries");
        }
        long combinations = 0L;
        for (SellRateDefinition rate : rates) {
            combinations += rate.combinationCount();
            if (combinations > MAX_CANDIDATE_COMBINATIONS) {
                throw new IllegalArgumentException(
                        "rates generate more than " + MAX_CANDIDATE_COMBINATIONS + " candidate combinations");
            }
        }
    }

    public List<CurrencyAmount> candidatePrices() {
        LinkedHashSet<CurrencyAmount> unique = new LinkedHashSet<>();
        for (SellRateDefinition rate : rates) {
            unique.addAll(rate.candidatePrices());
        }
        ArrayList<CurrencyAmount> sorted = new ArrayList<>(unique);
        sorted.sort(CurrencyAmount::compareTo);
        return List.copyOf(sorted);
    }

    public IntRange itemCount() {
        return rates.getFirst().itemCount();
    }

    public IntRange currencyCount() {
        return rates.getFirst().currencyCount();
    }

    public record IntRange(int min, int max) {
        public static final int MAX_SPAN = 256;

        public IntRange {
            if (min <= 0 || max < min || (long) max - min + 1L > MAX_SPAN) {
                throw new IllegalArgumentException(
                        "Sell price ranges must be positive, ordered, and contain at most " + MAX_SPAN + " values");
            }
        }

        public static IntRange fixed(int value) {
            return new IntRange(value, value);
        }

        public int size() {
            return max - min + 1;
        }
    }
}
