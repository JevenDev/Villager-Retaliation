package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;

public final class EditorClientChecks {
    private EditorClientChecks() { }

    public static int run() throws java.io.IOException {
        schemaFormsResolveNestedVariantsAndRequiredFields();
        stageReferencesAndTriggeredDeletionStaySafe();
        cameraSequenceEditsKeepThumbnailsAndUndoTogether();
        graphClipboardPreservesBranchesAndUndo();
        graphClipboardTransfersLocalizedDependencies();
        connectedNodeCreationIsAtomic();
        managedExportsPreserveExternalFilesAndBackups();
        previewScenariosRestoreIndependentChecks();
        recipesAndObjectivesValidate();
        nodeRenamingAndDeletionPreserveReferences();
        namesAndSearchFollowCreatorWording();
        recoveryPreservesSavedDraftsAndDeletedBundles();
        connectorTargetsStaySeparateAtMinimumZoom();
        pendingFieldsSurviveRecovery();
        propertyEditsApplyTogether();
        invalidPropertiesDoNotPartiallyApply();
        stalePropertiesAreNotOverwritten();
        questConnectionsUseStageTransitions();
        cameraOwnersFollowRuntimeFormats();
        cameraAppendIsValidatedBeforeCommit();
        editorPreferencesSurviveRestart();
        automaticLayoutKeepsUsablePaneWidths();
        playtestLayoutPreservesGameCoordinates();
        plainTextAuthoringPreservesLocalizationAndUndo();
        fieldValidationDoesNotConsumeEdits();
        terminalResponsePortsReconnectAndUndo();
        forcedPortsPreserveNestedOwnership();
        graphCardsExposeChecksAndEditableBlocks();
        questPortsReplaceTerminalTransitions();
        graphArrangementHandlesCyclesAndLargeCards();
        behaviorBlocksRejectUnsupportedOwners();
        textToolsPreserveUnicodeSelectionAndToggleStyles();
        timeVariablesFollowWorldClockAndPreviewConditions();
        graphSummariesKeepFormattingOutOfTheFlow();
        graphStylesKeepVariantsAndChoicesDistinct();
        diagnosticsLocateMissingFieldsAndTheirOwningNodes();
        malformedFieldErrorsKeepLocationsAndPendingContent();
        endingNodesDoesNotSilentlyDeleteAuthoredChoices();
        return 38;
    }

    private static void schemaFormsResolveNestedVariantsAndRequiredFields() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-schema-forms"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:guided");
        var schema = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
        var forms = new EditorSchema(schema);
        JsonObject objective = new JsonObject(); objective.addProperty("type", "block_break");
        JsonObject fields = forms.effective(schema.getAsJsonObject("$defs").getAsJsonObject("objective"), objective);
        check(fields.getAsJsonObject("properties").has("block"), "objective variants expose subtype fields");
        check(forms.seed(fields).getAsJsonObject().has("id"), "required objective values are seeded");
        JsonObject transition = forms.at(quest.json(), "/stages/0/transition");
        check(!forms.choices(transition).isEmpty(), "nested transition forms resolve references");
        check(forms.choices(com.jvn.villagerretaliation.dialogue.DialogueCamera.schema()).size() == 3, "all structured camera alternatives remain available");
        check(forms.choices(new JsonObject()).get("Object").isJsonObject(), "empty extension lists can create structured entries");
        JsonObject node = forms.at(quest.json(), "/stages/0");
        check(node.getAsJsonObject("properties").has("on_enter"), "optional stage actions are available");
        JsonObject action = schema.getAsJsonObject("$defs").getAsJsonObject("action");
        check(forms.choices(action).containsKey("experience"), "runtime actions appear as typed choices");
        JsonObject experience = new JsonObject(); experience.addProperty("type", "experience");
        check(forms.effective(action, experience).getAsJsonObject("properties").has("amount"), "action forms use runtime field declarations");
        JsonObject original = quest.json();
        var template = new JsonObject(); template.addProperty("type", "trade");
        String added = EditorContentTools.appendObjective(quest, "start", template);
        check(added.endsWith("/objectives/0") && quest.json().getAsJsonArray("stages").get(0).getAsJsonObject().has("complete_when"), "advanced objectives connect completion automatically");
        quest.undo(); check(quest.json().equals(original), "guided objective creation undoes atomically");
    }

    private static void stageReferencesAndTriggeredDeletionStaySafe() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-stage-refs"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:stage_refs");
        DialogueGraph.addNode(quest, "other");
        quest.edit("Add stage-filtered event", root -> {
            JsonObject event = new JsonObject(); event.addProperty("id", "event"); event.addProperty("event", "player_tick");
            event.addProperty("stage", "other"); var values = new com.google.gson.JsonArray(); values.add(event); root.add("events", values);
        });
        EditorContentTools.renameNode(quest, "other", "renamed");
        check(quest.json().getAsJsonArray("events").get(0).getAsJsonObject().get("stage").getAsString().equals("renamed"), "stage renames update event filters");
        quest.undo();
        check(quest.json().getAsJsonArray("events").get(0).getAsJsonObject().get("stage").getAsString().equals("other"), "event filters undo with their stage rename");
        var forced = workspace.createDocument(DefinitionKind.FORCED, "test:triggered");
        forced.edit("Add nested branch", root -> {
            JsonObject response = root.getAsJsonArray("options").get(0).getAsJsonObject();
            response.addProperty("end_conversation", false);
            var follow = new JsonObject(); var lines = new com.google.gson.JsonArray(); lines.add("Keep the parent safe"); follow.add("lines", lines);
            response.add("follow_up", follow);
        });
        var original = forced.json();
        EditorContentTools.deleteNode(forced, "/options/0/follow_up", "");
        check(forced.json().getAsJsonArray("options").isEmpty(), "deleting a nested follow-up removes its owning response");
        forced.undo(); check(forced.json().equals(original), "triggered branch deletion is fully undoable");
    }

    private static void cameraSequenceEditsKeepThumbnailsAndUndoTogether() {
        var document = tree();
        JsonObject shot = new JsonObject(); shot.addProperty("preset", "over_shoulder_left");
        EditorDocumentOperations.appendShot(document, "/nodes/start", shot, "first");
        EditorDocumentOperations.appendShot(document, "/nodes/start", shot, "second");
        var original = document.json(); var metadata = document.metadata();
        EditorCameraShots.edit(document, "/nodes/start", 1, "up", ignored -> { });
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/start", 2).get(0).getAsString().equals("second"), "shot movement includes its thumbnail");
        document.undo();
        check(document.json().equals(original) && document.metadata().equals(metadata), "shot order and thumbnails undo together");
        EditorCameraShots.edit(document, "/nodes/start", 0, "update", value -> value.addProperty("hold_seconds", 3));
        var camera = com.jvn.villagerretaliation.dialogue.DialogueCamera.read(document.json().getAsJsonObject("nodes").getAsJsonObject("start"));
        check(camera.shots().getFirst().holdSeconds() == 3, "timing edits use runtime camera fields");
        EditorCameraShots.edit(document, "/nodes/start", 1, "delete", ignored -> { });
        EditorCameraShots.edit(document, "/nodes/start", 0, "delete", ignored -> { });
        check(!document.json().getAsJsonObject("nodes").getAsJsonObject("start").has("camera"), "removing the last shot removes its camera cleanly");
        document.undo();
        check(document.json().getAsJsonObject("nodes").getAsJsonObject("start").has("camera"), "deleted shots remain undoable");
    }

    private static void graphClipboardPreservesBranchesAndUndo() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-clipboard"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:clipboard");
        EditorContentTools.recipe(workspace, document, "Branching dialogue");
        var clipboard = new EditorGraphClipboard();
        var original = document.json();
        clipboard.copy(document, java.util.Set.of("start", "follow_up"));
        var pasted = clipboard.paste(document);
        check(pasted.size() == 2 && DialogueGraph.edges(document).stream().anyMatch(edge -> edge.from().equals("start_copy") && edge.to().equals("follow_up_copy")), "pasting a selection remaps its internal branches");
        document.undo();
        check(document.json().equals(original), "pasting multiple nodes undoes as one change");
    }

    private static void graphClipboardTransfersLocalizedDependencies() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-cross-clipboard"));
        var source = workspace.createDocument(DefinitionKind.CONVERSATION, "test:source");
        var target = workspace.createDocument(DefinitionKind.CONVERSATION, "test:target");
        DialogueGraph.addNode(source, "finish");
        workspace.addDialogueResponse(source, "start", "continue", "Choose this branch", "finish");
        var locale = workspace.textLocation(source, "test:source.greeting", "en_us").document();
        var french = new com.google.gson.JsonObject();
        french.addProperty("test:source.greeting", "Bonjour");
        workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE, locale.resourcePath().replace("en_us.json", "fr_fr.json"), french));
        source.edit("Add runtime variable gate", root -> {
            var condition = new JsonObject(); condition.addProperty("type", "quest_fact"); condition.addProperty("scope", "player"); condition.addProperty("key", "test:source.greeting");
            condition.addProperty("value", "ready");
            var conditions = new com.google.gson.JsonArray(); conditions.add(condition);
            root.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("responses").get(0).getAsJsonObject().add("conditions", conditions);
        });
        var clipboard = new EditorGraphClipboard();
        clipboard.copy(workspace, source, java.util.Set.of("start"));
        JsonObject before = target.json();
        var pasted = clipboard.paste(workspace, target);
        check(pasted.size() == 2, "cross-file paste includes connected nodes");
        check(DialogueGraph.edges(target).stream().anyMatch(edge -> edge.from().equals("start_copy") && edge.to().equals("finish_copy")), "cross-file branches point at copied destinations");
        var node = target.json().getAsJsonArray("nodes").asList().stream().map(value -> value.getAsJsonObject())
                .filter(value -> DialogueGraph.string(value, "id").equals("start_copy")).findFirst().orElseThrow();
        check(node.getAsJsonArray("responses").get(0).getAsJsonObject().getAsJsonArray("conditions").get(0)
                .getAsJsonObject().get("key").getAsString().equals("test:source.greeting"), "localization remapping preserves runtime variable keys");
        var text = node.getAsJsonArray("exchanges").get(0).getAsJsonObject().get("text");
        check(workspace.resolveText(target, text, "fr_fr").equals("Bonjour"), "cross-file paste preserves translated wording");
        check(workspace.resolveText(target, text, "en_us").equals("Hello."), "cross-file paste preserves English wording");
        check(!text.getAsJsonObject().get("key").getAsString().equals("test:source.greeting"), "pasted localization references are independent");
        check(workspace.validate().stream().noneMatch(issue -> issue.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR), "pasted localized graph passes bundle validation: " + workspace.validate());
        var pastedJson = target.json();
        target.undo(); check(target.json().equals(before), "cross-file paste undoes graph and wording together");
        target.redo(); check(target.json().equals(pastedJson) && workspace.resolveText(target, text, "fr_fr").equals("Bonjour"), "cross-file paste redoes graph and wording together");
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:incompatible");
        try { clipboard.paste(workspace, tree); throw new AssertionError("incompatible graph paste accepted"); }
        catch (IllegalArgumentException expected) { check(DialogueGraph.nodes(tree).size() == 1, "incompatible paste is atomic"); }
    }

    private static void connectedNodeCreationIsAtomic() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-connected"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:connected");
        workspace.addDialogueResponse(document, "start", "reply", "Continue", "");
        var original = document.json();
        EditorContentTools.createConnected(document, "/nodes/start/responses/0/next", "new_destination");
        check(DialogueGraph.edges(document).getFirst().to().equals("new_destination"), "new nodes connect directly to their selected output");
        document.undo();
        check(document.json().equals(original), "node creation and connection undo together");
    }

    private static void managedExportsPreserveExternalFilesAndBackups() throws java.io.IOException {
        var root = java.nio.file.Files.createTempDirectory("vr-managed-export");
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(root.resolve("drafts"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:managed");
        var output = root.resolve("pack");
        com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, output);
        java.nio.file.Files.writeString(output.resolve("custom.txt"), "Keep this file");
        document.edit("Revise", value -> value.addProperty("priority", 3));
        var backup = com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, output);
        check(backup != null && java.nio.file.Files.exists(backup.resolve(document.resourcePath())), "updating an export retains its previous version");
        check(java.nio.file.Files.readString(output.resolve("custom.txt")).equals("Keep this file"), "managed exports retain unowned files");
        java.nio.file.Files.writeString(output.resolve(document.resourcePath()), "external edit");
        try {
            com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, output);
            throw new AssertionError("external edits must block re-export");
        } catch (java.io.IOException expected) {
            check(expected.getMessage().contains("external edit"), "conflicts identify the edited file");
        }
        check(java.nio.file.Files.readString(output.resolve(document.resourcePath())).equals("external edit"), "a rejected export never replaces external edits");
    }

    public static void questSimulationUsesRuntimeCompletionRules() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-quest-preview"));
        var document = workspace.createDocument(DefinitionKind.QUEST, "test:simulation");
        EditorContentTools.recipe(workspace, document, "Fetch quest");
        DialogueGraph.addNode(document, "finish");
        EditorDocumentOperations.nextStage(document, "start", "finish");
        var original = document.json();
        var session = new com.jvn.villagerretaliation.authoring.preview.QuestPreviewSession(document,
                new com.jvn.villagerretaliation.authoring.preview.PreviewConditions());
        check(!session.ready() && !session.blockers().isEmpty(), "quest testing explains unmet runtime predicates");
        session.completed(session.objectives().getFirst(), true);
        check(session.ready(), "satisfying an objective meets runtime composition rules");
        session.advance();
        check(session.currentStage().equals("finish"), "quest testing follows authored stage transitions");
        session.advance();
        check(session.outcome().equals("complete"), "quest testing follows terminal transitions");
        session.reset();
        check(session.currentStage().equals("start") && !session.ready() && session.outcome().isEmpty(), "replay resets simulated progress");
        check(document.json().equals(original), "quest testing never changes the authored source");
    }

    private static void previewScenariosRestoreIndependentChecks() {
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        conditions.reputation(12, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL);
        conditions.dayTime(6000L);
        conditions.checkSuccess("first", true);
        conditions.checkSuccess("second", false);
        conditions.quest("test:quest", "completed");
        var saved = conditions.snapshot();
        conditions.reputation(-20, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL);
        conditions.checkSuccess("second", true);
        conditions.restore(saved);
        check(conditions.checkSuccess("first") && !conditions.checkSuccess("second"), "checks can succeed and fail independently within a scenario");
        check(conditions.snapshot().equals(saved), "scenario loading restores its full simulated context");
    }

    private static void recipesAndObjectivesValidate() throws java.io.IOException {
        for (String recipe : java.util.List.of("Branching dialogue", "Fetch quest", "Triggered encounter", "Reputation choice")) {
            var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-recipe"));
            var kind = recipe.equals("Fetch quest") ? DefinitionKind.QUEST : recipe.equals("Triggered encounter") ? DefinitionKind.FORCED : DefinitionKind.TREE;
            var document = workspace.createDocument(kind, "test:recipe");
            EditorContentTools.recipe(workspace, document, recipe);
            var errors = workspace.validate().stream().filter(value -> value.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR).toList();
            check(errors.isEmpty(), recipe + " passes runtime validation: " + errors);
            if (kind == DefinitionKind.QUEST) {
                var stage = document.json().getAsJsonArray("stages").get(0).getAsJsonObject();
                check(stage.getAsJsonArray("complete_when").get(0).getAsString().equals("objective_1"), "guided objectives connect their completion requirements");
            }
        }
    }

    private static void nodeRenamingAndDeletionPreserveReferences() throws java.io.IOException {
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(java.nio.file.Files.createTempDirectory("vr-references"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:references");
        EditorContentTools.recipe(workspace, document, "Branching dialogue");
        var original = document.json();
        EditorContentTools.renameNode(document, "follow_up", "renamed");
        check(DialogueGraph.edges(document).getFirst().to().equals("renamed"), "renaming updates incoming references");
        document.undo();
        check(document.json().equals(original), "renaming and its references undo together");
        DialogueGraph.addNode(document, "replacement");
        rejects(() -> EditorContentTools.deleteNode(document, "follow_up", ""));
        EditorContentTools.deleteNode(document, "follow_up", "replacement");
        check(DialogueGraph.edges(document).getFirst().to().equals("replacement"), "deletion redirects incoming references");
        document.undo();
        check(DialogueGraph.edges(document).getFirst().to().equals("follow_up"), "deletion and redirection undo together");
    }

    private static void namesAndSearchFollowCreatorWording() {
        check(EditorContentTools.resourceId("Village Welcome").equals("example:village_welcome"), "readable names generate resource IDs");
        check(EditorContentTools.resourceId("test:welcome").equals("test:welcome"), "explicit namespaces are preserved");
        check(EditorContentTools.matches(tree(), "hello"), "search finds authored dialogue wording");
        check(!EditorContentTools.matches(tree(), "missing phrase"), "search rejects unrelated content");
    }

    private static void recoveryPreservesSavedDraftsAndDeletedBundles() throws java.io.IOException {
        var directory = java.nio.file.Files.createTempDirectory("vr-recovery-check");
        var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(directory);
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:recovery");
        workspace.save();
        check(!workspace.dirty(), "saved workspace is clean");
        quest.edit("Change draft", root -> root.addProperty("extension", "recover me"));
        JsonObject buffers = new JsonObject();
        buffers.addProperty("raw", "{unfinished");
        workspace.saveRecovery(buffers);
        check(workspace.dirty() && workspace.recoveryAvailable(), "recovery never marks drafts saved");
        var saved = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.open(directory);
        check(!saved.documents().getFirst().json().has("extension"), "recovery keeps the explicit save intact");
        var recovered = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.recover(directory);
        check(recovered.dirty() && recovered.recoveryBuffers().equals(buffers), "recovery restores unsaved buffers and dirty state");
        check(recovered.documents().getFirst().json().get("extension").getAsString().equals("recover me"), "recovery retains changed content");
        recovered.remove(recovered.documents().getFirst());
        check(recovered.documents().isEmpty(), "deleting a quest moves its locale too");
        recovered.save();
        var reopened = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.open(directory);
        check(!reopened.recoveryAvailable() && reopened.deletedNames().size() == 1, "explicit save clears recovery and preserves deleted files");
        reopened.restoreDeleted(0);
        check(reopened.documents().size() == 2 && reopened.dirty(), "restoring a deleted quest includes its locale and marks the workspace dirty");
        check(reopened.documents().getFirst().json().has("extension"), "restored quest retains unsaved authored content");
    }

    private static void connectorTargetsStaySeparateAtMinimumZoom() {
        for (float zoom : new float[] {.35f, .5f, .7f, 1, 2}) {
            float first = 100;
            float second = first + EditorGraph.ROW * zoom;
            for (float y = first - 20; y <= second + 20; y += .25f) {
                check(!(EditorGraphHit.port(10, y, 10, first, EditorGraph.ROW * zoom)
                        && EditorGraphHit.port(10, y, 10, second, EditorGraph.ROW * zoom)), "adjacent ports never share a hit target");
            }
            check(EditorGraphHit.port(10, first, 10, first, EditorGraph.ROW * zoom), "port centres remain clickable at every zoom");
        }
    }

    private static void pendingFieldsSurviveRecovery() {
        var edits = new EditorFieldEdits();
        var document = tree();
        edits.update("/priority", document.json().get("priority"), "unfinished number");
        var restored = new EditorFieldEdits();
        restored.restore(edits.snapshot());
        check(restored.snapshot().equals(edits.snapshot()) && restored.pending(), "recovery preserves invalid pending input without applying it");
        rejects(() -> restored.apply(document));
        check(document.json().get("priority").getAsInt() == 1, "recovering invalid input does not change the document");
    }

    private static void endingNodesDoesNotSilentlyDeleteAuthoredChoices() {
        var forced = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/choices.json",
                json("{\"lines\":[\"Hello\"],\"options\":[{\"id\":\"story\",\"label\":\"Story\",\"follow_up\":{\"lines\":[\"Keep this narrative\"]}}]}"));
        var conversation = new AuthoringDocument(DefinitionKind.CONVERSATION, "data/test/quests/_shared/conversations/choices.json",
                json("{\"nodes\":[{\"id\":\"start\",\"responses\":[{\"id\":\"reply\",\"label\":{\"key\":\"reply\"},\"next\":\"other\",\"extension\":null}]}],\"outcomes\":[{\"id\":\"done\"}]}"));
        for (var document : java.util.List.of(forced, conversation)) {
            var before = document.json();
            String node = document == forced ? "" : "start";
            try {
                DialogueGraph.setTerminal(document, node, true);
                throw new AssertionError("ending must not discard authored choices");
            } catch (IllegalArgumentException expected) {
                check(expected.getMessage().contains(document == forced ? "/options" : "/nodes/0/responses"), "ending error locates the player choices");
            }
            check(before.equals(document.json()) && !document.canUndo(), "rejected ending preserves choices, nested dialogue, extensions and history");
            document.edit("Explicitly remove choices", root -> {
                if (document == forced) root.remove("options");
                else root.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("responses");
            });
            DialogueGraph.setTerminal(document, node, true);
            check(DialogueGraph.nodes(document).getFirst().terminal(), "authors can still end a node after explicitly removing its choices");
            while (document.canUndo()) document.undo();
            check(document.json().equals(before), "undo restores the explicitly removed authored choices");
        }
    }

    private static void diagnosticsLocateMissingFieldsAndTheirOwningNodes() {
        var document = tree();
        var missing = EditorDiagnosticLocation.resolve(document, "/nodes/start/responses/0/next");
        check(missing.pointer().equals("/nodes/start") && missing.nodeId().equals("start"),
                "missing fields open the existing owning node");
        var existing = EditorDiagnosticLocation.resolve(document, "/nodes/start/lines/0");
        check(existing.pointer().equals("/nodes/start/lines/0") && existing.nodeId().equals("start"),
                "existing fields retain their exact location and select their graph node");
        document.edit("Escaped node", root -> root.getAsJsonObject("nodes").add("a/b~c", json("{\"id\":\"public_id\",\"lines\":[\"Hi\"]}")));
        var escaped = EditorDiagnosticLocation.resolve(document, "/nodes/a~1b~0c/lines/3");
        check(escaped.pointer().equals("/nodes/a~1b~0c/lines") && escaped.nodeId().equals("public_id"),
                "escaped keys and missing array elements resolve without confusing storage keys and IDs");
        check(EditorDiagnosticLocation.resolve(document, "/entries/0/start").pointer().isEmpty(), "missing root fields fall back to file settings");
        var malformed = new AuthoringDocument(DefinitionKind.TREE, document.resourcePath(), json("{\"nodes\":null}"));
        check(EditorDiagnosticLocation.resolve(malformed, "/nodes/start").pointer().equals("/nodes"),
                "malformed graphs still expose their invalid fields for repair");
        var forced = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/test.json",
                json("{\"entries\":[{\"lines\":[\"Hi\"],\"options\":[{\"follow_up\":{\"lines\":[\"Again\"]}}]}]}"));
        check(EditorDiagnosticLocation.resolve(forced, "/entries/0/options/0/follow_up/trigger").nodeId()
                .equals("/entries/0/options/0/follow_up"), "nested forced diagnostics select the nearest follow-up");
    }

    private static void malformedFieldErrorsKeepLocationsAndPendingContent() {
        var document = tree();
        var original = document.json();
        var edits = new EditorFieldEdits();
        edits.update("/display/title", original.getAsJsonObject("display").get("title"), "Retain my text");
        edits.update("/priority", original.get("priority"), "[broken");
        try {
            edits.apply(document);
            throw new AssertionError("malformed field must be rejected");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("/priority"), "parse errors name the field to repair");
        }
        check(edits.pending() && document.json().equals(original), "failed apply keeps all pending edits and leaves the model untouched");
        edits.update("/priority", original.get("priority"), "2");
        edits.apply(document);
        check(document.json().getAsJsonObject("display").get("title").getAsString().equals("Retain my text"),
                "repairing the invalid field retains the other authored text");
    }

    private static void graphSummariesKeepFormattingOutOfTheFlow() {
        var document = tree();
        document.edit("Format greeting", root -> root.getAsJsonObject("nodes").getAsJsonObject("start")
                .getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("<b>Hello</b>, {player}!")));
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString());
        check(cards.getFirst().blocks().stream().anyMatch(block -> block.title().equals("Say") && block.text().equals("Hello, {player}!")),
                "graph wording hides formatting tags and preserves useful variables");
        check(document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").get(0)
                .getAsString().equals("<b>Hello</b>, {player}!"), "graph summaries never alter authored markup");
    }

    private static void graphStylesKeepVariantsAndChoicesDistinct() {
        var document = tree();
        DialogueGraph.addResponse(document, "start", "continue", "<b>Literal choice</b>", "");
        document.edit("Style lines", root -> {
            var start = root.getAsJsonObject("nodes").getAsJsonObject("start");
            start.add("lines", JsonParser.parseString("[\"<b>Hello</b>, {player}!\",\"<i>Goodbye</i>\"]"));
        });
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString());
        var spoken = cards.getFirst().blocks().stream().filter(block -> block.title().equals("Say one of")).findFirst().orElseThrow();
        check(spoken.styledText().equals("<b>Hello</b>, {player}! (+1)") && spoken.text().equals("Hello, {player}! (+1)"),
                "styled graph previews preserve tags, variables and variant counts separately from plain summaries");
        var choice = cards.getFirst().blocks().stream().filter(block -> block.output() != null).findFirst().orElseThrow();
        check(choice.styledText() == null && choice.text().equals("<b>Literal choice</b>"),
                "choice labels keep the literal text used by runtime choices");
    }

    private static void textToolsPreserveUnicodeSelectionAndToggleStyles() {
        String text = "Hello, Zo\u00eb \ud83c\udf3b!";
        int from = EditorTextEdit.bytes("Hello, "), to = EditorTextEdit.bytes("Hello, Zo\u00eb \ud83c\udf3b");
        var italic = EditorTextEdit.format(text, from, to, "i");
        check(italic.text().equals("Hello, <i>Zo\u00eb \ud83c\udf3b</i>!"), "formatting respects UTF-8 selection boundaries");
        var plain = EditorTextEdit.format(italic.text(), italic.start(), italic.end(), "i");
        check(plain.text().equals(text), "repeating a format removes the surrounding tag");
        var variable = EditorTextEdit.insert(plain.text(), plain.start(), plain.end(), "{player}");
        check(variable.text().equals("Hello, {player}!"), "variable insertion replaces the selection");
        check(variable.start() == variable.end(), "inserted variables leave a collapsed cursor");
        var empty = EditorTextEdit.format("", 0, 0, "color:gold");
        check(empty.text().equals("<color:gold>text</color>") && empty.end() - empty.start() == 4,
                "formatting an empty field selects an editable text span");
        check(EditorTextEdit.format(empty.text(), 0, EditorTextEdit.bytes(empty.text()), "color:gold").text().equals("text"),
                "formatting a selected tagged span toggles off");
    }

    private static void timeVariablesFollowWorldClockAndPreviewConditions() {
        var morning = com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(0);
        check(morning.get("time").equals("06:00") && morning.get("day").equals("1"), "day one begins at 06:00");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(6000).get("time").equals("12:00"), "noon is 12:00");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(12000).get("time_of_day").equals("evening"), "sunset uses runtime evening boundary");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(14000).get("time_of_day").equals("night"), "night matches condition boundary");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(18000).get("time").equals("00:00"), "midnight wraps clock");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(24000).get("day").equals("2"), "world day increases at the tick rollover");
        check(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.time(-1).get("time").equals("05:59"), "negative ticks use floor modulo");
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        conditions.worldTime(0);
        conditions.dayTime(18000L);
        check(conditions.resolveDayTime(6000) == 18000, "preview time overrides live world time");
        var id = net.minecraft.resources.ResourceLocation.parse("test:clock");
        var night = json("{\"conditions\":[{\"type\":\"time\",\"times\":[\"night\"]}]}");
        check(conditions.evaluate(id, night) == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.MATCHED,
                "time conditions use the preview override");
        conditions.dayTime((Long) null);
        check(conditions.resolveDayTime(6000) == 6000, "clearing the override returns to world time");
        check(conditions.evaluate(id, night) == com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome.UNMET,
                "world-time conditions use the captured world context");
    }

    private static void propertyEditsApplyTogether() {
        var document = tree();
        var edits = new EditorFieldEdits();
        edits.update("/display/title", document.json().getAsJsonObject("display").get("title"), "Changed");
        edits.update("/priority", document.json().get("priority"), "7");
        check(edits.pending(), "pending text must be tracked before save or selection");
        check(edits.apply(document), "all pending fields should apply together");
        check(document.json().getAsJsonObject("display").get("title").getAsString().equals("Changed"), "title edit retained");
        check(document.json().get("priority").getAsInt() == 7, "numeric edit retained");
        check(!edits.pending(), "committed buffers are cleared");
        document.undo();
        check(document.json().get("priority").getAsInt() == 1
                && document.json().getAsJsonObject("display").get("title").getAsString().equals("Original"),
                "one undo restores the atomic field batch");
    }

    private static void invalidPropertiesDoNotPartiallyApply() {
        var document = tree();
        JsonObject before = document.json();
        var edits = new EditorFieldEdits();
        edits.update("/display/title", before.getAsJsonObject("display").get("title"), "Must remain pending");
        edits.update("/priority", before.get("priority"), "not a number");
        rejects(() -> edits.apply(document));
        check(document.json().equals(before), "an invalid numeric field must not commit earlier valid fields");
        check(edits.pending(), "invalid pending buffers remain available to repair");
        edits.update("/priority", before.get("priority"), "2");
        edits.apply(document);
        check(document.json().get("priority").getAsInt() == 2, "repaired buffer can be applied");
    }

    private static void stalePropertiesAreNotOverwritten() {
        var document = tree();
        var edits = new EditorFieldEdits();
        edits.update("/priority", document.json().get("priority"), "9");
        document.edit("External edit", root -> root.addProperty("priority", 4));
        rejects(() -> edits.apply(document));
        check(document.json().get("priority").getAsInt() == 4, "stale field buffers must not overwrite newer data");
    }

    private static void questConnectionsUseStageTransitions() {
        var document = new AuthoringDocument(DefinitionKind.QUEST, "data/test/quests/line/quest/quest.json",
                json("{\"id\":\"test:quest\",\"stages\":[{\"id\":\"first\",\"transition\":{\"complete\":true},\"objectives\":[]},{\"id\":\"second\",\"transition\":{\"complete\":true}}]}"));
        EditorDocumentOperations.nextStage(document, "first", "second");
        JsonObject first = document.json().getAsJsonArray("stages").get(0).getAsJsonObject();
        check(first.getAsJsonObject("transition").get("stage").getAsString().equals("second"), "quest transition must use a supported stage reference");
        check(!first.has("responses") && first.has("objectives"), "stage connection must not invent dialogue responses or remove objectives");
        JsonObject before = document.json();
        rejects(() -> EditorDocumentOperations.nextStage(document, "first", "missing"));
        check(document.json().equals(before), "missing destinations cannot mutate the quest");
    }

    private static void cameraOwnersFollowRuntimeFormats() {
        var tree = tree();
        EditorDocumentOperations.cameraOwner(tree, "/nodes/start");
        check("/nodes/start".equals(EditorDocumentOperations.cameraSelection(tree, "/nodes/start/lines/0")),
                "selecting spoken text attaches cameras to its owning node");
        check(EditorDocumentOperations.cameraSelection(tree, "/metadata") == null, "unrelated metadata has no camera target");
        rejects(() -> EditorDocumentOperations.cameraOwner(tree, "/metadata"));
        rejects(() -> EditorDocumentOperations.cameraOwner(tree, "/nodes/start/speaker"));
        var conversation = new AuthoringDocument(DefinitionKind.CONVERSATION,
                "data/test/quests/_shared/conversations/talk.json", json("{\"nodes\":[{\"id\":\"start\","
                + "\"exchanges\":[{\"id\":\"greet\",\"speaker\":{\"type\":\"player\"},\"text\":{\"key\":\"greet\"}}],"
                + "\"responses\":[{\"id\":\"continue\",\"label\":{\"key\":\"continue\"},\"next\":\"start\"}]}]}"));
        EditorDocumentOperations.cameraOwner(conversation, "/nodes/0");
        EditorDocumentOperations.cameraOwner(conversation, "/nodes/0/exchanges/0");
        check("/nodes/0/exchanges/0".equals(EditorDocumentOperations.cameraSelection(conversation, "/nodes/0/exchanges/0/text/key")),
                "localized text attaches cameras to the nearest exchange");
        var resource = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/pools/greet.json",
                json("{\"entries\":[{\"text\":\"Hello\",\"metadata\":{\"text\":\"Hidden\"}}]}"));
        check("/entries/0".equals(EditorDocumentOperations.cameraSelection(resource, "/entries/0/text")),
                "supporting dialogue resources retain camera ownership");
        rejects(() -> EditorDocumentOperations.cameraOwner(resource, "/entries/0/metadata"));
        rejects(() -> EditorDocumentOperations.cameraOwner(conversation, "/nodes/0/exchanges/0/text"));
        rejects(() -> EditorDocumentOperations.cameraOwner(conversation, "/nodes/0/responses/0"));
    }

    private static void cameraAppendIsValidatedBeforeCommit() {
        var document = tree();
        JsonObject shot = json("{\"preset\":\"first_person\"}");
        for (int index = 0; index < 32; index++) EditorDocumentOperations.appendShot(document, "/nodes/start", shot);
        JsonObject before = document.json();
        rejects(() -> EditorDocumentOperations.appendShot(document, "/nodes/start", shot));
        check(document.json().equals(before), "the 33rd shot must not leave an invalid sequence in the document");
        rejects(() -> EditorDocumentOperations.appendShot(document, "/metadata", shot));
        check(document.json().equals(before), "unsupported camera owners must remain untouched");
        var invalid = tree();
        rejects(() -> EditorDocumentOperations.appendShot(invalid, "/nodes/start", json("{\"preset\":\"first_person\",\"fov\":999}")));
        check(!invalid.json().getAsJsonObject("nodes").getAsJsonObject("start").has("camera"), "invalid shot must not create a partial camera");
    }

    private static void editorPreferencesSurviveRestart() throws java.io.IOException {
        var directory = java.nio.file.Files.createTempDirectory("vr-editor-preferences-");
        var path = directory.resolve("settings.json");
        try {
            check(!EditorPreferences.load(path).guideDismissed(), "first use shows the guide");
            var skipped = new EditorPreferences(true, EditorPreferences.CURRENT_LAYOUT, "C:/drafts/test");
            skipped.save(path);
            check(EditorPreferences.load(path).equals(skipped), "skip and layout state survive restart");
            java.nio.file.Files.writeString(path, "not json");
            try {
                EditorPreferences.load(path);
                throw new AssertionError("malformed preferences must not be silently accepted");
            } catch (java.io.IOException expected) { }
        } finally {
            java.nio.file.Files.deleteIfExists(path);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }

    private static void playtestLayoutPreservesGameCoordinates() throws java.io.IOException {
        var layout = PlaytestLayout.calculate(1920, 1080, 1280, 720, 2F / 3, 3, false);
        check(layout.width() == 1280 && layout.screenWidth() == 853, "game dimensions preserve framebuffer scaling");
        check(layout.scale() == 3 && layout.guiWidth() == 427, "game GUI dimensions use the reduced viewport");
        var small = PlaytestLayout.calculate(960, 600, 960, 600, .625F, 3, false);
        check(small.scale() == 1 && small.guiWidth() == 600, "small game panes retain a usable GUI scale");
        check(PlaytestLayout.clampFraction(.9F, 1200, 1) <= .7F, "the editor keeps its minimum width");
        var path = java.nio.file.Files.createTempDirectory("vr-playtest-prefs").resolve("preferences.json");
        EditorPreferences.defaults().withPlaytestFraction(.6F).withScale(1.2F).save(path);
        check(EditorPreferences.load(path).playtestFraction() == .6F, "the split survives preference updates and restart");
    }

    private static void automaticLayoutKeepsUsablePaneWidths() {
        for (float width : new float[] {800, 1024, 1280, 1920, 2560}) {
            var layout = EditorLayout.forWidth(width);
            float content = width * layout.contentFraction();
            float remaining = width - content;
            float details = remaining * layout.detailsFraction();
            check(content >= 200 && content <= 250, "content column stays readable");
            check(layout.compact() == (width < 1200), "small windows use tabbed details");
            check(layout.compact() || remaining - details >= 600, "wide layout leaves room for the graph");
        }
    }

    private static void plainTextAuthoringPreservesLocalizationAndUndo() throws java.io.IOException {
        var directory = java.nio.file.Files.createTempDirectory("vr-editor-wording-");
        try {
            var workspace = com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace.create(directory);
            workspace.createDocument(DefinitionKind.QUEST, "test:authored_quest");
            var conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:wording");
            DialogueGraph.addNode(conversation, "finish");
            workspace.addDialogueLine(conversation, "finish");
            var before = conversation.json();
            workspace.addDialogueResponse(conversation, "start", "continue", "Tell me more.", "finish");
            var response = conversation.json().getAsJsonArray("nodes").get(0).getAsJsonObject()
                    .getAsJsonArray("responses").get(0).getAsJsonObject();
            check(workspace.resolveText(conversation, response.get("label"), "en_us").equals("Tell me more."),
                    "response wording is localized automatically");
            var location = workspace.textLocation(conversation, response.getAsJsonObject("label").get("key").getAsString(), "en_us");
            check(location != null, "direct wording editor finds the locale field");
            conversation.undo();
            check(conversation.json().equals(before), "undo restores the node together with its translation");
            conversation.redo();
            check(workspace.resolveText(conversation, response.get("label"), "en_us").equals("Tell me more."),
                    "redo restores both response and wording");
            workspace.addDialogueLine(conversation, "start");
            var exchanges = conversation.json().getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges");
            check(exchanges.size() == 2 && workspace.resolveText(conversation,
                    exchanges.get(1).getAsJsonObject().get("text"), "en_us").equals("Hello."), "new spoken line has editable wording");
            var unchanged = conversation.json();
            rejects(() -> workspace.addDialogueResponse(conversation, "start", "bad", "Invalid", "missing"));
            check(conversation.json().equals(unchanged), "invalid destination cannot partially edit a conversation");
            String effect = EditorBlocks.append(conversation, "/nodes/0", EditorBlocks.Kind.EXPERIENCE);
            check(effect.equals("/nodes/0/effects/0"), "conversation actions use effect blocks");
            EditorBlocks.append(conversation, "/nodes/0/responses/0", EditorBlocks.Kind.REPUTATION_GATE);
            var quest = workspace.documents().stream().filter(candidate -> candidate.kind() == DefinitionKind.QUEST).findFirst().orElseThrow();
            EditorBlocks.append(quest, "/stages/0", EditorBlocks.Kind.EXPERIENCE);
            var tree = workspace.createDocument(DefinitionKind.TREE, "test:blocks");
            EditorBlocks.append(tree, "/nodes/start", EditorBlocks.Kind.MESSAGE);
            EditorBlocks.append(tree, "/nodes/start", EditorBlocks.Kind.REPUTATION_GATE);
            var errors = workspace.validate().stream().filter(diagnostic ->
                    diagnostic.severity() == com.jvn.villagerretaliation.authoring.core.AuthoringDiagnostic.Severity.ERROR).toList();
            check(errors.isEmpty(), "normal text authoring produces exportable conversation content: " + errors);
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
            }
        }
    }

    private static void fieldValidationDoesNotConsumeEdits() {
        var document = tree();
        var edits = new EditorFieldEdits();
        edits.update("/priority", document.json().get("priority"), "7");
        edits.validate(document);
        check(edits.pending() && document.json().get("priority").getAsInt() == 1, "preflight preserves draft buffers and source");
        edits.apply(document);
        check(document.json().get("priority").getAsInt() == 7, "validated edits still apply");
    }

    private static void terminalResponsePortsReconnectAndUndo() {
        var document = tree();
        DialogueGraph.addNode(document, "finish");
        DialogueGraph.addResponse(document, "start", "bye", "Goodbye", "");
        var output = DialogueGraph.outputs(document).getFirst();
        check(output.to().isEmpty(), "terminal responses still expose a connection port");
        DialogueGraph.connect(document, output.pointer(), "finish");
        var response = document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0).getAsJsonObject();
        check(!response.has("end") && response.get("next").getAsString().equals("finish"), "reconnect must remove the terminal flag");
        document.undo();
        response = document.json().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0).getAsJsonObject();
        check(response.get("end").getAsBoolean() && !response.has("next"), "undo restores the end branch");
        JsonObject before = document.json();
        rejects(() -> DialogueGraph.connect(document, "/priority", "finish"));
        rejects(() -> DialogueGraph.connect(document, output.pointer(), "missing"));
        check(document.json().equals(before), "bad ports and destinations cannot mutate unrelated properties");
    }

    private static void forcedPortsPreserveNestedOwnership() {
        var document = new AuthoringDocument(DefinitionKind.FORCED, "data/test/forced_dialogue/en_us/ports.json",
                json("""
                        {"entries":[{"id":"first","lines":["First"],"options":[{"id":"end","label":"Continue","end_conversation":true}]},
                                    {"id":"second","lines":["Second"]}]}
                        """));
        String port = "/entries/0/options/0/follow_up";
        DialogueGraph.connect(document, port, "/entries/1");
        var option = document.json().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("options").get(0).getAsJsonObject();
        check(!option.get("end_conversation").getAsBoolean(), "connected forced options must continue");
        check(option.getAsJsonObject("follow_up").getAsJsonArray("lines").get(0).getAsString().equals("Second"),
                "forced links embed the destination");
        JsonObject before = document.json();
        rejects(() -> DialogueGraph.connect(document, port, "/entries/0"));
        check(document.json().equals(before), "forced links reject recursive ancestors atomically");
        document.undo();
        check(!document.json().getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonArray("options").get(0).getAsJsonObject().has("follow_up"),
                "undo removes the embedded branch");
    }

    private static void graphCardsExposeChecksAndEditableBlocks() {
        var document = new AuthoringDocument(DefinitionKind.CONVERSATION, "data/test/quests/_shared/conversations/cards.json",
                json("""
                        {"topics":[{"start":"start"}],"nodes":[
                          {"id":"start","exchanges":[{"text":"Hello"}],"effects":["reward"],
                           "responses":[{"id":"ask","label":"Try it","check":"roll"}]},
                          {"id":"yes","outcome":"done"},{"id":"no","outcome":"done"}],
                         "checks":[{"id":"roll","success":"yes","failure":"no"}]}
                        """));
        JsonObject before = document.json();
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.toString());
        var start = cards.getFirst();
        check(start.blocks().stream().anyMatch(block -> block.title().equals("Do") && block.pointer().equals("/nodes/0/effects/0")),
                "effects are editable blocks on their node");
        check(start.blocks().stream().anyMatch(block -> block.title().equals("Choice") && block.pointer().equals("/nodes/0/responses/0")),
                "choice wording is selectable independently of its check");
        check(start.blocks().stream().filter(block -> block.output() != null).count() == 2, "checks expose both outcome ports");
        DialogueGraph.connect(document, "/checks/0/success", "no");
        check(document.json().getAsJsonArray("checks").get(0).getAsJsonObject().get("failure").getAsString().equals("no"),
                "reconnecting pass preserves fail");
        document.undo();
        check(document.json().equals(before), "viewing cards and undoing a wire preserve the document");
        DialogueGraph.connect(document, "/nodes/1/next", "no");
        var yes = document.json().getAsJsonArray("nodes").get(1).getAsJsonObject();
        check(!yes.has("outcome") && yes.get("next").getAsString().equals("no"), "connecting a finish node replaces its outcome");
        document.edit("Share check", root -> {
            var responses = root.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("responses");
            var duplicate = responses.get(0).getAsJsonObject().deepCopy();
            duplicate.addProperty("id", "ask_again");
            responses.add(duplicate);
        });
        var shared = EditorGraph.cards(document, value -> value == null ? "" : value.toString()).getFirst();
        check(shared.blocks().stream().filter(block -> block.output() != null).count() == 4,
                "shared checks expose exactly two ports for each response");
    }

    private static void questPortsReplaceTerminalTransitions() {
        var document = new AuthoringDocument(DefinitionKind.QUEST, "data/test/quests/line/quest/quest.json",
                json("""
                        {"entry_stage":"first","stages":[{"id":"first","transition":{"complete":true},"objectives":[]},
                                                       {"id":"second","transition":{"fail":true}}]}
                        """));
        var cards = EditorGraph.cards(document, value -> value.toString());
        check(cards.getFirst().blocks().getFirst().text().equals("Complete quest"), "terminal quest result is visible on the output");
        DialogueGraph.connect(document, "/stages/0/transition", "second");
        check(document.json().getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonObject("transition").get("stage").getAsString().equals("second"),
                "quest output replaces the terminal transition with a supported stage reference");
        document.undo();
        check(document.json().getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonObject("transition").get("complete").getAsBoolean(),
                "undo restores quest completion");
    }

    private static void graphArrangementHandlesCyclesAndLargeCards() {
        var document = tree();
        DialogueGraph.addNode(document, "second");
        DialogueGraph.addNode(document, "isolated");
        DialogueGraph.setEntry(document, "start");
        DialogueGraph.addResponse(document, "start", "forward", "Go", "second");
        DialogueGraph.addResponse(document, "second", "back", "Back", "start");
        for (int i = 0; i < 8; i++) DialogueGraph.addResponse(document, "start", "end_" + i, "Finish", "");
        var cards = EditorGraph.cards(document, value -> value == null ? "" : value.toString());
        var positions = EditorGraph.arrange(cards);
        check(positions.size() == 3, "cyclic and disconnected nodes all receive positions");
        check(positions.get("second")[0] > positions.get("start")[0], "flow proceeds left to right");
        var start = cards.stream().filter(card -> card.node().id().equals("start")).findFirst().orElseThrow();
        check(positions.get("isolated")[1] >= positions.get("start")[1] + start.height() + 40,
                "variable height cards never overlap in their column");
        check(EditorGraph.arrange(java.util.List.of()).isEmpty(), "empty graphs need no layout");
        var duplicates = new AuthoringDocument(DefinitionKind.CONVERSATION, "data/test/quests/_shared/conversations/duplicates.json",
                json("{\"nodes\":[{\"id\":\"same\",\"outcome\":\"done\"},{\"id\":\"same\",\"outcome\":\"done\"}]}"));
        rejects(() -> EditorGraph.cards(duplicates, value -> value.toString()));
    }

    private static void behaviorBlocksRejectUnsupportedOwners() {
        var document = tree();
        JsonObject before = document.json();
        rejects(() -> EditorBlocks.append(document, "/metadata", EditorBlocks.Kind.EXPERIENCE));
        check(document.json().equals(before), "unsupported block placement cannot edit metadata");
        String path = EditorBlocks.append(document, "/nodes/start", EditorBlocks.Kind.REPUTATION_GATE);
        check(path.equals("/nodes/start/conditions/0"), "condition block selects its editable fields");
        document.undo();
        check(document.json().equals(before), "block insertion is one undo step");
        var conversation = new AuthoringDocument(DefinitionKind.CONVERSATION, "data/test/quests/_shared/conversations/blocks.json",
                json("{\"nodes\":[{\"id\":\"start\",\"outcome\":\"done\"}]}"));
        check(!EditorBlocks.available(conversation, "/nodes/0").contains(EditorBlocks.Kind.REPUTATION_GATE),
                "conversation node conditions are not offered where the runtime ignores them");
        EditorBlocks.append(conversation, "/nodes/0", EditorBlocks.Kind.EXPERIENCE);
        EditorBlocks.append(conversation, "/nodes/0", EditorBlocks.Kind.REPUTATION);
        var effects = conversation.json().getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("effects");
        check(!effects.get(0).getAsJsonObject().get("id").equals(effects.get(1).getAsJsonObject().get("id")),
                "conversation effect IDs remain unique");
    }

    private static AuthoringDocument tree() {
        return new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/editor.json",
                json("{\"id\":\"test:editor\",\"priority\":1,\"display\":{\"title\":\"Original\"},\"metadata\":{},"
                + "\"nodes\":{\"start\":{\"lines\":[\"Hello\"],\"speaker\":{},\"end\":true}}}"));
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    private static void rejects(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException expected) {
            return;
        }
        throw new AssertionError("Expected invalid editor operation to fail");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
