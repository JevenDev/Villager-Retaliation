package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationCompiler;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSchema;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleRuntimeMaterializer;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.debug.QuestDiagnostic;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public final class AuthoringValidation {
    private AuthoringValidation() {
    }

    public static List<AuthoringDiagnostic> validate(List<AuthoringDocument> documents) {
        List<AuthoringDiagnostic> result = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        Map<String, AuthoringDocument> ids = new LinkedHashMap<>();
        List<QuestBundleTransactions.RawResource> bundleResources = new ArrayList<>();
        JsonObject questSchema = QuestV2Schema.export();
        for (AuthoringDocument document : documents) {
            if (!paths.add(document.resourcePath().toLowerCase(java.util.Locale.ROOT))) {
                error(result, document, "", "Duplicate resource path");
            }
            JsonObject root = document.json();
            if ((document.kind() == DefinitionKind.TREE || document.kind() == DefinitionKind.FORCED) && controlOnly(root)) continue;
            if (document.kind() == DefinitionKind.TREE || document.kind() == DefinitionKind.CONVERSATION || document.kind() == DefinitionKind.QUEST) {
                String id = document.id();
                if (ResourceLocation.tryParse(id) == null || !id.contains(":")) error(result, document, "/id", "A namespaced resource ID is required");
                String locale = document.kind() == DefinitionKind.TREE && document.resourcePath().split("/").length > 3
                        ? document.resourcePath().split("/")[3] : "";
                if (ids.putIfAbsent(document.kind() + ":" + locale + ":" + id, document) != null) error(result, document, "/id", "Duplicate definition ID " + id);
            }
            ResourceLocation resource = resourceLocation(document.resourcePath());
            if (resource == null) {
                error(result, document, "", "Invalid data resource location");
                continue;
            }
            if (resource.getPath().startsWith("quests/")) {
                bundleResources.add(QuestBundleTransactions.RawResource.valid(0, "authoring", resource, root));
            }
            try {
                switch (document.kind()) {
                    case CONVERSATION -> issues(result, document, "", ConversationSchema.validate(root));
                    case QUEST -> issues(result, document, "", QuestJsonSchema.validate(root, questSchema));
                    case TREE -> {
                        if (!root.has("nodes") || !root.get("nodes").isJsonObject()) error(result, document, "/nodes", "Dialogue trees require an object of nodes");
                        if (!root.has("entries") || !root.get("entries").isJsonArray() || root.getAsJsonArray("entries").isEmpty()) {
                            error(result, document, "/entries", "A dialogue tree requires at least one entry");
                        }
                        validateRuntimeFields(result, document, root, "", questSchema);
                        for (String field : root.keySet()) {
                            if (!Set.of("id", "display", "metadata", "conditions", "availability", "available_when", "entries", "nodes", "quest", "replace", "remove").contains(field)) {
                                warning(result, document, "/" + JsonPointers.escape(field), "Unrecognized tree field is preserved for extensions: " + field);
                            }
                        }
                        ResourceLocation id = ResourceLocation.tryParse(document.id());
                        if (id != null) {
                            var parsed = com.jvn.villagerretaliation.dialogue.DialogueTreeResources.readGeneratedTree(resource, root, id);
                            if (parsed == null) error(result, document, "", "Runtime dialogue parser rejected the tree");
                            else {
                                if (parsed.entries().size() != DialogueGraph.array(root, "entries").size()) {
                                    error(result, document, "/entries", "Runtime dialogue parser rejected one or more entries");
                                }
                                for (DialogueGraph.Node node : DialogueGraph.nodes(document)) {
                                    var compiledNode = parsed.nodes().get(node.id());
                                    if (compiledNode == null || compiledNode.responses().size() != DialogueGraph.array(
                                            JsonPointers.get(root, node.pointer()).getAsJsonObject(), "responses").size()) {
                                        error(result, document, node.pointer() + "/responses", "Runtime dialogue parser rejected one or more responses");
                                    }
                                }
                            }
                        }
                    }
                    case FORCED -> {
                        validateRuntimeFields(result, document, root, "", questSchema);
                        for (DialogueGraph.Node node : DialogueGraph.nodes(document)) {
                            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
                            if (controlOnly(value)) continue;
                            if (node.entry()) {
                                var trigger = com.jvn.villagerretaliation.util.DatapackJsonReader.readEnum(value, "trigger",
                                        com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger.class)
                                        .or(() -> com.jvn.villagerretaliation.util.DatapackJsonReader.readEnum(value, "event",
                                                com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger.class));
                                if (trigger.isEmpty()) error(result, document, node.pointer() + "/trigger", "Unknown or missing forced dialogue trigger");
                            }
                        }
                    }
                    case RESOURCE -> { }
                }
                if (document.kind() != DefinitionKind.RESOURCE) validateGraph(result, document);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                error(result, document, "", "Invalid structure: " + ex.getMessage());
            }
        }
        if (!bundleResources.isEmpty()) validateBundles(result, bundleResources, documents);
        return List.copyOf(result);
    }

    private static void validateBundles(List<AuthoringDiagnostic> result, List<QuestBundleTransactions.RawResource> resources,
            List<AuthoringDocument> documents) {
        try {
            var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
            for (var issue : bundles.diagnostics()) {
                result.add(new AuthoringDiagnostic(issue.rejected() ? AuthoringDiagnostic.Severity.ERROR : AuthoringDiagnostic.Severity.WARNING,
                        path(issue.location()), "", issue.message()));
            }
            Set<String> questIds = new HashSet<>();
            documents.stream().filter(document -> document.kind() == DefinitionKind.QUEST).forEach(document -> questIds.add(document.id()));
            for (var bundle : bundles.bundles().values()) {
                if (bundle.questId() != null) {
                    var materialized = QuestBundleRuntimeMaterializer.materialize(bundle);
                    String source = path(bundle.source(QuestBundlePath.Kind.QUEST, bundle.questId()).resource());
                    for (String error : materialized.errors()) result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, source, "", error));
                    if (materialized.errors().isEmpty() && QuestV2Parser.parse(bundle.source(QuestBundlePath.Kind.QUEST, bundle.questId()).resource(),
                            materialized.quest()).isEmpty()) {
                        result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, source, "", "Quest runtime structural validation rejected the definition"));
                    }
                }
                for (var entry : bundle.definitions().getOrDefault(QuestBundlePath.Kind.CONVERSATION, Map.of()).entrySet()) {
                    var compiled = ConversationCompiler.compile(bundle, entry.getKey());
                    for (var issue : compiled.diagnostics()) {
                        result.add(new AuthoringDiagnostic(issue.severity() == QuestDiagnostic.Severity.ERROR
                                ? AuthoringDiagnostic.Severity.ERROR : AuthoringDiagnostic.Severity.WARNING,
                                path(bundle.source(QuestBundlePath.Kind.CONVERSATION, entry.getKey()).resource()), issue.jsonPointer(), issue.message()));
                    }
                    String quest = DialogueGraph.string(entry.getValue(), "quest");
                    if (!quest.isBlank() && !questIds.contains(quest)) {
                        result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.WARNING,
                                path(bundle.source(QuestBundlePath.Kind.CONVERSATION, entry.getKey()).resource()), "/quest",
                                "Quest reference is external to this workspace and must exist in the target pack: " + quest));
                    }
                }
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, "", "", "Bundle compilation failed: " + ex.getMessage()));
        }
    }

    private static void validateRuntimeFields(List<AuthoringDiagnostic> result, AuthoringDocument document,
            JsonElement value, String pointer, JsonObject questSchema) {
        if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++) {
                validateRuntimeFields(result, document, value.getAsJsonArray().get(i), pointer + "/" + i, questSchema);
            }
        } else if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet()) {
                String child = pointer + "/" + JsonPointers.escape(entry.getKey());
                if (entry.getKey().equals("camera")) {
                    issues(result, document, child, QuestJsonSchema.validate(entry.getValue(), DialogueCamera.schema()));
                } else if (Set.of("conditions", "actions").contains(entry.getKey())) {
                    JsonObject schema = new JsonObject();
                    schema.addProperty("type", "array");
                    JsonObject item = new JsonObject();
                    item.addProperty("$ref", "#/$defs/" + (entry.getKey().equals("conditions") ? "condition" : "action"));
                    schema.add("items", item);
                    schema.add("$defs", questSchema.get("$defs"));
                    issues(result, document, child, QuestJsonSchema.validate(entry.getValue(), schema));
                } else validateRuntimeFields(result, document, entry.getValue(), child, questSchema);
            }
        }
    }

    private static void validateGraph(List<AuthoringDiagnostic> result, AuthoringDocument document) {
        List<DialogueGraph.Node> nodes = DialogueGraph.nodes(document);
        if (nodes.isEmpty()) {
            error(result, document, "", "Definition has no nodes");
            return;
        }
        Set<String> ids = new HashSet<>();
        for (var node : nodes) {
            if (!ids.add(node.id())) error(result, document, node.pointer(), "Duplicate node ID " + node.id());
            JsonObject value = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
            String responseField = document.kind() == DefinitionKind.FORCED ? "options" : "responses";
            Set<String> responseIds = new HashSet<>();
            for (JsonElement response : DialogueGraph.array(value, responseField)) {
                if (!response.isJsonObject()) {
                    error(result, document, node.pointer() + "/" + responseField, "Responses must be objects");
                } else if (!responseIds.add(DialogueGraph.string(response.getAsJsonObject(), "id"))
                        && document.kind() != DefinitionKind.FORCED) {
                    error(result, document, node.pointer() + "/" + responseField, "Duplicate response ID");
                }
            }
            if (document.kind() == DefinitionKind.TREE && value.has("end") && value.get("end").isJsonPrimitive()
                    && value.get("end").getAsBoolean() && !DialogueGraph.array(value, "responses").isEmpty()) {
                warning(result, document, node.pointer() + "/responses", "Responses are ignored while this node is terminal");
            }
        }
        List<DialogueGraph.Edge> edges = DialogueGraph.edges(document);
        for (var edge : edges) {
            if (!ids.contains(edge.to())) error(result, document, edge.pointer(), "Missing target node " + edge.to());
        }
        JsonObject root = document.json();
        String entryField = document.kind() == DefinitionKind.CONVERSATION ? "topics" : "entries";
        Set<String> entryIds = new HashSet<>();
        if (document.kind() == DefinitionKind.TREE || document.kind() == DefinitionKind.CONVERSATION) {
            int index = 0;
            for (JsonElement entry : DialogueGraph.array(root, entryField)) {
                if (!entry.isJsonObject()) error(result, document, "/" + entryField + "/" + index, "Entry must be an object");
                else {
                    JsonObject object = entry.getAsJsonObject();
                    if (!entryIds.add(DialogueGraph.string(object, "id"))) error(result, document, "/" + entryField + "/" + index, "Duplicate entry ID");
                    if (!ids.contains(DialogueGraph.string(object, "start"))) error(result, document, "/" + entryField + "/" + index + "/start", "Missing entry node");
                }
                index++;
            }
        }
        if (document.kind() == DefinitionKind.QUEST && !ids.contains(DialogueGraph.string(root, "entry_stage"))) {
            error(result, document, "/entry_stage", "Missing entry stage");
        }
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        nodes.stream().filter(DialogueGraph.Node::entry).forEach(node -> pending.add(node.id()));
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!reachable.add(id)) continue;
            edges.stream().filter(edge -> edge.from().equals(id)).forEach(edge -> pending.add(edge.to()));
        }
        for (var node : nodes) {
            if (!reachable.contains(node.id())) warning(result, document, node.pointer(), "Node is unreachable from an entry");
        }
    }

    public static ResourceLocation resourceLocation(String path) {
        if (!path.startsWith("data/")) return null;
        int slash = path.indexOf('/', 5);
        return slash < 0 ? null : ResourceLocation.tryParse(path.substring(5, slash) + ":" + path.substring(slash + 1));
    }

    private static String path(ResourceLocation resource) {
        return resource == null ? "" : "data/" + resource.getNamespace() + "/" + resource.getPath();
    }

    private static void issues(List<AuthoringDiagnostic> result, AuthoringDocument document, String prefix, List<QuestJsonSchema.Issue> issues) {
        for (var issue : issues) error(result, document, prefix + issue.pointer(), issue.message());
    }
    private static boolean controlOnly(JsonObject root) {
        if (booleanValue(root, "remove")) return true;
        return booleanValue(root, "replace")
                && root.keySet().stream().allMatch(key -> key.equals("replace") || key.equals("metadata"));
    }

    private static boolean booleanValue(JsonObject root, String key) {
        return root.has(key) && root.get(key).isJsonPrimitive() && root.get(key).getAsJsonPrimitive().isBoolean()
                && root.get(key).getAsBoolean();
    }

    private static void error(List<AuthoringDiagnostic> result, AuthoringDocument document, String pointer, String message) {
        result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.ERROR, document.resourcePath(), pointer, message));
    }
    private static void warning(List<AuthoringDiagnostic> result, AuthoringDocument document, String pointer, String message) {
        result.add(new AuthoringDiagnostic(AuthoringDiagnostic.Severity.WARNING, document.resourcePath(), pointer, message));
    }
}
