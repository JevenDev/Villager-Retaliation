package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;

final class EditorGraphActions {
    private EditorGraphActions() { }

    static boolean needsRedirect(AuthoringDocument document, java.util.Set<String> selection) {
        if (document.kind() == DefinitionKind.FORCED) return false;
        return DialogueGraph.nodes(document).stream().anyMatch(node -> selection.contains(node.id())
                && (node.entry() || EditorContentTools.usages(document, node.id()).stream().anyMatch(edge -> !selection.contains(edge.from()))));
    }

    static void delete(AuthoringDocument document, java.util.Set<String> selection, String redirect) {
        var nodes = DialogueGraph.nodes(document);
        var selected = nodes.stream().filter(node -> selection.contains(node.id())).toList();
        if (selection.isEmpty() || selected.size() != selection.size()) throw new IllegalArgumentException("Select existing nodes to delete");
        if (needsRedirect(document, selection) && (selection.contains(redirect) || nodes.stream().noneMatch(node -> node.id().equals(redirect))))
            throw new IllegalArgumentException("Choose a remaining node for incoming connections and start points");
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        var removed = new java.util.LinkedHashSet<>(selection);
        if (document.kind() == DefinitionKind.FORCED) {
            var branches = selected.stream().filter(node -> selected.stream().noneMatch(parent -> parent != node
                    && node.pointer().startsWith(parent.pointer() + "/"))).toList();
            for (var node : nodes) if (branches.stream().anyMatch(parent -> node.pointer().startsWith(parent.pointer() + "/"))) removed.add(node.id());
            for (int i = branches.size() - 1; i >= 0; i--) EditorContentTools.deleteNode(draft, branches.get(i).id(), "");
        } else {
            var references = selected.stream().flatMap(node -> EditorContentTools.usages(document, node.id()).stream())
                    .filter(edge -> !selection.contains(edge.from())).toList();
            draft.edit("Delete nodes", root -> {
                for (var edge : references) JsonPointers.set(root, edge.pointer(), new com.google.gson.JsonPrimitive(redirect));
                for (int i = selected.size() - 1; i >= 0; i--) JsonPointers.remove(root, selected.get(i).pointer());
            });
        }
        EditorMutation.validate(null, document, draft.json());
        document.edit(selection.size() == 1 ? "Delete node" : "Delete selected nodes", root -> {
            root.keySet().clear(); draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        }, metadata -> {
            if (metadata.has("node_positions")) removed.forEach(metadata.getAsJsonObject("node_positions")::remove);
            if (metadata.has("bookmarks")) metadata.getAsJsonArray("bookmarks").asList().removeIf(value -> removed.contains(value.getAsString()));
            if (metadata.has("graph_groups")) for (var entry : metadata.getAsJsonObject("graph_groups").entrySet()) {
                var members = entry.getValue().getAsJsonObject().getAsJsonArray("nodes");
                if (members != null) members.asList().removeIf(value -> removed.contains(value.getAsString()));
            }
        });
    }

    static String create(AuthoringDocument document, String output, String id, boolean insert, float x, float y) {
        var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        var edge = output.isEmpty() ? null : DialogueGraph.outputs(document).stream().filter(value -> value.pointer().equals(output)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("This connection no longer exists"));
        JsonObject previous = edge != null && insert && document.kind() == DefinitionKind.FORCED && !edge.to().isEmpty()
                ? JsonPointers.get(document.json(), output).getAsJsonObject().deepCopy() : null;
        String created;
        if (edge == null) {
            DialogueGraph.addNode(draft, id);
            created = document.kind() == DefinitionKind.FORCED ? DialogueGraph.nodes(draft).getLast().id() : id;
        } else created = EditorContentTools.createConnected(draft, output, id);
        var node = DialogueGraph.nodes(draft).stream().filter(value -> value.id().equals(created)).findFirst().orElseThrow();
        if (insert && edge != null && !edge.to().isEmpty()) draft.edit("Keep connection destination", root -> {
            var owner = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            if (document.kind() == DefinitionKind.FORCED) {
                var response = new JsonObject();
                response.addProperty("id", "continue"); response.addProperty("label", "Continue");
                response.addProperty("end_conversation", false); response.add("follow_up", previous);
                var options = new JsonArray(); options.add(response); owner.add("options", options);
            } else if (document.kind() == DefinitionKind.QUEST) {
                var transition = new JsonObject(); transition.addProperty("stage", edge.to()); owner.add("transition", transition);
            } else {
                owner.remove("end"); owner.remove("outcome"); owner.addProperty("next", edge.to());
            }
        });
        EditorMutation.validate(null, document, draft.json());
        document.edit(insert ? "Insert node into connection" : edge == null ? "Add node" : "Create connected node", root -> {
            root.keySet().clear(); draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
        }, metadata -> {
            if (!metadata.has("node_positions")) metadata.add("node_positions", new JsonObject());
            var position = new JsonObject(); position.addProperty("x", x); position.addProperty("y", y);
            metadata.getAsJsonObject("node_positions").add(created, position);
            metadata.addProperty("graph_card_layout", 1);
        });
        return created;
    }
}
