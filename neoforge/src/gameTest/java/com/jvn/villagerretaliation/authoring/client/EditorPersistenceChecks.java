package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

final class EditorPersistenceChecks {
    static int run() throws IOException {
        sessionFailureDoesNotBlockRecovery();
        unchangedRequestsAreSkippedAndFailuresRetry();
        frozenWritesKeepTheirOwnContent();
        newerWritesAndSavesCancelOlderRecovery();
        workspaceRevisionsTrackPersistedState();
        return 5;
    }

    private static void sessionFailureDoesNotBlockRecovery() throws IOException {
        var workspace = workspace();
        try {
            workspace.createDocument(DefinitionKind.TREE, "test:recovery");
            Path settings = workspace.directory().resolve("sessions.json"); Files.writeString(settings, "{broken");
            var tasks = new ArrayList<Runnable>(); var persistence = new EditorRecovery(settings, tasks::add);
            check(persistence.session(workspace).isEmpty(), "malformed optional session data does not prevent workspace recovery");
            fails(() -> persistence.write(workspace, new JsonObject(), false, new JsonObject(), false));
            check(tasks.size() == 1, "recovery is scheduled even when reading and saving session settings fail");
            tasks.removeFirst().run(); persistence.poll();
            check(workspace.recoveryAvailable() && workspace.recover().documents().size() == 1, "document recovery remains usable after settings failure");
        } finally { cleanup(workspace.directory()); }
    }

    private static void unchangedRequestsAreSkippedAndFailuresRetry() throws IOException {
        var workspace = workspace();
        try {
            workspace.createDocument(DefinitionKind.TREE, "test:pending");
            var tasks = new ArrayList<Runnable>();
            var persistence = new EditorRecovery(workspace.directory().resolve("sessions.json"), tasks::add);
            JsonObject buffers = new JsonObject(); buffers.addProperty("raw", "{pending");
            persistence.write(workspace, buffers, true, new JsonObject(), false);
            persistence.write(workspace, buffers, true, new JsonObject(), false);
            check(tasks.size() == 1, "identical pending requests are coalesced");
            Path recovery = workspace.directory().resolve("workspace.recovery.json"); Files.createDirectory(recovery);
            tasks.removeFirst().run(); fails(persistence::poll);
            Files.delete(recovery);
            persistence.write(workspace, buffers, true, new JsonObject(), false);
            check(tasks.size() == 1, "failed requests retry without requiring another edit");
            tasks.removeFirst().run(); persistence.poll();
            persistence.write(workspace, buffers, true, new JsonObject(), false);
            check(tasks.isEmpty(), "unchanged completed recovery is not serialized or written again");
            buffers.addProperty("raw", "{changed");
            persistence.write(workspace, buffers, true, new JsonObject(), false);
            check(tasks.size() == 1, "unfinished input alone invalidates recovery");
            tasks.removeFirst().run(); persistence.poll();
            check(workspace.recover().recoveryBuffers().get("raw").getAsString().equals("{changed"), "changed input is persisted");
        } finally { cleanup(workspace.directory()); }
    }

    private static void frozenWritesKeepTheirOwnContent() throws IOException {
        var workspace = workspace();
        try {
            var document = workspace.createDocument(DefinitionKind.TREE, "test:frozen");
            String asset = "data/test/functions/frozen.mcfunction";
            workspace.importResources(Map.of(asset, new byte[] {1, 2, 3}));
            JsonObject buffers = new JsonObject(); buffers.addProperty("text", "before");
            var write = workspace.prepareRecovery(buffers, true);
            document.edit("Later edit", json -> json.addProperty("later", true));
            workspace.writeTextFile(asset, "changed");
            buffers.addProperty("text", "after");
            write.write(); var recovered = workspace.recover();
            check(!recovered.documents().getFirst().json().has("later"), "worker snapshots do not observe later document edits");
            check(recovered.readFile(asset)[0] == 1 && recovered.recoveryBuffers().get("text").getAsString().equals("before"),
                    "captured assets and buffers remain isolated");
        } finally { cleanup(workspace.directory()); }
    }

    private static void newerWritesAndSavesCancelOlderRecovery() throws IOException {
        var workspace = workspace();
        try {
            var document = workspace.createDocument(DefinitionKind.TREE, "test:ordered");
            var older = workspace.prepareRecovery(new JsonObject(), true);
            document.edit("New state", json -> json.addProperty("new", true));
            var newer = workspace.prepareRecovery(new JsonObject(), true);
            newer.write(); older.write();
            check(workspace.recover().documents().getFirst().json().has("new"), "out-of-order workers cannot replace newer recovery");
            var queued = workspace.prepareRecovery(new JsonObject(), true);
            workspace.save(); queued.write();
            check(!workspace.recoveryAvailable(), "save prevents queued recovery from resurrecting the deleted file");
            var stale = workspace.prepareRecovery(new JsonObject(), true);
            var reopened = AuthoringWorkspace.open(workspace.directory());
            reopened.discardRecovery(); stale.write();
            check(!reopened.recoveryAvailable(), "reopened workspaces share recovery cancellation");
        } finally { cleanup(workspace.directory()); }
    }

    private static void workspaceRevisionsTrackPersistedState() throws IOException {
        var workspace = workspace();
        try {
            long initial = workspace.revision();
            check(initial == workspace.revision(), "idle revision reads are stable");
            var document = workspace.createDocument(DefinitionKind.TREE, "test:versions");
            long added = workspace.revision(); check(added > initial, "membership changes advance workspace revisions");
            document.editMetadata("Layout", metadata -> metadata.addProperty("x", 2));
            long layout = workspace.revision(); check(layout > added, "recovery tracks layout edits");
            document.undo(); long undone = workspace.revision(); check(undone > layout, "undo invalidates recovery");
            workspace.save(); long saved = workspace.revision(); check(saved > undone, "save advances the baseline generation");
            workspace.createFolder("assets/test/empty"); long folder = workspace.revision(); check(folder > saved, "empty folders participate in snapshots");
            workspace.importResources(Map.of("assets/test/textures/image.png", new byte[] {1}));
            long asset = workspace.revision(); check(asset > folder, "asset replacement invalidates recovery");
            var pack = workspace.packMetadata(); pack.getAsJsonObject("pack").addProperty("description", "Changed"); workspace.setPackMetadata(pack);
            check(workspace.revision() > asset, "pack settings invalidate recovery");
        } finally { cleanup(workspace.directory()); }
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("vr-editor-persistence")); }
    private static void fails(EditorOperations.Action action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected a persistence failure");
    }
    private static void cleanup(Path root) throws IOException {
        try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
