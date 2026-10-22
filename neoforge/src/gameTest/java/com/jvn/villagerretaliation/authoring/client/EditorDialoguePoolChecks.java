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
        return 4;
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

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
