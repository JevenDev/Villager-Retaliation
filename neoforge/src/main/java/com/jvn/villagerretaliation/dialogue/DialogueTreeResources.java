package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class DialogueTreeResources {
    private static final String RESOURCE_ROOT = "dialogue_trees/";
    private static volatile CachedTrees cachedTrees = CachedTrees.empty();

    private DialogueTreeResources() {
    }

    public static void warm(MinecraftServer server) {
        load(server, VillagerLocale.DEFAULT_LOCALE);
    }

    public static void clearCache() {
        cachedTrees = CachedTrees.empty();
    }

    public static List<DialogueOptionDefinition> entryOptions(DialogueContext context, DialogueDisposition disposition) {
        return trees(context.level().getServer(), context.locale()).stream()
                .filter(tree -> tree.matches(context))
                .flatMap(tree -> tree.entries().stream()
                        .filter(entry -> entry.matches(context, disposition))
                        .map(entry -> entry.toOption(tree.id())))
                .sorted(Comparator.comparingInt(DialogueOptionDefinition::order).thenComparing(DialogueOptionDefinition::id))
                .toList();
    }

    public static Optional<DialogueTreeDefinition> tree(MinecraftServer server, String locale, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(load(server, locale).get(id));
    }

    private static Collection<DialogueTreeDefinition> trees(MinecraftServer server, String locale) {
        return load(server, locale).values();
    }

    private static Map<ResourceLocation, DialogueTreeDefinition> load(MinecraftServer server, String locale) {
        String normalizedLocale = VillagerLocale.normalize(locale);
        CachedTrees current = cachedTrees;
        if (current.server() == server) {
            Map<ResourceLocation, DialogueTreeDefinition> cached = current.treesByLocale().get(normalizedLocale);
            if (cached != null) {
                return cached;
            }
        }

        synchronized (DialogueTreeResources.class) {
            current = cachedTrees;
            Map<String, Map<ResourceLocation, DialogueTreeDefinition>> treesByLocale = current.server() == server
                    ? new LinkedHashMap<>(current.treesByLocale())
                    : new LinkedHashMap<>();
            Map<ResourceLocation, DialogueTreeDefinition> cached = treesByLocale.get(normalizedLocale);
            if (cached != null) {
                return cached;
            }

            Map<ResourceLocation, DialogueTreeDefinition> trees = read(server, normalizedLocale);
            treesByLocale.put(normalizedLocale, trees);
            cachedTrees = new CachedTrees(server, Map.copyOf(treesByLocale));
            return trees;
        }
    }

    private static Map<ResourceLocation, DialogueTreeDefinition> read(MinecraftServer server, String locale) {
        Map<ResourceLocation, DialogueTreeDefinition> trees = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> sources = new LinkedHashMap<>();
        readLocale(server, VillagerLocale.DEFAULT_LOCALE, trees, sources);
        if (!VillagerLocale.DEFAULT_LOCALE.equals(locale)) {
            readLocale(server, locale, trees, sources);
        }
        return Map.copyOf(trees);
    }

    private static void readLocale(
            MinecraftServer server,
            String locale,
            Map<ResourceLocation, DialogueTreeDefinition> trees,
            Map<ResourceLocation, ResourceLocation> sources) {
        String root = RESOURCE_ROOT + locale;
        server.getResourceManager()
                .listResources(root, location -> location.getPath().endsWith(".json"))
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> readFile(entry.getKey(), entry.getValue(), locale, trees, sources));
    }

    private static void readFile(
            ResourceLocation location,
            Resource resource,
            String locale,
            Map<ResourceLocation, DialogueTreeDefinition> trees,
            Map<ResourceLocation, ResourceLocation> sources) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            DialogueTreeDefinition definition = readTree(location, root, fallbackTreeId(location, locale));
            if (definition == null) {
                return;
            }
            ResourceLocation previous = sources.put(definition.id(), location);
            if (previous != null) {
                DatapackDiagnostics.warnDuplicateId(location, "dialogue tree", definition.id().toString(), previous);
            }
            trees.put(definition.id(), definition);
        } catch (IOException | IllegalStateException | JsonParseException exception) {
            DatapackDiagnostics.warnSkippedFile(location, "dialogue tree", exception);
        }
    }

    private static DialogueTreeDefinition readTree(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }
        JsonObject display = DatapackJsonReader.readObject(root, "display");
        Map<String, DialogueTreeDefinition.Node> nodes = readNodes(location, root);
        if (nodes.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree must define at least one node.");
            return null;
        }

        List<DialogueTreeDefinition.Entry> entries = readEntries(location, root);
        if (entries.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree must define at least one entry.");
            return null;
        }
        List<DialogueTreeDefinition.Entry> validEntries = entries.stream()
                .filter(entry -> {
                    if (entry.start().isBlank()) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "dialogue tree entry \"" + entry.id() + "\"",
                                "entry must define start.");
                        return false;
                    }
                    if (!nodes.containsKey(entry.start())) {
                        DatapackDiagnostics.warnInvalidDialogueCondition(
                                location,
                                "dialogue tree entry \"" + entry.id() + "\"",
                                "start references unknown node \"" + entry.start() + "\".");
                        return false;
                    }
                    return true;
                })
                .toList();
        if (validEntries.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree has no valid entries.");
            return null;
        }

        return new DialogueTreeDefinition(
                id,
                display == null ? "" : DatapackJsonReader.readString(display, "title"),
                display == null ? "" : DatapackJsonReader.readString(display, "description"),
                DialogueEntryMetadata.read(location, "dialogue tree", "file root", root),
                DialogueCondition.readList(location, "dialogue tree", root),
                validEntries,
                nodes
        );
    }

    private static List<DialogueTreeDefinition.Entry> readEntries(ResourceLocation location, JsonObject root) {
        JsonElement element = root.get("entries");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "entries must be an array.");
            return List.of();
        }

        List<DialogueTreeDefinition.Entry> entries = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                JsonObject entry = child.getAsJsonObject();
                String context = "dialogue tree entries[" + index + "]";
                entries.add(new DialogueTreeDefinition.Entry(
                        firstNonBlank(DatapackJsonReader.readString(entry, "id"), "entry_" + index),
                        DatapackJsonReader.readString(entry, "label"),
                        DialogueEntryMetadata.read(location, "dialogue tree entry", context, entry),
                        DatapackJsonReader.readString(entry, "start"),
                        DatapackJsonReader.readEnum(entry, "request", DialogueRequestType.class).orElse(DialogueRequestType.STORY),
                        DatapackJsonReader.readBoolean(entry, "show_for_adults", true),
                        DatapackJsonReader.readBoolean(entry, "show_for_babies", true),
                        readProfessions(location, context, entry),
                        readDispositions(entry),
                        DialogueCondition.readList(location, context, entry),
                        DatapackJsonReader.readBoolean(entry, "force_camera_towards_villager"),
                        DatapackJsonReader.readInt(entry, "order", index)
                ));
            }
            index++;
        }
        return List.copyOf(entries);
    }

    private static Map<String, DialogueTreeDefinition.Node> readNodes(ResourceLocation location, JsonObject root) {
        JsonElement element = root.get("nodes");
        if (element == null || element.isJsonNull()) {
            return Map.of();
        }

        Map<String, DialogueTreeDefinition.Node> nodes = new LinkedHashMap<>();
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "nodes must be an object keyed by node id.");
            return Map.of();
        }
        for (Map.Entry<String, JsonElement> child : element.getAsJsonObject().entrySet()) {
            if (child.getValue().isJsonObject()) {
                DialogueTreeDefinition.Node node = readNode(location, child.getKey(), child.getValue().getAsJsonObject());
                nodes.put(node.id(), node);
            }
        }
        return Map.copyOf(nodes);
    }

    private static DialogueTreeDefinition.Node readNode(ResourceLocation location, String fallbackId, JsonObject node) {
        String id = firstNonBlank(DatapackJsonReader.readString(node, "id"), fallbackId);
        String context = "dialogue tree node \"" + id + "\"";
        return new DialogueTreeDefinition.Node(
                id,
                DatapackJsonReader.readLines(node),
                VillagerActionDefinition.readList(location, context, node),
                DialogueCondition.readList(location, context, node),
                readResponses(location, context, node),
                DatapackJsonReader.readBoolean(node, "end", false)
        );
    }

    private static List<DialogueTreeDefinition.Response> readResponses(ResourceLocation location, String context, JsonObject node) {
        JsonElement element = node.get("responses");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "responses must be an array.");
            return List.of();
        }

        List<DialogueTreeDefinition.Response> responses = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                JsonObject response = child.getAsJsonObject();
                String id = firstNonBlank(DatapackJsonReader.readString(response, "id"), "response_" + index);
                String responseContext = context + ".responses[" + index + "]";
                responses.add(new DialogueTreeDefinition.Response(
                        id,
                        DatapackJsonReader.readString(response, "label"),
                        DialogueEntryMetadata.read(location, "dialogue tree response", responseContext, response),
                        DatapackJsonReader.readString(response, "next"),
                        DatapackJsonReader.readEnum(response, "request", DialogueRequestType.class).orElse(DialogueRequestType.STORY),
                        DatapackJsonReader.readLines(response),
                        VillagerActionDefinition.readList(location, responseContext, response),
                        DialogueCondition.readList(location, responseContext, response),
                        DatapackJsonReader.readBoolean(response, "end", false),
                        DatapackJsonReader.readInt(response, "order", index)
                ));
            }
            index++;
        }
        return List.copyOf(responses);
    }

    private static Set<VillagerProfession> readProfessions(ResourceLocation location, String context, JsonObject entry) {
        Set<VillagerProfession> professions = new LinkedHashSet<>();
        for (String value : DatapackJsonReader.readStringList(entry, "professions")) {
            Optional<VillagerProfession> profession = VillagerProfessionUtil.parse(value);
            if (profession.isPresent()) {
                professions.add(profession.get());
            } else {
                DatapackDiagnostics.warnUnknownProfession(location, context, value);
            }
        }
        return Set.copyOf(professions);
    }

    private static Set<DialogueDisposition> readDispositions(JsonObject entry) {
        EnumSet<DialogueDisposition> dispositions = EnumSet.noneOf(DialogueDisposition.class);
        for (String value : DatapackJsonReader.readStringList(entry, "disposition", "dispositions")) {
            DatapackJsonReader.readEnum(value, DialogueDisposition.class).ifPresent(dispositions::add);
        }
        return Set.copyOf(dispositions);
    }

    private static ResourceLocation fallbackTreeId(ResourceLocation location, String locale) {
        String root = RESOURCE_ROOT + locale + "/";
        String path = location.getPath();
        if (!path.startsWith(root) || !path.endsWith(".json")) {
            return null;
        }
        String idPath = path.substring(root.length(), path.length() - ".json".length());
        return idPath.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), idPath);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private record CachedTrees(MinecraftServer server, Map<String, Map<ResourceLocation, DialogueTreeDefinition>> treesByLocale) {
        private static CachedTrees empty() {
            return new CachedTrees(null, Map.of());
        }
    }
}
