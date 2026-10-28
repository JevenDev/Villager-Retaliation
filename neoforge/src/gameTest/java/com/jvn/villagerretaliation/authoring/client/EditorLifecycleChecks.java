package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EditorLifecycleChecks {
    static int run() throws IOException {
        groupedHistoryRejectsRemovedAndReplacedMembers();
        historyProtectsAllAffectedDrafts();
        missingDraftsRecoverIndependently();
        commandsRunInOrderAndReportFailures();
        return 4;
    }

    private static void groupedHistoryRejectsRemovedAndReplacedMembers() throws IOException {
        var workspace = workspace();
        try {
            var first = document("first"); var second = document("second");
            workspace.add(first); workspace.add(second);
            workspace.editDocuments("Replace wording", Map.of(first, value(2), second, value(2)));
            workspace.remove(second);
            rejects(first::undo);
            check(first.value("/value").getAsInt() == 2 && second.value("/value").getAsInt() == 2, "failed undo changes no group member");
            workspace.restoreDeleted(0);
            var restored = workspace.documents().stream().filter(document -> document.resourcePath().equals(second.resourcePath())).findFirst().orElseThrow();
            rejects(first::undo);
            check(restored.value("/value").getAsInt() == 2, "restoring a file does not revive history targeting a detached instance");
            var third = document("third"); workspace.add(third);
            workspace.editDocuments("Another group", Map.of(first, value(3), third, value(3)));
            first.undo(); workspace.remove(third);
            rejects(first::redo);
            check(first.value("/value").getAsInt() == 2, "failed redo is atomic too");
        } finally { cleanup(workspace.directory()); }
    }

    private static void historyProtectsAllAffectedDrafts() throws IOException {
        var workspace = workspace();
        try {
            var owner = document("owner"); workspace.add(owner);
            String path = "data/test/locales/fr_fr.json";
            workspace.editWithLocales("Create wording", owner, value(2), Map.of(path, value(3)));
            var locale = workspace.documents().stream().filter(document -> document.resourcePath().equals(path)).findFirst().orElseThrow();
            var buffers = new EditorDocumentBuffers();
            buffers.fields(locale).update("/value", locale.value("/value"), "unfinished");
            rejects(() -> EditorOperations.history(owner, false, buffers::pending));
            check(workspace.documents().contains(locale) && owner.value("/value").getAsInt() == 2, "pending fields in another member block undo before any mutation");
            buffers.fields(locale).clear(); buffers.raw(locale, "{unfinished");
            rejects(() -> EditorOperations.history(owner, false, buffers::pending));
            buffers.discardRaw(locale);
            check(EditorOperations.history(owner, false, buffers::pending) && !workspace.documents().contains(locale), "resolved buffers allow creation undo");
            check(EditorOperations.history(owner, true, buffers::pending) && workspace.documents().contains(locale), "redo can reinsert a file deliberately removed by undo");
        } finally { cleanup(workspace.directory()); }
    }

    private static void missingDraftsRecoverIndependently() throws IOException {
        var workspace = workspace();
        try {
            var first = document("first"); var missing = document("missing");
            workspace.add(first); workspace.add(missing);
            var buffers = new EditorDocumentBuffers();
            buffers.fields(first).update("/value", first.value("/value"), "7");
            buffers.raw(missing, "{unfinished");
            workspace.remove(missing);
            workspace.saveRecovery(buffers.snapshot());
            var recovered = workspace.recover();
            var restored = new EditorDocumentBuffers(); restored.restore(recovered, recovered.recoveryBuffers());
            check(restored.orphanedPaths().equals(List.of(missing.resourcePath())), "missing input is retained separately");
            check(restored.applyValid(recovered).containsKey(missing.resourcePath()) && recovered.documents().getFirst().value("/value").getAsInt() == 7,
                    "an orphan does not prevent valid drafts from applying");
            var snapshot = restored.snapshot();
            var again = new EditorDocumentBuffers(); again.restore(recovered, snapshot);
            check(again.snapshot().equals(snapshot), "orphaned input survives subsequent recovery writes");
            recovered.restoreDeleted(0);
            var replacement = recovered.documents().stream().filter(document -> document.resourcePath().equals(missing.resourcePath())).findFirst().orElseThrow();
            again.raw(replacement, "{new unfinished");
            check(again.snapshot().getAsJsonObject("_orphaned").has(missing.resourcePath()), "a new draft at the same path cannot overwrite retained input");
            rejects(() -> again.reattach(recovered, missing.resourcePath()));
            again.discardRaw(replacement); again.reattach(recovered, missing.resourcePath());
            check(again.orphanedPaths().isEmpty() && again.raw(replacement).equals("{unfinished"), "restoring a file allows explicit reattachment of its exact input");
        } finally { cleanup(workspace.directory()); }
    }

    private static void commandsRunInOrderAndReportFailures() {
        var failures = new ArrayList<Exception>(); var events = new ArrayList<String>();
        var operations = new EditorOperations(failures::add);
        operations.beginFrame();
        operations.submit(() -> events.add("first"));
        operations.submit(() -> { throw new IOException("disk failure"); });
        operations.submit(() -> events.add("last"));
        check(events.isEmpty(), "rendering defers commands");
        operations.endFrame();
        check(events.equals(List.of("first", "last")) && failures.size() == 1, "a reported command failure does not drop later commands");
        operations.submit(() -> events.add("immediate"));
        check(events.size() == 3, "commands outside rendering run immediately");
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("vr-editor-lifecycle")); }
    private static JsonObject value(int number) { var value = new JsonObject(); value.addProperty("value", number); return value; }
    private static AuthoringDocument document(String name) { return new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/custom/" + name + ".json", value(1)); }
    private static void rejects(EditorOperations.Action action) throws IOException {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Expected operation to be rejected");
    }
    private static void cleanup(Path root) throws IOException {
        try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
