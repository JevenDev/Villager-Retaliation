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
import java.util.List;
import java.util.Locale;

final class EditorContentTools {
    private EditorContentTools() { }

    static String resourceId(String name) {
        if (name.contains(":")) { DefinitionKind.TREE.path(name); return name; }
        String slug = name.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "_").replaceAll("^_+|_+$", "");
        if (slug.isBlank()) throw new IllegalArgumentException("Enter a name or namespace:id");
        return "example:" + slug;
    }

    static boolean matches(AuthoringDocument document, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return term.isEmpty() || (document.id() + " " + document.resourcePath() + " " + document.json())
                .toLowerCase(Locale.ROOT).contains(term);
    }

    static List<DialogueGraph.Edge> usages(AuthoringDocument document, String id) {
        return DialogueGraph.edges(document).stream().filter(edge -> edge.to().equals(id)).distinct().toList();
    }

    static void renameNode(AuthoringDocument document, String id, String replacement) {
        if (document.kind() == DefinitionKind.FORCED) throw new IllegalArgumentException("Triggered follow-ups use their owning response as identity");
        if (!replacement.matches("[A-Za-z0-9_.:-]+")) throw new IllegalArgumentException("Use letters, numbers, dots, underscores, colons or hyphens");
        if (DialogueGraph.nodes(document).stream().anyMatch(node -> node.id().equals(replacement))) throw new IllegalArgumentException("Node already exists");
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        var references = usages(document, id);
        document.edit("Rename node", root -> {
            references.forEach(edge -> JsonPointers.set(root, edge.pointer(), new JsonPrimitive(replacement)));
            for (String field : List.of("entries", "topics")) for (var element : DialogueGraph.array(root, field)) {
                if (element.isJsonObject() && id.equals(DialogueGraph.string(element.getAsJsonObject(), "start"))) element.getAsJsonObject().addProperty("start", replacement);
            }
            if (id.equals(DialogueGraph.string(root, "entry_stage"))) root.addProperty("entry_stage", replacement);
            JsonObject owner = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (owner.has("id")) owner.addProperty("id", replacement);
            else {
                int separator = node.pointer().lastIndexOf('/');
                JsonObject parent = JsonPointers.get(root, node.pointer().substring(0, separator)).getAsJsonObject();
                parent.add(replacement, owner.deepCopy());
                JsonPointers.remove(root, node.pointer());
            }
        });
    }

    static void deleteNode(AuthoringDocument document, String id, String redirect) {
        var references = usages(document, id).stream().filter(edge -> !edge.from().equals(id)).toList();
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        if (node.entry()) throw new IllegalArgumentException("Choose another start node before deleting this one");
        if (!references.isEmpty() && (redirect.isBlank() || redirect.equals(id)
                || DialogueGraph.nodes(document).stream().noneMatch(value -> value.id().equals(redirect))))
            throw new IllegalArgumentException("Choose a replacement for " + references.size() + " incoming connections");
        document.edit("Delete and redirect node", root -> {
            references.forEach(edge -> JsonPointers.set(root, edge.pointer(), new JsonPrimitive(redirect)));
            JsonPointers.remove(root, node.pointer());
        });
    }

    static String appendObjective(AuthoringDocument document, String stageId, String type, String resource, int count) {
        if (document.kind() != DefinitionKind.QUEST || count < 1) throw new IllegalArgumentException("Choose a quest stage and positive count");
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(stageId)).findFirst().orElseThrow();
        JsonObject objective = new JsonObject();
        objective.addProperty("type", type);
        objective.addProperty("count", count);
        String field = switch (type) { case "item_check" -> "item"; case "mob_kill" -> "entity"; case "block_break", "block_place", "block_interact" -> "block"; case "trade" -> ""; default -> throw new IllegalArgumentException("Unsupported objective"); };
        if (!field.isEmpty()) {
            if (net.minecraft.resources.ResourceLocation.tryParse(resource) == null) throw new IllegalArgumentException("Choose a valid " + field + " ID");
            objective.addProperty(field, resource);
        }
        JsonObject stage = JsonPointers.get(document.json(), node.pointer()).getAsJsonObject();
        var ids = DialogueGraph.array(stage, "objectives").asList().stream().filter(JsonElement::isJsonObject)
                .map(value -> DialogueGraph.string(value.getAsJsonObject(), "id")).toList();
        String candidate = "objective_" + (ids.size() + 1);
        while (ids.contains(candidate)) candidate += "_new";
        String id = candidate;
        objective.addProperty("id", id);
        document.edit("Add objective", root -> {
            JsonObject owner = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (!owner.has("objectives")) owner.add("objectives", new JsonArray());
            owner.getAsJsonArray("objectives").add(objective);
            JsonArray completion = new JsonArray();
            if (owner.has("complete_when")) {
                JsonElement previous = owner.get("complete_when");
                if (previous.isJsonArray()) previous.getAsJsonArray().forEach(completion::add);
                else completion.add(previous);
            }
            if (!owner.has("complete_when")) for (var previous : DialogueGraph.array(stage, "objectives")) {
                if (previous.isJsonObject() && (!previous.getAsJsonObject().has("optional") || !previous.getAsJsonObject().get("optional").getAsBoolean()))
                    completion.add(DialogueGraph.string(previous.getAsJsonObject(), "id"));
            }
            completion.add(id);
            owner.add("complete_when", completion);
        });
        return node.pointer() + "/objectives/" + ids.size();
    }

    static void recipe(AuthoringWorkspace workspace, AuthoringDocument document, String recipe) {
        if (recipe.equals("Fetch quest")) {
            appendObjective(document, "start", "item_check", "minecraft:emerald", 3);
            return;
        }
        if (recipe.equals("Triggered encounter")) {
            document.edit("Set encounter", root -> { root.addProperty("trigger", "retaliation_started"); root.getAsJsonArray("lines").set(0, new JsonPrimitive("We need to talk about what happened.")); });
            return;
        }
        DialogueGraph.addNode(document, "follow_up");
        workspace.addDialogueResponse(document, "start", "continue", "Tell me more.", "follow_up");
        if (recipe.equals("Reputation choice")) EditorBlocks.append(document, "/nodes/start/responses/0", EditorBlocks.Kind.REPUTATION_GATE);
    }
}
