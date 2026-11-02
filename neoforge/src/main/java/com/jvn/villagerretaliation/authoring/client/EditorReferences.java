package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
        String field = field(pointer);
        var result = new LinkedHashSet<Location>();
        if (List.of("key", "text_key", "line_key", "label_key", "response_key").contains(field) && owner.resourcePath().startsWith("data/")) {
            var location = workspace.textLocation(owner, value, locale);
            if (location != null && !DefinitionKind.unscopedPath(location.document().resourcePath()).contains("/dialogue/"))
                result.add(new Location(location.document(), location.pointer()));
            else result.addAll(messages(workspace, owner, value, locale));
        }
        String resourceRoot = referenceRoot(owner, pointer, field);
        if (!resourceRoot.isEmpty()) result.addAll(resources(candidates, owner, resourceRoot, value, locale));
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
            case "template" -> resourceRoot.isEmpty() ? "villagerretaliation:encounter/v1" : "";
            default -> "";
        };
        var matches = candidates.stream().filter(candidate -> candidate.resourcePath().equals(value)
                || candidate.id().equals(value) && (kind != null && candidate.kind() == kind
                || !resourceSchema.isEmpty() && resourceSchema.equals(DialogueGraph.string(candidate.json(), "schema")))).toList();
        String root = DefinitionKind.contentRoot(owner.resourcePath());
        var scoped = matches.stream().filter(candidate -> DefinitionKind.contentRoot(candidate.resourcePath()).equals(root)).toList();
        (scoped.isEmpty() ? matches : scoped).forEach(candidate -> result.add(new Location(candidate, "")));
        for (var id : com.jvn.villagerretaliation.dialogue.DialogueWordPools.references(value))
            result.addAll(resources(candidates, owner, "dialogue_words", id.toString(), locale));
        var tags = java.util.regex.Pattern.compile("</?([a-z][a-z0-9_]*)>").matcher(value);
        while (tags.find()) result.addAll(resources(candidates, owner, "dialogue_styles", tags.group(1), locale));
        return List.copyOf(result);
    }

    private static String field(String pointer) {
        String path = pointer.replaceFirst("/[0-9]+$", "");
        return path.substring(path.lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
    }

    private static String referenceRoot(AuthoringDocument owner, String pointer, String field) {
        String path = DefinitionKind.unscopedPath(owner.resourcePath());
        if (field.equals("template") && (path.contains("/dialogue/") || path.contains("/dialogue_templates/"))) return "dialogue_templates";
        if (field.equals("extends")) {
            if (path.contains("/dialogue_templates/")) return "dialogue_templates";
            if (path.contains("/dialogue_words/")) return "dialogue_words";
        }
        if (field.equals("id") && pointer.contains("/")) {
            String parent = pointer.substring(0, pointer.lastIndexOf('/'));
            var value = owner.value(parent);
            if (value.isJsonObject() && List.of("context", "context_rule").contains(DialogueGraph.string(value.getAsJsonObject(), "type")))
                return "dialogue_contexts";
        }
        return "";
    }

    static List<String> identifiers(AuthoringDocument document) {
        var ids = new LinkedHashSet<String>();
        ids.add(document.id()); ids.add(document.resourcePath());
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        String[] parts = path.split("/", 5);
        if (parts.length >= 4 && path.endsWith(".json")) {
            boolean localized = List.of("dialogue_words", "dialogue_templates").contains(parts[2]);
            if (localized && parts.length == 5) ids.add(parts[1] + ":" + parts[4].substring(0, parts[4].length() - 5));
            else if (List.of("dialogue_styles", "dialogue_contexts").contains(parts[2])) {
                String suffix = path.substring(("data/" + parts[1] + "/" + parts[2] + "/").length());
                ids.add(parts[1] + ":" + suffix.substring(0, suffix.length() - 5));
                if (parts[2].equals("dialogue_styles")) ids.add(DialogueGraph.string(document.json(), "tag"));
            }
        }
        return List.copyOf(ids);
    }

    private static boolean inScope(AuthoringDocument owner, AuthoringDocument candidate) {
        String scope = DefinitionKind.contentRoot(candidate.resourcePath());
        return scope.split("/").length != 4 || scope.equals(DefinitionKind.contentRoot(owner.resourcePath()));
    }

    private static List<Location> resources(List<AuthoringDocument> candidates, AuthoringDocument owner, String category, String id, String locale) {
        var matches = candidates.stream().filter(candidate -> inScope(owner, candidate)
                && DefinitionKind.unscopedPath(candidate.resourcePath()).matches("data/[^/]+/" + category + "/.+[.]json")
                && identifiers(candidate).contains(id)).toList();
        String language = com.jvn.villagerretaliation.util.VillagerLocale.normalize(locale);
        boolean localized = List.of("dialogue_words", "dialogue_templates").contains(category);
        int best = matches.stream().mapToInt(candidate -> resourceRank(owner, candidate, category, language, localized)).min().orElse(100);
        return best >= 100 ? List.of() : matches.stream()
                .filter(candidate -> resourceRank(owner, candidate, category, language, localized) == best)
                .map(candidate -> new Location(candidate, "")).toList();
    }

    private static int resourceRank(AuthoringDocument owner, AuthoringDocument candidate, String category, String language, boolean localized) {
        String path = DefinitionKind.unscopedPath(candidate.resourcePath());
        int rank = 0;
        if (localized) {
            String actual = path.split("/", 5)[3];
            if (!actual.equals(language)) { if (!actual.equals("en_us")) return 100; rank = 2; }
        }
        return rank + (DefinitionKind.contentRoot(candidate.resourcePath()).equals(DefinitionKind.contentRoot(owner.resourcePath())) ? 0 : 1);
    }

    private static List<Location> messages(AuthoringWorkspace workspace, AuthoringDocument owner, String key, String locale) {
        var result = new java.util.LinkedHashMap<String, Location>();
        String language = com.jvn.villagerretaliation.util.VillagerLocale.normalize(locale);
        var candidates = workspace.documents().stream().filter(candidate -> inScope(owner, candidate)
                && DefinitionKind.unscopedPath(candidate.resourcePath()).matches("data/[^/]+/dialogue/[^/]+/.+[.]json"))
                .sorted(java.util.Comparator.comparingInt((AuthoringDocument candidate) -> resourceRank(owner, candidate, "dialogue", language, true))
                        .thenComparing(AuthoringDocument::resourcePath)).toList();
        for (var candidate : candidates) {
            if (resourceRank(owner, candidate, "dialogue", language, true) >= 100) continue;
            var root = candidate.json();
            if (removed(root)) continue;
            var messages = DialogueGraph.array(root, "messages");
            for (int i = 0; i < messages.size(); i++) if (messages.get(i).isJsonObject())
                addMessage(result, candidate, messages.get(i).getAsJsonObject(), "/messages/" + i, key);
            if (candidate.resourcePath().contains("/messages/")) addMessage(result, candidate, root, "", key);
        }
        return List.copyOf(result.values());
    }

    private static void addMessage(Map<String, Location> result, AuthoringDocument document, JsonObject entry, String pointer, String key) {
        if (!key.equals(DialogueGraph.string(entry, "key")) || removed(entry)) return;
        String field = List.of("variants", "lines", "line", "text", "arguments").stream().filter(entry::has).findFirst().orElse("key");
        String path = DefinitionKind.unscopedPath(document.resourcePath()).replaceFirst("/dialogue/[^/]+/", "/dialogue/");
        String identity = DialogueGraph.string(entry, "id");
        result.putIfAbsent(path + "#" + (identity.isEmpty() ? pointer : identity), new Location(document, pointer + "/" + field));
    }

    private static boolean removed(JsonObject value) {
        return value.has("remove") && value.get("remove").isJsonPrimitive() && value.get("remove").getAsJsonPrimitive().isBoolean()
                && value.get("remove").getAsBoolean();
    }

    static List<Location> usages(AuthoringWorkspace workspace, Location target, String locale) {
        return all(workspace, locale).stream().filter(reference -> reference.target().document() == target.document()
                && (target.pointer().isEmpty() || reference.target().pointer().equals(target.pointer()))).map(Reference::source).toList();
    }

    static List<Reference> all(AuthoringWorkspace workspace, String locale) {
        var candidates = new java.util.HashMap<String, List<AuthoringDocument>>();
        for (var document : workspace.documents()) for (String id : identifiers(document))
            candidates.computeIfAbsent(id, ignored -> new ArrayList<>()).add(document);
        var result = new ArrayList<Reference>();
        for (var document : workspace.documents()) collect(workspace, document, document.json(), "", locale, candidates, new java.util.HashMap<>(), result);
        return List.copyOf(result);
    }

    private static void collect(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement value, String pointer,
                                String locale, Map<String, List<AuthoringDocument>> candidates,
                                Map<List<String>, List<Location>> resolved, List<Reference> result) {
        if (value.isJsonObject()) {
            for (var entry : value.getAsJsonObject().entrySet())
                collect(workspace, owner, entry.getValue(), pointer + "/" + JsonPointers.escape(entry.getKey()), locale, candidates, resolved, result);
        } else if (value.isJsonArray()) {
            for (int i = 0; i < value.getAsJsonArray().size(); i++)
                collect(workspace, owner, value.getAsJsonArray().get(i), pointer + "/" + i, locale, candidates, resolved, result);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && !(owner.resourcePath().contains("/dialogue/") && (pointer.equals("/key") || pointer.matches("/messages/[0-9]+/key")))) {
            String text = value.getAsString();
            var possible = new LinkedHashSet<AuthoringDocument>(candidates.getOrDefault(text, List.of()));
            for (var id : com.jvn.villagerretaliation.dialogue.DialogueWordPools.references(text))
                possible.addAll(candidates.getOrDefault(id.toString(), List.of()));
            var tags = java.util.regex.Pattern.compile("</?([a-z][a-z0-9_]*)>").matcher(text);
            while (tags.find()) possible.addAll(candidates.getOrDefault(tags.group(1), List.of()));
            var key = List.of(field(pointer), referenceRoot(owner, pointer, field(pointer)), text);
            var targets = resolved.computeIfAbsent(key, ignored -> definitions(workspace, owner, pointer, text, locale, List.copyOf(possible)));
            for (var target : targets) result.add(new Reference(new Location(owner, pointer), target));
        }
    }
}
