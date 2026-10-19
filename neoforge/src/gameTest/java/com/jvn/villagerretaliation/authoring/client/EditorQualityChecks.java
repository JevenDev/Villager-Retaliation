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
        savedChangesTrackFilesFieldsAndRecovery();
        fieldRevertsPreserveOtherEditsAndRejectStaleReviews();
        quickFixesRepairMissingDestinationsAtomically();
        dialogueCoverageRecordsActualBranchesAndDistinctNodePaths();
        questCoverageAccumulatesAndRejectsOldLogic();
        liveCoverageMapsRuntimePathsWithoutInferringChoices();
        return 10;
    }

    private static void resourceLibraryPersistsAndFiltersUnavailableChoices() throws IOException {
        var path = Files.createTempDirectory("vr-editor-library").resolve("library.json");
        var library = new EditorLibrary(path); library.load();
        library.resource("items", "minecraft:stone", true);
        library.resource("items", "minecraft:diamond", false);
        library.resource("items", "missing:resource", true);
        var example = json("""
                {"type":"block","kind":"TREE","collection":"actions","payload":{"type":"experience","amount":1}}
                """);
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
        rejects(() -> EditorSnippets.insert(workspace, target, "", new JsonObject()));
        check(target.json().equals(before), "malformed snippet payloads fail before changing the draft");
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
        var completion = JsonPointers.get(quest.json(), "/stages/0/complete_when").getAsJsonArray();
        check(completion.contains(objectives.get(1).getAsJsonObject().get("id")), "objective snippets join the stage's completion requirements");
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

    private static void savedChangesTrackFilesFieldsAndRecovery() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-saved-changes"));
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:changes"); workspace.save();
        byte[] saved = workspace.savedFile(tree.resourcePath());
        tree.edit("Change wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new JsonPrimitive("Draft")));
        check(workspace.changedFilePaths().equals(List.of(tree.resourcePath())), "saved change review lists modified content files");
        var before = JsonParser.parseString(new String(saved, java.nio.charset.StandardCharsets.UTF_8));
        var diff = EditorChanges.compare(before, tree.json());
        check(diff.size() == 1 && diff.getFirst().pointer().equals("/nodes/start/lines/0"), "changes locate individual wording fields");
        var recoveredPath = tree.resourcePath(); workspace.saveRecovery(new JsonObject());
        var recovered = AuthoringWorkspace.recover(workspace.directory());
        check(java.util.Arrays.equals(saved, recovered.savedFile(recoveredPath)) && recovered.changedFilePaths().contains(recoveredPath), "recovered drafts compare against the saved workspace rather than the recovery snapshot");
        workspace.rename(tree, "test:renamed_changes");
        check(workspace.changedFilePaths().containsAll(List.of(recoveredPath, tree.resourcePath())), "renames expose the removed and added file paths");
        var added = workspace.createDocument(DefinitionKind.TREE, "test:new_changes");
        check(workspace.savedFile(added.resourcePath()) == null, "new files have no saved baseline");
        workspace.save(); check(workspace.changedFilePaths().isEmpty(), "saving refreshes every comparison baseline");
        tree.editMetadata("Move node", root -> root.addProperty("test_layout", true));
        check(workspace.changedFilePaths().isEmpty() && workspace.dirty(), "editor metadata does not appear as pack content changes");
        workspace.remove(added);
        check(workspace.changedFilePaths().contains(added.resourcePath()), "removed files remain visible in the change review");
    }

    private static void fieldRevertsPreserveOtherEditsAndRejectStaleReviews() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-field-reverts"));
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:revert"); workspace.save();
        var original = tree.json();
        tree.edit("Change wording and display", root -> {
            root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new JsonPrimitive("Draft"));
            root.addProperty("display", "Keep this edit");
        });
        var baseline = tree.json();
        var wording = EditorChanges.compare(original, baseline).stream().filter(change -> change.pointer().endsWith("/lines/0")).findFirst().orElseThrow();
        EditorChanges.revert(workspace, tree, baseline, wording);
        check(JsonPointers.get(tree.json(), wording.pointer()).getAsString().equals("Hello.") && tree.json().get("display").getAsString().equals("Keep this edit"), "field reverts preserve unrelated edits");
        tree.undo(); check(tree.json().equals(baseline), "field reverts can be undone");
        tree.edit("Newer wording", root -> root.addProperty("display", "Newer"));
        var latest = tree.json(); rejects(() -> EditorChanges.revert(workspace, tree, baseline, wording));
        check(tree.json().equals(latest), "stale change reviews cannot overwrite newer drafts");
        var first = JsonParser.parseString("[{\"id\":\"a\",\"value\":1},{\"id\":\"b\",\"value\":2}]");
        var reordered = JsonParser.parseString("[{\"id\":\"b\",\"value\":2},{\"id\":\"a\",\"value\":1}]");
        check(EditorChanges.compare(first, reordered).size() == 1 && EditorChanges.compare(first, reordered).getFirst().pointer().isEmpty(), "reordered identity lists revert together without mixing entries");
        var absent = new JsonObject(); var explicitNull = new JsonObject(); explicitNull.add("field", com.google.gson.JsonNull.INSTANCE);
        var nullChange = EditorChanges.compare(absent, explicitNull).getFirst();
        check(nullChange.before() == null && nullChange.after().isJsonNull(), "change review distinguishes absent fields from explicit nulls");
    }

    private static void quickFixesRepairMissingDestinationsAtomically() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-quick-fixes"));
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:fixes");
        tree.edit("Broken reference", root -> root.getAsJsonArray("entries").get(0).getAsJsonObject().addProperty("start", "missing"));
        var issue = workspace.validate().stream().filter(value -> value.message().equals("Missing entry node")).findFirst().orElseThrow();
        var target = EditorQuickFixes.target(tree, issue); var before = tree.json();
        check(target != null, "missing entry diagnostics expose a targeted repair");
        EditorQuickFixes.apply(workspace, tree, target, "missing", true);
        check(DialogueGraph.nodes(tree).stream().anyMatch(node -> node.id().equals("missing")) && workspace.validate().stream().noneMatch(value -> value.message().equals("Missing entry node")), "create destination repairs the reference and graph in one operation");
        tree.undo(); check(tree.json().equals(before), "quick fix creation undoes in one step");
        EditorQuickFixes.apply(workspace, tree, target, "start", false);
        check(tree.json().getAsJsonArray("entries").get(0).getAsJsonObject().get("start").getAsString().equals("start"), "replacement fixes select an existing destination");
        var latest = tree.json(); rejects(() -> EditorQuickFixes.apply(workspace, tree, target, "missing", true));
        check(tree.json().equals(latest), "stale fixes preserve newer references");
        tree.undo();
        rejects(() -> EditorQuickFixes.apply(workspace, tree, target, "bad id", true));
        check(tree.json().equals(before), "invalid destination names cannot partly change the graph");
        var other = new com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic(com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR, tree.resourcePath(), "/id", "Duplicate definition ID");
        check(EditorQuickFixes.target(tree, other) == null, "unrelated diagnostics never receive graph reference fixes");
    }

    private static void dialogueCoverageRecordsActualBranchesAndDistinctNodePaths() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-dialogue-coverage"));
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:coverage");
        DialogueGraph.addNode(tree, "identical");
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        var session = new com.jvn.villagerretaliation.authoring.preview.DialoguePreviewSession(tree.json(), net.minecraft.resources.ResourceLocation.parse(tree.id()), conditions, key -> null);
        session.start("identical");
        check(session.drainCoverage().nodes().equals(Set.of("/nodes/identical")), "equal-looking nodes keep their exact authored paths in coverage");
        workspace.addDialogueResponse(tree, "start", "continue", "Continue", "identical");
        session = new com.jvn.villagerretaliation.authoring.preview.DialoguePreviewSession(tree.json(), net.minecraft.resources.ResourceLocation.parse(tree.id()), conditions, key -> null);
        session.start("start");
        var initial = session.drainCoverage();
        check(initial.nodes().equals(Set.of("/nodes/start")) && initial.choices().isEmpty(), "showing a choice does not mark it as tested");
        session.choose(0);
        var selected = session.drainCoverage();
        check(selected.nodes().contains("/nodes/identical") && selected.choices().contains("/nodes/start/responses/0"), "taking a branch records both the choice and destination");
        check(session.drainCoverage().nodes().isEmpty() && session.drainCoverage().choices().isEmpty(), "coverage batches drain without replaying old results");
        var forced = json("""
                {"trigger":"container_opened","lines":["Hello"],"options":[{"id":"continue","label":"Go on","follow_up":{"lines":["Next"],"options":[{"id":"end","label":"Bye","end_conversation":true}]}}]}
                """);
        session = new com.jvn.villagerretaliation.authoring.preview.DialoguePreviewSession(forced, net.minecraft.resources.ResourceLocation.parse("test:forced_coverage"), conditions, key -> null);
        session.start(""); session.drainCoverage(); session.choose(0);
        var nested = session.drainCoverage();
        check(nested.nodes().contains("/options/0/follow_up") && nested.choices().contains("/options/0"), "forced follow-ups record authored ownership paths rather than temporary preview IDs");
        session.choose(0);
        check(session.drainCoverage().choices().contains("/options/0/follow_up/options/0"), "terminal responses count even without a destination edge");
        session.close();
    }

    private static void questCoverageAccumulatesAndRejectsOldLogic() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-quest-coverage"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest_coverage");
        DialogueGraph.addNode(quest, "finish"); EditorDocumentOperations.nextStage(quest, "start", "finish");
        var coverage = new EditorCoverage(); var observe = coverage.observe(quest);
        observe.accept(new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("/stages/0"), Set.of()));
        check(coverage.visited(quest, "/stages/0") && !coverage.visited(quest, "/stages/1"), "quest simulation records only entered stages");
        observe.accept(new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("/stages/1"), Set.of("/stages/0/transition")));
        check(coverage.visited(quest, "/stages/1") && coverage.chosen(quest, "/stages/0/transition/stage"), "quest transitions map to their graph connections");
        coverage.clear(quest); observe.accept(new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of(), Set.of()));
        check(!coverage.visited(quest, "/stages/0"), "clearing coverage does not re-add old drained events");
        observe.accept(new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("/stages/0"), Set.of()));
        quest.edit("Visual edit", root -> root.addProperty("description", "New wording"));
        check(coverage.visited(quest, "/stages/0"), "presentation edits preserve coverage");
        quest.edit("Change entry", root -> root.addProperty("entry_stage", "finish"));
        check(!coverage.visited(quest, "/stages/0"), "logic edits invalidate older coverage");
        observe.accept(new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("/stages/1"), Set.of()));
        check(!coverage.visited(quest, "/stages/1"), "events from an older running simulation cannot cover edited logic");
        var other = workspace.createDocument(DefinitionKind.QUEST, "test:other_coverage");
        check(!coverage.visited(other, "/stages/0"), "coverage stays attached to the tested document");
    }

    static void questSimulationRecordsCoverage() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-native-coverage"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:simulation_coverage");
        DialogueGraph.addNode(quest, "finish"); EditorDocumentOperations.nextStage(quest, "start", "finish");
        var simulation = new com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession(quest, new com.jvn.villagerretaliation.authoring.preview.PreviewConditions());
        check(simulation.drainCoverage().nodes().equals(Set.of("/stages/0")), "quest startup records the entry stage");
        simulation.advance(); var advanced = simulation.drainCoverage();
        check(advanced.nodes().equals(Set.of("/stages/1")) && advanced.choices().contains("/stages/0/transition"), "executed quest transitions produce precise coverage events");
        simulation.advance();
        check(simulation.drainCoverage().choices().contains("/stages/1/transition"), "terminal quest transitions count without a destination node");
    }

    private static void liveCoverageMapsRuntimePathsWithoutInferringChoices() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-live-coverage"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:live_coverage");
        var coverage = new EditorCoverage(); var source = quest.json();
        coverage.live(quest, source, new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("start"), Set.of()));
        check(coverage.visited(quest, "/stages/0") && !coverage.chosen(quest, "/stages/0/transition"), "live stage visits do not infer which choice was taken");
        coverage.live(quest, source, new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of(), Set.of("/stages/0/next")));
        check(coverage.chosen(quest, "/stages/0/transition"), "runtime next paths map back to authored transition fields");
        coverage.clear(quest);
        coverage.live(quest, source, new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of(), Set.of("transition:start")));
        check(coverage.chosen(quest, "/stages/0/transition"), "automatic live transitions use their exact source stage");
        quest.edit("Different logic", root -> root.addProperty("entry_stage", "different"));
        coverage.live(quest, source, new com.jvn.villagerretaliation.authoring.preview.PreviewCoverage(Set.of("start"), Set.of()));
        check(!coverage.visited(quest, "/stages/0"), "live results from an unapplied older pack are ignored");
        var controls = new com.jvn.villagerretaliation.authoring.preview.QuestPlaytestSession.Controls();
        check(controls.drainCoverage().nodes().isEmpty() && controls.drainCoverage().choices().isEmpty(), "fresh live playtests have no fabricated coverage");
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
