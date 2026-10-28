package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AuthoringPackFolder {
    private final Path root;
    private long revision;
    long revision() { return revision; }
    private final Map<String, String> hashes = new LinkedHashMap<>();
    private final Map<String, String> accepted = new LinkedHashMap<>();
    private final Set<String> overwrite = new LinkedHashSet<>();

    AuthoringPackFolder(Path root, Map<String, byte[]> files) {
        this.root = root.toAbsolutePath().normalize();
        files.forEach((path, bytes) -> hashes.put(path, hash(bytes)));
    }

    AuthoringPackFolder(Path root, JsonObject state) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        try {
            if (!state.has("folder") || !state.has("hashes") || !state.get("hashes").isJsonObject())
                throw new IOException("Pack-folder state requires a folder and file fingerprints");
            if (!this.root.toString().equals(state.get("folder").getAsString()))
                throw new IOException("This cached workspace belongs to a different pack folder");
            for (var entry : state.getAsJsonObject("hashes").entrySet())
                hashes.put(sourcePath(entry.getKey()), checkedHash(entry.getValue().getAsString()));
            if (state.has("accepted")) for (var entry : state.getAsJsonObject("accepted").entrySet())
                accepted.put(sourcePath(entry.getKey()), checkedHash(entry.getValue().getAsString()));
            if (state.has("overwrite")) for (var entry : state.getAsJsonArray("overwrite"))
                overwrite.add(sourcePath(entry.getAsString()));
        } catch (IllegalArgumentException | IllegalStateException | ClassCastException exception) {
            throw new IOException("Invalid pack-folder workspace state", exception);
        }
    }

    private static String sourcePath(String path) {
        AuthoringWorkspace.safeRelativePath(path);
        if (!AuthoringWorkspace.importable(path)) throw new IllegalArgumentException("Unsupported pack path " + path);
        return path;
    }

    private static String checkedHash(String hash) {
        if (!hash.equals("absent") && !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid source fingerprint");
        return hash;
    }

    Path root() { return root; }
    boolean dirty() { return !accepted.isEmpty() || !overwrite.isEmpty(); }

    JsonObject snapshot() {
        JsonObject state = new JsonObject(), saved = new JsonObject(), resolved = new JsonObject();
        state.addProperty("folder", root.toString());
        hashes.forEach(saved::addProperty); accepted.forEach(resolved::addProperty);
        state.add("hashes", saved); state.add("accepted", resolved);
        var forced = new com.google.gson.JsonArray();
        overwrite.forEach(forced::add); state.add("overwrite", forced);
        return state;
    }

    List<AuthoringWorkspace.SourceChange> changes(Map<String, byte[]> disk) {
        Map<String, String> baseline;
        synchronized (hashes) { baseline = Map.copyOf(hashes); }
        var paths = new java.util.TreeSet<>(baseline.keySet());
        paths.addAll(disk.keySet());
        var changes = new ArrayList<AuthoringWorkspace.SourceChange>();
        for (String path : paths) {
            String current = hash(disk.get(path));
            String previous = baseline.getOrDefault(path, "absent");
            if (!previous.equals(current)) changes.add(new AuthoringWorkspace.SourceChange(path, current,
                    previous.equals("absent") ? "Added" : current.equals("absent") ? "Deleted" : "Modified", disk.get(path)));
        }
        return List.copyOf(changes);
    }

    byte[] verify(AuthoringWorkspace.SourceChange change) throws IOException {
        byte[] current = read(change.path());
        if (!hash(current).equals(change.fingerprint())) throw new IOException("The disk file changed again. Refresh the review: " + change.path());
        return current;
    }

    void accept(AuthoringWorkspace.SourceChange change, boolean useDisk, byte[] editor) {
        revision++;
        synchronized (hashes) {
            if (change.fingerprint().equals("absent")) hashes.remove(change.path());
            else hashes.put(change.path(), change.fingerprint());
        }
        if (useDisk) { accepted.put(change.path(), hash(editor)); overwrite.remove(change.path()); }
        else { overwrite.add(change.path()); accepted.remove(change.path()); }
    }

    Save save(AuthoringWorkspace workspace) throws IOException {
        Map<String, byte[]> disk = workspace.scanSource();
        var external = changes(disk);
        if (!external.isEmpty()) throw new IOException("Review " + external.size() + " external pack change(s) before saving: " + external.getFirst().path());
        var paths = new LinkedHashSet<>(workspace.changedFilePaths());
        paths.addAll(overwrite);
        var writes = new LinkedHashMap<String, byte[]>();
        var current = Set.copyOf(workspace.filePaths());
        for (String path : paths) {
            byte[] bytes = current.contains(path) ? workspace.readFile(path) : null;
            if (!overwrite.contains(path) && hash(bytes).equals(accepted.get(path))) continue;
            if (!java.util.Arrays.equals(bytes, disk.get(path))) writes.put(path, bytes);
        }
        for (String path : writes.keySet()) {
            Path destination = target(path);
            for (Path parent = destination.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent())
                if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("A file occupies the destination folder: " + parent);
            if (Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) throw new IOException("A folder occupies the destination file: " + path);
        }
        var written = new ArrayList<String>();
        try {
            for (var entry : writes.entrySet()) {
                if (!java.util.Arrays.equals(read(entry.getKey()), disk.get(entry.getKey())))
                    throw new IOException("The disk file changed while saving: " + entry.getKey());
                write(entry.getKey(), entry.getValue());
                written.add(entry.getKey());
            }
            for (String folder : workspace.folderPaths()) Files.createDirectories(target(folder));
        } catch (IOException failure) {
            restore(disk, writes, written, failure);
            throw failure;
        }
        return new Save(disk, writes);
    }

    final class Save {
        private final Map<String, byte[]> before, writes;
        private final Map<String, String> next = new LinkedHashMap<>(hashes);

        private Save(Map<String, byte[]> before, Map<String, byte[]> writes) {
            this.before = before; this.writes = writes;
            writes.forEach((path, bytes) -> { if (bytes == null) next.remove(path); else next.put(path, hash(bytes)); });
        }

        JsonObject state() {
            JsonObject state = new JsonObject(), files = new JsonObject();
            state.addProperty("folder", root.toString());
            next.forEach(files::addProperty); state.add("hashes", files);
            return state;
        }

        void commit() {
            revision++;
            synchronized (hashes) { hashes.clear(); hashes.putAll(next); }
            accepted.clear(); overwrite.clear();
        }
        void rollback(IOException failure) { restore(before, writes, List.copyOf(writes.keySet()), failure); }
    }

    private void restore(Map<String, byte[]> before, Map<String, byte[]> writes, List<String> written, IOException failure) {
        for (String path : written.reversed()) {
            try {
                if (!java.util.Arrays.equals(read(path), writes.get(path)))
                    throw new IOException("A newer external edit prevents restoring " + path);
                write(path, before.get(path));
            } catch (IOException restore) { failure.addSuppressed(restore); }
        }
    }

    private Path target(String path) throws IOException {
        Path destination = root.resolve(AuthoringWorkspace.safeRelativePath(path));
        AuthoringWorkspace.rejectSymlinks(destination);
        return destination;
    }

    private byte[] read(String path) throws IOException {
        Path file = target(path);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 32L * 1024 * 1024)
            throw new IOException("Source is not a pack file within the 32 MiB limit: " + path);
        return Files.readAllBytes(file);
    }

    private void write(String path, byte[] bytes) throws IOException {
        if (bytes == null) Files.deleteIfExists(target(path));
        else AuthoringWorkspace.atomicWrite(target(path), bytes);
    }

    private static String hash(byte[] bytes) {
        return bytes == null ? "absent" : com.google.common.hash.Hashing.sha256().hashBytes(bytes).toString();
    }
}
