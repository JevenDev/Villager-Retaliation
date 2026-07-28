package com.jvn.villagerretaliation.sell;

import com.jvn.villagerretaliation.interaction.VillagerCurrencyResources;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DailySellMarket {
    private static final long DAY_TICKS = 24000L;

    private DailySellMarket() {
    }

    public static Optional<CurrencyAmount> price(MinecraftServer server, ItemStack stack) {
        return SellPriceResources.definition(server, stack).map(definition -> price(server, definition));
    }

    public static CurrencyAmount value(MinecraftServer server, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CurrencyAmount.ZERO;
        }
        return price(server, stack).orElse(CurrencyAmount.ZERO).multiply(stack.getCount());
    }

    public static Map<ResourceLocation, CurrencyAmount> snapshot(MinecraftServer server) {
        Map<ResourceLocation, CurrencyAmount> prices = new LinkedHashMap<>();
        SellPriceResources.definitions(server).forEach((item, definition) -> {
            if (!VillagerCurrencyResources.isCurrency(server, new ItemStack(item))) {
                prices.put(BuiltInRegistries.ITEM.getKey(item), price(server, definition));
            }
        });
        return Map.copyOf(prices);
    }

    public static long currentDay(MinecraftServer server) {
        return Math.floorDiv(server.overworld().getDayTime(), DAY_TICKS);
    }

    private static CurrencyAmount price(MinecraftServer server, SellPriceDefinition definition) {
        return selectPrice(server.overworld().getSeed(), currentDay(server), definition);
    }

    static CurrencyAmount selectPrice(long worldSeed, long day, SellPriceDefinition definition) {
        List<CurrencyAmount> candidates = definition.candidatePrices();
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }

        int size = candidates.size();
        long identity = mix64(worldSeed ^ hash(definition.id()));
        int start = Math.floorMod(identity, size);
        int step = 1 + Math.floorMod(mix64(identity ^ 0x9E3779B97F4A7C15L), size - 1);
        while (greatestCommonDivisor(step, size) != 1) {
            step++;
            if (step >= size) {
                step = 1;
            }
        }
        int dayOffset = Math.floorMod(day, size);
        int index = (int) Math.floorMod((long) start + (long) dayOffset * step, size);
        return candidates.get(index);
    }

    private static long hash(ResourceLocation id) {
        long hash = 0xcbf29ce484222325L;
        String value = id.toString();
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static int greatestCommonDivisor(int first, int second) {
        int left = Math.abs(first);
        int right = Math.abs(second);
        while (right != 0) {
            int remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }
}
