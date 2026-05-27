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
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), entries));
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
            ResourceLocation id = ResourceLocation.tryParse(structureId);
            if (id == null) {
                continue;
            }
            String name = targetName.isBlank() ? VillagerInteractionTextUtil.resourcePathName(id) : targetName;
            entries.put(id, new Entry(id, name, radius));
        }
    }

    public record Entry(ResourceLocation structureId, String targetName, int radius) {
    }

    private record CachedStructures(MinecraftServer server, List<Entry> entries) {
    }
}
