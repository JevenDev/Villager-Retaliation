package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;

final class EditorRegressionChecks {
    private EditorRegressionChecks() { }

    static int run() throws IOException {
        graphStorageCollisionsPreserveExistingNodes();
        clipboardPreservesExternalQuestReferences();
        listEditsKeepCameraThumbnailsWithTheirOwners();
        return 3;
    }

    private static void graphStorageCollisionsPreserveExistingNodes() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-graph-storage"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:storage");
        document.edit("Add aliased node", root -> root.getAsJsonObject("nodes").add("start_copy",
                json("{\"id\":\"keep_me\",\"lines\":[\"Keep this node\"],\"end\":true}")));
        var before = document.json();
        var clipboard = new EditorGraphClipboard(); clipboard.copy(document, Set.of("start"));
        var inserted = clipboard.paste(document);
        check(inserted.equals(java.util.List.of("start_copy_new")), "paste reserves storage keys and explicit IDs");
        check(document.value("/nodes/start_copy/id").getAsString().equals("keep_me"), "paste preserves the existing aliased node");
        document.undo(); check(document.json().equals(before), "safe paste undoes in one step");
        try { EditorContentTools.renameNode(document, "start", "start_copy"); throw new AssertionError("storage collision accepted"); }
        catch (IllegalArgumentException expected) { check(document.json().equals(before), "rejected rename leaves nodes and references intact"); }
    }

    private static void clipboardPreservesExternalQuestReferences() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-paste-references"));
        var source = workspace.createDocument(DefinitionKind.TREE, "test:source");
        DialogueGraph.addNode(source, "external_stage");
        source.edit("External quest condition", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").add("conditions",
                JsonParser.parseString("[{\"type\":\"quest_fact\",\"quest\":\"other:quest\",\"stage\":\"start\"},{\"type\":\"quest_fact\",\"quest_id\":\"other:quest\",\"stage\":\"external_stage\"}]")));
        var clipboard = new EditorGraphClipboard(); clipboard.copy(source, Set.of("start"));
        clipboard.paste(source);
        check(source.value("/nodes/start/conditions").equals(source.value("/nodes/start_copy/conditions")), "same-file paste preserves external stage references");
        var target = workspace.createDocument(DefinitionKind.TREE, "test:target");
        var inserted = clipboard.paste(target);
        check(inserted.size() == 1, "external stage conditions do not pull unrelated local nodes into a cross-file paste");
        check(source.value("/nodes/start/conditions").equals(target.value("/nodes/start_copy/conditions")), "cross-file paste preserves external stage references");
        var quest = workspace.createDocument(DefinitionKind.QUEST, "test:quest");
        DialogueGraph.addNode(quest, "finish");
        EditorDocumentOperations.nextStage(quest, "start", "finish");
        quest.edit("Local stage condition", root -> root.getAsJsonArray("stages").get(0).getAsJsonObject().add("conditions",
                JsonParser.parseString("[{\"type\":\"quest_fact\",\"quest\":\"test:quest\",\"stages\":[\"finish\"]}]")));
        clipboard.copy(quest, Set.of("start", "finish")); clipboard.paste(quest);
        check(quest.value("/stages/2/transition/stage").getAsString().equals("finish_copy"), "local transitions still follow copied stages");
        check(quest.value("/stages/2/conditions/0/stages/0").getAsString().equals("finish_copy"), "local stage filters follow copied stages");
    }

    private static void listEditsKeepCameraThumbnailsWithTheirOwners() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-camera-order"));
        var document = workspace.createDocument(DefinitionKind.CONVERSATION, "test:camera");
        DialogueGraph.addNode(document, "second");
        var shot = json("{\"preset\":\"over_shoulder_left\"}");
        EditorDocumentOperations.appendShot(document, "/nodes/0", shot, "first image");
        EditorDocumentOperations.appendShot(document, "/nodes/1", shot, "second image");
        EditorDocumentOperations.appendShot(document, "/nodes/0/exchanges/0", shot, "nested image");
        var before = document.json(); var metadata = document.metadata();
        EditorArrayOrder.move(document, "/nodes", 0, 1, document.value("/nodes").getAsJsonArray());
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/0", 1).get(0).getAsString().equals("second image"), "drag reorder moves owner thumbnails");
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/1/exchanges/0", 1).get(0).getAsString().equals("nested image"), "drag reorder moves nested thumbnails");
        document.undo(); check(document.json().equals(before) && document.metadata().equals(metadata), "order and images undo together");
        document.redo();
        EditorArrayOrder.edit(document, "/nodes", "Remove node", values -> values.remove(0));
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/0", 1).get(0).getAsString().equals("first image"), "removal shifts surviving thumbnails");
        EditorArrayOrder.edit(document, "/nodes", "Duplicate node", values -> values.add(values.get(0).deepCopy()));
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/1/exchanges/0", 1).get(0).getAsString().equals("nested image"), "duplication includes nested thumbnails");
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
