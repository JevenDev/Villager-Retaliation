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

    record Reference(Location source, Location target) { }

    private EditorReferences() { }

    static List<Location> definitions(AuthoringWorkspace workspace, AuthoringDocument owner, String pointer, String value, String locale) {
        return definitions(workspace, owner, pointer, value, locale, workspace.documents());
    }

    private static List<Location> definitions(AuthoringWorkspace workspace, AuthoringDocument owner, String pointer, String value,
                                              String locale, List<AuthoringDocument> candidates) {
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
        var matches = candidates.stream().filter(candidate -> candidate.resourcePath().equals(value)
                || candidate.id().equals(value) && (kind != null && candidate.kind() == kind
                || !resourceSchema.isEmpty() && resourceSchema.equals(com.jvn.villagerretaliation.authoring.core.DialogueGraph.string(candidate.json(), "schema")))).toList();
        String root = DefinitionKind.contentRoot(owner.resourcePath());
        var scoped = matches.stream().filter(candidate -> DefinitionKind.contentRoot(candidate.resourcePath()).equals(root)).toList();
        return (scoped.isEmpty() ? matches : scoped).stream().map(candidate -> new Location(candidate, "")).toList();
    }

    static List<Location> usages(AuthoringWorkspace workspace, Location target, String locale) {
        return all(workspace, locale).stream().filter(reference -> reference.target().equals(target)).map(Reference::source).toList();
    }

    static List<Reference> all(AuthoringWorkspace workspace, String locale) {
        var candidates = new java.util.HashMap<String, List<AuthoringDocument>>();
        for (var document : workspace.documents()) {
            candidates.computeIfAbsent(document.id(), ignored -> new ArrayList<>()).add(document);
            if (!document.id().equals(document.resourcePath()))
                candidates.computeIfAbsent(document.resourcePath(), ignored -> new ArrayList<>()).add(document);
        }
        var result = new ArrayList<Reference>();
        for (var document : workspace.documents()) collect(workspace, document, document.json(), "", locale, candidates, new java.util.HashMap<>(), result);
        return List.copyOf(result);
    }

    private static void collect(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement value, String pointer,
                                String locale, java.util.Map<String, List<AuthoringDocument>> candidates,
                                java.util.Map<List<String>, List<Location>> resolved, List<Reference> result) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet())
                collect(workspace, owner, entry.getValue(), pointer + "/" + JsonPointers.escape(entry.getKey()), locale, candidates, resolved, result);
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                collect(workspace, owner, value.getAsJsonArray().get(i), pointer + "/" + i, locale, candidates, resolved, result);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !(owner.resourcePath().contains("/dialogue/") && (pointer.equals("/key") || pointer.matches("/messages/[0-9]+/key")))) {
            String field = pointer.replaceFirst("/[0-9]+$", "");
            field = field.substring(field.lastIndexOf('/') + 1);
            var key = List.of(field, value.getAsString());
            var targets = resolved.computeIfAbsent(key, ignored -> definitions(workspace, owner, pointer, value.getAsString(),
                    locale, candidates.getOrDefault(value.getAsString(), List.of())));
            for (var target : targets) result.add(new Reference(new Location(owner, pointer), target));
        }
    }
}
