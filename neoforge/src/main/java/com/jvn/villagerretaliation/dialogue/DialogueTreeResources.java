package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCatalog;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTreeDefinition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueEntryMetadata;
import com.jvn.villagerretaliation.dialogue.normal.DialogueDisposition;
import com.jvn.villagerretaliation.dialogue.normal.DialogueRequestType;
import com.jvn.villagerretaliation.dialogue.normal.DialogueOptionDefinition;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.util.DatapackResourceLoader;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
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
        LoadedTrees loaded = load(context.level().getServer(), context.locale());
        return loaded.trees().values().stream()
                .filter(tree -> !isSuppressedBuiltInLegacyQuestTree(context, tree, loaded.sources().get(tree.id())))
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
        DialogueTreeDefinition external = load(server, locale).trees().get(id);
        if (external != null) {
            return Optional.of(external);
        }
        return VillagerQuestResources.questDialogueCatalog(server).tree(id);
    }

    public static void installTestTrees(
            MinecraftServer server,
            String locale,
            Collection<DialogueTreeDefinition> trees,
            Set<ResourceLocation> builtInTreeIds) {
        String normalizedLocale = VillagerLocale.normalize(locale);
        Map<ResourceLocation, DialogueTreeDefinition> definitions = new LinkedHashMap<>();
        Map<ResourceLocation, TreeSource> sources = new LinkedHashMap<>();
        if (trees != null) {
            for (DialogueTreeDefinition tree : trees) {
                if (tree == null) {
                    continue;
                }
                definitions.put(tree.id(), tree);
                sources.put(tree.id(), new TreeSource(
                        ResourceLocation.fromNamespaceAndPath(tree.id().getNamespace(), "test/" + tree.id().getPath()),
                        builtInTreeIds != null && builtInTreeIds.contains(tree.id())));
            }
        }
        cachedTrees = new CachedTrees(
                server,
                Map.of(normalizedLocale, new LoadedTrees(Map.copyOf(definitions), Map.copyOf(sources))));
    }

    private static LoadedTrees load(MinecraftServer server, String locale) {
        String normalizedLocale = VillagerLocale.normalize(locale);
        CachedTrees current = cachedTrees;
        if (current.server() == server) {
            LoadedTrees cached = current.treesByLocale().get(normalizedLocale);
            if (cached != null) {
                return cached;
            }
        }

        synchronized (DialogueTreeResources.class) {
            current = cachedTrees;
            Map<String, LoadedTrees> treesByLocale = current.server() == server
                    ? new LinkedHashMap<>(current.treesByLocale())
                    : new LinkedHashMap<>();
            LoadedTrees cached = treesByLocale.get(normalizedLocale);
            if (cached != null) {
                return cached;
            }

            LoadedTrees trees = read(server, normalizedLocale);
            treesByLocale.put(normalizedLocale, trees);
            cachedTrees = new CachedTrees(server, Map.copyOf(treesByLocale));
            return trees;
        }
    }

    private static LoadedTrees read(MinecraftServer server, String locale) {
        Map<ResourceLocation, DialogueTreeDefinition> trees = new LinkedHashMap<>();
        Map<ResourceLocation, TreeSource> sources = new LinkedHashMap<>();
        readLocale(server, VillagerLocale.DEFAULT_LOCALE, trees, sources);
        if (!VillagerLocale.DEFAULT_LOCALE.equals(locale)) {
            readLocale(server, locale, trees, sources);
        }
        return new LoadedTrees(Map.copyOf(trees), Map.copyOf(sources));
    }

    private static void readLocale(
            MinecraftServer server,
            String locale,
            Map<ResourceLocation, DialogueTreeDefinition> trees,
            Map<ResourceLocation, TreeSource> sources) {
        String root = RESOURCE_ROOT + locale;
        List<LoadedTreeResource> resources = DatapackResourceLoader.jsonResources(server, root).stream()
                .map(resource -> DatapackResourceLoader.readObject(resource.location(), "dialogue tree", resource.resource())
                        .map(json -> new LoadedTreeResource(resource, json)))
                .flatMap(Optional::stream)
                .toList();
        boolean replacementMode = resources.stream()
                .anyMatch(resource -> DatapackJsonReader.readBoolean(resource.root(), "replace"));
        if (replacementMode) {
            trees.clear();
            sources.clear();
        }
        for (LoadedTreeResource resource : resources) {
            if (replacementMode
                    && isBuiltInModResource(resource.resource())
                    && !DatapackJsonReader.readBoolean(resource.root(), "replace")) {
                continue;
            }
            readFile(resource.resource(), resource.root(), locale, trees, sources, replacementMode);
        }
    }

    private static void readFile(
            DatapackResourceLoader.JsonResource resource,
            JsonObject root,
            String locale,
            Map<ResourceLocation, DialogueTreeDefinition> trees,
            Map<ResourceLocation, TreeSource> sources,
            boolean replacementMode) {
        ResourceLocation location = resource.location();
        ResourceLocation fallbackId = fallbackTreeId(location, locale);
        if (DatapackJsonReader.readBoolean(root, "replace")) {
            if (!replacementMode) {
                trees.clear();
                sources.clear();
            }
            if (isControlOnly(root, "replace", "metadata")) {
                return;
            }
        }
        if (DatapackJsonReader.readBoolean(root, "remove")) {
            ResourceLocation removeId = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
            if (removeId != null) {
                trees.remove(removeId);
                sources.remove(removeId);
            }
            return;
        }
        DialogueTreeDefinition definition = readTree(location, root, fallbackId);
        if (definition == null) {
            return;
        }
        TreeSource previous = sources.put(definition.id(), new TreeSource(location, isBuiltInModResource(resource)));
        if (previous != null) {
            DatapackDiagnostics.warnDuplicateId(location, "dialogue tree", definition.id().toString(), previous.location());
        }
        trees.put(definition.id(), definition);
    }

    private static boolean isSuppressedBuiltInLegacyQuestTree(
            DialogueContext context,
            DialogueTreeDefinition tree,
            TreeSource source) {
        return source != null
                && source.builtInModResource()
                && VillagerQuestResources
                        .questDialogueCatalog(context.level().getServer())
                        .hasGeneratedQuestDialogue(tree.id());
    }

    private static boolean isBuiltInModResource(DatapackResourceLoader.JsonResource resource) {
        return VillagerRetaliation.MOD_ID.equals(resource.location().getNamespace())
                && resource.isFromPack(VillagerRetaliation.MOD_ID);
    }

    private record LoadedTreeResource(DatapackResourceLoader.JsonResource resource, JsonObject root) {
    }

    private static boolean isControlOnly(JsonObject root, String... allowedKeys) {
        Set<String> allowed = new java.util.HashSet<>(List.of(allowedKeys));
        for (String key : root.keySet()) {
            if (!allowed.contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static DialogueTreeDefinition readTree(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        ResourceLocation id = DatapackJsonReader.readResourceLocation(root, "id").orElse(fallbackId);
        if (id == null) {
            return null;
        }
        JsonObject display = DatapackJsonReader.readObject(root, "display");
        ResourceLocation defaultQuestId = defaultQuestId(location, root, defaultQuestIdFromQuestPath(location, id));
        Map<String, DialogueTreeDefinition.Node> nodes = readNodes(location, root, defaultQuestId);
        if (nodes.isEmpty()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "dialogue tree", "tree must define at least one node.");
            return null;
        }

        List<DialogueTreeDefinition.Entry> entries = readEntries(location, root, defaultQuestId);
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
                DialogueCondition.readList(location, "dialogue tree", root, defaultQuestId),
                validEntries,
                nodes
        );
    }

    public static DialogueTreeDefinition readGeneratedTree(ResourceLocation location, JsonObject root, ResourceLocation fallbackId) {
        return readTree(location, root, fallbackId);
    }

    private static List<DialogueTreeDefinition.Entry> readEntries(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
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
                ResourceLocation entryQuestId = defaultQuestId(location, entry, defaultQuestId);
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
                        DialogueCondition.readList(location, context, entry, entryQuestId),
                        DatapackJsonReader.readBoolean(entry, "force_camera_towards_villager"),
                        DatapackJsonReader.readInt(entry, "order", index)
                ));
            }
            index++;
        }
        return List.copyOf(entries);
    }

    private static Map<String, DialogueTreeDefinition.Node> readNodes(
            ResourceLocation location,
            JsonObject root,
            ResourceLocation defaultQuestId) {
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
                DialogueTreeDefinition.Node node = readNode(location, child.getKey(), child.getValue().getAsJsonObject(), defaultQuestId);
                nodes.put(node.id(), node);
            }
        }
        return Map.copyOf(nodes);
    }

    private static DialogueTreeDefinition.Node readNode(
            ResourceLocation location,
            String fallbackId,
            JsonObject node,
            ResourceLocation defaultQuestId) {
        String id = firstNonBlank(DatapackJsonReader.readString(node, "id"), fallbackId);
        String context = "dialogue tree node \"" + id + "\"";
        ResourceLocation nodeQuestId = defaultQuestId(location, node, defaultQuestId);
        return new DialogueTreeDefinition.Node(
                id,
                DatapackJsonReader.readLines(node),
                VillagerActionDefinition.readList(location, context, node, nodeQuestId),
                DialogueCondition.readList(location, context, node, nodeQuestId),
                readResponses(location, context, node, nodeQuestId),
                DatapackJsonReader.readBoolean(node, "end", false)
        );
    }

    private static List<DialogueTreeDefinition.Response> readResponses(
            ResourceLocation location,
            String context,
            JsonObject node,
            ResourceLocation defaultQuestId) {
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
                ResourceLocation responseQuestId = defaultQuestId(location, response, defaultQuestId);
                responses.add(new DialogueTreeDefinition.Response(
                        id,
                        DatapackJsonReader.readString(response, "label"),
                        DialogueEntryMetadata.read(location, "dialogue tree response", responseContext, response),
                        DatapackJsonReader.readString(response, "next"),
                        DatapackJsonReader.readEnum(response, "request", DialogueRequestType.class).orElse(DialogueRequestType.STORY),
                        DatapackJsonReader.readLines(response),
                        VillagerActionDefinition.readList(location, responseContext, response, responseQuestId),
                        DialogueCondition.readList(location, responseContext, response, responseQuestId),
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

    private static ResourceLocation defaultQuestId(ResourceLocation location, JsonObject entry, ResourceLocation fallback) {
        JsonObject metadata = DatapackJsonReader.readObject(entry, "metadata");
        if (metadata == null) {
            return fallback;
        }
        ResourceLocation questId = QuestIds.parse(DatapackJsonReader.readString(metadata, "quest"), location);
        return questId == null ? fallback : questId;
    }

    private static ResourceLocation fallbackTreeId(ResourceLocation location, String locale) {
        ResourceLocation questPathId = questPathId(location);
        if (questPathId != null) {
            return questPathId;
        }
        String root = RESOURCE_ROOT + locale + "/";
        String path = location.getPath();
        if (!path.startsWith(root) || !path.endsWith(".json")) {
            return null;
        }
        String idPath = path.substring(root.length(), path.length() - ".json".length());
        return idPath.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), idPath);
    }

    private static ResourceLocation defaultQuestIdFromQuestPath(ResourceLocation location, ResourceLocation treeId) {
        return questPathId(location) == null ? null : treeId;
    }

    private static ResourceLocation questPathId(ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(RESOURCE_ROOT) || !path.endsWith(".json")) {
            return null;
        }
        String remainder = path.substring(RESOURCE_ROOT.length());
        int localeEnd = remainder.indexOf('/');
        if (localeEnd < 0 || localeEnd + 1 >= remainder.length()) {
            return null;
        }
        String localizedPath = remainder.substring(localeEnd + 1);
        String questPrefix = "quests/";
        if (!localizedPath.startsWith(questPrefix)) {
            return null;
        }
        String idPath = localizedPath.substring(questPrefix.length(), localizedPath.length() - ".json".length());
        return idPath.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), idPath);
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private record LoadedTrees(
            Map<ResourceLocation, DialogueTreeDefinition> trees,
            Map<ResourceLocation, TreeSource> sources) {
    }

    private record TreeSource(ResourceLocation location, boolean builtInModResource) {
    }

    private record CachedTrees(MinecraftServer server, Map<String, LoadedTrees> treesByLocale) {
        private static CachedTrees empty() {
            return new CachedTrees(null, Map.of());
        }
    }
}
