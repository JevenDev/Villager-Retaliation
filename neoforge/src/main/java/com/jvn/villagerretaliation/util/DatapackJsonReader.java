package com.jvn.villagerretaliation.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.toucanlib.util.ToucanDatapackJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class DatapackJsonReader {
    private DatapackJsonReader() {
    }

    public static JsonObject readObject(JsonObject entry, String key) {
        return ToucanDatapackJson.readObject(entry, key);
    }

    public static String readString(JsonObject entry, String... keys) {
        return ToucanDatapackJson.readString(entry, keys);
    }

    public static List<String> readStringList(JsonObject entry, String... keys) {
        return ToucanDatapackJson.readStringList(entry, keys);
    }

    public static List<String> readStringValues(JsonElement element) {
        if (element == null || element.isJsonNull()) return List.of();
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (!child.isJsonPrimitive()) continue;
            String value = child.getAsString().trim();
            if (!value.isBlank()) values.add(value);
        }
        return List.copyOf(values);
    }

    public static List<String> readLines(JsonObject entry) {
        return ToucanDatapackJson.readLines(entry);
    }

    public static int readInt(JsonObject entry, String key, int fallback) {
        return readInt(entry.get(key), fallback);
    }

    public static int readInt(JsonObject entry, String snakeKey, String camelKey, int fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readInt(element, fallback);
    }

    public static int readInt(JsonElement element, int fallback) {
        return ToucanDatapackJson.readInt(element, fallback);
    }

    public static Integer readNullableInt(JsonObject entry, String key) {
        return ToucanDatapackJson.readNullableInt(entry, key);
    }

    public static Integer readNullableInt(JsonElement element) {
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
        return ToucanDatapackJson.readDurationTicks(entry, baseName, fallback, true);
    }

    public static Long readNullableLong(JsonObject entry, String key) {
        return ToucanDatapackJson.readNullableLong(entry, key);
    }

    public static double readDouble(JsonObject entry, String key, double fallback) {
        return readDouble(entry.get(key), fallback);
    }

    public static double readDouble(JsonObject entry, String snakeKey, String camelKey, double fallback) {
        JsonElement element = entry.has(snakeKey) ? entry.get(snakeKey) : entry.get(camelKey);
        return readDouble(element, fallback);
    }

    public static double readDouble(JsonElement element, double fallback) {
        return ToucanDatapackJson.readDouble(element, fallback);
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
        return ToucanDatapackJson.readBoolean(element, fallback);
    }

    public static boolean hasPrimitive(JsonObject entry, String key) {
        return ToucanDatapackJson.hasPrimitive(entry, key);
    }

    public static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        return readEnum(readString(entry, key), enumClass);
    }

    public static <E extends Enum<E>> Optional<E> readEnum(String value, Class<E> enumClass) {
        return ToucanDatapackJson.readEnum(value, enumClass);
    }

    public static <E extends Enum<E>> Set<E> readEnumSet(JsonObject entry, String key, Class<E> enumClass) {
        return ToucanDatapackJson.readEnumSet(entry, key, enumClass);
    }

    public static Optional<ResourceLocation> readResourceLocation(JsonObject entry, String key) {
        return parseResourceLocation(readString(entry, key));
    }

    public static Optional<ResourceLocation> parseResourceLocation(String value) {
        return ToucanDatapackJson.parseResourceLocation(value);
    }

    public static Set<ResourceLocation> readResourceLocations(JsonObject entry, String... keys) {
        return ToucanDatapackJson.readResourceLocations(entry, keys);
    }
}
