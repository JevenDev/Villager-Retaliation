package com.jvn.villagerretaliation.event;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class VillagerEventTriggerResources {
    private static final String RESOURCE_ROOT = "villager_events";
    private static volatile CachedTriggers cachedTriggers = new CachedTriggers(null, Map.of());

    private VillagerEventTriggerResources() {
    }

    public static void warm(MinecraftServer server) {
        triggers(server);
    }

    public static void clearCache() {
        cachedTriggers = new CachedTriggers(null, Map.of());
    }

    public static Collection<VillagerEventTriggerDefinition> triggers(MinecraftServer server) {
        return load(server).values();
    }

    private static Map<ResourceLocation, VillagerEventTriggerDefinition> load(MinecraftServer server) {
        CachedTriggers current = cachedTriggers;
        if (current.server() == server) {
            return current.triggers();
        }

        synchronized (VillagerEventTriggerResources.class) {
            current = cachedTriggers;
            if (current.server() == server) {
                return current.triggers();
            }

            Map<ResourceLocation, VillagerEventTriggerDefinition> triggers = read(server);
            cachedTriggers = new CachedTriggers(server, triggers);
            return triggers;
        }
    }

    private static Map<ResourceLocation, VillagerEventTriggerDefinition> read(MinecraftServer server) {
        Map<ResourceLocation, VillagerEventTriggerDefinition> triggers = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), triggers, sources));
        return Map.copyOf(triggers);
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<ResourceLocation, VillagerEventTriggerDefinition> triggers,
            Map<ResourceLocation, ResourceLocation> sources) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            VillagerEventTriggerDefinition definition = readTrigger(location, root, fallbackId(location));
            if (definition == null) {
                return;
            }
            ResourceLocation previous = sources.put(definition.id(), location);
            if (previous != null) {
                DatapackDiagnostics.warnDuplicateId(location, "villager event trigger", definition.id().toString(), previous);
            }
            triggers.put(definition.id(), definition);
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            DatapackDiagnostics.warnSkippedFile(location, "villager event trigger", exception);
        }
    }

    private static VillagerEventTriggerDefinition readTrigger(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation fallbackId) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }
        java.util.List<VillagerActionDefinition> actions = VillagerActionDefinition.readList(location, "villager event trigger \"" + id + "\"", root);
        if (actions.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "villager event trigger \"" + id + "\"", "trigger must define at least one action.");
            return null;
        }

        boolean repeatable = DatapackJsonReader.readBoolean(root, "repeatable", true);
        if (root.has("once")) {
            repeatable = !DatapackJsonReader.readBoolean(root, "once", false);
        }
        if (root.has("run_once")) {
            repeatable = !DatapackJsonReader.readBoolean(root, "run_once", false);
        }

        return new VillagerEventTriggerDefinition(
                id,
                VillagerEventTriggerDefinition.Listen.bySerializedName(DatapackJsonReader.readString(root, "listen", "event")),
                readMemoryTags(location, id, root),
                VillagerEventTriggerDefinition.Scope.bySerializedName(DatapackJsonReader.readString(root, "scope")),
                DialogueCondition.readList(location, "villager event trigger \"" + id + "\"", root),
                actions,
                readDurationTicks(root, "cooldown", 0L),
                repeatable);
    }

    private static Set<ResourceLocation> readMemoryTags(ResourceLocation location, ResourceLocation id, JsonObject root) {
        Set<ResourceLocation> values = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(root, "memory", "tag", "tags")) {
            VillageEventMemory.parseTagId(value).ifPresentOrElse(
                    values::add,
                    () -> DatapackDiagnostics.warnInvalidDialogueCondition(
                            location,
                            "villager event trigger \"" + id + "\"",
                            "invalid memory tag \"" + value + "\"."));
        }
        return Set.copyOf(values);
    }

    private static long readDurationTicks(JsonObject object, String baseName, long fallback) {
        Long ticks = readNullableLong(object, baseName + "_ticks");
        if (ticks != null) {
            return Math.max(0L, ticks);
        }
        Long days = readNullableLong(object, baseName + "_days");
        if (days != null) {
            return Math.max(0L, days * 24000L);
        }
        Long seconds = readNullableLong(object, baseName + "_seconds");
        if (seconds != null) {
            return Math.max(0L, seconds * 20L);
        }
        return fallback;
    }

    private static Long readNullableLong(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static ResourceLocation fallbackId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT + "/") || !path.endsWith(".json")) {
            return null;
        }
        String triggerPath = path.substring((RESOURCE_ROOT + "/").length(), path.length() - ".json".length());
        return triggerPath.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), triggerPath);
    }

    private record CachedTriggers(MinecraftServer server, Map<ResourceLocation, VillagerEventTriggerDefinition> triggers) {
    }
}
