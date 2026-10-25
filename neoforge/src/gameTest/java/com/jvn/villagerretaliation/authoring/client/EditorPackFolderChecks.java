package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class EditorPackFolderChecks {
    private static final String FIRST = "data/test/custom/first.json";
    private static final String SECOND = "data/test/custom/second.json";
    private EditorPackFolderChecks() { }

    static int run() throws IOException {
        savesOnlyEditedFilesAndKeepsMetadataOutsideThePack();
        externalChangesBlockWritesUntilReviewed();
        staleConflictDecisionsCannotOverwriteNewerChanges();
        externalAdditionsAndDeletionsStayScopedToOneFile();
        recoveryKeepsTheSourceAndGenericWorkspacesStayDetached();
        filesystemCollisionsFailBeforeWritingAnyFile();
        failedMetadataWritesRestorePackFiles();
        sourceStampsTrackOnlyPackResources();
        return 8;
    }

    private static void savesOnlyEditedFilesAndKeepsMetadataOutsideThePack() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            String untouched = Files.readString(pack.resolve(SECOND)), metadata = Files.readString(pack.resolve("pack.mcmeta"));
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            check(workspace.editsPackFolder() && workspace.location().equals(pack), "opening a pack attaches its source directory");
            check(!workspace.directory().startsWith(pack), "editor metadata stays outside the pack");
            workspace.writeTextFile(FIRST, "{\"value\":2}");
            workspace.save();
            check(value(pack.resolve(FIRST)) == 2, "Save changes the original pack file");
            check(Files.readString(pack.resolve(SECOND)).equals(untouched), "untouched JSON keeps its original formatting");
            check(Files.readString(pack.resolve("pack.mcmeta")).equals(metadata), "untouched metadata keeps its original bytes");
            check(Files.readString(pack.resolve("notes.txt")).equals("Keep this"), "unmanaged files are preserved");
            check(!Files.exists(pack.resolve("workspace.json")) && !workspace.dirty(), "source folders are not converted into draft folders");
            var reopened = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            check(reopened.sourceChanges(reopened.scanSource()).isEmpty(), "saved fingerprints survive reopening");
            check(reopened.documents().stream().filter(doc -> doc.resourcePath().equals(FIRST)).findFirst().orElseThrow().value("/value").getAsInt() == 2, "saved content survives reopening");
        } finally { cleanup(root); }
    }

    private static void externalChangesBlockWritesUntilReviewed() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            workspace.writeTextFile(FIRST, "{\"value\":3}");
            String external = "{ \"value\" : 20 }\n";
            Files.writeString(pack.resolve(SECOND), external);
            expectFailure(workspace::save, "saving cannot silently overwrite an external edit");
            check(value(pack.resolve(FIRST)) == 1, "external changes block the whole save before any writes");
            var change = workspace.sourceChanges(workspace.scanSource()).getFirst();
            check(change.path().equals(SECOND) && !workspace.sourceConflict(SECOND), "independent disk changes are identified");
            workspace.resolveSourceChange(change, true);
            workspace.save();
            check(value(pack.resolve(FIRST)) == 3, "local edits save after the review");
            check(Files.readString(pack.resolve(SECOND)).equals(external), "accepting disk changes preserves their exact bytes");
            check(!workspace.dirty(), "accepted disk changes become the saved baseline");
        } finally { cleanup(root); }
    }

    private static void staleConflictDecisionsCannotOverwriteNewerChanges() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            workspace.writeTextFile(FIRST, "{\"value\":3}");
            Files.writeString(pack.resolve(FIRST), "{\"value\":2}");
            var conflict = workspace.sourceChanges(workspace.scanSource()).getFirst();
            check(workspace.sourceConflict(FIRST), "the review identifies edits to the same file");
            Files.writeString(pack.resolve(FIRST), "{\"value\":4}");
            expectFailure(() -> workspace.resolveSourceChange(conflict, false), "a stale review cannot authorize overwriting a newer edit");
            workspace.resolveSourceChange(workspace.sourceChanges(workspace.scanSource()).getFirst(), false);
            Files.writeString(pack.resolve(FIRST), "{\"value\":5}");
            expectFailure(workspace::save, "an acknowledged version does not authorize overwriting future changes");
            workspace.resolveSourceChange(workspace.sourceChanges(workspace.scanSource()).getFirst(), false);
            workspace.save();
            check(value(pack.resolve(FIRST)) == 3, "explicitly keeping the editor version writes that version");
            Files.writeString(pack.resolve(FIRST), "{\"value\":6}");
            workspace.resolveSourceChange(workspace.sourceChanges(workspace.scanSource()).getFirst(), false);
            workspace.save();
            check(value(pack.resolve(FIRST)) == 3, "keeping an otherwise unchanged editor file still restores it to disk");
        } finally { cleanup(root); }
    }

    private static void externalAdditionsAndDeletionsStayScopedToOneFile() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            Files.delete(pack.resolve(FIRST));
            Files.writeString(pack.resolve("data/test/custom/third.json"), "{\"value\":30}");
            for (var change : workspace.sourceChanges(workspace.scanSource())) workspace.resolveSourceChange(change, true);
            check(!workspace.filePaths().contains(FIRST) && workspace.filePaths().contains(SECOND), "disk deletion removes only the reviewed file");
            check(workspace.filePaths().contains("data/test/custom/third.json"), "new disk resources enter the workspace");
            var second = workspace.documents().stream().filter(doc -> doc.resourcePath().equals(SECOND)).findFirst().orElseThrow();
            workspace.remove(second);
            workspace.importResources(Map.of("data/test/custom/fourth.json", "{\"value\":40}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            workspace.save();
            check(!Files.exists(pack.resolve(SECOND)) && value(pack.resolve("data/test/custom/fourth.json")) == 40, "local additions and deletions update the pack");
            Files.delete(pack.resolve("pack.mcmeta"));
            check(AuthoringWorkspace.hasPackWorkspace(pack, root.resolve("cache")), "known packs remain identifiable when their metadata is deleted");
            var reopened = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            var missing = reopened.sourceChanges(reopened.scanSource()).getFirst();
            expectFailure(() -> reopened.resolveSourceChange(missing, true), "review cannot remove required pack metadata");
            reopened.resolveSourceChange(missing, false); reopened.save();
            check(Files.isRegularFile(pack.resolve("pack.mcmeta")), "keeping metadata restores the deleted file");
        } finally { cleanup(root); }
    }

    private static void recoveryKeepsTheSourceAndGenericWorkspacesStayDetached() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            workspace.writeTextFile(FIRST, "{\"value\":7}");
            JsonObject buffers = new JsonObject(); buffers.addProperty("raw", "{unfinished");
            workspace.saveRecovery(buffers);
            var recovery = workspace.recover();
            check(recovery.editsPackFolder() && recovery.location().equals(pack), "recovery retains the explicitly opened source");
            check(recovery.recoveryBuffers().get("raw").getAsString().equals("{unfinished"), "source recovery retains uncommitted text buffers");
            recovery.save();
            check(value(pack.resolve(FIRST)) == 7 && !recovery.recoveryAvailable(), "recovered edits save to the source and clear recovery");
            expectFailure(() -> AuthoringWorkspace.open(workspace.directory()), "linked caches must be opened through their explicit source folder");
            check(value(pack.resolve(FIRST)) == 7, "opening workspace metadata alone never authorizes source writes");
            expectFailure(() -> AuthoringWorkspace.openPack(pack, pack.resolve("cache")), "pack metadata cannot be stored inside the source");
            expectFailure(() -> new AuthoringWorkspace.SourceChange("../outside", "absent", "Deleted", null), "source review paths cannot escape the pack");
            expectFailure(() -> new AuthoringWorkspace.SourceChange("notes.txt", "absent", "Deleted", null), "source review cannot affect unmanaged root files");
        } finally { cleanup(root); }
    }

    private static void filesystemCollisionsFailBeforeWritingAnyFile() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            workspace.writeTextFile(FIRST, "{\"value\":9}");
            String occupied = "data/test/custom/occupied.json";
            workspace.importResources(Map.of(occupied, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            Files.createDirectories(pack.resolve(occupied));
            expectFailure(workspace::save, "a directory cannot be overwritten by a source file");
            check(value(pack.resolve(FIRST)) == 1 && Files.isDirectory(pack.resolve(occupied)), "preflight failure preserves every existing path");
            check(workspace.dirty(), "failed saves retain pending edits");
        } finally { cleanup(root); }
    }

    private static void failedMetadataWritesRestorePackFiles() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            String original = Files.readString(pack.resolve(FIRST));
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            workspace.writeTextFile(FIRST, "{\"value\":11}");
            Path metadata = workspace.directory().resolve("workspace.json");
            Files.delete(metadata); Files.createDirectory(metadata);
            expectFailure(workspace::save, "metadata failure cannot leave a partially saved pack");
            check(Files.readString(pack.resolve(FIRST)).equals(original), "rollback restores the exact previous source bytes");
            check(workspace.dirty() && workspace.sourceChanges(workspace.scanSource()).isEmpty(), "failed saves keep edits and the original source baseline");
            Files.delete(metadata);
            workspace.save();
            check(value(pack.resolve(FIRST)) == 11, "the same edits can be saved after the filesystem failure is resolved");
        } finally { cleanup(root); }
    }

    private static void sourceStampsTrackOnlyPackResources() throws IOException {
        Path root = fixture();
        try {
            Path pack = root.resolve("pack");
            var workspace = AuthoringWorkspace.openPack(pack, root.resolve("cache"));
            String initial = workspace.sourceStamp();
            Files.writeString(pack.resolve("notes.txt"), "Unrelated changes");
            check(workspace.sourceStamp().equals(initial), "unmanaged files do not trigger pack reloads");
            var time = Files.getLastModifiedTime(pack.resolve(FIRST));
            Files.writeString(pack.resolve(FIRST), "{\"value\":2}\n");
            Files.setLastModifiedTime(pack.resolve(FIRST), java.nio.file.attribute.FileTime.fromMillis(time.toMillis() + 1000));
            check(!workspace.sourceStamp().equals(initial), "same-size file edits change the metadata stamp");
            String edited = workspace.sourceStamp();
            Files.delete(pack.resolve(FIRST));
            check(!workspace.sourceStamp().equals(edited), "deletions change the metadata stamp");
            check(workspace.sourceChanges(workspace.scanSource()).size() == 1, "full checks still compare source content");
        } finally { cleanup(root); }
    }

    private static Path fixture() throws IOException {
        Path root = Files.createTempDirectory("vr-pack-folder-"), pack = root.resolve("pack");
        Files.createDirectories(pack.resolve("data/test/custom"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Test\"}}\n");
        Files.writeString(pack.resolve(FIRST), "{\"value\":1}\n");
        Files.writeString(pack.resolve(SECOND), "{  \"value\" : 10  }\n");
        Files.writeString(pack.resolve("notes.txt"), "Keep this");
        return root;
    }

    private static int value(Path file) throws IOException { return JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("value").getAsInt(); }
    private static void cleanup(Path root) throws IOException {
        try (var files = Files.walk(root)) { for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(file); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expectFailure(Action action, String message) throws IOException {
        try { action.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
    private interface Action { void run() throws IOException; }
}
