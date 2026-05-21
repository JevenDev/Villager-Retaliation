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

public final class BiomeStoryResources {
    private static final String RESOURCE_ROOT = "story_biomes";

    private static volatile CachedBiomes cachedBiomes = new CachedBiomes(null, Map.of());

    private BiomeStoryResources() {
    }

    public static Map<ResourceLocation, Entry> entriesByBiome(MinecraftServer server) {
        CachedBiomes current = cachedBiomes;
        if (current.server() == server) {
            return current.entriesByBiome();
        }

        synchronized (BiomeStoryResources.class) {
            current = cachedBiomes;
            if (current.server() == server) {
                return current.entriesByBiome();
            }

            Map<ResourceLocation, Entry> entries = read(server);
            cachedBiomes = new CachedBiomes(server, entries);
            return entries;
        }
    }

    private static Map<ResourceLocation, Entry> read(MinecraftServer server) {
        Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getValue(), entries));
        return Map.copyOf(entries);
    }

    private static void readFile(Resource resource, Map<ResourceLocation, Entry> entries) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entryArray = root.getAsJsonArray("entries");
            if (entryArray != null) {
                for (JsonElement element : entryArray) {
                    if (element.isJsonObject()) {
                        readEntry(element.getAsJsonObject(), entries);
                    }
                }
                return;
            }

            readEntry(root, entries);
        } catch (IOException | IllegalStateException ignored) {
            // Invalid datapack files are ignored so one custom biome story list cannot break dialogue.
        }
    }

    private static void readEntry(JsonObject entry, Map<ResourceLocation, Entry> entries) {
        String targetName = readString(entry, "name");
        List<String> biomeIds = new ArrayList<>();
        biomeIds.addAll(readStringList(entry, "biome"));
        biomeIds.addAll(readStringList(entry, "biomes"));
        for (String biomeId : biomeIds) {
            ResourceLocation id = ResourceLocation.tryParse(biomeId);
            if (id == null) {
                continue;
            }
            String name = targetName.isBlank() ? VillagerInteractionTextUtil.resourcePathName(id) : targetName;
            entries.put(id, new Entry(id, name));
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

    public record Entry(ResourceLocation biomeId, String targetName) {
    }

    private record CachedBiomes(MinecraftServer server, Map<ResourceLocation, Entry> entriesByBiome) {
    }
}
