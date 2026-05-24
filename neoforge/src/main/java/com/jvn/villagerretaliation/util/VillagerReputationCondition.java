package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public record VillagerReputationCondition(
        Set<VillagerReputationLevel> levels,
        Integer minReputation,
        Integer maxReputation) {
    private static final VillagerReputationCondition EMPTY = new VillagerReputationCondition(Set.of(), null, null);

    public static VillagerReputationCondition empty() {
        return EMPTY;
    }

    public static VillagerReputationCondition read(JsonObject entry) {
        Set<VillagerReputationLevel> levels = readLevels(entry);
        Integer minReputation = readOptionalInt(entry, "min_reputation").orElse(null);
        Integer maxReputation = readOptionalInt(entry, "max_reputation").orElse(null);
        if (levels.isEmpty() && minReputation == null && maxReputation == null) {
            return empty();
        }
        return new VillagerReputationCondition(levels, minReputation, maxReputation);
    }

    public boolean isEmpty() {
        return this.levels.isEmpty() && this.minReputation == null && this.maxReputation == null;
    }

    public boolean matches(int reputation, VillagerReputationLevel level) {
        if (!this.levels.isEmpty() && !this.levels.contains(level)) {
            return false;
        }
        if (this.minReputation != null && reputation < this.minReputation) {
            return false;
        }
        return this.maxReputation == null || reputation <= this.maxReputation;
    }

    private static Set<VillagerReputationLevel> readLevels(JsonObject entry) {
        EnumSet<VillagerReputationLevel> levels = EnumSet.noneOf(VillagerReputationLevel.class);
        for (String value : readStringList(entry, "reputation_level")) {
            readLevel(value).ifPresent(levels::add);
        }
        for (String value : readStringList(entry, "reputation_levels")) {
            readLevel(value).ifPresent(levels::add);
        }
        return Set.copyOf(levels);
    }

    private static Optional<VillagerReputationLevel> readLevel(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(VillagerReputationLevel.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> readOptionalInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsInt());
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return Optional.empty();
        }
    }

    private static List<String> readStringList(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive()) {
                continue;
            }
            String value = child.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }
}
