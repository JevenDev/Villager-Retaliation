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

    static String playtestProblem(AuthoringDocument document, com.jvn.villagerretaliation.campaign.selection.WorldCampaignSelection.Selection selection) {
        String[] path = document.resourcePath().split("/", 5);
        boolean addon = path.length == 5 && path[2].equals("campaign_addons");
        if (addon && !selection.addons().contains(net.minecraft.resources.ResourceLocation.parse(path[1] + ":" + path[3])))
            return "Enable addon " + path[1] + ":" + path[3] + " when creating the editor test world.";
        String owner = path.length == 5 && path[2].equals("world_campaigns") ? path[1] + ":" + path[3]
                : DialogueGraph.string(document.json(), "world_campaign");
        var campaign = owner.isBlank() ? addon ? com.jvn.villagerretaliation.campaign.selection.WorldCampaign.SHARED : null
                : net.minecraft.resources.ResourceLocation.parse(owner);
        if (!selection.allows(campaign)) return "This content is outside the test world's campaign. Create a test world with its campaign and addons selected.";
        return null;
    }

    static AuthoringDocument contentScope(AuthoringWorkspace workspace, String path) {
        return workspace.documents().stream().filter(value -> value.kind().campaignManifest()
                && (path + "/").startsWith(DefinitionKind.contentRoot(value.resourcePath()))).findFirst().orElse(null);
    }

    static boolean matches(AuthoringDocument document, String query) {
        String term = query.strip().toLowerCase(Locale.ROOT);
        return term.isEmpty() || (document.id() + " " + document.resourcePath() + " " + document.json())
                .toLowerCase(Locale.ROOT).contains(term);
    }

    static List<DialogueGraph.Edge> usages(AuthoringDocument document, String id) {
        var references = new java.util.LinkedHashMap<String, DialogueGraph.Edge>();
        DialogueGraph.edges(document).stream().filter(edge -> edge.to().equals(id)).forEach(edge -> references.put(edge.pointer(), edge));
        if (document.kind() != DefinitionKind.FORCED) collectReferences(document, document.json(), "", id, references);
        return List.copyOf(references.values());
    }

    private static void collectReferences(AuthoringDocument document, JsonElement value, String pointer, String id,
            java.util.Map<String, DialogueGraph.Edge> references) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            String quest = object.has("quest") ? DialogueGraph.string(object, "quest") : DialogueGraph.string(object, "quest_id");
            if (!quest.isBlank() && !quest.equals(document.id())) return;
            for (var entry : object.entrySet()) {
                if (List.of("metadata", "text", "label", "lines", "variants", "display").contains(entry.getKey())) continue;
                String path = pointer + "/" + JsonPointers.escape(entry.getKey());
                if (List.of("stage", "next", "start", "entry_stage", "success", "failure").contains(entry.getKey())
                        && entry.getValue().isJsonPrimitive() && id.equals(entry.getValue().getAsString())) {
                    String owner = DialogueGraph.nodes(document).stream().filter(node -> path.startsWith(node.pointer() + "/"))
                            .map(DialogueGraph.Node::id).findFirst().orElse("");
                    references.putIfAbsent(path, new DialogueGraph.Edge(owner, id, path, "Reference"));
                } else if (entry.getKey().equals("stages") && entry.getValue().isJsonArray()) {
                    int index = 0;
                    for (var child : entry.getValue().getAsJsonArray()) {
                        if (child.isJsonPrimitive() && id.equals(child.getAsString())) references.putIfAbsent(path + "/" + index,
                                new DialogueGraph.Edge("", id, path + "/" + index, "Stage filter"));
                        else collectReferences(document, child, path + "/" + index, id, references);
                        index++;
                    }
                } else collectReferences(document, entry.getValue(), path, id, references);
            }
        } else if (value.isJsonArray()) for (int i = 0; i < value.getAsJsonArray().size(); i++) collectReferences(document, value.getAsJsonArray().get(i), pointer + "/" + i, id, references);
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
                if (parent.has(replacement)) throw new IllegalArgumentException("A node already uses the storage key " + replacement);
                parent.add(replacement, owner.deepCopy());
                JsonPointers.remove(root, node.pointer());
            }
        }, metadata -> {
            if (metadata.has("node_positions")) {
                var positions = metadata.getAsJsonObject("node_positions");
                if (positions.has(id)) positions.add(replacement, positions.remove(id));
            }
            for (var entry : EditorSchema.object(metadata.get("preview_scenario_tests")).entrySet()) {
                var test = entry.getValue().getAsJsonObject();
                if (id.equals(DialogueGraph.string(test, "start"))) test.addProperty("start", replacement);
                if (DialogueGraph.string(test, "expectation").equals("Node or stage") && id.equals(DialogueGraph.string(test, "target")))
                    test.addProperty("target", replacement);
            }
            for (var group : EditorSchema.object(metadata.get("graph_groups")).entrySet()) {
                var members = group.getValue().getAsJsonObject().getAsJsonArray("nodes");
                for (int i = 0; i < members.size(); i++) if (members.get(i).getAsString().equals(id)) members.set(i, new JsonPrimitive(replacement));
            }
            if (metadata.has("bookmarks")) {
                var bookmarks = metadata.getAsJsonArray("bookmarks");
                for (int i = 0; i < bookmarks.size(); i++) if (bookmarks.get(i).getAsString().equals(id)) bookmarks.set(i, new JsonPrimitive(replacement));
            }
        });
    }

    static String createConnected(AuthoringDocument document, String output, String id) {
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        String selected = id;
        if (document.kind() == DefinitionKind.FORCED) {
            draft.edit("Add follow-up", root -> {
                var followUp = DialogueGraph.defaultNode(DefinitionKind.FORCED, "follow_up");
                followUp.remove("id"); followUp.remove("trigger"); followUp.add("options", new JsonArray());
                JsonPointers.set(root, output, followUp);
                JsonObject response = JsonPointers.get(root, output.substring(0, output.lastIndexOf('/'))).getAsJsonObject();
                response.addProperty("end_conversation", false);
            });
            selected = output;
        } else {
            DialogueGraph.addNode(draft, id);
            DialogueGraph.connect(draft, output, id);
        }
        document.edit("Create connected node", root -> { root.keySet().clear(); draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue())); });
        return selected;
    }

    static void deleteNode(AuthoringDocument document, String id, String redirect) {
        var references = usages(document, id).stream().filter(edge -> !edge.from().equals(id)).toList();
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        if (document.kind() == DefinitionKind.FORCED) {
            if (node.pointer().isEmpty() || node.entry() && DialogueGraph.array(document.json(), "entries").size() < 2)
                throw new IllegalArgumentException("Keep one entry, or remove this dialogue using Delete file");
            String removal = node.pointer().endsWith("/follow_up") ? node.pointer().substring(0, node.pointer().length() - "/follow_up".length()) : node.pointer();
            document.edit("Delete triggered branch", root -> JsonPointers.remove(root, removal));
            return;
        }
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
        return appendObjective(document, stageId, objective);
    }

    static String appendObjective(AuthoringDocument document, String stageId, JsonObject template) {
        if (document.kind() != DefinitionKind.QUEST) throw new IllegalArgumentException("Choose a quest stage");
        var node = DialogueGraph.nodes(document).stream().filter(value -> value.id().equals(stageId)).findFirst().orElseThrow();
        JsonObject objective = template.deepCopy();
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
            if (!objective.has("optional") || !objective.get("optional").getAsBoolean()) completion.add(id);
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
