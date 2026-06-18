package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class BiomeStoryResources {
    private static final String RESOURCE_ROOT = "story_biomes";

    private static final ServerResourceCache<Map<ResourceLocation, Entry>> CACHED_BIOMES =
            ServerResourceCache.create(Map::of, BiomeStoryResources::read);

    private BiomeStoryResources() {
    }

    public static void warm(MinecraftServer server) {
        entriesByBiome(server);
    }

    public static void clearCache() {
        CACHED_BIOMES.clear();
    }

    public static Map<ResourceLocation, Entry> entriesByBiome(MinecraftServer server) {
        return CACHED_BIOMES.get(server);
    }

    private static Map<ResourceLocation, Entry> read(MinecraftServer server) {
        Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                RESOURCE_ROOT,
                (location, resource) -> DialogueJsonResources.readEntryObjects(
                        location,
                        "story biome",
                        resource,
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
}
