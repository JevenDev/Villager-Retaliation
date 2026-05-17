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

    public static <T> T choose(RandomSource random, List<T> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot choose from an empty list");
        }

        return values.get(random.nextInt(values.size()));
    }
}
