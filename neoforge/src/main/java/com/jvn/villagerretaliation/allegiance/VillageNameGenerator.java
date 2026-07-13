package com.jvn.villagerretaliation.allegiance;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class VillageNameGenerator {
    private static final int MAX_COMBINATION_ATTEMPTS = 100_000;

    private VillageNameGenerator() {
    }

    static String generate(UUID id, Set<String> unavailableNames) {
        VillageNameResources.NamePool namePool = VillageNameResources.currentNamePool();
        if (namePool.isEmpty()) {
            return fallbackName(id, unavailableNames);
        }
        List<String> prefixes = namePool.prefixes();
        List<String> suffixes = namePool.suffixes();
        long seed = mix(id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23));
        long combinationCount = (long) prefixes.size() * suffixes.size();
        int attempts = (int) Math.min(combinationCount, MAX_COMBINATION_ATTEMPTS);
        int start = Math.floorMod(Long.hashCode(seed), attempts);
        int step = coprimeStep(Long.hashCode(Long.rotateLeft(seed, 17)), attempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            long combinationIndex = combinationCount <= MAX_COMBINATION_ATTEMPTS
                    ? (start + (long) attempt * step) % attempts
                    : Math.floorMod(mix(seed + attempt * 0x9E3779B97F4A7C15L), combinationCount);
            int firstIndex = (int) (combinationIndex / suffixes.size());
            int secondIndex = (int) (combinationIndex % suffixes.size());
            String candidate = prefixes.get(firstIndex) + suffixes.get(secondIndex);
            if (isSafeName(candidate) && !unavailableNames.contains(normalize(candidate))) {
                return candidate;
            }
        }
        return fallbackName(id, unavailableNames);
    }

    private static int coprimeStep(int seed, int size) {
        if (size <= 1) {
            return 1;
        }
        int step = Math.floorMod(seed, size - 1) + 1;
        while (greatestCommonDivisor(step, size) != 1) {
            step = step == size - 1 ? 1 : step + 1;
        }
        return step;
    }

    private static int greatestCommonDivisor(int first, int second) {
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    private static String fallbackName(UUID id, Set<String> unavailableNames) {
        String base = "Village " + id.toString().substring(0, 8);
        int suffix = 2;
        while (unavailableNames.contains(normalize(base + " " + suffix))) {
            suffix++;
        }
        return unavailableNames.contains(normalize(base)) ? base + " " + suffix : base;
    }

    private static boolean isSafeName(String name) {
        return !name.isBlank()
                && name.codePointCount(0, name.length()) <= 32
                && name.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint) || codePoint == '\u00a7');
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
