package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class EditorChangeCacheChecks {
    static int run() throws IOException {
        changesFollowHistoryMovesAndSaveBaselines();
        reviewReusesFilesAndFieldDiffs();
        cachedBytesRemainDefensive();
        benchmarkIdleChangesAndRecovery();
        return 3;
    }

    private static void changesFollowHistoryMovesAndSaveBaselines() throws IOException {
        var workspace = workspace();
        try {
            var document = document("original"); workspace.add(document); workspace.save();
            String original = document.resourcePath(), moved = "data/test/custom/moved.json";
            check(workspace.changedFilePaths().isEmpty(), "saved files have no changes");
            document.edit("Edit value", root -> root.addProperty("value", 2));
            var paths = workspace.changedFilePaths();
            check(paths.equals(List.of(original)) && paths == workspace.changedFilePaths(), "unchanged comparisons reuse the cached paths");
            document.undo(); check(workspace.changedFilePaths().isEmpty(), "undo returns to saved content");
            document.redo(); check(workspace.changedFilePaths().equals(paths), "redo invalidates cached paths");
            workspace.applyFileMove(workspace.previewFileMove(original, moved));
            check(workspace.changedFilePaths().containsAll(List.of(original, moved)), "moves preserve old and new path comparisons");
            workspace.save(); check(workspace.changedFilePaths().isEmpty() && workspace.savedFile(original) == null, "save replaces the indexed baseline paths");
            document.editMetadata("Layout", root -> root.addProperty("x", 5));
            check(workspace.changedFilePaths().isEmpty(), "layout edits do not become exported file changes");
            workspace.remove(document); check(workspace.changedFilePaths().equals(List.of(moved)), "deletion invalidates cached comparisons");
            workspace.restoreDeleted(0); check(workspace.changedFilePaths().isEmpty(), "restoring equal content clears the file difference");
            var restored = workspace.documents().getFirst(); restored.edit("Recovery edit", root -> root.addProperty("value", 3));
            workspace.saveRecovery(new JsonObject());
            var recovered = workspace.recover();
            check(recovered.changedFilePaths().equals(List.of(moved)), "recovered comparisons use the saved workspace baseline");
            String text = "data/test/functions/example.mcfunction";
            recovered.importResources(Map.of(text, "say hello".getBytes(StandardCharsets.UTF_8)));
            check(recovered.changedFilePaths().contains(text), "supporting file creation invalidates comparisons");
        } finally { cleanup(workspace.directory()); }
    }

    private static void reviewReusesFilesAndFieldDiffs() throws IOException {
        var workspace = workspace();
        try {
            var first = document("first"); var second = document("second"); workspace.add(first); workspace.add(second); workspace.save();
            first.edit("First edit", root -> root.addProperty("value", 2));
            var review = new EditorChanges(); var files = review.files(workspace); var file = files.getFirst(); var fields = file.changes();
            check(files == review.files(workspace) && fields == file.changes() && file.rows() == file.rows(), "idle review reuses file snapshots, field diffs and rendered wording");
            second.edit("Other file", root -> root.addProperty("value", 3));
            var refreshed = review.files(workspace);
            check(refreshed.stream().filter(value -> value.path.equals(first.resourcePath())).findFirst().orElseThrow() == file,
                    "editing another document preserves unchanged file diffs");
            first.edit("New first value", root -> root.addProperty("value", 4));
            var updated = review.files(workspace).stream().filter(value -> value.path.equals(first.resourcePath())).findFirst().orElseThrow();
            check(updated != file && updated.changes().getFirst().after().getAsInt() == 4, "content edits refresh the affected diff");
            workspace.save(); check(review.files(workspace).isEmpty(), "saved baselines invalidate review snapshots");
            var pack = workspace.packMetadata(); pack.getAsJsonObject("pack").addProperty("description", "New description"); workspace.setPackMetadata(pack);
            check(review.files(workspace).getFirst().path.equals("pack.mcmeta"), "pack settings invalidate the review");
        } finally { cleanup(workspace.directory()); }
    }

    private static void cachedBytesRemainDefensive() throws IOException {
        var workspace = workspace();
        try {
            var document = document("copies"); workspace.add(document); workspace.save();
            byte[] current = workspace.readFile(document.resourcePath()), saved = workspace.savedFile(document.resourcePath());
            current[0] = '!'; saved[0] = '!';
            check(workspace.readFile(document.resourcePath())[0] == '{' && workspace.savedFile(document.resourcePath())[0] == '{', "public file reads cannot mutate cached bytes");
            document.edit("Update", root -> root.addProperty("value", 5));
            check(new String(workspace.savedFile(document.resourcePath()), StandardCharsets.UTF_8).contains("1"), "current edits cannot mutate saved byte snapshots");
            check(workspace.changedFilePaths().size() == 1, "defensive reads preserve comparison accuracy");
        } finally { cleanup(workspace.directory()); }
    }

    private static void benchmarkIdleChangesAndRecovery() throws IOException {
        var workspace = workspace();
        try {
            for (int i = 0; i < 1000; i++) workspace.add(document("file_" + i));
            workspace.save(); var review = new EditorChanges();
            for (int i = 0; i < 100; i++) { workspace.changedFilePaths(); review.files(workspace); }
            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) { workspace.changedFilePaths(); review.files(workspace); }
            System.out.printf("Editor cached change paths and review (1000 files): %.6f ms per idle check%n", (System.nanoTime() - start) / 1e9);
            workspace.importResources(Map.of("assets/test/textures/large.png", new byte[16 * 1024 * 1024]));
            var recovery = new EditorRecovery(workspace.directory().resolve("sessions.json"), Runnable::run);
            recovery.write(workspace, new JsonObject(), false, new JsonObject(), true);
            start = System.nanoTime();
            for (int i = 0; i < 100; i++) recovery.write(workspace, new JsonObject(), false, new JsonObject(), true);
            System.out.printf("Editor unchanged recovery (16 MiB asset): %.6f ms per check%n", (System.nanoTime() - start) / 1e6 / 100);
        } finally { cleanup(workspace.directory()); }
    }

    private static AuthoringWorkspace workspace() throws IOException { return AuthoringWorkspace.create(Files.createTempDirectory("vr-editor-change-cache")); }
    private static AuthoringDocument document(String name) {
        var json = new JsonObject(); json.addProperty("value", 1);
        return new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/custom/" + name + ".json", json);
    }
    private static void cleanup(Path root) throws IOException {
        try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
