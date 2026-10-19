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
        graphInsertionPreservesDestinationsAndLayoutHistory();
        graphGroupsAndBranchFocusHandleCyclesAndRenames();
        shortcutsRejectConflictsAndPersistOverrides();
        dialogueScenarioTestsIsolateConditionsAndReportFailures();
        scenarioTestsPreserveLegacySnapshotsAndRejectInvalidData();
        return 8;
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

    private static void graphInsertionPreservesDestinationsAndLayoutHistory() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-graph-insertion"));
        for (var kind : List.of(DefinitionKind.TREE, DefinitionKind.QUEST, DefinitionKind.CONVERSATION)) {
            var document = workspace.createDocument(kind, "test:insert_" + kind.name().toLowerCase(java.util.Locale.ROOT));
            var first = DialogueGraph.nodes(document).getFirst();
            DialogueGraph.addNode(document, "finish");
            String output;
            if (kind == DefinitionKind.TREE) {
                workspace.addDialogueResponse(document, first.id(), "continue", "Continue", "finish");
                output = DialogueGraph.edges(document).getFirst().pointer();
            } else {
                output = DialogueGraph.outputs(document).stream().filter(edge -> edge.from().equals(first.id())).findFirst().orElseThrow().pointer();
                DialogueGraph.connect(document, output, "finish");
                output = DialogueGraph.edges(document).stream().filter(edge -> edge.from().equals(first.id())).findFirst().orElseThrow().pointer();
            }
            var before = document.json(); var metadata = document.metadata();
            EditorGraphActions.create(document, output, "middle", true, 321, 654);
            check(DialogueGraph.edges(document).stream().anyMatch(edge -> edge.from().equals(first.id()) && edge.to().equals("middle"))
                    && DialogueGraph.edges(document).stream().anyMatch(edge -> edge.from().equals("middle") && edge.to().equals("finish")), "inserting a node preserves both halves of the connection for " + kind);
            check(document.metadata().getAsJsonObject("node_positions").getAsJsonObject("middle").get("x").getAsInt() == 321, "inserted nodes keep the canvas drop position");
            document.undo(); check(document.json().equals(before) && document.metadata().equals(metadata), "inserted content and positions undo together");
            document.redo();
            rejects(() -> EditorGraphActions.create(document, "/missing", "invalid", true, 0, 0));
        }
        var forced = workspace.createDocument(DefinitionKind.FORCED, "test:forced");
        forced.edit("Nested branch", root -> root.add("options", JsonParser.parseString("""
                [{"id":"continue","label":"Continue","end_conversation":false,"follow_up":{"lines":["Keep this"],"options":[]}}]
                """)));
        var edge = DialogueGraph.edges(forced).getFirst();
        var before = forced.json();
        String added = EditorGraphActions.create(forced, edge.pointer(), "middle", true, 100, 200);
        check(JsonPointers.get(forced.json(), added + "/options/0/follow_up/lines/0").getAsString().equals("Keep this"), "forced insertion preserves the nested destination subtree");
        forced.undo(); check(forced.json().equals(before), "forced insertion undoes without losing its old follow-up");
        check(EditorGraphHit.wire(50, 0, 0, 0, 100, 0, 1) && !EditorGraphHit.wire(50, 30, 0, 0, 100, 0, 1), "wire hit testing follows the rendered curve without selecting distant canvas space");
    }

    private static void graphGroupsAndBranchFocusHandleCyclesAndRenames() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-graph-groups"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:groups");
        DialogueGraph.addNode(document, "b"); DialogueGraph.addNode(document, "c"); DialogueGraph.addNode(document, "unrelated");
        workspace.addDialogueResponse(document, "start", "to_b", "Continue", "b");
        workspace.addDialogueResponse(document, "b", "to_c", "Continue", "c");
        workspace.addDialogueResponse(document, "c", "to_b", "Again", "b");
        check(EditorGraphOrganization.branch(document, Set.of("b"), false).equals(Set.of("b", "c")), "downstream focus handles cycles");
        check(EditorGraphOrganization.branch(document, Set.of("c"), true).equals(Set.of("start", "b", "c")), "upstream focus excludes unrelated nodes");
        EditorGraphOrganization.saveGroup(document, "", "Branch", "A loop", Set.of("b", "c"));
        EditorContentTools.renameNode(document, "b", "renamed");
        var members = document.metadata().getAsJsonObject("graph_groups").getAsJsonObject("Branch").getAsJsonArray("nodes");
        check(members.asList().stream().anyMatch(value -> value.getAsString().equals("renamed")), "comment group members follow node renames");
        document.undo();
        workspace.save();
        var restored = AuthoringWorkspace.open(workspace.directory()).documents().getFirst();
        check(restored.metadata().getAsJsonObject("graph_groups").getAsJsonObject("Branch").get("comment").getAsString().equals("A loop"), "comment groups survive workspace saves");
        check(!restored.json().has("graph_groups"), "comment groups stay outside exported game definitions");
        rejects(() -> EditorGraphOrganization.saveGroup(document, "", "Branch", "Duplicate", Set.of("c")));
    }

    private static void shortcutsRejectConflictsAndPersistOverrides() throws IOException {
        var commands = List.of(new EditorCommands.Command("Save", "Ctrl+S", true, () -> { }), new EditorCommands.Command("Find", "Ctrl+F", true, () -> { }));
        check(EditorShortcuts.parse("shift + ctrl + g").label().equals("Ctrl+Shift+G"), "shortcut parsing normalizes modifier order");
        rejects(() -> EditorShortcuts.validate(commands, java.util.Map.of("Find", "Ctrl+S")));
        rejects(() -> EditorShortcuts.parse("G"));
        rejects(() -> EditorShortcuts.parse("Ctrl+Ctrl+G"));
        rejects(() -> EditorShortcuts.parse("F9"));
        rejects(() -> EditorShortcuts.parse("F11"));
        rejects(() -> EditorShortcuts.parse("Ctrl+Space"));
        var path = Files.createTempDirectory("vr-shortcuts").resolve("library.json");
        var library = new EditorLibrary(path);
        library.shortcut("Find", "Ctrl+Shift+G");
        var restored = new EditorLibrary(path); restored.load();
        EditorShortcuts.validate(commands, restored.shortcuts());
        check(EditorShortcuts.binding(commands.getLast(), restored.shortcuts()).equals("Ctrl+Shift+G"), "shortcut overrides persist");
        restored.shortcut("Find", "");
        check(EditorShortcuts.binding(commands.getLast(), restored.shortcuts()).isEmpty(), "commands can be explicitly unbound");
        restored.resetShortcuts();
        check(EditorShortcuts.binding(commands.getLast(), restored.shortcuts()).equals("Ctrl+F"), "restoring defaults removes overrides");
    }

    private static void saveScenario(com.jvn.villagerretaliation.authoring.core.AuthoringDocument document, String name,
                                     com.jvn.villagerretaliation.authoring.preview.PreviewConditions conditions, JsonObject config) {
        document.editMetadata("Save test scenario", metadata -> {
            if (!metadata.has("preview_scenarios")) metadata.add("preview_scenarios", new JsonObject());
            if (!metadata.has("preview_scenario_tests")) metadata.add("preview_scenario_tests", new JsonObject());
            metadata.getAsJsonObject("preview_scenarios").add(name, conditions.snapshot());
            if (config != null) metadata.getAsJsonObject("preview_scenario_tests").add(name, config);
        });
    }

    private static void dialogueScenarioTestsIsolateConditionsAndReportFailures() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-scenario-dialogue"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:scenarios");
        DialogueGraph.addNode(document, "finish");
        workspace.addDialogueResponse(document, "start", "continue", "Continue", "finish");
        document.edit("Conditional branch", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses")
                .get(0).getAsJsonObject().add("conditions", JsonParser.parseString("[{\"type\":\"reputation\",\"min\":5}]")));
        var high = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions(); high.reputation(10, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL);
        var low = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions(); low.reputation(0, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL);
        saveScenario(document, "Allowed", high, EditorScenarioTests.config("start", "Node or stage", "finish", List.of(new EditorScenarioTests.Step("choice", "continue"))));
        saveScenario(document, "Blocked", low, EditorScenarioTests.config("start", "Choice unavailable", "continue", List.of()));
        saveScenario(document, "Wrong outcome", high, EditorScenarioTests.config("start", "Node or stage", "start", List.of(new EditorScenarioTests.Step("choice", "continue"))));
        saveScenario(document, "Missing response", high, EditorScenarioTests.config("start", "Node or stage", "finish", List.of(new EditorScenarioTests.Step("choice", "deleted"))));
        document.edit("Condition on destination", root -> root.getAsJsonObject("nodes").getAsJsonObject("finish")
                .add("conditions", JsonParser.parseString("[{\"type\":\"reputation\",\"min\":5}]")));
        var unknown = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        unknown.override("reputation", com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNKNOWN);
        saveScenario(document, "Unknown context", unknown, EditorScenarioTests.config("start", "Choice unavailable", "continue", List.of()));
        saveScenario(document, "Blocked node", low, EditorScenarioTests.config("finish", "Node or stage", "finish", List.of()));
        saveScenario(document, "Ended", high, EditorScenarioTests.config("finish", "Dialogue ended", "", List.of()));
        document.edit("Conditional entry", root -> root.getAsJsonArray("entries").get(0).getAsJsonObject()
                .add("conditions", JsonParser.parseString("[{\"type\":\"reputation\",\"min\":5}]")));
        saveScenario(document, "Blocked entry", low, EditorScenarioTests.config("", "Choice unavailable", "default", List.of()));
        var before = document.json(); var metadata = document.metadata();
        var coverage = new EditorCoverage();
        var results = EditorScenarioTests.run(document, (key, simulated) -> null, coverage.observe(document));
        check(results.get(0).passed() && results.get(1).passed() && !results.get(2).passed() && !results.get(3).passed(), "batch tests isolate reputation snapshots and distinguish mismatches from execution failures: " + results);
        check(!results.get(4).passed() && !results.get(5).passed() && results.get(6).passed() && results.get(7).passed(), "unknown conditions and blocked nodes cannot pass assertions, while terminal dialogue and disabled entry assertions work");
        check(results.get(3).node().equals("start") && results.get(3).message().contains("deleted"), "failed steps identify the affected node and missing choice");
        check(document.json().equals(before) && document.metadata().equals(metadata), "running scenarios leaves authored definitions and snapshots unchanged");
        check(coverage.visited(document, "/nodes/finish") && coverage.chosen(document, "/nodes/start/responses/0"), "batch runs contribute actual execution coverage");
        document.editMetadata("Remove extra tests", value -> { value.getAsJsonObject("preview_scenarios").remove("Wrong outcome"); value.getAsJsonObject("preview_scenarios").remove("Missing response"); value.getAsJsonObject("preview_scenarios").remove("Unknown context"); value.getAsJsonObject("preview_scenarios").remove("Blocked node"); });
        EditorContentTools.renameNode(document, "start", "renamed");
        var test = document.metadata().getAsJsonObject("preview_scenario_tests").getAsJsonObject("Allowed");
        check(test.get("start").getAsString().equals("renamed"), "scenario starting nodes follow node renames");
        workspace.save();
        var restored = AuthoringWorkspace.open(workspace.directory()).documents().getFirst();
        check(EditorScenarioTests.run(restored, (key, simulated) -> null, ignored -> { }).stream().allMatch(EditorScenarioTests.Result::passed), "scenario steps and expectations survive workspace saves");
    }

    static void questScenarioTestsSimulateObjectivesAndOutcomes() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-scenario-quest"));
        var document = workspace.createDocument(DefinitionKind.QUEST, "test:scenario_quest");
        EditorContentTools.appendObjective(document, "start", "item_check", "minecraft:stone", 3);
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        String objective = JsonPointers.get(document.json(), "/stages/0/objectives/0/id").getAsString();
        DialogueGraph.addNode(document, "finish");
        EditorDocumentOperations.nextStage(document, "start", "finish");
        saveScenario(document, "Complete", conditions, EditorScenarioTests.config("", "Quest outcome", "complete",
                List.of(new EditorScenarioTests.Step("objective", objective), new EditorScenarioTests.Step("advance", ""), new EditorScenarioTests.Step("advance", ""))));
        saveScenario(document, "Unmet objective", conditions, EditorScenarioTests.config("", "Quest outcome", "complete", List.of(new EditorScenarioTests.Step("advance", ""))));
        saveScenario(document, "Start later", conditions, EditorScenarioTests.config("finish", "Quest outcome", "complete", List.of(new EditorScenarioTests.Step("advance", ""))));
        var before = document.json();
        var results = EditorScenarioTests.run(document, (key, simulated) -> null, ignored -> { });
        check(results.get(0).passed() && !results.get(1).passed() && results.get(2).passed(), "quest scenario tests simulate objective completion, enforce blockers and support selected starting stages: " + results);
        check(results.get(1).node().equals("start") && document.json().equals(before), "failed quest tests keep their current stage without changing authored progress");
        var later = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(document.kind(), document.resourcePath(), document.json());
        saveScenario(later, "Start later", conditions, EditorScenarioTests.config("finish", "Quest outcome", "complete", List.of(new EditorScenarioTests.Step("advance", ""))));
        var visited = new java.util.HashSet<String>();
        EditorScenarioTests.run(later, (key, simulated) -> null, batch -> visited.addAll(batch.nodes()));
        check(visited.equals(Set.of("/stages/1")), "selected-stage scenario tests do not mark the skipped entry stage as covered");
    }

    private static void scenarioTestsPreserveLegacySnapshotsAndRejectInvalidData() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-scenario-compatibility"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:legacy_scenarios");
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        saveScenario(document, "Legacy", conditions, null);
        saveScenario(document, "Invalid", conditions, new JsonObject());
        saveScenario(document, "Valid", conditions, EditorScenarioTests.config("start", "Node or stage", "start", List.of()));
        var results = EditorScenarioTests.run(document, (key, simulated) -> null, ignored -> { });
        check(results.get(0).skipped() && !results.get(1).passed() && !results.get(1).skipped() && results.get(2).passed(), "legacy snapshots remain available while invalid expectations fail without stopping later tests");
        rejects(() -> EditorScenarioTests.config("", "Node or stage", "", List.of()));
        rejects(() -> EditorScenarioTests.config("", "Dialogue ended", "", java.util.Collections.nCopies(257, new EditorScenarioTests.Step("advance", ""))));
        rejects(() -> EditorScenarioTests.config("", "Dialogue ended", "", List.of(new EditorScenarioTests.Step("unknown", ""))));
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
