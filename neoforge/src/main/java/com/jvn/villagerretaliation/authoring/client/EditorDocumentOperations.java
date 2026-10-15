package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import java.util.HashSet;
import java.util.Set;

public final class EditorDocumentOperations {
    private EditorDocumentOperations() { }

    public static void nextStage(AuthoringDocument document, String from, String to) {
        if (document.kind() != DefinitionKind.QUEST) throw new IllegalArgumentException("Select a quest definition.");
        var nodes = DialogueGraph.nodes(document);
        var source = nodes.stream().filter(node -> node.id().equals(from)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Select an existing source stage."));
        if (nodes.stream().noneMatch(node -> node.id().equals(to))) throw new IllegalArgumentException("Select an existing destination stage.");
        document.edit("Connect quest stages", root -> JsonPointers.get(root, source.pointer()).getAsJsonObject().addProperty("next", to));
    }

    public static JsonObject cameraOwner(AuthoringDocument document, String pointer) {
        JsonElement selected = JsonPointers.get(document.json(), pointer);
        if (!selected.isJsonObject() || !cameraOwners(document).contains(pointer))
            throw new IllegalArgumentException("Select a dialogue node, exchange, response or line that supports cameras.");
        return selected.getAsJsonObject();
    }

    public static String cameraSelection(AuthoringDocument document, String pointer) {
        return cameraOwners(document).stream().filter(path -> pointer.equals(path) || pointer.startsWith(path + "/"))
                .max(java.util.Comparator.comparingInt(String::length)).orElse(null);
    }

    private static Set<String> cameraOwners(AuthoringDocument document) {
        Set<String> allowed = new HashSet<>();
        JsonObject root = document.json();
        for (var node : DialogueGraph.nodes(document)) {
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (document.kind() == DefinitionKind.CONVERSATION) {
                allowed.add(node.pointer());
                addArrayOwners(value, node.pointer(), "exchanges", allowed, false);
            } else {
                addOwner(value, node.pointer(), allowed);
                if (document.kind() == DefinitionKind.QUEST) {
                    addArrayOwners(value, node.pointer(), "scenes", allowed, true);
                    if (value.has("dialogue") && value.get("dialogue").isJsonObject()) {
                        for (var slot : value.getAsJsonObject("dialogue").entrySet()) {
                            if (slot.getValue().isJsonObject())
                                addOwner(slot.getValue().getAsJsonObject(), node.pointer() + "/dialogue/" + JsonPointers.escape(slot.getKey()), allowed);
                        }
                    }
                }
            }
        }
        if (document.kind() == DefinitionKind.RESOURCE && document.resourcePath().contains("/dialogue/"))
            addResourceOwners(root, "", allowed);
        return allowed;
    }

    private static void addResourceOwners(JsonElement value, String pointer, Set<String> allowed) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.keySet().stream().anyMatch(key ->
                    Set.of("lines", "line", "variants", "response", "responses", "exchanges", "text", "text_key", "line_key").contains(key)))
                allowed.add(pointer);
            for (var entry : object.entrySet()) {
                if (!Set.of("metadata", "conditions", "actions", "rewards", "camera", "speaker", "voices").contains(entry.getKey()))
                    addResourceOwners(entry.getValue(), pointer + "/" + JsonPointers.escape(entry.getKey()), allowed);
            }
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                addResourceOwners(value.getAsJsonArray().get(i), pointer + "/" + i, allowed);
        }
    }

    public static void appendShot(AuthoringDocument document, String pointer, JsonObject shot) {
        cameraOwner(document, pointer);
        document.edit("Capture camera shot", root -> {
            JsonObject owner = JsonPointers.get(root, pointer).getAsJsonObject();
            JsonObject camera = owner.has("camera") ? owner.getAsJsonObject("camera") : new JsonObject();
            if (!camera.has("shots") && !camera.isEmpty())
                throw new IllegalArgumentException("Convert or remove the existing preset before appending a shot sequence.");
            if (!camera.has("shots")) camera.add("shots", new JsonArray());
            camera.getAsJsonArray("shots").add(shot.deepCopy());
            owner.add("camera", camera);
            DialogueCamera.read(owner);
        });
    }

    private static void addOwner(JsonObject owner, String pointer, Set<String> paths) {
        paths.add(pointer);
        for (String field : new String[] {"exchanges", "lines", "variants", "responses", "options"})
            addArrayOwners(owner, pointer, field, paths, field.equals("responses") || field.equals("options"));
    }

    private static void addArrayOwners(JsonObject owner, String pointer, String field, Set<String> paths, boolean nested) {
        if (!owner.has(field) || !owner.get(field).isJsonArray()) return;
        JsonArray values = owner.getAsJsonArray(field);
        for (int index = 0; index < values.size(); index++) {
            if (!values.get(index).isJsonObject()) continue;
            String child = pointer + "/" + field + "/" + index;
            if (nested) addOwner(values.get(index).getAsJsonObject(), child, paths);
            else paths.add(child);
        }
    }
}
