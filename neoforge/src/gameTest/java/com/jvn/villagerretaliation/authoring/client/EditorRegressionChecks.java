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
        cleanRecoveryDropsAbandonedEditsButKeepsBuffers();
        playtestSnapshotsIncludeSupportingFiles();
        testWorldAssociationsRestoreOnlyTheirOwnPack();
        return 6;
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
        EditorArrayOrder.edit(document, "/nodes", "Duplicate node", values -> values.add(values.get(0)));
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/1/exchanges/0", 1).get(0).getAsString().equals("nested image"), "duplication includes nested thumbnails");
        document.editMetadata("Distinct thumbnail", values -> values.getAsJsonObject("camera_thumbnails").getAsJsonArray("/nodes/1").set(0, new com.google.gson.JsonPrimitive("duplicate image")));
        EditorArrayOrder.edit(document, "/nodes", "Duplicate identical node", values -> values.add(values.get(1)));
        check(EditorCameraShots.thumbnails(document.metadata(), "/nodes/2", 1).get(0).getAsString().equals("duplicate image"), "equal JSON entries keep their distinct thumbnails when duplicated");
        EditorArrayOrder.edit(document, "/nodes", "Add fresh identical node", values -> values.add(values.get(1).deepCopy()));
        check(!document.metadata().getAsJsonObject("camera_thumbnails").has("/nodes/3"), "fresh entries do not inherit thumbnails merely because their JSON matches");
    }

    private static void cleanRecoveryDropsAbandonedEditsButKeepsBuffers() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-recovery-clean"));
        var document = workspace.createDocument(DefinitionKind.TREE, "test:recovery");
        workspace.save();
        document.edit("Temporary change", root -> root.addProperty("priority", 99));
        EditorRecovery.write(workspace, new JsonObject(), false);
        check(workspace.recoveryAvailable(), "changed drafts retain recovery");
        document.undo();
        EditorRecovery.write(workspace, new JsonObject(), false);
        check(!workspace.recoveryAvailable() && !AuthoringWorkspace.open(workspace.directory()).recoveryAvailable(), "undoing to saved state removes abandoned recovery across restart");
        JsonObject buffers = new JsonObject(); buffers.addProperty("raw", "{unfinished");
        EditorRecovery.write(workspace, buffers, true);
        var recovered = AuthoringWorkspace.recover(workspace.directory());
        check(recovered.recoveryBuffers().equals(buffers), "clean documents still recover pending raw input");
        var edits = new EditorFieldEdits();
        edits.update("/priority", new com.google.gson.JsonPrimitive(1), "invalid number");
        buffers = new JsonObject(); buffers.add("fields", edits.snapshot());
        EditorRecovery.write(workspace, buffers, edits.pending());
        check(AuthoringWorkspace.recover(workspace.directory()).recoveryBuffers().equals(buffers), "invalid field buffers survive clean-document recovery");
        edits.clear();
        EditorRecovery.write(workspace, new JsonObject(), false);
        check(!workspace.recoveryAvailable(), "discarding the last buffer removes recovery");
    }

    private static AuthoringWorkspace workspaceWithFunction() throws IOException {
        var root = Files.createTempDirectory("vr-test-files");
        var pack = root.resolve("pack");
        Files.createDirectories(pack.resolve("data/test/function"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Test pack\"}}");
        Files.writeString(pack.resolve("data/test/function/probe.mcfunction"), "say before");
        var workspace = AuthoringWorkspace.create(root.resolve("workspace")); workspace.importPack(pack);
        workspace.createDocument(DefinitionKind.TREE, "test:playtest");
        return workspace;
    }

    private static void playtestSnapshotsIncludeSupportingFiles() throws IOException {
        var workspace = workspaceWithFunction();
        var snapshot = EditorTestSnapshot.capture(workspace);
        var hashes = workspace.supportingFileHashes();
        check(hashes == workspace.supportingFileHashes(), "unchanged supporting files reuse cached hashes");
        workspace.writeTextFile("data/test/function/probe.mcfunction", "say after");
        check(!snapshot.current(workspace, false) && !snapshot.current(workspace, true), "function edits invalidate both full and logic snapshots");
        workspace.writeTextFile("data/test/function/probe.mcfunction", "say before");
        check(snapshot.current(workspace, true), "restoring function bytes restores the applied logic");
        workspace.applyFileMove(workspace.previewFileMove("data/test/function/probe.mcfunction", "data/test/function/moved.mcfunction"));
        check(!snapshot.current(workspace, true), "supporting file path changes invalidate the snapshot");
        workspace.undoFileMove(); check(snapshot.current(workspace, true), "file move undo restores the supporting fingerprint");
        var metadata = workspace.packMetadata(); metadata.getAsJsonObject("pack").addProperty("description", "Changed pack");
        workspace.setPackMetadata(metadata);
        check(!snapshot.current(workspace, false), "pack metadata is part of the applied snapshot");
        metadata.getAsJsonObject("pack").addProperty("description", "Test pack"); workspace.setPackMetadata(metadata);
        var document = workspace.documents().getFirst();
        document.edit("Visual wording", root -> root.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("lines").set(0, new com.google.gson.JsonPrimitive("New wording")));
        check(!snapshot.current(workspace, false) && snapshot.current(workspace, true), "visual-only JSON changes still preview without a logic reload");
    }

    private static void testWorldAssociationsRestoreOnlyTheirOwnPack() throws IOException {
        var workspace = workspaceWithFunction();
        var world = Files.createTempDirectory("vr-persisted-test-world");
        var pack = world.resolve("datapacks/test_content");
        com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, pack);
        check(EditorTestSnapshot.restore(world, workspace.directory()) == null, "a pack alone never enables live controls in an ordinary world");
        EditorTestSnapshot.remember(world, workspace.directory());
        var restored = EditorTestSnapshot.restore(world, workspace.directory());
        check(restored != null && restored.current(workspace, false), "association and exported content restore without in-memory session state");
        var other = AuthoringWorkspace.create(Files.createTempDirectory("vr-other-workspace"));
        check(EditorTestSnapshot.restore(world, other.directory()) == null, "test worlds remain scoped to their workspace");
        try { EditorTestSnapshot.remember(world, other.directory()); throw new AssertionError("association overwritten"); }
        catch (IOException expected) { check(EditorTestSnapshot.restore(world, workspace.directory()) != null, "rejected reassociation preserves ownership"); }
        workspace.writeTextFile("data/test/function/probe.mcfunction", "say changed");
        check(!EditorTestSnapshot.restore(world, workspace.directory()).current(workspace, true), "reopening compares against exported files, not the current draft");
        com.jvn.villagerretaliation.authoring.core.ManagedPackExport.export(workspace, pack);
        check(EditorTestSnapshot.restore(world, workspace.directory()).current(workspace, false), "a new applied export is restored after restart");
        Files.writeString(pack.resolve("data/test/function/probe.mcfunction"), "say external edit");
        try { EditorTestSnapshot.restore(world, workspace.directory()); throw new AssertionError("external edit accepted as the applied snapshot"); }
        catch (IOException expected) { check(expected.getMessage().contains("external edit"), "external changes report the stale test pack"); }
        var invalid = new JsonObject(); invalid.add("version", new JsonObject()); invalid.addProperty("workspace", workspace.directory().toString());
        Files.writeString(world.resolve("authoring-workspace.json"), invalid.toString());
        try { EditorTestSnapshot.restore(world, workspace.directory()); throw new AssertionError("invalid version shape accepted"); }
        catch (IOException expected) { check(expected.getMessage().contains("Invalid"), "invalid version shapes produce a recoverable diagnostic"); }
        Files.writeString(world.resolve("authoring-workspace.json"), "{invalid");
        try { EditorTestSnapshot.restore(world, workspace.directory()); throw new AssertionError("malformed association accepted"); }
        catch (IOException expected) { check(expected.getMessage().contains("Invalid"), "malformed associations fail with a diagnostic"); }
    }

    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
