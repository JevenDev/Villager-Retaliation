package com.jvn.villagerretaliation.util;

import java.util.List;
import net.minecraft.util.RandomSource;

public final class VillagerRetaliationRandomUtil {
    private VillagerRetaliationRandomUtil() {
    }

    public static boolean chance(RandomSource random, double chance) {
        return chance >= 1.0D || chance > 0.0D && random.nextDouble() < chance;
    }

    public static int between(RandomSource random, int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) {
            return minInclusive;
        }

        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    public static int index(RandomSource random, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Cannot choose from an empty collection");
        }

        return random.nextInt(size);
    }

    public static <T> T choose(RandomSource random, List<T> values) {
        return values.get(index(random, values.size()));
    }

    @SafeVarargs
    public static <T> T choose(RandomSource random, T... values) {
        return values[index(random, values.length)];
    }
}
