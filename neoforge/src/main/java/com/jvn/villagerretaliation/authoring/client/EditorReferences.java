package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.List;

final class EditorReferences {
    record Location(AuthoringDocument document, String pointer) {
        String label() { return document.id() + pointer; }
    }

    private EditorReferences() { }

    static List<Location> definitions(AuthoringWorkspace workspace, AuthoringDocument owner, String pointer, String value, String locale) {
        String field = pointer.substring(pointer.lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
        if (field.matches("[0-9]+")) {
            String parent = pointer.substring(0, pointer.lastIndexOf('/'));
            field = parent.substring(parent.lastIndexOf('/') + 1);
        }
        if (field.equals("key") && owner.resourcePath().startsWith("data/")) {
            var location = workspace.textLocation(owner, value, locale);
            if (location != null) return List.of(new Location(location.document(), location.pointer()));
        }
        DefinitionKind kind = switch (field) {
            case "quest", "quest_id", "quests" -> DefinitionKind.QUEST;
            case "conversation", "conversation_id", "conversations" -> DefinitionKind.CONVERSATION;
            case "tree", "tree_id", "dialogue_tree", "dialogue_tree_id" -> DefinitionKind.TREE;
            case "forced_dialogue", "forced_dialogue_id" -> DefinitionKind.FORCED;
            case "world_campaign" -> DefinitionKind.WORLD_CAMPAIGN;
            default -> null;
        };
        String resourceSchema = switch (field) {
            case "scene", "scene_ref", "scene_id" -> "villagerretaliation:scene/v1";
            case "template" -> "villagerretaliation:encounter/v1";
            default -> "";
        };
        var matches = workspace.documents().stream().filter(candidate -> candidate.resourcePath().equals(value)
                || candidate.id().equals(value) && (kind != null && candidate.kind() == kind
                || !resourceSchema.isEmpty() && resourceSchema.equals(com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(candidate.json(), "schema")))).toList();
        String root = DefinitionKind.contentRoot(owner.resourcePath());
        var scoped = matches.stream().filter(candidate -> DefinitionKind.contentRoot(candidate.resourcePath()).equals(root)).toList();
        return (scoped.isEmpty() ? matches : scoped).stream().map(candidate -> new Location(candidate, "")).toList();
    }

    static List<Location> usages(AuthoringWorkspace workspace, Location target, String locale) {
        var result = new ArrayList<Location>();
        for (var document : workspace.documents()) collect(workspace, document, document.json(), "", target, locale, result);
        return List.copyOf(result);
    }

    private static void collect(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement value, String pointer,
                                Location target, String locale, List<Location> result) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet())
                collect(workspace, owner, entry.getValue(), pointer + "/" + JsonPointers.escape(entry.getKey()), target, locale, result);
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                collect(workspace, owner, value.getAsJsonArray().get(i), pointer + "/" + i, target, locale, result);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !(owner.resourcePath().contains("/dialogue/") && (pointer.equals("/key") || pointer.matches("/messages/[0-9]+/key")))
                && definitions(workspace, owner, pointer, value.getAsString(), locale).contains(target)) {
            result.add(new Location(owner, pointer));
        }
    }
}
