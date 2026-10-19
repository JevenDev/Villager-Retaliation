package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorWorkflowChecks {
    private EditorWorkflowChecks() { }

    static int run() throws IOException {
        historyLabelsFollowUndoRedoAndGroups();
        referencesResolveScopedDefinitionsAndLocalization();
        graphSelectionKeepsOtherNodesAndRelativeOffsets();
        listMovesAreAtomicAndRejectStaleDrags();
        fieldIssuesRetainEveryPendingEdit();
        openFilesCyclePinRenameAndRemove();
        sessionsRestoreContextAfterRestart();
        commandSearchKeepsAvailabilityAndShortcuts();
        return 8;
    }

    private static void historyLabelsFollowUndoRedoAndGroups() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-history-labels"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:history");
        document.edit("Revise title", root -> root.addProperty("title", "One"));
        document.edit("Revise title again", root -> root.addProperty("title", "Two"));
        check(document.undoLabel().equals("Revise title again") && document.undoLabels().get(1).equals("Revise title"), "history exposes operation labels in undo order");
        document.undo();
        check(document.redoLabel().equals("Revise title again") && document.undoLabel().equals("Revise title"), "undo moves the same label to redo");
        document.redo();
        check(document.undoLabel().equals("Revise title again"), "redo restores the operation label");
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
        var conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation");
        conversation.edit("Assign quest", root -> root.addProperty("quest", quest.id()));
        DialogueGraph.addNode(conversation, "finish");
        workspace.addDialogueResponse(conversation, "start", "reply", "A localized reply", "finish");
        String label = conversation.undoLabel();
        var before = conversation.json(); conversation.undo();
        check(conversation.redoLabel().equals(label), "grouped localized edits retain their redo label");
        conversation.redo();
        check(conversation.json().equals(before) && conversation.undoLabel().equals(label), "grouped history labels do not change atomic undo behavior");
    }

    private static void referencesResolveScopedDefinitionsAndLocalization() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-references"));
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
        var conversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation");
        conversation.edit("Assign quest", root -> root.addProperty("quest", quest.id()));
        var definition = new EditorReferences.Location(quest, "");
        check(EditorReferences.definitions(workspace, conversation, "/quest", quest.id(), "en_us").equals(List.of(definition)), "quest references navigate to their definition");
        check(EditorReferences.usages(workspace, definition, "en_us").contains(new EditorReferences.Location(conversation, "/quest")), "find usages searches other documents");
        check(EditorReferences.definitions(workspace, conversation, "/text", quest.id(), "en_us").isEmpty(), "plain wording is not treated as a definition reference");
        String prefix = quest.json().get("localization_prefix").getAsString();
        var locale = workspace.documents().stream().filter(value -> value.resourcePath().endsWith("/quest/locales/en_us.json")).findFirst().orElseThrow();
        locale.edit("Add title", root -> root.addProperty(prefix + ".title", "A title"));
        quest.edit("Set title", root -> root.add("display", JsonParser.parseString("{\"title\":{\"key\":\"#title\"}}")));
        var translation = EditorReferences.definitions(workspace, quest, "/display/title/key", "#title", "en_us").getFirst();
        check(translation.document() == locale && translation.pointer().endsWith(".title"), "relative localization keys resolve to their owning locale entry");
        check(EditorReferences.usages(workspace, translation, "en_us").contains(new EditorReferences.Location(quest, "/display/title/key")), "localization usages retain the source field");
        var messages = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue/en_us/greetings.json",
                JsonParser.parseString("{\"messages\":[{\"key\":\"shared.greeting\",\"text\":\"Hello\"}]}").getAsJsonObject());
        workspace.add(messages);
        var tree = workspace.createDocument(DefinitionKind.TREE, "test:message_ref");
        tree.edit("Use message key", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines")
                .set(0, JsonParser.parseString("{\"key\":\"shared.greeting\"}")));
        var greeting = EditorReferences.definitions(workspace, tree, "/nodes/start/lines/0/key", "shared.greeting", "en_us").getFirst();
        check(greeting.document() == messages && greeting.pointer().equals("/messages/0/text"), "ordinary message keys resolve to their authored payload");
        check(EditorReferences.usages(workspace, greeting, "en_us").equals(List.of(new EditorReferences.Location(tree, "/nodes/start/lines/0/key"))),
                "message declarations are not counted as their own usages");
        var addon = workspace.createDocument(DefinitionKind.CAMPAIGN_ADDON, "test:addon");
        var scopedQuest = workspace.createDocument(DefinitionKind.QUEST, "test:quest", addon);
        var scopedConversation = workspace.createDocument(DefinitionKind.CONVERSATION, "test:conversation", addon);
        check(EditorReferences.definitions(workspace, scopedConversation, "/quest", quest.id(), "en_us").equals(List.of(new EditorReferences.Location(scopedQuest, ""))), "definition lookup prefers the source campaign scope");
    }

    private static void graphSelectionKeepsOtherNodesAndRelativeOffsets() {
        var document = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/layout.json", JsonParser.parseString("{\"nodes\":{\"a\":{\"lines\":[\"A\"]},\"b\":{\"lines\":[\"B\"]},\"c\":{\"lines\":[\"C\"]}}}").getAsJsonObject());
        var cards = EditorGraph.cards(document, Object::toString);
        var points = Map.of("a", new float[] {7, 9}, "b", new float[] {507, 49}, "c", new float[] {1100, 500});
        check(EditorGraphSelection.box(cards, points, 900, 300, 0, 0).equals(Set.of("a", "b")), "reverse marquee selection intersects visible card bounds");
        var aligned = EditorGraphSelection.layout(cards, points, Set.of("a", "b"), "Align top");
        check(aligned.get("b")[1] == 9 && aligned.get("c")[1] == 500 && points.get("b")[1] == 49, "alignment edits selected nodes without mutating its input");
        EditorGraphSelection.snap(aligned, Set.of("a", "b"), "a", 16);
        check(aligned.get("a")[0] == 0 && aligned.get("a")[1] == 16 && aligned.get("b")[0] - aligned.get("a")[0] == 500, "snapping moves the group together and preserves relative offsets");
        var spaced = EditorGraphSelection.layout(cards, points, Set.of("a", "b", "c"), "Space horizontally");
        check(Math.abs((spaced.get("b")[0] - spaced.get("a")[0]) - (spaced.get("c")[0] - spaced.get("b")[0])) < .01f, "spacing distributes selected cards evenly");
    }

    private static void listMovesAreAtomicAndRejectStaleDrags() {
        var document = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/list.json", JsonParser.parseString("{\"values\":[{\"id\":\"a\"},{\"id\":\"b\"},{\"id\":\"c\"}]}").getAsJsonObject());
        var before = document.json(); var baseline = before.getAsJsonArray("values");
        EditorArrayOrder.move(document, "/values", 0, 2, baseline);
        check(document.json().getAsJsonArray("values").get(2).getAsJsonObject().get("id").getAsString().equals("a"), "list movement preserves entry identity and payload");
        var changed = document.json();
        rejects(() -> EditorArrayOrder.move(document, "/values", 1, 0, baseline));
        check(document.json().equals(changed), "a stale list drag cannot overwrite edits");
        document.undo(); check(document.json().equals(before), "reordering is a single undoable edit");
        rejects(() -> EditorArrayOrder.move(document, "/values", -1, 0, baseline));
    }

    private static void fieldIssuesRetainEveryPendingEdit() {
        var document = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/fields.json", JsonParser.parseString("{\"a\":1,\"b\":2,\"text\":\"Original\"}").getAsJsonObject());
        var edits = new EditorFieldEdits();
        edits.update("/a", new JsonPrimitive(1), "broken"); edits.update("/b", new JsonPrimitive(2), "\"wrong type\"");
        edits.update("/text", new JsonPrimitive("Original"), "Revised");
        var issues = edits.issues(document);
        check(issues.keySet().equals(Set.of("/a", "/b")) && edits.pending(), "inline issues report every invalid field without consuming pending edits");
        check(edits.candidate(document, "/text").getAsString().equals("Revised") && document.json().get("text").getAsString().equals("Original"), "field previews do not mutate the draft");
        rejects(() -> edits.apply(document));
        check(document.json().get("a").getAsInt() == 1 && edits.pending(), "invalid fields cannot partially apply");
    }

    private static void openFilesCyclePinRenameAndRemove() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-open-files"));
        var first = workspace.createDocument(DefinitionKind.TREE, "test:first");
        var second = workspace.createDocument(DefinitionKind.TREE, "test:second");
        var files = new EditorOpenFiles(); files.open(workspace, first.resourcePath()); files.open(workspace, second.resourcePath());
        files.pin(workspace, first.resourcePath());
        check(files.cycle(workspace, first.resourcePath(), 1).equals(second.resourcePath()) && files.cycle(workspace, first.resourcePath(), -1).equals(second.resourcePath()), "file cycling wraps in both directions");
        workspace.rename(first, "test:renamed");
        check(files.entries(workspace).getFirst().currentPath().equals(first.resourcePath()) && files.entries(workspace).getFirst().pinned(), "pins follow renamed document identities");
        workspace.remove(second);
        check(files.entries(workspace).size() == 1, "deleted files leave the switcher");
        files.close(workspace, first.resourcePath());
        check(files.entries(workspace).isEmpty() && workspace.documents().contains(first), "closing a file retains its workspace draft");
    }

    private static void sessionsRestoreContextAfterRestart() throws IOException {
        var directory = Files.createTempDirectory("vr-editor-session");
        var workspace = AuthoringWorkspace.create(directory.resolve("workspace"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:resume"); workspace.save();
        var navigation = new EditorNavigation();
        var location = new EditorNavigation.Location(document, document.resourcePath(), 0, false, "/nodes/start/lines", 120, 80, .75f, 60, 140);
        navigation.record(workspace, location);
        var files = new EditorOpenFiles(); files.pin(workspace, document.resourcePath());
        var session = EditorSession.snapshot(workspace, navigation, files, location, true);
        new EditorSession(directory.resolve("sessions.json")).save(workspace, session);
        var reopened = AuthoringWorkspace.open(workspace.directory());
        var saved = new EditorSession(directory.resolve("sessions.json")).read(reopened);
        var current = EditorSession.location(reopened, saved.getAsJsonObject("current"));
        check(current.document() != document && current.pointer().equals(location.pointer()) && current.detailsScroll() == 140 && current.zoom() == .75f, "restart restores the field, graph and both panel scroll positions");
        var restored = new EditorOpenFiles(); restored.restore(reopened, saved.getAsJsonArray("open_files"));
        check(restored.entries(reopened).getFirst().pinned() && saved.get("snap_grid").getAsBoolean(), "pins and grid preferences survive restart");
        var missing = saved.getAsJsonObject("current").deepCopy(); missing.addProperty("path", "missing.json");
        check(EditorSession.location(reopened, missing) == null, "session restoration skips missing files");
    }

    private static void commandSearchKeepsAvailabilityAndShortcuts() {
        var save = new EditorCommands.Command("Save drafts", "Ctrl+S", true, () -> { });
        var node = new EditorCommands.Command("Add node", "", false, () -> { });
        check(EditorCommands.matching(List.of(save, node), " SAVE ").equals(List.of(save)), "command search ignores case and surrounding whitespace");
        check(!EditorCommands.matching(List.of(save, node), "node").getFirst().enabled() && save.shortcut().equals("Ctrl+S"), "commands retain availability and shortcut hints");
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
}
