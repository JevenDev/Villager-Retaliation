package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.ServerResourceCache;
import com.jvn.villagerretaliation.util.VillagerInteractionTextUtil;
import com.jvn.villagerretaliation.util.VillagerWorldTargetCache;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class DangerousStructureStoryResources {
    private static final String RESOURCE_ROOT = "story_structures";
    private static final int DEFAULT_RADIUS = 96;

    private static final ServerResourceCache<List<Entry>> CACHED_STRUCTURES =
            ServerResourceCache.create(List::of, DangerousStructureStoryResources::read);

    private DangerousStructureStoryResources() {
    }

    public static void warm(MinecraftServer server) {
        entries(server);
    }

    public static void clearCache() {
        CACHED_STRUCTURES.clear();
    }

    public static List<Entry> entries(MinecraftServer server) {
        return CACHED_STRUCTURES.get(server);
    }

    private static List<Entry> read(MinecraftServer server) {
        Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();
        DatapackResourceLoader.forEachJsonResource(
                server,
                RESOURCE_ROOT,
                (location, resource) -> readFile(location, resource, entries));
        return List.copyOf(entries.values());
    }

    private static void readFile(ResourceLocation location, Resource resource, Map<ResourceLocation, Entry> entries) {
        DialogueJsonResources.readEntryObjects(
                location,
                "story structure",
                resource,
                root -> Math.max(1, DialogueJsonResources.readInt(root, "radius", DEFAULT_RADIUS)),
                (entry, fallbackRadius) -> readEntry(entry, fallbackRadius, entries)
        );
    }

    private static void readEntry(JsonObject entry, int fallbackRadius, Map<ResourceLocation, Entry> entries) {
        String targetName = DialogueJsonResources.readString(entry, "name");
        int radius = Math.max(1, DialogueJsonResources.readInt(entry, "radius", fallbackRadius));
        List<String> structureIds = new ArrayList<>();
        structureIds.addAll(DialogueJsonResources.readStringList(entry, "structure"));
        structureIds.addAll(DialogueJsonResources.readStringList(entry, "structures"));
        for (String structureId : structureIds) {
            ResourceLocation parsedId = ResourceLocation.tryParse(structureId);
            if (parsedId == null) {
                continue;
            }
            ResourceLocation id = VillagerWorldTargetCache.canonicalStructureId(parsedId);
            String name = targetName.isBlank() ? VillagerInteractionTextUtil.resourcePathName(parsedId) : targetName;
            entries.put(id, new Entry(id, name, radius));
        }
    }

    public record Entry(ResourceLocation structureId, String targetName, int radius) {
    }
}
