package com.jvn.villagerretaliation.allegiance;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class VillageNameGenerator {
    private static final String[] FIRST = {
            "Ash", "Autumn", "Black", "Bright", "Cinder", "Cloud", "Cold", "Dawn",
            "Deep", "Dragon", "Eagle", "Elder", "Ember", "Fair", "Fallow", "Fern",
            "Frost", "Gold", "Gray", "Green", "High", "Iron", "Kings", "Lake",
            "Little", "Long", "Maple", "Mist", "Moon", "Moss", "North", "Oak",
            "Old", "Pine", "Raven", "Red", "River", "Rose", "Silver", "Snow",
            "South", "Star", "Stone", "Storm", "Summer", "Sun", "Thorn", "West",
            "White", "Wild", "Willow", "Wind", "Winter", "Wolf"
    };
    private static final String[] SECOND = {
            "barrow", "brook", "bury", "cliff", "combe", "cross", "dale", "den",
            "fall", "field", "ford", "gate", "glen", "grove", "hall", "haven",
            "hearth", "hill", "hold", "hollow", "keep", "mere", "mill", "moor",
            "pass", "reach", "rest", "ridge", "stead", "stone", "vale", "watch",
            "water", "wick", "wood", "worth"
    };

    private VillageNameGenerator() {
    }

    static String generate(UUID id, Set<String> unavailableNames) {
        long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
        int attempts = FIRST.length * SECOND.length;
        for (int attempt = 0; attempt < attempts; attempt++) {
            int firstIndex = Math.floorMod(Long.hashCode(seed + attempt * 0x9E3779B97F4A7C15L), FIRST.length);
            int secondIndex = Math.floorMod(Long.hashCode(Long.rotateLeft(seed, 17) + attempt * 31L), SECOND.length);
            String candidate = FIRST[firstIndex] + SECOND[secondIndex];
            if (!unavailableNames.contains(normalize(candidate))) {
                return candidate;
            }
        }
        String base = FIRST[Math.floorMod(Long.hashCode(seed), FIRST.length)]
                + SECOND[Math.floorMod(Long.hashCode(Long.rotateLeft(seed, 17)), SECOND.length)];
        int suffix = 2;
        while (unavailableNames.contains(normalize(base + " " + suffix))) {
            suffix++;
        }
        return base + " " + suffix;
    }

    static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        return value ^ value >>> 33;
    }
}
