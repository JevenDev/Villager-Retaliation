package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorGraphClipboard {
    private AuthoringDocument owner;
    private final Map<String, JsonObject> copied = new LinkedHashMap<>();
    private JsonArray checks = new JsonArray();
    private AuthoringDocument snapshot;
    private JsonObject copiedPositions = new JsonObject();
    private final Map<String, Map<String, JsonElement>> translations = new LinkedHashMap<>();

    void copy(AuthoringWorkspace workspace, AuthoringDocument document, Set<String> ids) {
        copy(document, ids);
        Set<String> keys = new java.util.LinkedHashSet<>();
        collectStrings(snapshot.json(), "key", keys);
        var languages = workspace.documents().stream().map(AuthoringDocument::resourcePath)
                .filter(path -> path.contains("/locales/") && path.endsWith(".json"))
                .map(path -> path.substring(path.lastIndexOf('/') + 1, path.length() - 5)).distinct().toList();
        for (int index = 0; index < keys.size(); index++) {
            String key = new ArrayList<>(keys).get(index);
            Map<String, JsonElement> localized = new LinkedHashMap<>();
            for (String language : languages) {
                var location = workspace.textLocation(document, key, language);
                if (location == null || !location.document().resourcePath().endsWith("/locales/" + language + ".json")) continue;
                JsonElement payload = JsonPointers.get(location.document().json(), location.pointer()).deepCopy();
                localized.put(language, payload);
                collectStrings(payload, "key", keys);
            }
            translations.put(key, localized);
        }
    }

    void copy(AuthoringDocument document, Set<String> ids) {
        if (document.kind() == DefinitionKind.FORCED) throw new IllegalArgumentException("Use Duplicate node for nested triggered dialogue");
        copied.clear();
        for (var node : DialogueGraph.nodes(document)) if (ids.contains(node.id()))
            copied.put(node.id(), JsonPointers.get(document.json(), node.pointer()).getAsJsonObject().deepCopy());
        if (copied.isEmpty()) throw new IllegalArgumentException("Select nodes to copy");
        checks = DialogueGraph.array(document.json(), "checks").deepCopy();
        snapshot = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        copiedPositions = EditorSchema.object(document.metadata().get("node_positions")).deepCopy();
        translations.clear();
        owner = document;
    }

    List<String> paste(AuthoringDocument document) {
        return paste(null, document);
    }

    List<String> paste(AuthoringWorkspace workspace, AuthoringDocument document) {
        if (copied.isEmpty()) throw new IllegalArgumentException("Select nodes to copy first");
        if (document.kind() != owner.kind()) throw new IllegalArgumentException("Paste into another " + owner.kind().name().toLowerCase(java.util.Locale.ROOT) + " file");
        boolean crossFile = document != owner;
        Map<String, JsonObject> selection = new LinkedHashMap<>(copied);
        if (crossFile) {
            Map<String, JsonObject> available = new LinkedHashMap<>();
            DialogueGraph.nodes(snapshot).forEach(node -> available.put(node.id(), JsonPointers.get(snapshot.json(), node.pointer()).getAsJsonObject()));
            boolean changed;
            do {
                Set<String> referenced = new HashSet<>();
                selection.values().forEach(value -> collectTargets(value, referenced));
                for (var check : checks) if (check.isJsonObject()) {
                    String id = DialogueGraph.string(check.getAsJsonObject(), "id");
                    Set<String> required = new HashSet<>();
                    selection.values().forEach(value -> collectStrings(value, "check", required));
                    if (required.contains(id)) collectTargets(check, referenced);
                }
                changed = false;
                for (String id : referenced) if (available.containsKey(id) && !selection.containsKey(id)) {
                    selection.put(id, available.get(id)); changed = true;
                }
            } while (changed);
        }
        Set<String> used = new HashSet<>();
        DialogueGraph.nodes(document).forEach(node -> used.add(node.id()));
        Map<String, String> names = new LinkedHashMap<>();
        selection.keySet().forEach(id -> names.put(id, unique(id, used)));
        Set<String> usedChecks = new HashSet<>();
        DialogueGraph.array(document.json(), "checks").forEach(value -> usedChecks.add(DialogueGraph.string(value.getAsJsonObject(), "id")));
        Map<String, String> checkNames = new HashMap<>();
        for (JsonObject node : selection.values()) collectChecks(node, checkNames, usedChecks);
        JsonArray addedChecks = new JsonArray();
        for (var value : checks) {
            JsonObject check = value.getAsJsonObject().deepCopy();
            String id = DialogueGraph.string(check, "id");
            if (checkNames.containsKey(id)) {
                check.addProperty("id", checkNames.get(id));
                rewrite(check, names, checkNames);
                addedChecks.add(check);
            }
        }
        List<JsonObject> nodes = new ArrayList<>();
        selection.forEach((id, value) -> {
            JsonObject node = value.deepCopy();
            if (node.has("id") || document.kind() != DefinitionKind.TREE) node.addProperty("id", names.get(id));
            rewrite(node, names, checkNames);
            nodes.add(node);
        });
        Set<String> effects = new HashSet<>();
        collectEffects(document.json(), effects, false);
        nodes.forEach(node -> collectEffects(node, effects, true));
        addedChecks.forEach(value -> collectEffects(value, effects, true));
        JsonArray addedTopics = new JsonArray();
        if (document.kind() == DefinitionKind.CONVERSATION) {
            Set<String> topicIds = new HashSet<>();
            DialogueGraph.array(document.json(), "topics").forEach(value -> topicIds.add(DialogueGraph.string(value.getAsJsonObject(), "id")));
            for (String id : copied.keySet()) {
                JsonObject topic = DialogueGraph.array(snapshot.json(), "topics").asList().stream().map(JsonElement::getAsJsonObject)
                        .filter(value -> DialogueGraph.string(value, "start").equals(id)).findFirst().map(JsonObject::deepCopy).orElseGet(JsonObject::new);
                topic.addProperty("id", unique(id, topicIds));
                topic.addProperty("start", names.get(id));
                if (!topic.has("label")) topic.add("label", snapshot.json().get("title").deepCopy());
                addedTopics.add(topic);
            }
        }
        JsonArray addedOutcomes = new JsonArray();
        Map<String, String> outcomeNames = new HashMap<>();
        JsonObject addedCharacters = new JsonObject();
        if (crossFile && document.kind() == DefinitionKind.CONVERSATION) {
            Set<String> referenced = new HashSet<>();
            nodes.forEach(node -> collectStrings(node, "outcome", referenced));
            Set<String> usedOutcomes = new HashSet<>();
            DialogueGraph.array(document.json(), "outcomes").forEach(value -> usedOutcomes.add(DialogueGraph.string(value.getAsJsonObject(), "id")));
            for (var value : DialogueGraph.array(snapshot.json(), "outcomes")) {
                JsonObject outcome = value.getAsJsonObject().deepCopy();
                String id = DialogueGraph.string(outcome, "id");
                if (!referenced.contains(id)) continue;
                String renamed = unique(id, usedOutcomes);
                outcomeNames.put(id, renamed); outcome.addProperty("id", renamed); addedOutcomes.add(outcome);
            }
            nodes.forEach(node -> rewriteStrings(node, "outcome", outcomeNames));
            JsonObject characters = EditorSchema.object(snapshot.json().get("characters"));
            Map<String, String> roles = new HashMap<>();
            Set<String> usedRoles = new HashSet<>(EditorSchema.object(document.json().get("characters")).keySet());
            Set<String> requiredRoles = new HashSet<>();
            nodes.forEach(node -> collectStrings(node, "role", requiredRoles));
            for (String role : requiredRoles) if (characters.has(role)) {
                String renamed = unique(role, usedRoles);
                roles.put(role, renamed); addedCharacters.add(renamed, characters.get(role).deepCopy());
            }
            nodes.forEach(node -> rewriteStrings(node, "role", roles));
        }
        Map<String, JsonObject> localeChanges = new LinkedHashMap<>();
        if (crossFile) {
            Set<String> keys = new java.util.LinkedHashSet<>();
            nodes.forEach(node -> collectStrings(node, "key", keys));
            collectStrings(addedChecks, "key", keys); collectStrings(addedOutcomes, "key", keys); collectStrings(addedTopics, "key", keys);
            for (int index = 0; index < keys.size(); index++) {
                String key = new ArrayList<>(keys).get(index);
                var localized = translations.get(key);
                if (localized == null || !localized.containsKey("en_us"))
                    throw new IllegalArgumentException("Copy this selection with its workspace open. Missing English wording: " + key);
                localized.values().forEach(payload -> collectStrings(payload, "key", keys));
            }
            if (!keys.isEmpty() && workspace == null) throw new IllegalArgumentException("Paste localized nodes with a workspace open");
            Map<String, String> keyNames = new LinkedHashMap<>();
            String prefix = DialogueGraph.string(document.json(), "localization_prefix");
            if (prefix.isBlank()) prefix = document.id();
            Set<String> usedKeys = new HashSet<>();
            if (workspace != null) for (var resource : workspace.documents()) {
                JsonObject json = resource.json();
                usedKeys.addAll(json.has("messages") && json.get("messages").isJsonObject() ? json.getAsJsonObject("messages").keySet() : json.keySet());
            }
            for (String key : keys) keyNames.put(key, unique(prefix + ".paste." + key.replace('#', '_'), usedKeys));
            for (String key : keys) for (var translation : translations.get(key).entrySet()) {
                String path = localePath(document.resourcePath(), translation.getKey());
                JsonObject locale = localeChanges.computeIfAbsent(path, ignored -> workspace.documents().stream()
                        .filter(resource -> resource.resourcePath().equals(path)).map(AuthoringDocument::json).findFirst().orElseGet(JsonObject::new));
                JsonObject messages = locale.has("messages") ? locale.getAsJsonObject("messages") : locale;
                JsonElement payload = translation.getValue().deepCopy();
                rewriteStrings(payload, "key", keyNames);
                messages.add(keyNames.get(key), payload);
            }
            nodes.forEach(node -> rewriteStrings(node, "key", keyNames));
            rewriteStrings(addedChecks, "key", keyNames); rewriteStrings(addedOutcomes, "key", keyNames); rewriteStrings(addedTopics, "key", keyNames);
        }
        var defaultPositions = EditorGraph.arrange(EditorGraph.cards(snapshot, value -> value == null ? "" : value.toString()));
        AuthoringDocument draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        draft.editMetadata("Copy layout", metadata -> document.metadata().entrySet().forEach(entry -> metadata.add(entry.getKey(), entry.getValue())));
        draft.edit("Paste nodes", root -> {
            String field = document.kind() == DefinitionKind.QUEST ? "stages" : "nodes";
            int index = 0;
            for (String name : names.values()) {
                if (root.get(field).isJsonObject()) root.getAsJsonObject(field).add(name, nodes.get(index++));
                else root.getAsJsonArray(field).add(nodes.get(index++));
            }
            if (!addedTopics.isEmpty()) addedTopics.forEach(root.getAsJsonArray("topics")::add);
            if (!addedOutcomes.isEmpty()) addedOutcomes.forEach(root.getAsJsonArray("outcomes")::add);
            if (!addedCharacters.isEmpty()) {
                if (!root.has("characters")) root.add("characters", new JsonObject());
                addedCharacters.entrySet().forEach(entry -> root.getAsJsonObject("characters").add(entry.getKey(), entry.getValue()));
            }
            if (!addedChecks.isEmpty()) {
                if (!root.has("checks")) root.add("checks", new JsonArray());
                addedChecks.forEach(root.getAsJsonArray("checks")::add);
            }
        }, metadata -> {
            if (!metadata.has("node_positions")) metadata.add("node_positions", new JsonObject());
            JsonObject positions = metadata.getAsJsonObject("node_positions");
            metadata.addProperty("graph_card_layout", 1);
            names.forEach((oldId, newId) -> {
                JsonObject point = copiedPositions.has(oldId) ? copiedPositions.getAsJsonObject(oldId).deepCopy() : new JsonObject();
                if (!point.has("x") && defaultPositions.containsKey(oldId)) { point.addProperty("x", defaultPositions.get(oldId)[0]); point.addProperty("y", defaultPositions.get(oldId)[1]); }
                point.addProperty("x", (point.has("x") ? point.get("x").getAsFloat() : 0) + 50);
                point.addProperty("y", (point.has("y") ? point.get("y").getAsFloat() : 0) + 50);
                positions.add(newId, point);
            });
        });
        if (workspace != null) workspace.applyGraphPaste(document, draft.json(), draft.metadata(), localeChanges);
        else document.edit("Paste nodes", root -> {
            for (String key : List.copyOf(root.keySet())) root.remove(key);
            draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        }, metadata -> {
            for (String key : List.copyOf(metadata.keySet())) metadata.remove(key);
            draft.metadata().entrySet().forEach(entry -> metadata.add(entry.getKey(), entry.getValue()));
        });
        return List.copyOf(names.values());
    }

    private static String localePath(String path, String language) {
        int conversations = path.indexOf("/conversations/");
        String bundle = conversations >= 0 ? path.substring(0, conversations + 1) : path.substring(0, path.lastIndexOf('/') + 1);
        return bundle + "locales/" + language + ".json";
    }

    private static void collectTargets(JsonElement value, Set<String> targets) {
        for (String field : List.of("next", "stage", "success", "failure")) collectStrings(value, field, targets);
    }

    private static void collectStrings(JsonElement value, String field, Set<String> values) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (referenceField(object, field) && object.has(field) && object.get(field).isJsonPrimitive() && object.getAsJsonPrimitive(field).isString())
                values.add(object.get(field).getAsString());
            for (var entry : object.entrySet()) if (!entry.getKey().equals("metadata")) collectStrings(entry.getValue(), field, values);
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collectStrings(child, field, values));
    }

    private static void rewriteStrings(JsonElement value, String field, Map<String, String> names) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (referenceField(object, field) && object.has(field) && object.get(field).isJsonPrimitive() && names.containsKey(object.get(field).getAsString()))
                object.addProperty(field, names.get(object.get(field).getAsString()));
            for (var entry : object.entrySet()) if (!entry.getKey().equals("metadata")) rewriteStrings(entry.getValue(), field, names);
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> rewriteStrings(child, field, names));
    }

    private static boolean referenceField(JsonObject object, String field) {
        if (field.equals("key")) return object.keySet().equals(Set.of("key"));
        if (field.equals("role")) return DialogueGraph.string(object, "type").equals("role");
        return true;
    }

    private static String unique(String id, Set<String> used) {
        String next = id + "_copy";
        while (!used.add(next)) next += "_new";
        return next;
    }

    private static void collectChecks(JsonElement value, Map<String, String> names, Set<String> used) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("check") && entry.getValue().isJsonPrimitive()) names.computeIfAbsent(entry.getValue().getAsString(), id -> unique(id, used));
            else collectChecks(entry.getValue(), names, used);
        }
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collectChecks(child, names, used));
    }

    private static void rewrite(JsonElement value, Map<String, String> names, Map<String, String> checks) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            var mapping = entry.getKey().equals("check") ? checks : names;
            if (Set.of("next", "stage", "success", "failure", "check").contains(entry.getKey()) && entry.getValue().isJsonPrimitive()
                    && mapping.containsKey(entry.getValue().getAsString())) entry.setValue(new JsonPrimitive(mapping.get(entry.getValue().getAsString())));
            else if (!Set.of("metadata", "text", "label", "lines", "variants").contains(entry.getKey())) rewrite(entry.getValue(), names, checks);
        }
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> rewrite(child, names, checks));
    }

    private static void collectEffects(JsonElement value, Set<String> used, boolean rename) {
        if (value.isJsonObject()) for (var entry : value.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("effects") && entry.getValue().isJsonArray()) for (var child : entry.getValue().getAsJsonArray()) {
                if (!child.isJsonObject()) continue;
                var effect = child.getAsJsonObject();
                String id = DialogueGraph.string(effect, "id");
                if (rename) effect.addProperty("id", unique(id, used)); else used.add(id);
            }
            collectEffects(entry.getValue(), used, rename);
        }
        else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> collectEffects(child, used, rename));
    }
}
