package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class EditorBulkEdit {
    private static final Set<String> REFERENCES = Set.of("type", "id", "key", "next", "transition", "stage", "success", "failure", "outcome", "check", "responses", "options", "follow_up");
    private final ImString value = new ImString(8192);
    private AuthoringDocument owner;
    private Set<String> selection = Set.of();
    private String field = "";
    private Plan preview;

    record Plan(AuthoringDocument document, JsonObject baseline, JsonObject changed, Map<String, JsonElement> before, JsonElement after) {
        void apply(AuthoringWorkspace workspace) {
            if (!document.json().equals(baseline)) throw new IllegalStateException("The document changed. Preview the bulk edit again");
            EditorMutation.validate(workspace, document, changed);
            document.edit("Edit selected node properties", root -> before.keySet().forEach(path -> JsonPointers.set(root, path, after)));
        }
    }

    static Map<String, List<String>> commonFields(AuthoringDocument document, Set<String> ids) {
        var nodes = DialogueGraph.nodes(document).stream().filter(node -> ids.contains(node.id())).toList();
        if (nodes.size() != ids.size() || nodes.size() < 2) return Map.of();
        JsonObject root = document.json();
        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, JsonElement> first = new LinkedHashMap<>();
        scalars(JsonPointers.get(root, nodes.getFirst().pointer()), "", first);
        first.forEach((path, initial) -> {
            var paths = nodes.stream().map(node -> node.pointer() + path).toList();
            boolean common = paths.stream().allMatch(candidate -> {
                try {
                    return sameType(initial, JsonPointers.get(root, candidate))
                            && compatibleBlocks(JsonPointers.get(root, nodes.getFirst().pointer()), JsonPointers.get(root, candidate.substring(0, candidate.length() - path.length())), path);
                }
                catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException exception) { return false; }
            });
            if (common) result.put(path, paths);
        });
        return result;
    }

    private static void scalars(JsonElement value, String path, Map<String, JsonElement> result) {
        if (value.isJsonPrimitive()) result.put(path, value);
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().stream().filter(entry -> !REFERENCES.contains(entry.getKey()))
                .forEach(entry -> scalars(entry.getValue(), path + "/" + JsonPointers.escape(entry.getKey()), result));
        else if (value.isJsonArray()) for (int i = 0; i < value.getAsJsonArray().size(); i++)
            scalars(value.getAsJsonArray().get(i), path + "/" + i, result);
    }

    private static boolean compatibleBlocks(JsonElement first, JsonElement other, String path) {
        String parent = path.substring(0, path.lastIndexOf('/'));
        while (!parent.isEmpty()) {
            var a = JsonPointers.get(first, parent);
            var b = JsonPointers.get(other, parent);
            if (a.isJsonObject() && a.getAsJsonObject().has("type")
                    && (!b.isJsonObject() || !a.getAsJsonObject().get("type").equals(b.getAsJsonObject().get("type")))) return false;
            parent = parent.substring(0, parent.lastIndexOf('/'));
        }
        return true;
    }

    private static boolean sameType(JsonElement first, JsonElement second) {
        if (!second.isJsonPrimitive()) return false;
        var a = first.getAsJsonPrimitive(); var b = second.getAsJsonPrimitive();
        return a.isString() == b.isString() && a.isNumber() == b.isNumber() && a.isBoolean() == b.isBoolean();
    }

    static Plan preview(AuthoringDocument document, Set<String> ids, String field, String text) {
        var paths = commonFields(document, ids).get(field);
        if (paths == null) throw new IllegalArgumentException("Choose a property shared by all selected nodes");
        JsonObject baseline = document.json(), changed = baseline.deepCopy();
        JsonElement initial = JsonPointers.get(baseline, paths.getFirst());
        JsonElement after = initial.getAsJsonPrimitive().isString() ? new JsonPrimitive(text) : JsonParser.parseString(text);
        if (!sameType(initial, after)) throw new IllegalArgumentException("Keep the property's existing value type");
        var schema = EditorSchema.forDocument(document);
        Map<String, JsonElement> before = new LinkedHashMap<>();
        for (String path : paths) {
            var issues = schema.scalarIssues(baseline, path, after);
            if (!issues.isEmpty()) throw new IllegalArgumentException(path + ": " + issues.getFirst());
            before.put(path, JsonPointers.get(baseline, path).deepCopy()); JsonPointers.set(changed, path, after);
        }
        return new Plan(document, baseline, changed, before, after);
    }

    void render(AuthoringWorkspace workspace, AuthoringDocument document, Set<String> ids,
                Consumer<ImGuiAuthoringEditor.EditorOperation> run, Runnable refresh) {
        if (ids.size() < 2) return;
        if (owner != document || !selection.equals(ids)) { owner = document; selection = Set.copyOf(ids); field = ""; preview = null; }
        if (!ImGui.collapsingHeader("Bulk properties (" + ids.size() + " nodes)")) return;
        var common = commonFields(document, ids);
        if (!common.containsKey(field)) { field = ""; preview = null; }
        if (common.isEmpty()) { ImGui.textWrapped("These nodes have no shared editable scalar properties."); return; }
        if (ImGui.beginCombo("Shared property", field.isEmpty() ? "Choose a property..." : field)) {
            for (var entry : common.entrySet()) if (ImGui.selectable(entry.getKey(), entry.getKey().equals(field))) {
                field = entry.getKey(); preview = null;
                var original = JsonPointers.get(document.json(), entry.getValue().getFirst());
                boolean mixed = entry.getValue().stream().anyMatch(path -> !JsonPointers.get(document.json(), path).equals(original));
                value.set(mixed ? "" : original.getAsJsonPrimitive().isString() ? original.getAsString() : original.toString());
            }
            ImGui.endCombo();
        }
        if (field.isEmpty()) return;
        var paths = common.get(field);
        var original = JsonPointers.get(document.json(), paths.getFirst());
        boolean mixed = paths.stream().anyMatch(path -> !JsonPointers.get(document.json(), path).equals(original));
        ImGui.textWrapped(mixed ? "Current values: mixed" : "Current value: " + original);
        ImGui.textDisabled(original.getAsJsonPrimitive().isBoolean() ? "Enter true or false" : original.getAsJsonPrimitive().isNumber() ? "Enter a number" : "Enter plain text");
        if (ImGui.inputText("New value", value)) preview = null;
        if (ImGui.button("Preview bulk edit")) run.accept(() -> preview = preview(document, ids, field, value.get()));
        if (preview == null) return;
        var plan = preview;
        plan.before().forEach((path, before) -> ImGui.textWrapped(path + ": " + before + " -> " + plan.after()));
        if (ImGui.button("Apply to selected nodes")) run.accept(() -> { plan.apply(workspace); preview = null; refresh.run(); });
    }
}
