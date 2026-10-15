package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;

public final class EditorClientChecks {
    private EditorClientChecks() { }

    public static int run() {
        propertyEditsApplyTogether();
        invalidPropertiesDoNotPartiallyApply();
        stalePropertiesAreNotOverwritten();
        questConnectionsUseStageTransitions();
        cameraOwnersFollowRuntimeFormats();
        cameraAppendIsValidatedBeforeCommit();
        return 6;
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
