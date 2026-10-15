package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;

public final class EditorClientChecks {
    private EditorClientChecks() { }

    public static int run() throws java.io.IOException {
        propertyEditsApplyTogether();
        invalidPropertiesDoNotPartiallyApply();
        stalePropertiesAreNotOverwritten();
        questConnectionsUseStageTransitions();
        cameraOwnersFollowRuntimeFormats();
        cameraAppendIsValidatedBeforeCommit();
        editorPreferencesSurviveRestart();
        automaticLayoutKeepsUsablePaneWidths();
        plainTextAuthoringPreservesLocalizationAndUndo();
        fieldValidationDoesNotConsumeEdits();
        return 10;
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
                json("{\"id\":\"test:quest\",\"stages\":[{\"id\":\"first\",\"next\":{\"complete\":true},\"objectives\":[]},{\"id\":\"second\",\"next\":{\"complete\":true}}]}"));
        EditorDocumentOperations.nextStage(document, "first", "second");
        JsonObject first = document.json().getAsJsonArray("stages").get(0).getAsJsonObject();
        check(first.get("next").getAsString().equals("second"), "quest next must use a supported stage reference");
        check(!first.has("responses") && first.has("objectives"), "stage connection must not invent dialogue responses or remove objectives");
        JsonObject before = document.json();
        rejects(() -> EditorDocumentOperations.nextStage(document, "first", "missing"));
        check(document.json().equals(before), "missing destinations cannot mutate the quest");
    }

    private static void cameraOwnersFollowRuntimeFormats() {
        var tree = tree();
        EditorDocumentOperations.cameraOwner(tree, "/nodes/start");
        rejects(() -> EditorDocumentOperations.cameraOwner(tree, "/metadata"));
        rejects(() -> EditorDocumentOperations.cameraOwner(tree, "/nodes/start/speaker"));
        var conversation = new AuthoringDocument(DefinitionKind.CONVERSATION,
                "data/test/quests/_shared/conversations/talk.json", json("{\"nodes\":[{\"id\":\"start\","
                + "\"exchanges\":[{\"id\":\"greet\",\"speaker\":{\"type\":\"player\"},\"text\":{\"key\":\"greet\"}}],"
                + "\"responses\":[{\"id\":\"continue\",\"label\":{\"key\":\"continue\"},\"next\":\"start\"}]}]}"));
        EditorDocumentOperations.cameraOwner(conversation, "/nodes/0");
        EditorDocumentOperations.cameraOwner(conversation, "/nodes/0/exchanges/0");
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
            com.jvn.villagerretaliation.authoring.core.DialogueGraph.addNode(conversation, "finish");
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
