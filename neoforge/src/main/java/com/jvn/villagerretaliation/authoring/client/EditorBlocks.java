package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.List;

final class EditorBlocks {
    enum Kind {
        REPUTATION_GATE("Require reputation", true, "{\"type\":\"reputation\",\"min\":0}"),
        MESSAGE("Show a message", false, "{\"type\":\"notification\",\"text\":\"Hello.\"}"),
        EXPERIENCE("Give experience", false, "{\"type\":\"experience\",\"amount\":1}"),
        REPUTATION("Change reputation", false, "{\"type\":\"reputation\",\"amount\":1}");

        final String label;
        final boolean condition;
        final String json;

        Kind(String label, boolean condition, String json) {
            this.label = label;
            this.condition = condition;
            this.json = json;
        }
    }

    private EditorBlocks() { }

    static List<Kind> available(AuthoringDocument document, String pointer) {
        if (document.kind() == DefinitionKind.RESOURCE) return List.of();
        JsonObject root = document.json();
        boolean nodeOwner = false, responseOwner = false, exchangeOwner = false;
        for (var node : DialogueGraph.nodes(document)) {
            if (pointer.equals(node.pointer())) nodeOwner = true;
            JsonObject value = JsonPointers.get(root, node.pointer()).getAsJsonObject();
            String responseField = document.kind() == DefinitionKind.FORCED ? "options" : "responses";
            var responses = DialogueGraph.array(value, responseField);
            for (int i = 0; i < responses.size(); i++)
                if (responses.get(i).isJsonObject() && pointer.equals(node.pointer() + "/" + responseField + "/" + i)) responseOwner = true;
            var exchanges = DialogueGraph.array(value, "exchanges");
            for (int i = 0; i < exchanges.size(); i++)
                if (exchanges.get(i).isJsonObject() && pointer.equals(node.pointer() + "/exchanges/" + i)) exchangeOwner = true;
        }
        List<Kind> result = new ArrayList<>();
        if (document.kind() == DefinitionKind.CONVERSATION) {
            if (responseOwner || exchangeOwner) result.add(Kind.REPUTATION_GATE);
            if (nodeOwner || responseOwner) result.addAll(List.of(Kind.EXPERIENCE, Kind.REPUTATION));
        } else if (document.kind() == DefinitionKind.QUEST) {
            if (nodeOwner) result.addAll(List.of(Kind.EXPERIENCE, Kind.REPUTATION));
        } else if (nodeOwner || responseOwner) result.addAll(List.of(Kind.values()));
        return List.copyOf(result);
    }

    static String append(AuthoringDocument document, String pointer, Kind kind) {
        if (!available(document, pointer).contains(kind)) throw new IllegalArgumentException("This block is not supported at the selected location.");
        String field = kind.condition ? "conditions" : document.kind() == DefinitionKind.CONVERSATION ? "effects"
                : document.kind() == DefinitionKind.QUEST ? "on_enter" : "actions";
        JsonObject selected = JsonPointers.get(document.json(), pointer).getAsJsonObject();
        if (field.equals("on_enter") && !selected.has(field) && selected.has("entry_actions")) field = "entry_actions";
        String targetField = field;
        int index = selected.has(field) ? selected.getAsJsonArray(field).size() : 0;
        document.edit("Add " + kind.label.toLowerCase(java.util.Locale.ROOT), root -> {
            JsonObject owner = JsonPointers.get(root, pointer).getAsJsonObject();
            JsonArray values = owner.has(targetField) ? owner.getAsJsonArray(targetField) : new JsonArray();
            JsonObject block = JsonParser.parseString(kind.json).getAsJsonObject();
            if (targetField.equals("effects")) {
                JsonObject effect = new JsonObject();
                String id = "effect_" + (index + 1);
                java.util.Set<String> used = new java.util.HashSet<>();
                collectEffectIds(root, used);
                while (used.contains(id)) id += "_new";
                effect.addProperty("id", id);
                effect.add("action", block);
                block = effect;
            }
            values.add(block);
            owner.add(targetField, values);
        });
        return pointer + "/" + field + "/" + index;
    }

    private static void collectEffectIds(com.google.gson.JsonElement value, java.util.Set<String> ids) {
        if (value.isJsonArray()) {
            for (var child : value.getAsJsonArray()) collectEffectIds(child, ids);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            for (var effect : DialogueGraph.array(object, "effects"))
                if (effect.isJsonObject()) ids.add(DialogueGraph.string(effect.getAsJsonObject(), "id"));
            for (var child : object.entrySet()) collectEffectIds(child.getValue(), ids);
        }
    }
}
