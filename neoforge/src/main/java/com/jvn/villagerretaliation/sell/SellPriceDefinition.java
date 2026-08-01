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
        IntRange itemCount,
        IntRange currencyCount,
        ResourceLocation marketGroup) {

    public SellPriceDefinition(ResourceLocation id, Item item, IntRange itemCount, IntRange currencyCount) {
        this(id, item, itemCount, currencyCount, item == null ? null : BuiltInRegistries.ITEM.getKey(item));
    }

    public SellPriceDefinition {
        if (id == null || item == null || itemCount == null || currencyCount == null || marketGroup == null) {
            throw new IllegalArgumentException(
                    "Sell price definitions require an id, item, both ranges, and a market group");
        }
    }

    public List<CurrencyAmount> candidatePrices() {
        LinkedHashSet<CurrencyAmount> unique = new LinkedHashSet<>();
        for (long items = this.itemCount.min(); items <= this.itemCount.max(); items++) {
            for (long currency = this.currencyCount.min(); currency <= this.currencyCount.max(); currency++) {
                unique.add(CurrencyAmount.of(currency, items));
            }
        }
        ArrayList<CurrencyAmount> sorted = new ArrayList<>(unique);
        sorted.sort(CurrencyAmount::compareTo);
        return List.copyOf(sorted);
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
    }
}
