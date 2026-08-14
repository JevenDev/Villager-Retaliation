package com.jvn.villagerretaliation.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ContentTags {
    private ContentTags() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replace(':', '.')
                .replace('/', '.')
                .replaceAll("[^a-z0-9_.-]+", "_");
        while (normalized.contains("..")) normalized = normalized.replace("..", ".");
        return normalized.replaceAll("^[._-]+|[._-]+$", "");
    }

    public static Set<String> normalizeAll(Collection<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String tag = normalize(value);
            if (!tag.isBlank()) normalized.add(tag);
        }
        return Collections.unmodifiableSet(normalized);
    }

    public static <V> Map<String, V> normalizeKeys(Map<String, V> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, V> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String tag = normalize(key);
            if (!tag.isBlank()) normalized.put(tag, value);
        });
        return Collections.unmodifiableMap(normalized);
    }
}
