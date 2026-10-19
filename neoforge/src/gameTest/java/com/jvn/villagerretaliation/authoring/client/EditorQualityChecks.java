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

final class EditorQualityChecks {
    private EditorQualityChecks() { }

    static int run() throws IOException {
        resourceLibraryPersistsAndFiltersUnavailableChoices();
        snippetsPreserveDependenciesAcrossRestartAndUndo();
        behaviorSnippetsValidateAndAvoidDuplicateIds();
        bulkPropertiesPreviewMixedValuesAndRejectStaleOrInvalidEdits();
        return 4;
    }

    private static void resourceLibraryPersistsAndFiltersUnavailableChoices() throws IOException {
        var path = Files.createTempDirectory("vr-editor-library").resolve("library.json");
        var library = new EditorLibrary(path); library.load();
        library.resource("items", "minecraft:stone", true);
        library.resource("items", "minecraft:diamond", false);
        library.resource("items", "missing:resource", true);
        var example = new JsonObject(); example.addProperty("type", "block");
        library.saveSnippet("Example", example);
        var restored = new EditorLibrary(path); restored.load();
        check(restored.favorite("items", "minecraft:stone") && !restored.favorite("blocks", "minecraft:stone"), "favorites persist within their resource category");
        var ranked = EditorResourcePicker.ranked(List.of("minecraft:dirt", "minecraft:diamond", "minecraft:stone"), "", restored.resources("items", true), restored.resources("items", false));
        check(ranked.equals(List.of("minecraft:stone", "minecraft:diamond", "minecraft:dirt")), "favorites and recent choices rank first without introducing unavailable resources");
        check(EditorResourcePicker.ranked(List.of("minecraft:diamond_ore", "minecraft:diamond"), "diamond", List.of("minecraft:diamond_ore"), List.of()).getFirst().equals("minecraft:diamond"), "exact matches remain ahead of favorites while searching");
        restored.resource("items", "minecraft:stone", true);
        check(!restored.favorite("items", "minecraft:stone"), "favorites can be removed");
        check(restored.snippets().equals(List.of("Example")), "named snippets persist");
        rejects(() -> { try { restored.saveSnippet("Example", new JsonObject()); } catch (IOException exception) { throw new AssertionError(exception); } });
        restored.removeSnippet("Example");
        check(restored.snippets().isEmpty(), "snippets can be removed without replacing another entry");
        Files.writeString(path, "invalid json");
        var broken = new EditorLibrary(path);
        try { broken.load(); throw new AssertionError("Expected malformed library failure"); } catch (IOException expected) { }
        try { broken.resource("items", "minecraft:stone", true); throw new AssertionError("Expected protected library failure"); } catch (IOException expected) { }
        check(Files.readString(path).equals("invalid json"), "unreadable libraries are preserved without overwriting user data");
        var blocked = Files.createTempDirectory("vr-library-failure");
        var unavailable = new EditorLibrary(blocked);
        try { unavailable.resource("items", "minecraft:stone", true); throw new AssertionError("Expected save failure"); }
        catch (IOException expected) { check(!unavailable.favorite("items", "minecraft:stone"), "failed writes leave library state unchanged"); }
    }

    private static void snippetsPreserveDependenciesAcrossRestartAndUndo() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-snippets"));
        var source = workspace.createDocument(DefinitionKind.TREE, "test:snippet_source");
        DialogueGraph.addNode(source, "finish");
        workspace.addDialogueResponse(source, "start", "continue", "Continue", "finish");
        var library = new EditorLibrary(workspace.directory().resolve("library.json"));
        library.saveSnippet("Branch", EditorSnippets.nodes(workspace, source, Set.of("start")));
        var restored = new EditorLibrary(workspace.directory().resolve("library.json")); restored.load();
        var target = workspace.createDocument(DefinitionKind.TREE, "test:snippet_target");
        var before = target.json();
        var added = EditorSnippets.insert(workspace, target, "", restored.snippet("Branch"));
        check(added.size() == 2 && DialogueGraph.edges(target).stream().anyMatch(edge -> edge.from().equals(added.getFirst()) && added.contains(edge.to())), "persisted snippets include connected dependencies and rewrite links");
        target.undo(); check(target.json().equals(before), "snippet insertion undoes in one step");
        target.redo();
        var repeated = EditorSnippets.insert(workspace, target, "", restored.snippet("Branch"));
        check(repeated.stream().noneMatch(added::contains), "repeated insertions allocate unique node IDs");
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:snippet_quest");
        var original = quest.json(); rejects(() -> EditorSnippets.insert(workspace, quest, "", restored.snippet("Branch")));
        check(quest.json().equals(original), "incompatible graph snippets cannot partly modify a document");
    }

    private static void behaviorSnippetsValidateAndAvoidDuplicateIds() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-block-snippets"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:block_snippets");
        String path = EditorContentTools.appendObjective(quest, "start", "item_check", "minecraft:emerald", 3);
        var snippet = EditorSnippets.block(quest, path);
        var before = quest.json();
        EditorSnippets.insert(workspace, quest, path, snippet);
        var objectives = JsonPointers.get(quest.json(), "/stages/0/objectives").getAsJsonArray();
        check(objectives.size() == 2 && !objectives.get(0).getAsJsonObject().get("id").equals(objectives.get(1).getAsJsonObject().get("id")), "objective snippets avoid duplicate IDs");
        quest.undo(); check(quest.json().equals(before), "block insertion is atomic and undoable");
        rejects(() -> EditorSnippets.insert(workspace, quest, "/stages/0", snippet));
        check(quest.json().equals(before), "a snippet cannot be inserted into an unrelated object");
        snippet.getAsJsonObject("payload").addProperty("count", -4);
        rejects(() -> EditorSnippets.insert(workspace, quest, path, snippet));
        check(quest.json().equals(before), "invalid behavior snippets leave the document unchanged");
    }

    private static void bulkPropertiesPreviewMixedValuesAndRejectStaleOrInvalidEdits() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-bulk-properties"));
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:bulk");
        DialogueGraph.addNode(tree, "second");
        tree.edit("Set wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("second").getAsJsonArray("lines").set(0, new JsonPrimitive("Different")));
        var ids = Set.of("start", "second");
        check(EditorBulkEdit.commonFields(tree, ids).containsKey("/lines/0") && !EditorBulkEdit.commonFields(tree, ids).containsKey("/id"), "bulk fields include shared wording and exclude identities");
        var before = tree.json();
        var plan = EditorBulkEdit.preview(tree, ids, "/lines/0", "Updated");
        check(!plan.before().get("/nodes/start/lines/0").equals(plan.before().get("/nodes/second/lines/0")) && tree.json().equals(before), "preview retains mixed values without modifying the draft");
        plan.apply(workspace);
        check(JsonPointers.get(tree.json(), "/nodes/start/lines/0").getAsString().equals("Updated") && JsonPointers.get(tree.json(), "/nodes/second/lines/0").getAsString().equals("Updated"), "bulk apply changes all selected nodes");
        tree.undo(); check(tree.json().equals(before), "all bulk edits undo together");
        tree.redo();
        var stale = EditorBulkEdit.preview(tree, ids, "/lines/0", "Stale");
        tree.edit("New wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new JsonPrimitive("Keep")));
        var latest = tree.json(); rejects(() -> stale.apply(workspace));
        check(tree.json().equals(latest), "stale bulk previews preserve newer edits");
        rejects(() -> EditorBulkEdit.preview(tree, ids, "/end", "123"));
        rejects(() -> EditorBulkEdit.preview(tree, Set.of("start", "missing"), "/lines/0", "No"));
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
