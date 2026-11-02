package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;

final class EditorDialoguePoolChecks {
    private EditorDialoguePoolChecks() { }

    static int run() throws IOException {
        recognitionKeepsOtherResourcesInTheirEditors();
        copyingPreservesSelectionRulesAndKeepsIdsUnique();
        variationsKeepTheirRepresentationAndHistory();
        removingEntriesAndLinesRebasesCameraMetadata();
        inheritedWordingEditsPreserveRulesAndUndo();
        templatePreviewsFollowUnsavedScopedDefinitions();
        return 6;
    }

    private static AuthoringDocument document(String json) {
        return new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/en_us/pool.json", JsonParser.parseString(json).getAsJsonObject());
    }

    private static void recognitionKeepsOtherResourcesInTheirEditors() {
        var document = document("""
                {"lines":[{"id":"plain","lines":["Hello"]},{"id":"rich","variants":[{"id":"a","text":"Welcome","weight":2}]}]}
                """);
        var pool = new EditorDialoguePool();
        var cache = new EditorDocumentCache();
        check(pool.supports(document, cache.json(document)), "both pool text representations support cards");
        var entry = new EditorDialoguePool.Entry(1, document.value("/lines/1").getAsJsonObject(), "variants");
        check(entry.textPointer(0).equals("/lines/1/variants/0/text") && entry.text(0).getAsString().equals("Welcome"), "variant cards edit the actual text pointer");
        document.edit("Break pool shape", root -> root.addProperty("lines", "Invalid"));
        check(!pool.supports(document, cache.json(document)), "malformed edits return to resource properties");
        document.undo(); check(pool.supports(document, cache.json(document)), "undo refreshes card recognition");
        var locale = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/quests/example/locales/en_us.json", document.json());
        check(!pool.supports(locale, cache.json(locale)), "localization files keep their existing editor");
        var scoped = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/world_campaigns/story/dialogue/en_us/pool.json", document.json());
        check(pool.supports(scoped, cache.json(scoped)), "campaign-scoped pools use the same cards");
        var empty = document("{\"lines\":[]}");
        check(pool.supports(empty, cache.json(empty)), "empty pools remain editable");
    }

    private static void copyingPreservesSelectionRulesAndKeepsIdsUnique() {
        var document = document("""
                {"metadata":{"source":"keep"},"lines":[{"id":"story","request":"share_story","weight":120,
                "story_biome":"minecraft:badlands","custom":{"future":true},"lines":["First","Second"]}]}
                """);
        var original = document.json();
        int copy = EditorDialoguePool.append(document, 0);
        check(copy == 1 && document.value("/lines/1/story_biome").equals(document.value("/lines/0/story_biome")), "duplicates preserve selection rules");
        check(document.value("/lines/1/custom").equals(document.value("/lines/0/custom")), "unknown properties survive copying");
        check(document.value("/lines/1/id").getAsString().equals("story_copy"), "duplicates get another identity");
        EditorDialoguePool.append(document, 0);
        check(document.value("/lines/2/id").getAsString().equals("story_copy_2"), "repeated duplicates avoid collisions");
        document.undo(); document.undo();
        check(document.json().equals(original), "undo restores the exact pool and metadata");
        int added = EditorDialoguePool.append(document, -1);
        check(document.value("/lines/" + added + "/lines").getAsJsonArray().size() == 1, "new entries start with a playable line");
    }

    private static void variationsKeepTheirRepresentationAndHistory() throws IOException {
        var document = document("""
                {"lines":[{"id":"plain","request":"greeting","lines":["Hello"]},
                {"id":"rich","request":"greeting","variants":[{"id":"line","text":"Welcome","weight":5,"conditions":[{"type":"baby"}]}]}]}
                """);
        var original = document.json();
        int copy = EditorDialoguePool.appendLine(document, 1, 0);
        check(copy == 1 && !document.value("/lines/1/variants/0/id").equals(document.value("/lines/1/variants/1/id")), "copied variants have unique IDs");
        check(document.value("/lines/1/variants/0/conditions").equals(document.value("/lines/1/variants/1/conditions")), "copied variations retain their own conditions");
        EditorDialoguePool.appendLine(document, 0, -1);
        check(document.value("/lines/0/lines/1").isJsonPrimitive(), "plain arrays stay plain");
        EditorDialoguePool.removeLine(document, 0, 0);
        var before = document.json();
        try { EditorDialoguePool.removeLine(document, 0, 0); throw new AssertionError("removed the last variation"); }
        catch (IllegalArgumentException expected) { check(document.json().equals(before), "rejecting last-line removal is atomic"); }
        document.undo(); document.undo(); document.undo();
        check(document.json().equals(original), "variation edits undo without changing the file format");
        document.redo();
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("editor-pool")); workspace.add(document); workspace.save();
        var reopened = AuthoringWorkspace.open(workspace.directory());
        check(reopened.documents().getFirst().json().equals(document.json()), "card edits survive workspace saves and reopening");
    }

    private static void removingEntriesAndLinesRebasesCameraMetadata() {
        var document = document("""
                {"lines":[{"lines":["First"]},{"lines":["Second","Third"]}]}
                """);
        document.editMetadata("Camera previews", root -> root.add("camera_thumbnails", JsonParser.parseString("""
                {"/lines/1/lines/1":"preview"}
                """)));
        var original = document.metadata();
        EditorDialoguePool.remove(document, 0);
        check(document.metadata().getAsJsonObject("camera_thumbnails").has("/lines/0/lines/1"), "entry removal keeps camera previews with their content");
        EditorDialoguePool.removeLine(document, 0, 0);
        check(document.metadata().getAsJsonObject("camera_thumbnails").has("/lines/0/lines/0"), "line removal rebases nested previews");
        document.undo(); document.undo();
        check(document.metadata().equals(original), "undo restores camera metadata with the removed content");
    }

    private static void inheritedWordingEditsPreserveRulesAndUndo() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("editor-inherited-wording"));
        var parent = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue_words/en_us/base.json", JsonParser.parseString("""
                {"role":"sentence","selection_group":"test:tone","values":[{"id":"plain","text":"I am all right."},{"id":"quiet","text":"Taking it slowly today.",
                "priority":10,"weight":3,"variant":"quiet","conditions":[{"type":"mood","moods":["stressed"]}]}]}
                """).getAsJsonObject());
        var child = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue_words/en_us/child.json",
                JsonParser.parseString("{\"extends\":\"test:base\"}").getAsJsonObject());
        workspace.add(parent); workspace.add(child);
        var original = child.json();
        EditorWordPoolPreview.overrideWording(workspace, child, "quiet", "Could do with a rest.");
        var pools = com.jvn.villagerretaliation.authoring.preview.PreviewWordPools.lookupDefinitions(workspace, child, "en_us",
                (id, locale) -> com.jvn.villagerretaliation.dialogue.DialogueWordPool.EMPTY);
        var entry = pools.apply(net.minecraft.resources.ResourceLocation.parse("test:child")).entries().stream().filter(value -> value.id().equals("quiet")).findFirst().orElseThrow();
        check(entry.text().equals("Could do with a rest.") && entry.priority() == 10 && entry.weight() == 3
                && entry.variant().equals("quiet") && entry.conditions().size() == 1, "wording edits keep inherited selection rules");
        check(!child.json().has("values"), "wording edits do not create full replacement entries");
        child.undo(); check(child.json().equals(original), "wording edits undo as one change");
        child.redo();
        var before = child.json();
        try { EditorWordPoolPreview.overrideWording(workspace, child, "missing", "Hello."); throw new AssertionError("accepted missing inherited entry"); }
        catch (IllegalArgumentException expected) { check(child.json().equals(before), "invalid inherited edits leave the draft intact"); }
    }

    private static void templatePreviewsFollowUnsavedScopedDefinitions() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("editor-template-preview"));
        var template = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue_templates/en_us/greeting.json",
                JsonParser.parseString("{\"parameters\":[\"body\"],\"text\":\"{body}\"}").getAsJsonObject());
        var scoped = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/world_campaigns/story/dialogue_templates/en_us/greeting.json",
                JsonParser.parseString("{\"parameters\":[\"body\"],\"text\":\"Welcome, {body}\"}").getAsJsonObject());
        var owner = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/world_campaigns/story/dialogue/en_us/messages.json",
                JsonParser.parseString("{\"messages\":[{\"key\":\"hello\",\"template\":\"test:greeting\",\"arguments\":{\"body\":\"{word:test:hello}\"}}]}").getAsJsonObject());
        workspace.add(template); workspace.add(scoped); workspace.add(owner);
        var reference = JsonParser.parseString("{\"key\":\"hello\"}");
        check(workspace.resolveText(owner, reference, "en_us").equals("Welcome, {word:test:hello}"),
                "keyed messages expand unsaved presentation templates");
        check(EditorWordPoolPreview.templates(workspace, owner).equals(java.util.List.of("Welcome, {word:test:hello}")),
                "phrase previews expand the owning campaign's unsaved templates");
        scoped.edit("Change template", root -> root.addProperty("text", "Hello, {body}"));
        check(EditorWordPoolPreview.templates(workspace, owner).equals(java.util.List.of("Hello, {word:test:hello}")),
                "parent template edits update previews without saving");
        scoped.undo();
        check(EditorWordPoolPreview.templates(workspace, owner).equals(java.util.List.of("Welcome, {word:test:hello}")),
                "template undo restores the preview");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
