package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringValidation;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.authoring.preview.AuthoringDialoguePreview;
import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import com.jvn.villagerretaliation.authoring.preview.PreviewWordPools;
import com.jvn.villagerretaliation.authoring.preview.WordPoolPreview;
import com.jvn.villagerretaliation.dialogue.DialogueWordHistory;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects;
import com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

final class EditorDialogueAuthoring {
    static final List<String> PROFESSIONS = List.of("armorer", "butcher", "cartographer", "cleric", "farmer", "fisherman",
            "fletcher", "leatherworker", "librarian", "mason", "shepherd", "toolsmith", "weaponsmith", "none", "nitwit");
    private static final Pattern POOL = Pattern.compile("\\{word:([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\|[^}]*)?}");
    record Wording(String pointer, String label, String text) { }

    static boolean supports(AuthoringDocument document) {
        if (document == null || document.kind() != DefinitionKind.RESOURCE) return false;
        String path = DefinitionKind.unscopedPath(document.resourcePath());
        return path.contains("/dialogue_templates/") || path.contains("/dialogue_words/")
                || path.contains("/dialogue/") && document.json().has("messages") && document.json().get("messages").isJsonArray();
    }

    static String previewPointer(AuthoringDocument document, String pointer) {
        if (!supports(document) || !document.json().has("messages")) return pointer;
        String anchor = EditorDialogueLinks.anchor(document, pointer);
        if (!anchor.isEmpty()) return anchor;
        return pointer.isEmpty() && !document.json().getAsJsonArray("messages").isEmpty() ? "/messages/0" : pointer;
    }

    static List<Wording> wording(JsonElement value, String pointer) {
        var result = new ArrayList<Wording>();
        collect(value, pointer, "Villager says", result, 0);
        return List.copyOf(result);
    }

    private static void collect(JsonElement value, String pointer, String label, List<Wording> result, int depth) {
        if (value == null || depth > 16) return;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            result.add(new Wording(pointer, label, value.getAsString()));
        } else if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) collect(array.get(i), pointer + "/" + i,
                    array.size() > 1 ? label + " / " + (i + 1) : label, result, depth + 1);
        } else if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            if (object.has("arguments") && object.get("arguments").isJsonObject())
                for (var entry : object.getAsJsonObject("arguments").entrySet())
                    collect(entry.getValue(), pointer + "/arguments/" + JsonPointers.escape(entry.getKey()),
                            entry.getKey().equals("body") ? "Villager says" : EditorContentBrowser.humanize(entry.getKey()), result, depth + 1);
            for (String key : List.of("text", "line", "lines", "variants", "values", "groups", "text_overrides"))
                if (object.has(key)) {
                    if (key.equals("text_overrides") && object.get(key).isJsonObject())
                        for (var entry : object.getAsJsonObject(key).entrySet())
                            collect(entry.getValue(), pointer + "/text_overrides/" + JsonPointers.escape(entry.getKey()),
                                    "Custom wording / " + EditorContentBrowser.humanize(entry.getKey()), result, depth + 1);
                    else collect(object.get(key), pointer + "/" + key, label, result, depth + 1);
                }
        }
    }

    static List<String> pools(String wording) {
        var result = new java.util.LinkedHashSet<String>();
        var matcher = POOL.matcher(wording);
        while (matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    static String readable(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return EditorContentBrowser.humanize(path.replace('/', ' ').replace('.', ' '));
    }

    static void usePool(AuthoringWorkspace workspace, AuthoringDocument document, String pointer, String id, String locale) {
        var key = ResourceLocation.parse(id);
        if (PreviewWordPools.lookupDefinitions(workspace, document, locale, AuthoringDialoguePreview::defaultWordPool).apply(key).entries().isEmpty())
            throw new IllegalArgumentException("Choose a pool with at least one wording entry");
        var original = document.value(pointer);
        if (!original.isJsonPrimitive() || !original.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Choose a line of wording first");
        var changed = document.json();
        JsonPointers.set(changed, pointer, new JsonPrimitive("{word:" + id + "}"));
        EditorMutation.validate(workspace, document, changed);
        document.edit("Use phrase pool", root -> JsonPointers.set(root, pointer, new JsonPrimitive("{word:" + id + "}")));
    }

    static AuthoringDocument createPool(AuthoringWorkspace workspace, AuthoringDocument owner, String pointer, String name, String locale) {
        if (!locale.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Choose a valid language");
        String namespace = DefinitionKind.unscopedPath(owner.resourcePath()).split("/")[1];
        String id = EditorContentTools.resourceId(name);
        id = name.contains(":") ? id : namespace + ":" + id.split(":", 2)[1];
        var key = ResourceLocation.parse(id);
        String scope = DefinitionKind.contentRoot(owner.resourcePath());
        if (!scope.startsWith("data/" + key.getNamespace() + "/")) throw new IllegalArgumentException("Use this content folder's namespace");
        String path = scope + "dialogue_words/" + locale + "/" + key.getPath() + ".json";
        if (workspace.filePaths().contains(path)) throw new IllegalArgumentException("A pool with this name already exists");
        var text = owner.value(pointer);
        if (!text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString() || !pools(text.getAsString()).isEmpty())
            throw new IllegalArgumentException("Choose literal wording to turn into a pool");
        var root = new JsonObject();
        var values = new JsonArray();
        var entry = new JsonObject(); entry.addProperty("id", "wording_1"); entry.addProperty("text", text.getAsString());
        values.add(entry); root.add("values", values);
        var pool = new AuthoringDocument(DefinitionKind.RESOURCE, path, root);
        var changed = owner.json();
        String token = "{word:" + id + "}";
        JsonPointers.set(changed, pointer, new JsonPrimitive(token));
        validate(workspace, owner, changed, pool);
        workspace.add(pool);
        owner.edit("Share wording in a pool", value -> JsonPointers.set(value, pointer, new JsonPrimitive(token)));
        return pool;
    }

    static String addWording(AuthoringWorkspace workspace, AuthoringDocument document, boolean conditional, String locale) {
        var changed = document.json();
        if (changed.has("values") && !changed.get("values").isJsonArray()) throw new IllegalArgumentException("Pool values must be a list");
        var values = changed.has("values") ? changed.getAsJsonArray("values") : new JsonArray();
        if (values.size() >= 256) throw new IllegalArgumentException("Use at most 256 word pool values");
        String id = EditorReferences.identifiers(document).stream().filter(value -> value.contains(":") && !value.startsWith("data/")).findFirst().orElseThrow();
        var inherited = PreviewWordPools.lookupDefinitions(workspace, document, locale, AuthoringDialoguePreview::defaultWordPool).apply(ResourceLocation.parse(id));
        var used = inherited.entries().stream().map(com.jvn.villagerretaliation.dialogue.DialogueWordPool.Entry::id).collect(java.util.stream.Collectors.toSet());
        int suffix = 1;
        while (used.contains("wording_" + suffix)) suffix++;
        var entry = new JsonObject(); entry.addProperty("id", "wording_" + suffix); entry.addProperty("text", "New wording.");
        if (conditional) { entry.addProperty("weight", 1); entry.add("conditions", new JsonArray()); }
        int index = values.size(); values.add(entry); changed.add("values", values);
        EditorMutation.validate(workspace, document, changed);
        var replacement = values.deepCopy();
        document.edit("Add alternative wording", root -> root.add("values", replacement));
        return "/values/" + index;
    }

    static EditorReferences.Location professionOverride(AuthoringWorkspace workspace, AuthoringDocument shared, String pointer, String profession) {
        if (!PROFESSIONS.contains(profession) || !EditorDialogueLinks.profession(shared).isEmpty()
                || !shared.resourcePath().contains("/dialogue/"))
            throw new IllegalArgumentException("Choose shared wording and a vanilla profession");
        String anchor = EditorDialogueLinks.anchor(shared, pointer);
        if (anchor.isEmpty()) throw new IllegalArgumentException("Choose a shared message first");
        var message = shared.value(anchor).getAsJsonObject();
        String key = DialogueGraph.string(message, "key");
        if (key.isBlank()) throw new IllegalArgumentException("The shared message needs a wording reference");
        String path = shared.resourcePath().replaceFirst("/dialogue/([^/]+)/(?:global/)?", "/dialogue/$1/professions/" + profession + "/");
        var target = workspace.documents().stream().filter(value -> value.resourcePath().equals(path)).findFirst().orElse(null);
        var root = target == null ? new JsonObject() : target.json();
        var messages = root.has("messages") ? root.getAsJsonArray("messages") : new JsonArray();
        for (int i = 0; i < messages.size(); i++)
            if (messages.get(i).isJsonObject() && key.equals(DialogueGraph.string(messages.get(i).getAsJsonObject(), "key")))
                return new EditorReferences.Location(target, "/messages/" + i);
        int index = messages.size();
        messages.add(message.deepCopy()); root.add("messages", messages);
        if (target == null) {
            target = new AuthoringDocument(DefinitionKind.RESOURCE, path, root);
            validate(workspace, shared, shared.json(), target);
            workspace.add(target);
        } else {
            EditorMutation.validate(workspace, target, root);
            var replacement = messages.deepCopy();
            target.edit("Add profession wording", value -> value.add("messages", replacement));
        }
        return new EditorReferences.Location(target, "/messages/" + index);
    }

    static AuthoringDocument professionPool(AuthoringWorkspace workspace, AuthoringDocument owner, String pointer, String parent, String locale) {
        String profession = EditorDialogueLinks.profession(owner);
        if (profession.isEmpty()) throw new IllegalArgumentException("Choose profession wording first");
        if (!pools(owner.value(pointer).getAsString()).contains(parent)) throw new IllegalArgumentException("Choose a pool used by this wording");
        var key = ResourceLocation.parse(parent);
        String namespace = DefinitionKind.unscopedPath(owner.resourcePath()).split("/")[1];
        String id = namespace + ":authoring/professions/" + profession + "/" + key.getNamespace() + "/" + key.getPath();
        String path = DefinitionKind.contentRoot(owner.resourcePath()) + "dialogue_words/" + locale + "/" + id.split(":", 2)[1] + ".json";
        var pool = workspace.documents().stream().filter(value -> value.resourcePath().equals(path)).findFirst().orElse(null);
        if (pool != null && !parent.equals(DialogueGraph.string(pool.json(), "extends")))
            throw new IllegalArgumentException("A different pool already uses this name");
        var root = new JsonObject(); root.addProperty("extends", parent);
        if (pool == null) pool = new AuthoringDocument(DefinitionKind.RESOURCE, path, root);
        var changed = owner.json();
        String text = owner.value(pointer).getAsString().replace("{word:" + parent + "}", "{word:" + id + "}")
                .replace("{word:" + parent + "|", "{word:" + id + "|");
        JsonPointers.set(changed, pointer, new JsonPrimitive(text));
        if (!workspace.documents().contains(pool)) {
            validate(workspace, owner, changed, pool); workspace.add(pool);
        } else EditorMutation.validate(workspace, owner, changed);
        owner.edit("Use profession wording pool", value -> JsonPointers.set(value, pointer, new JsonPrimitive(text)));
        return pool;
    }

    private static void validate(AuthoringWorkspace workspace, AuthoringDocument owner, JsonObject changed, AuthoringDocument added) {
        var before = AuthoringValidation.validate(workspace.documents());
        var candidate = new ArrayList<>(workspace.documents().stream().map(document -> document == owner
                ? new AuthoringDocument(owner.kind(), owner.resourcePath(), changed) : document).toList());
        candidate.add(added);
        var issue = AuthoringValidation.validate(candidate).stream().filter(value -> value.severity() == AuthoringDiagnostic.Severity.ERROR && !before.contains(value)).findFirst();
        if (issue.isPresent()) throw new IllegalArgumentException(issue.get().pointer() + ": " + issue.get().message());
    }

    static WordPoolPreview.Result example(AuthoringWorkspace workspace, AuthoringDocument owner, JsonElement value,
            String locale, String profession, long seed) {
        if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) value = value.getAsJsonArray().get(0);
        if (value.isJsonObject() && value.getAsJsonObject().has("key")) {
            String key = DialogueGraph.string(value.getAsJsonObject(), "key");
            var definitions = EditorReferences.definitions(workspace, owner, "/key", key, locale);
            var target = definitions.stream().filter(location -> EditorDialogueLinks.profession(location.document()).equals(profession)).findFirst();
            if (target.isEmpty()) target = definitions.stream().filter(location -> EditorDialogueLinks.profession(location.document()).isEmpty()).findFirst();
            if (target.isPresent()) {
                owner = target.get().document();
                String anchor = EditorDialogueLinks.anchor(owner, target.get().pointer());
                value = owner.value(anchor.isEmpty() ? target.get().pointer() : anchor);
            }
        }
        String text = workspace.resolveText(owner, value, locale);
        var conditions = new PreviewConditions();
        conditions.profession(profession.isEmpty() ? "none" : profession);
        conditions.contexts(PreviewWordPools.contexts(workspace, owner));
        conditions.placeholder("player", "Alex");
        return WordPoolPreview.inspect(text,
                PreviewWordPools.lookupDefinitions(workspace, owner, locale, AuthoringDialoguePreview::defaultWordPool),
                conditions, seed, new DialogueWordHistory(), "authoring-example");
    }

    static String plain(String text) {
        return DialogueTextSegment.plainText(DialogueTextSegment.parse(text, DialogueTextEffects.NONE));
    }
}
