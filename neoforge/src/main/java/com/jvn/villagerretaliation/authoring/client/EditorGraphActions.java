package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;

final class EditorGraphActions {
    private EditorGraphActions() { }

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
