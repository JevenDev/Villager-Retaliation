package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class BiomeStoryResources {
    private static final String RESOURCE_ROOT = "story_biomes";

    private static volatile CachedBiomes cachedBiomes = new CachedBiomes(null, Map.of());

    private BiomeStoryResources() {
    }

    public static void warm(MinecraftServer server) {
        entriesByBiome(server);
    }

    public static void clearCache() {
        cachedBiomes = new CachedBiomes(null, Map.of());
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
                .forEach(entry -> DialogueJsonResources.readEntryObjects(
                        entry.getValue(),
                        json -> readEntry(json, entries)
                ));
        return Map.copyOf(entries);
    }

    private static void readEntry(JsonObject entry, Map<ResourceLocation, Entry> entries) {
        String targetName = DialogueJsonResources.readString(entry, "name");
        List<String> biomeIds = new ArrayList<>();
        biomeIds.addAll(DialogueJsonResources.readStringList(entry, "biome"));
        biomeIds.addAll(DialogueJsonResources.readStringList(entry, "biomes"));
        for (String biomeId : biomeIds) {
            ResourceLocation id = ResourceLocation.tryParse(biomeId);
            if (id == null) {
                continue;
            }
            String name = targetName.isBlank() ? VillagerInteractionTextUtil.resourcePathName(id) : targetName;
            entries.put(id, new Entry(id, name));
        }
    }

    public record Entry(ResourceLocation biomeId, String targetName) {
    }

    private record CachedBiomes(MinecraftServer server, Map<ResourceLocation, Entry> entriesByBiome) {
    }
}
