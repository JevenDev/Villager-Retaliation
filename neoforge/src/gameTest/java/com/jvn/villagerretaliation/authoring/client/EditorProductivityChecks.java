package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

final class EditorProductivityChecks {
    private EditorProductivityChecks() { }

    static int run() throws IOException {
        nestedBulkEditsPreserveBlockTypesAndUndo();
        workspaceReplacementsAreAtomicAcrossHistoryAndStaleFiles();
        snippetManagementPreservesExistingLibraries();
        return 3;
    }

    private static void nestedBulkEditsPreserveBlockTypesAndUndo() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-nested-bulk"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:bulk");
        String first = DialogueGraph.nodes(quest).getFirst().id();
        DialogueGraph.addNode(quest, "second");
        for (String id : List.of(first, "second")) EditorContentTools.appendObjective(quest, id, "item_check", "minecraft:stone", 2);
        var selected = Set.of(first, "second");
        var fields = EditorBulkEdit.commonFields(quest, selected);
        check(fields.containsKey("/objectives/0/count") && !fields.containsKey("/objectives/0/type") && !fields.containsKey("/objectives/0/id"), "nested counts are editable while block types and identities are protected");
        var before = quest.json();
        EditorBulkEdit.preview(quest, selected, "/objectives/0/count", "7").apply(workspace);
        check(fields.get("/objectives/0/count").stream().allMatch(path -> JsonPointers.get(quest.json(), path).getAsInt() == 7), "bulk edits reach every selected objective");
        quest.undo(); check(quest.json().equals(before), "nested bulk edits undo together");
        quest.edit("Different block type", root -> JsonPointers.set(root, "/stages/1/objectives/0/type", new JsonPrimitive("block_break")));
        check(!EditorBulkEdit.commonFields(quest, selected).containsKey("/objectives/0/count"), "different block types at the same array index are not treated as equivalent");
    }

    private static void workspaceReplacementsAreAtomicAcrossHistoryAndStaleFiles() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-batch-wording"));
        var a = workspace.createDocument(DefinitionKind.TREE, "test:a");
        var b = workspace.createDocument(DefinitionKind.TREE, "test:b");
        var beforeA = a.json(); var beforeB = b.json();
        var plan = EditorWordingReplacement.preview(List.of(a, b), "Hello", "Welcome");
        plan.apply(workspace);
        check(JsonPointers.get(a.json(), "/nodes/start/lines/0").getAsString().startsWith("Welcome")
                && JsonPointers.get(b.json(), "/nodes/start/lines/0").getAsString().startsWith("Welcome"), "workspace replacements update selected files");
        b.undo(); check(a.json().equals(beforeA) && b.json().equals(beforeB), "undo from either file restores the entire replacement batch");
        a.redo();
        b.edit("Newer edit", root -> root.addProperty("label", "Keep"));
        rejects(a::undo);
        check(b.json().get("label").getAsString().equals("Keep"), "grouped undo cannot erase later edits in another file");
        var stale = EditorWordingReplacement.preview(List.of(a, b), "Welcome", "Changed");
        b.edit("Changed after preview", root -> root.addProperty("label", "New"));
        var latestA = a.json(); var latestB = b.json();
        rejects(() -> stale.apply(workspace));
        check(a.json().equals(latestA) && b.json().equals(latestB), "a stale file prevents every replacement in the batch");
        var removed = EditorWordingReplacement.preview(List.of(a, b), "Welcome", "Changed");
        workspace.remove(b); rejects(() -> removed.apply(workspace));
        check(a.json().equals(latestA), "removed files invalidate the batch before other files change");
    }

    private static void snippetManagementPreservesExistingLibraries() throws IOException {
        var directory = Files.createTempDirectory("vr-snippet-management");
        var library = new EditorLibrary(directory.resolve("library.json"));
        var snippet = JsonParser.parseString("""
                {"type":"block","kind":"TREE","collection":"actions","payload":{"type":"experience","amount":1}}
                """).getAsJsonObject();
        library.saveSnippet("Original", snippet);
        library.categorizeSnippet("Original", "Rewards");
        library.renameSnippet("Original", "Experience");
        snippet.getAsJsonObject("payload").addProperty("amount", 5);
        library.updateSnippet("Experience", snippet);
        check(library.snippets("reWards", "Rewards").equals(List.of("Experience")), "snippet search includes categories and ignores case");
        var export = directory.resolve("export.json"); library.exportSnippets(export);
        check(library.importSnippets(export) == 1 && library.snippets().contains("Experience (imported 2)"), "import handles name collisions without replacing existing snippets");
        var restored = new EditorLibrary(directory.resolve("library.json")); restored.load();
        check(restored.snippet("Experience").get("category").getAsString().equals("Rewards")
                && restored.snippet("Experience").getAsJsonObject("payload").get("amount").getAsInt() == 5, "renames, categories and updated contents persist");
        rejects(() -> { try { restored.renameSnippet("Experience", "Experience (imported 2)"); } catch (IOException exception) { throw new AssertionError(exception); } });
        String existing = Files.readString(export);
        try { library.exportSnippets(export); throw new AssertionError("Expected protected export"); } catch (java.nio.file.FileAlreadyExistsException expected) { }
        check(Files.readString(export).equals(existing), "exports preserve existing files");
        var invalid = directory.resolve("invalid.json");
        Files.writeString(invalid, "{\"version\":1,\"snippets\":{\"Good\":" + snippet + ",\"Bad\":{}}}");
        int count = library.snippets().size();
        try { library.importSnippets(invalid); throw new AssertionError("Expected invalid import"); } catch (IOException expected) { }
        check(library.snippets().size() == count, "invalid imports do not partially add snippets");
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
