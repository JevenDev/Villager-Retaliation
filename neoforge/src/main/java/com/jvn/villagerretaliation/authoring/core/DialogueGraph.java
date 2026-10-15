package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DialogueGraph {
    private DialogueGraph() {
    }

    public record Node(String id, String pointer, String label, boolean entry, boolean terminal) {
    }
    public record Edge(String from, String to, String pointer, String label) {
    }

    public static List<Node> nodes(AuthoringDocument document) {
        JsonObject root = document.json();
        List<Node> result = new ArrayList<>();
        Set<String> entries = new HashSet<>();
        String entryField = document.kind() == DefinitionKind.CONVERSATION ? "topics" : "entries";
        for (JsonElement entry : array(root, entryField)) {
            if (entry.isJsonObject()) entries.add(string(entry.getAsJsonObject(), "start"));
        }
        if (document.kind() == DefinitionKind.QUEST) entries.add(string(root, "entry_stage"));
        if (document.kind() == DefinitionKind.FORCED) {
            if (root.has("entries")) {
                JsonArray values = array(root, "entries");
                for (int i = 0; i < values.size(); i++) {
                    if (values.get(i).isJsonObject()) forced(values.get(i).getAsJsonObject(), "/entries/" + i, true, result);
                }
            } else forced(root, "", true, result);
            return List.copyOf(result);
        }
        String field = document.kind() == DefinitionKind.QUEST ? "stages" : "nodes";
        JsonElement values = root.get(field);
        if (values == null) return List.of();
        if (values.isJsonObject()) {
            values.getAsJsonObject().entrySet().forEach(entry -> {
                if (entry.getValue().isJsonObject()) {
                    JsonObject value = entry.getValue().getAsJsonObject();
                    String id = string(value, "id");
                    result.add(node(id.isBlank() ? entry.getKey() : id, "/" + field + "/" + JsonPointers.escape(entry.getKey()), value, entries));
                }
            });
        } else if (values.isJsonArray()) {
            for (int i = 0; i < values.getAsJsonArray().size(); i++) {
                if (values.getAsJsonArray().get(i).isJsonObject()) {
                    JsonObject value = values.getAsJsonArray().get(i).getAsJsonObject();
                    result.add(node(string(value, "id"), "/" + field + "/" + i, value, entries));
                }
            }
        }
        return List.copyOf(result);
    }

    private static Node node(String id, String pointer, JsonObject value, Set<String> entries) {
        String label = text(value.get("lines"));
        if (label.isEmpty()) label = text(value.get("exchanges"));
        return new Node(id, pointer, label.isEmpty() ? id : label, entries.contains(id),
                bool(value, "end") || value.has("outcome") || terminalTransition(value.get("next"))
                        || (!value.has("responses") && !value.has("next")));
    }

    private static boolean terminalTransition(JsonElement value) {
        if (value == null || !value.isJsonObject()) return false;
        JsonObject next = value.getAsJsonObject();
        return bool(next, "complete") || bool(next, "abandon") || bool(next, "fail");
    }

    private static void forced(JsonObject value, String pointer, boolean entry, List<Node> result) {
        String label = text(value.get("lines"));
        if (label.isEmpty()) label = text(value.get("line"));
        result.add(new Node(pointer, pointer, label.isEmpty() ? string(value, "id") : label, entry, array(value, "options").isEmpty()));
        JsonArray options = array(value, "options");
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isJsonObject() && options.get(i).getAsJsonObject().has("follow_up")
                    && options.get(i).getAsJsonObject().get("follow_up").isJsonObject()) {
                forced(options.get(i).getAsJsonObject().getAsJsonObject("follow_up"), pointer + "/options/" + i + "/follow_up", false, result);
            }
        }
    }

    public static List<Edge> edges(AuthoringDocument document) {
        List<Edge> result = new ArrayList<>();
        JsonObject root = document.json();
        for (Node node : nodes(document)) {
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (document.kind() == DefinitionKind.FORCED) {
                JsonArray options = array(value, "options");
                for (int i = 0; i < options.size(); i++) {
                    if (!options.get(i).isJsonObject()) continue;
                    JsonObject response = options.get(i).getAsJsonObject();
                    String pointer = node.pointer() + "/options/" + i + "/follow_up";
                    if (response.has("follow_up")) result.add(new Edge(node.id(), pointer, pointer, text(response.get("label"))));
                }
                continue;
            }
            if (value.has("next")) addNext(result, node.id(), value.get("next"), node.pointer() + "/next", "Next");
            JsonArray responses = array(value, "responses");
            for (int i = 0; i < responses.size(); i++) {
                if (!responses.get(i).isJsonObject()) continue;
                JsonObject response = responses.get(i).getAsJsonObject();
                if (response.has("next")) addNext(result, node.id(), response.get("next"),
                        node.pointer() + "/responses/" + i + "/next", text(response.get("label")));
                if (response.has("check")) {
                    for (JsonElement checkElement : array(root, "checks")) {
                        if (!checkElement.isJsonObject()) continue;
                        JsonObject check = checkElement.getAsJsonObject();
                        if (!string(check, "id").equals(string(response, "check"))) continue;
                        for (String outcome : List.of("success", "failure")) {
                            int index = array(root, "checks").asList().indexOf(checkElement);
                            result.add(new Edge(node.id(), string(check, outcome), "/checks/" + index + "/" + outcome, outcome));
                        }
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addNext(List<Edge> edges, String from, JsonElement next, String pointer, String label) {
        String target = next.isJsonPrimitive() ? next.getAsString()
                : next.isJsonObject() ? string(next.getAsJsonObject(), "stage") : "";
        if (!target.isBlank()) {
            edges.add(new Edge(from, target, pointer + (next.isJsonObject() ? "/stage" : ""), label));
        }
    }

    public static void addNode(AuthoringDocument document, String id) {
        requireNewId(document, id);
        document.edit("Add node", root -> {
            JsonObject value = defaultNode(document.kind(), id);
            switch (document.kind()) {
                case TREE -> {
                    if (!root.has("nodes")) root.add("nodes", new JsonObject());
                    root.getAsJsonObject("nodes").add(id, value);
                }
                case CONVERSATION, QUEST -> ensureArray(root, document.kind() == DefinitionKind.QUEST ? "stages" : "nodes").add(value);
                case FORCED -> {
                    if (!root.has("entries")) {
                        throw new IllegalArgumentException("Add branches with responses in a single-entry forced dialogue");
                    }
                    ensureArray(root, "entries").add(value);
                }
                default -> throw new IllegalArgumentException("This resource has no dialogue graph");
            }
        });
    }

    public static void duplicateNode(AuthoringDocument document, String oldId, String newId) {
        requireNewId(document, newId);
        Node selected = find(document, oldId);
        document.edit("Duplicate node", root -> {
            JsonObject value = JsonPointers.get(root, selected.pointer()).getAsJsonObject().deepCopy();
            if (document.kind() == DefinitionKind.TREE) {
                if (value.has("id")) value.addProperty("id", newId);
                root.getAsJsonObject("nodes").add(newId, value);
            } else if (document.kind() == DefinitionKind.FORCED) {
                if (selected.pointer().endsWith("/follow_up")) {
                    String optionPointer = selected.pointer().substring(0, selected.pointer().length() - "/follow_up".length());
                    String optionsPointer = optionPointer.substring(0, optionPointer.lastIndexOf('/'));
                    JsonArray options = JsonPointers.get(root, optionsPointer).getAsJsonArray();
                    requireUniqueEntryId(options, newId);
                    JsonObject option = JsonPointers.get(root, optionPointer).getAsJsonObject().deepCopy();
                    option.addProperty("id", newId);
                    options.add(option);
                } else {
                    value.addProperty("id", newId);
                    if (selected.pointer().isEmpty()) {
                        JsonObject original = root.deepCopy();
                        if (string(original, "id").equals(newId)) throw new IllegalArgumentException("Duplicate entry ID " + newId);
                        root.keySet().clear();
                        if (original.has("replace")) root.add("replace", original.get("replace").deepCopy());
                        JsonArray entries = new JsonArray();
                        entries.add(original);
                        entries.add(value);
                        root.add("entries", entries);
                    } else {
                        JsonArray entries = ensureArray(root, "entries");
                        requireUniqueEntryId(entries, newId);
                        entries.add(value);
                    }
                }
            } else {
                value.addProperty("id", newId);
                ensureArray(root, document.kind() == DefinitionKind.QUEST ? "stages" : "nodes").add(value);
            }
        });
    }

    private static void requireUniqueEntryId(JsonArray values, String id) {
        for (JsonElement value : values) {
            if (value.isJsonObject() && string(value.getAsJsonObject(), "id").equals(id)) {
                throw new IllegalArgumentException("Duplicate entry ID " + id);
            }
        }
    }

    public static void removeNode(AuthoringDocument document, String id) {
        Node selected = find(document, id);
        document.edit("Delete node", root -> JsonPointers.remove(root, selected.pointer()));
    }

    public static void addResponse(AuthoringDocument document, String nodeId, String responseId, String label, String targetId) {
        Node selected = find(document, nodeId);
        Node target = targetId == null || targetId.isBlank() ? null : find(document, targetId);
        document.edit("Add response", root -> {
            JsonObject node = JsonPointers.get(root, selected.pointer()).getAsJsonObject();
            String key = document.kind() == DefinitionKind.FORCED ? "options" : "responses";
            for (JsonElement value : array(node, key)) {
                if (value.isJsonObject() && string(value.getAsJsonObject(), "id").equals(responseId)) {
                    throw new IllegalArgumentException("Duplicate response ID " + responseId);
                }
            }
            JsonObject response = new JsonObject();
            response.addProperty("id", responseId);
            if (document.kind() == DefinitionKind.CONVERSATION) {
                JsonObject localized = new JsonObject();
                localized.addProperty("key", label);
                response.add("label", localized);
            } else response.addProperty("label", label);
            if (document.kind() == DefinitionKind.FORCED) {
                response.addProperty("end_conversation", target == null);
                if (target != null) {
                    if (selected.pointer().startsWith(target.pointer())) throw new IllegalArgumentException("Forced follow-ups cannot contain their own ancestor");
                    response.add("follow_up", JsonPointers.get(root, target.pointer()).deepCopy());
                }
            } else if (target != null) response.addProperty("next", target.id());
            else if (document.kind() == DefinitionKind.CONVERSATION) {
                throw new IllegalArgumentException("Conversation responses require a target node");
            } else response.addProperty("end", true);
            node.remove("outcome");
            node.remove("next");
            node.remove("end");
            ensureArray(node, key).add(response);
        });
    }

    public static void connect(AuthoringDocument document, String edgePointer, String targetId) {
        Node target = find(document, targetId);
        document.edit("Connect branch", root -> {
            if (document.kind() == DefinitionKind.FORCED) {
                if (edgePointer.startsWith(target.pointer())) throw new IllegalArgumentException("Forced follow-ups cannot contain their own ancestor");
                JsonPointers.set(root, edgePointer, JsonPointers.get(root, target.pointer()));
            } else JsonPointers.set(root, edgePointer, new JsonPrimitive(targetId));
        });
    }

    public static void removeResponse(AuthoringDocument document, String edgePointer) {
        if (!edgePointer.contains("/responses/") && !edgePointer.contains("/options/")) {
            throw new IllegalArgumentException("Select a response branch to remove it");
        }
        String pointer = edgePointer.replaceFirst("/(next|follow_up)(/stage)?$", "");
        document.edit("Remove response", root -> JsonPointers.remove(root, pointer));
    }

    public static void setEntry(AuthoringDocument document, String nodeId) {
        find(document, nodeId);
        document.edit("Set entry", root -> {
            if (document.kind() == DefinitionKind.QUEST) root.addProperty("entry_stage", nodeId);
            else if (document.kind() == DefinitionKind.TREE || document.kind() == DefinitionKind.CONVERSATION) {
                String field = document.kind() == DefinitionKind.TREE ? "entries" : "topics";
                JsonArray entries = ensureArray(root, field);
                if (entries.isEmpty()) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("id", "default");
                    if (document.kind() == DefinitionKind.TREE) entry.addProperty("label", "Talk");
                    else {
                        JsonObject label = new JsonObject();
                        label.addProperty("key", document.id() + ".topic");
                        entry.add("label", label);
                    }
                    entries.add(entry);
                }
                entries.get(0).getAsJsonObject().addProperty("start", nodeId);
            } else throw new IllegalArgumentException("Forced dialogue entries start independently");
        });
    }

    public static void setTerminal(AuthoringDocument document, String nodeId, boolean terminal) {
        Node selected = find(document, nodeId);
        document.edit("Set terminal", root -> {
            JsonObject node = JsonPointers.get(root, selected.pointer()).getAsJsonObject();
            if (document.kind() == DefinitionKind.CONVERSATION) {
                if (terminal) {
                    JsonArray outcomes = array(root, "outcomes");
                    if (outcomes.isEmpty()) throw new IllegalArgumentException("Create an outcome first");
                    node.addProperty("outcome", string(outcomes.get(0).getAsJsonObject(), "id"));
                    node.remove("responses");
                    node.remove("next");
                } else node.remove("outcome");
            } else if (document.kind() == DefinitionKind.QUEST) {
                if (terminal) {
                    JsonObject next = new JsonObject();
                    next.addProperty("complete", true);
                    node.add("next", next);
                }
                else node.remove("next");
            } else if (document.kind() == DefinitionKind.FORCED) {
                if (terminal) node.remove("options");
            } else node.addProperty("end", terminal);
        });
    }

    private static void requireNewId(AuthoringDocument document, String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_.-]+")) throw new IllegalArgumentException("Use a nonempty node ID with letters, numbers, dots, dashes or underscores");
        if (nodes(document).stream().anyMatch(node -> node.id().equals(id))) throw new IllegalArgumentException("Duplicate node ID " + id);
    }

    private static Node find(AuthoringDocument document, String id) {
        return nodes(document).stream().filter(node -> node.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown node " + id));
    }

    public static JsonObject defaultNode(DefinitionKind kind, String id) {
        JsonObject node = new JsonObject();
        if (kind != DefinitionKind.TREE) node.addProperty("id", id);
        if (kind == DefinitionKind.QUEST) {
            node.add("objectives", new JsonArray());
            JsonObject next = new JsonObject();
            next.addProperty("complete", true);
            node.add("next", next);
        } else if (kind == DefinitionKind.CONVERSATION) {
            node.add("exchanges", new JsonArray());
            node.addProperty("outcome", "done");
        } else {
            JsonArray lines = new JsonArray();
            lines.add("Hello.");
            node.add("lines", lines);
            if (kind == DefinitionKind.TREE) node.addProperty("end", true);
            else node.addProperty("trigger", "container_opened");
        }
        return node;
    }

    public static JsonArray array(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    private static JsonArray ensureArray(JsonObject object, String key) {
        if (!object.has(key)) object.add(key, new JsonArray());
        return object.getAsJsonArray(key);
    }

    public static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() && object.get(key).getAsJsonPrimitive().isBoolean()
                && object.get(key).getAsBoolean();
    }

    private static String text(JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (value.isJsonPrimitive()) return value.getAsString();
        if (value.isJsonArray()) return value.getAsJsonArray().isEmpty() ? "" : text(value.getAsJsonArray().get(0));
        JsonObject object = value.getAsJsonObject();
        if (object.has("text")) return text(object.get("text"));
        return string(object, "key");
    }
}
