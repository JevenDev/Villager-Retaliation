package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class DangerousStructureStoryResources {
    private static final String RESOURCE_ROOT = "story_structures";
    private static final int DEFAULT_RADIUS = 96;

    private static volatile CachedStructures cachedStructures = new CachedStructures(null, List.of());

    private DangerousStructureStoryResources() {
    }

    public static void warm(MinecraftServer server) {
        entries(server);
    }

    public static void clearCache() {
        cachedStructures = new CachedStructures(null, List.of());
    }

    public static List<Entry> entries(MinecraftServer server) {
        CachedStructures current = cachedStructures;
        if (current.server() == server) {
            return current.entries();
        }

        synchronized (DangerousStructureStoryResources.class) {
            current = cachedStructures;
            if (current.server() == server) {
                return current.entries();
            }

            List<Entry> entries = read(server);
            cachedStructures = new CachedStructures(server, entries);
            return entries;
        }
    }

    private static List<Entry> read(MinecraftServer server) {
        Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getValue(), entries));
        return List.copyOf(entries.values());
    }

    private static void readFile(Resource resource, Map<ResourceLocation, Entry> entries) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int fallbackRadius = Math.max(1, readInt(root, "radius", DEFAULT_RADIUS));
            JsonArray entryArray = root.getAsJsonArray("entries");
            if (entryArray != null) {
                for (JsonElement element : entryArray) {
                    if (element.isJsonObject()) {
                        readEntry(element.getAsJsonObject(), fallbackRadius, entries);
                    }
                }
                return;
            }

            readEntry(root, fallbackRadius, entries);
        } catch (IOException | IllegalStateException ignored) {
            // Invalid datapack files are ignored so one custom structure list cannot break dialogue.
        }
    }

    private static void readEntry(JsonObject entry, int fallbackRadius, Map<ResourceLocation, Entry> entries) {
        String targetName = readString(entry, "name");
        int radius = Math.max(1, readInt(entry, "radius", fallbackRadius));
        List<String> structureIds = new ArrayList<>();
        structureIds.addAll(readStringList(entry, "structure"));
        structureIds.addAll(readStringList(entry, "structures"));
        for (String structureId : structureIds) {
            ResourceLocation id = ResourceLocation.tryParse(structureId);
            if (id == null) {
                continue;
            }
            String name = targetName.isBlank() ? VillagerInteractionTextUtil.resourcePathName(id) : targetName;
            entries.put(id, new Entry(id, name, radius));
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
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsInt();
    }

    public record Entry(ResourceLocation structureId, String targetName, int radius) {
    }

    private record CachedStructures(MinecraftServer server, List<Entry> entries) {
    }
}
