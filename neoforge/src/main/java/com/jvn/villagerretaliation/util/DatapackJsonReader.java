package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class DatapackJsonReader {
    private DatapackJsonReader() {
    }

    public static JsonObject readObject(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
    }

    public static String readString(JsonObject entry, String... keys) {
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString().trim();
            }
        }
        return "";
    }

    public static List<String> readStringList(JsonObject entry, String... keys) {
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            JsonElement element = entry.get(key);
            if (element == null || element.isJsonNull()) {
                continue;
            }
            if (element.isJsonPrimitive()) {
                String value = element.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
                continue;
            }
            if (!element.isJsonArray()) {
                continue;
            }

            for (JsonElement child : element.getAsJsonArray()) {
                if (!child.isJsonPrimitive()) {
                    continue;
                }
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    public static List<String> readLines(JsonObject entry) {
        List<String> lines = readStringList(entry, "lines");
        if (!lines.isEmpty()) {
            return lines;
        }
        String text = readString(entry, "text");
        return text.isBlank() ? List.of() : List.of(text);
    }

    public static int readInt(JsonObject entry, String key, int fallback) {
        return readInt(entry.get(key), fallback);
    }

    public static int readInt(JsonObject entry, String snakeKey, String camelKey, int fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readInt(element, fallback);
    }

    public static int readInt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    public static Integer readNullableInt(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    public static Optional<Integer> readOptionalInt(JsonObject entry, String key) {
        return Optional.ofNullable(readNullableInt(entry, key));
    }

    public static long readDurationTicks(JsonObject entry, String baseName, long fallback) {
        Long ticks = readNullableLong(entry, baseName + "_ticks");
        if (ticks != null) {
            return Math.max(0L, ticks);
        }
        Long days = readNullableLong(entry, baseName + "_days");
        if (days != null) {
            return Math.max(0L, days * 24000L);
        }
        Long seconds = readNullableLong(entry, baseName + "_seconds");
        if (seconds != null) {
            return Math.max(0L, seconds * 20L);
        }
        return fallback;
    }

    public static Long readNullableLong(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    public static double readDouble(JsonObject entry, String key, double fallback) {
        return readDouble(entry.get(key), fallback);
    }

    public static double readDouble(JsonObject entry, String snakeKey, String camelKey, double fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readDouble(element, fallback);
    }

    public static double readDouble(JsonElement element, double fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    public static boolean readBoolean(JsonObject entry, String key) {
        return readBoolean(entry.get(key), false);
    }

    public static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        return readBoolean(entry.get(key), fallback);
    }

    public static boolean readBoolean(JsonObject entry, String snakeKey, String camelKey, boolean fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readBoolean(element, fallback);
    }

    public static boolean readBoolean(JsonElement element, boolean fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return Boolean.parseBoolean(primitive.getAsString());
        }
        return fallback;
    }

    public static boolean hasPrimitive(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive();
    }

    public static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return readEnum(readString(entry, key), enumClass);
    }

    public static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static <E extends Enum<E>> Set<E> readEnumSet(JsonObject entry, String key, Class<E> enumClass) {
        Set<E> values = new LinkedHashSet<>();
        for (String value : readStringList(entry, key)) {
            readEnum(value, enumClass).ifPresent(values::add);
        }
        return values;
    }

    public static Optional<ResourceLocation> readResourceLocation(JsonObject entry, String key) {
        return parseResourceLocation(readString(entry, key));
    }

    public static Optional<ResourceLocation> parseResourceLocation(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(normalized));
    }

    public static Set<ResourceLocation> readResourceLocations(JsonObject entry, String... keys) {
        Set<ResourceLocation> locations = new LinkedHashSet<>();
        for (String key : keys) {
            for (String value : readStringList(entry, key)) {
                parseResourceLocation(value).ifPresent(locations::add);
            }
        }
        return locations;
    }
}
