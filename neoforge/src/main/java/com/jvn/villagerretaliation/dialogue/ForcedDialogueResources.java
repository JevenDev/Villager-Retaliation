package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

public final class ForcedDialogueResources {
    private static final String RESOURCE_ROOT = "forced_dialogue";
    private static volatile CachedForcedDialogues cachedDialogues = CachedForcedDialogues.empty();

    private ForcedDialogueResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server);
    }

    public static void clearCache() {
        cachedDialogues = CachedForcedDialogues.empty();
    }

    public static Optional<ForcedDialogueDefinition> select(MinecraftServer server, ForcedDialogueTrigger trigger, ResourceLocation lootTable) {
        return load(server).stream()
                .filter(definition -> definition.trigger() == trigger)
                .filter(definition -> definition.matchesLootTable(lootTable))
                .min(Comparator.comparingInt(ForcedDialogueDefinition::priority)
                        .thenComparing(definition -> definition.lootTables().isEmpty() ? 1 : 0));
    }

    private static List<ForcedDialogueDefinition> load(MinecraftServer server) {
        CachedForcedDialogues current = cachedDialogues;
        if (current.server() == server) {
            return current.definitions();
        }

        synchronized (ForcedDialogueResources.class) {
            current = cachedDialogues;
            if (current.server() == server) {
                return current.definitions();
            }

            List<ForcedDialogueDefinition> loaded = read(server);
            cachedDialogues = new CachedForcedDialogues(server, loaded);
            return loaded;
        }
    }

    private static List<ForcedDialogueDefinition> read(MinecraftServer server) {
        Map<String, ForcedDialogueDefinition> definitions = new LinkedHashMap<>();
        server.getResourceManager()
                .listResources(RESOURCE_ROOT, location -> location.getNamespace().equals(VillagerRetaliation.MOD_ID)
                        && location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), definitions));
        return List.copyOf(definitions.values());
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            Map<String, ForcedDialogueDefinition> definitions) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray entries = root.getAsJsonArray("entries");
            if (entries != null) {
                int index = 0;
                for (JsonElement element : entries) {
                    if (element.isJsonObject()) {
                        readEntry(location, element.getAsJsonObject(), index).ifPresent(definition -> definitions.put(definition.id(), definition));
                    }
                    index++;
                }
                return;
            }

            readEntry(location, root, 0).ifPresent(definition -> definitions.put(definition.id(), definition));
        } catch (IOException | IllegalStateException | JsonParseException ignored) {
            // Invalid datapack files are ignored so one custom event cannot break every forced dialogue.
        }
    }

    private static Optional<ForcedDialogueDefinition> readEntry(ResourceLocation location, JsonObject entry, int index) {
        Optional<ForcedDialogueTrigger> trigger = readEnum(entry, "trigger", ForcedDialogueTrigger.class);
        if (trigger.isEmpty()) {
            trigger = readEnum(entry, "event", ForcedDialogueTrigger.class);
        }
        String line = readString(entry, "line");
        if (trigger.isEmpty() || line.isBlank()) {
            return Optional.empty();
        }

        String id = readString(entry, "id");
        if (id.isBlank()) {
            id = fallbackId(location, index);
        }

        List<ForcedDialogueOption> options = readOptions(entry);
        if (options.isEmpty()) {
            options = List.of(new ForcedDialogueOption(
                    "leave",
                    "Leave",
                    "",
                    0,
                    false,
                    true,
                    0
            ));
        }

        return Optional.of(new ForcedDialogueDefinition(
                id,
                trigger.get(),
                line,
                readBoolean(entry, "initiate_dialogue", true),
                readBoolean(entry, "aggro_immediately"),
                readBoolean(entry, "requires_line_of_sight", true),
                Math.max(1.0D, readDouble(entry, "witness_radius", 12.0D)),
                readInt(entry, "reputation", 0),
                readInt(entry, "priority", 0),
                readLootTables(entry),
                options
        ));
    }

    private static Set<ResourceLocation> readLootTables(JsonObject entry) {
        java.util.LinkedHashSet<ResourceLocation> lootTables = new java.util.LinkedHashSet<>();
        readResourceLocation(entry, "loot_table").ifPresent(lootTables::add);
        JsonArray entries = entry.getAsJsonArray("loot_tables");
        if (entries != null) {
            for (JsonElement element : entries) {
                if (element.isJsonPrimitive()) {
                    parseResourceLocation(element.getAsString()).ifPresent(lootTables::add);
                }
            }
        }
        return Set.copyOf(lootTables);
    }

    private static List<ForcedDialogueOption> readOptions(JsonObject entry) {
        JsonArray entries = entry.getAsJsonArray("options");
        if (entries == null) {
            return List.of();
        }

        List<ForcedDialogueOption> options = new ArrayList<>();
        int index = 0;
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                index++;
                continue;
            }
            JsonObject option = element.getAsJsonObject();
            String id = readString(option, "id");
            String label = readString(option, "label");
            if (id.isBlank() || label.isBlank()) {
                index++;
                continue;
            }
            options.add(new ForcedDialogueOption(
                    id,
                    label,
                    readString(option, "response"),
                    readInt(option, "reputation", 0),
                    readBoolean(option, "aggro"),
                    readBoolean(option, "end_conversation", true),
                    readInt(option, "order", index)
            ));
            index++;
        }
        return List.copyOf(options);
    }

    static String resolveTemplate(String text, ForcedDialogueContext context) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("villager", context.villagerName());
        replacements.put("player", context.playerName());
        replacements.put("item", context.itemName());
        replacements.put("count", Integer.toString(context.itemCount()));
        replacements.put("container", context.containerName());
        replacements.put("loot_table", context.lootTable());
        replacements.put("x", Integer.toString(context.x()));
        replacements.put("y", Integer.toString(context.y()));
        replacements.put("z", Integer.toString(context.z()));
        return VillagerDialogueResources.resolveTemplate(text, replacements);
    }

    private static <E extends Enum<E>> Optional<E> readEnum(JsonObject entry, String key, Class<E> enumClass) {
        String value = readString(entry, key);
        if (value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static String readString(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    private static Optional<ResourceLocation> readResourceLocation(JsonObject entry, String key) {
        return parseResourceLocation(readString(entry, key));
    }

    private static Optional<ResourceLocation> parseResourceLocation(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value.trim()));
    }

    private static int readInt(JsonObject entry, String key, int fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static double readDouble(JsonObject entry, String key, double fallback) {
        JsonElement element = entry.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException | UnsupportedOperationException ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject entry, String key) {
        JsonElement element = entry.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    }

    private static boolean readBoolean(JsonObject entry, String key, boolean fallback) {
        JsonElement element = entry.get(key);
        return element == null || !element.isJsonPrimitive() ? fallback : element.getAsBoolean();
    }

    private static String fallbackId(ResourceLocation location, int index) {
        return location.getPath().replace('/', '_').replace(".json", "") + "_" + index;
    }

    private record CachedForcedDialogues(MinecraftServer server, List<ForcedDialogueDefinition> definitions) {
        private static CachedForcedDialogues empty() {
            return new CachedForcedDialogues(null, List.of());
        }
    }

    public enum ForcedDialogueTrigger {
        CONTAINER_THEFT,
        CONTAINER_OPENED
    }

    public record ForcedDialogueDefinition(
            String id,
            ForcedDialogueTrigger trigger,
            String line,
            boolean initiateDialogue,
            boolean aggroImmediately,
            boolean requiresLineOfSight,
            double witnessRadius,
            int reputationDelta,
            int priority,
            Set<ResourceLocation> lootTables,
            List<ForcedDialogueOption> options) {
        private boolean matchesLootTable(ResourceLocation lootTable) {
            return this.lootTables.isEmpty() || (lootTable != null && this.lootTables.contains(lootTable));
        }
    }

    public record ForcedDialogueOption(
            String id,
            String label,
            String response,
            int reputationDelta,
            boolean aggro,
            boolean endConversation,
            int order) {
    }

    public record ForcedDialogueContext(
            String villagerName,
            String playerName,
            String itemName,
            int itemCount,
            String containerName,
            String lootTable,
            int x,
            int y,
            int z) {
    }
}
