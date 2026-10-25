package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class EditorBufferChecks {
    private static final String FIRST = "data/test/custom/first.json";
    private static final String SECOND = "data/test/custom/second.json";
    private static final String THIRD = "data/test/custom/third.json";
    private EditorBufferChecks() { }

    static int run() throws IOException {
        validDraftsSaveIndependentlyOfInvalidJson();
        staleRawDraftsCannotOverwriteNewerEdits();
        allDocumentDraftsSurviveRecovery();
        fieldsRemainSharedAcrossEditingContexts();
        movedDocumentsKeepTheirDrafts();
        return 5;
    }

    private static void validDraftsSaveIndependentlyOfInvalidJson() throws IOException {
        var workspace = fixture();
        try {
            AuthoringDocument first = document(workspace, FIRST), second = document(workspace, SECOND), third = document(workspace, THIRD);
            var buffers = new EditorDocumentBuffers();
            buffers.raw(first, "{unfinished");
            buffers.fields(second).update("/value", second.value("/value"), "7");
            buffers.raw(third, "{\"value\":9}");
            var issues = buffers.applyValid(workspace);
            workspace.save();
            check(issues.keySet().equals(java.util.Set.of(FIRST)), "invalid JSON reports only its own file");
            check(first.value("/value").getAsInt() == 1 && buffers.raw(first).equals("{unfinished"), "invalid JSON stays out of the saved model and remains editable");
            check(second.value("/value").getAsInt() == 7 && third.value("/value").getAsInt() == 9, "valid fields and full JSON save independently");
            check(!buffers.pending(second) && !buffers.pending(third) && buffers.pending(first), "only unresolved drafts remain pending");
            check(second.canUndo() && third.canUndo(), "saving drafts retains ordinary edit history");
        } finally { cleanup(workspace.directory()); }
    }

    private static void staleRawDraftsCannotOverwriteNewerEdits() throws IOException {
        var workspace = fixture();
        try {
            var first = document(workspace, FIRST);
            var buffers = new EditorDocumentBuffers();
            buffers.raw(first, "{\"value\":3}");
            first.edit("Other edit", root -> root.addProperty("value", 8));
            var issues = buffers.applyValid(workspace);
            check(issues.containsKey(FIRST) && first.value("/value").getAsInt() == 8, "newer content blocks stale raw drafts");
            buffers.raw(first, "{\"value\":4}");
            check(buffers.applyValid(workspace).containsKey(FIRST), "further typing cannot silently reset the original baseline");
            buffers.discardRaw(first);
            buffers.raw(first, "{\"value\":5}");
            check(buffers.applyValid(workspace).isEmpty() && first.value("/value").getAsInt() == 5, "discarding and reopening starts a new baseline");
        } finally { cleanup(workspace.directory()); }
    }

    private static void allDocumentDraftsSurviveRecovery() throws IOException {
        var workspace = fixture();
        try {
            var buffers = new EditorDocumentBuffers();
            buffers.raw(document(workspace, FIRST), "{first");
            buffers.raw(document(workspace, SECOND), "{second");
            var third = document(workspace, THIRD);
            buffers.fields(third).update("/value", third.value("/value"), "not a number");
            JsonObject state = buffers.snapshot();
            workspace.saveRecovery(state);
            var recovered = workspace.recover();
            var restored = new EditorDocumentBuffers();
            restored.restore(recovered, recovered.recoveryBuffers());
            check(restored.snapshot().equals(state), "recovery retains every file's exact raw and field input");
            check(restored.applyValid(recovered).size() == 3, "recovered invalid drafts remain separate and unapplied");
            recovered.save();
            check(restored.pending(), "saving valid model contents does not discard invalid editor drafts");
        } finally { cleanup(workspace.directory()); }
    }

    private static void fieldsRemainSharedAcrossEditingContexts() throws IOException {
        var workspace = fixture();
        try {
            var buffers = new EditorDocumentBuffers();
            var first = document(workspace, FIRST);
            EditorFieldEdits inline = buffers.fields(first), details = buffers.fields(first);
            inline.update("/value", first.value("/value"), "12");
            check(details == inline && details.text("/value", "1").equals("12"), "inline, properties and translation views share one file draft");
            first.edit("Newer value", root -> root.addProperty("value", 20));
            details.update("/value", first.value("/value"), "12");
            check(buffers.applyValid(workspace).containsKey(FIRST), "field drafts retain their stale-edit checks");
            check(details.text("/value", "20").equals("12"), "stale text remains available for correction");
        } finally { cleanup(workspace.directory()); }
    }

    private static void movedDocumentsKeepTheirDrafts() throws IOException {
        var workspace = fixture();
        try {
            var first = document(workspace, FIRST);
            var buffers = new EditorDocumentBuffers();
            buffers.raw(first, "{\"value\":42}");
            String renamed = "data/test/custom/renamed.json";
            workspace.applyFileMove(workspace.previewFileMove(FIRST, renamed));
            check(buffers.snapshot().has(renamed) && !buffers.snapshot().has(FIRST), "document drafts follow resource path changes");
            check(buffers.applyValid(workspace).isEmpty() && first.value("/value").getAsInt() == 42, "renamed drafts apply to the same document");
        } finally { cleanup(workspace.directory()); }
    }

    static void supportingFilesKeepIndependentDraftsAndBaselines() throws IOException {
        var workspace = fixture();
        try {
            String invalid = "assets/test/lang/en_us.json", valid = "data/test/functions/first.mcfunction";
            workspace.importResources(Map.of(invalid, "{}".getBytes(StandardCharsets.UTF_8), valid, "say before\n".getBytes(StandardCharsets.UTF_8)));
            JsonObject files = new JsonObject();
            files.add(invalid, fileDraft(workspace, invalid, "{broken"));
            files.add(valid, fileDraft(workspace, valid, "say after\n"));
            JsonObject state = new JsonObject(); state.add("files", files); state.addProperty("selected", invalid);
            var editor = new EditorPackFiles();
            editor.recover(workspace, state);
            editor.select(valid);
            check(editor.anyPending() && editor.pending(invalid), "switching supporting files keeps earlier input");
            var issues = editor.applyValid(workspace);
            check(issues.keySet().equals(java.util.Set.of(invalid)), "valid supporting files save alongside invalid JSON");
            check(new String(workspace.readFile(valid), StandardCharsets.UTF_8).equals("say after\n"), "the valid supporting file is applied");
            var saved = editor.recovery();
            var restored = new EditorPackFiles(); restored.recover(workspace, saved);
            check(restored.anyPending() && restored.recovery().equals(saved), "supporting-file recovery includes all drafts and original bytes");
            workspace.writeTextFile(invalid, "{\"new\":true}");
            check(restored.applyValid(workspace).containsKey(invalid), "restored file drafts still reject newer source content");
            editor.close(); restored.close();
        } finally { cleanup(workspace.directory()); }
    }

    private static JsonObject fileDraft(AuthoringWorkspace workspace, String path, String text) throws IOException {
        byte[] bytes = workspace.readFile(path);
        JsonObject result = new JsonObject();
        result.addProperty("baseline", java.util.Base64.getEncoder().encodeToString(bytes));
        result.addProperty("original", new String(bytes, StandardCharsets.UTF_8));
        result.addProperty("text", text);
        return result;
    }

    private static AuthoringWorkspace fixture() throws IOException {
        var workspace = AuthoringWorkspace.create(Files.createTempDirectory("vr-file-buffers-"));
        workspace.importResources(Map.of(FIRST, "{\"value\":1}".getBytes(StandardCharsets.UTF_8),
                SECOND, "{\"value\":2}".getBytes(StandardCharsets.UTF_8), THIRD, "{\"value\":3}".getBytes(StandardCharsets.UTF_8)));
        workspace.save(); return workspace;
    }

    private static AuthoringDocument document(AuthoringWorkspace workspace, String path) {
        return workspace.documents().stream().filter(document -> document.resourcePath().equals(path)).findFirst().orElseThrow();
    }

    private static void cleanup(Path root) throws IOException {
        try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
