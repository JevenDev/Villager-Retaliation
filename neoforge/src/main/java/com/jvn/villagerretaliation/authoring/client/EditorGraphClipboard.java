package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
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

    void copy(AuthoringDocument document, Set<String> ids) {
        if (document.kind() == DefinitionKind.FORCED) throw new IllegalArgumentException("Use Duplicate node for nested triggered dialogue");
        copied.clear();
        for (var node : DialogueGraph.nodes(document)) if (ids.contains(node.id()))
            copied.put(node.id(), JsonPointers.get(document.json(), node.pointer()).getAsJsonObject().deepCopy());
        if (copied.isEmpty()) throw new IllegalArgumentException("Select nodes to copy");
        checks = DialogueGraph.array(document.json(), "checks").deepCopy();
        owner = document;
    }

    List<String> paste(AuthoringDocument document) {
        if (document != owner || copied.isEmpty()) throw new IllegalArgumentException("Paste into the source file to preserve shared localization references");
        Set<String> used = new HashSet<>();
        DialogueGraph.nodes(document).forEach(node -> used.add(node.id()));
        Map<String, String> names = new LinkedHashMap<>();
        copied.keySet().forEach(id -> names.put(id, unique(id, used)));
        Set<String> usedChecks = new HashSet<>();
        DialogueGraph.array(document.json(), "checks").forEach(value -> usedChecks.add(DialogueGraph.string(value.getAsJsonObject(), "id")));
        Map<String, String> checkNames = new HashMap<>();
        for (JsonObject node : copied.values()) collectChecks(node, checkNames, usedChecks);
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
        copied.forEach((id, value) -> {
            JsonObject node = value.deepCopy();
            if (node.has("id") || document.kind() != DefinitionKind.TREE) node.addProperty("id", names.get(id));
            rewrite(node, names, checkNames);
            nodes.add(node);
        });
        Set<String> effects = new HashSet<>();
        collectEffects(document.json(), effects, false);
        nodes.forEach(node -> collectEffects(node, effects, true));
        addedChecks.forEach(value -> collectEffects(value, effects, true));
        var defaultPositions = EditorGraph.arrange(EditorGraph.cards(document, value -> value == null ? "" : value.toString()));
        document.edit("Paste nodes", root -> {
            String field = document.kind() == DefinitionKind.QUEST ? "stages" : "nodes";
            int index = 0;
            for (String name : names.values()) {
                if (root.get(field).isJsonObject()) root.getAsJsonObject(field).add(name, nodes.get(index++));
                else root.getAsJsonArray(field).add(nodes.get(index++));
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
                JsonObject point = positions.has(oldId) ? positions.getAsJsonObject(oldId).deepCopy() : new JsonObject();
                if (!point.has("x") && defaultPositions.containsKey(oldId)) { point.addProperty("x", defaultPositions.get(oldId)[0]); point.addProperty("y", defaultPositions.get(oldId)[1]); }
                point.addProperty("x", (point.has("x") ? point.get("x").getAsFloat() : 0) + 50);
                point.addProperty("y", (point.has("y") ? point.get("y").getAsFloat() : 0) + 50);
                positions.add(newId, point);
            });
        });
        return List.copyOf(names.values());
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
