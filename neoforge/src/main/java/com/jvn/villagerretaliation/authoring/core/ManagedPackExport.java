package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ManagedPackExport {
    private static final String MANIFEST = ".vr-authoring.json";
    private ManagedPackExport() { }

    public static Path export(AuthoringWorkspace workspace, Path destination) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        for (Path path = target; path != null; path = path.getParent())
            if (Files.isSymbolicLink(path)) throw new IOException("Export cannot traverse symbolic links: " + path);
        Map<String, String> previous = new LinkedHashMap<>();
        boolean existing = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (existing) {
            Path manifest = target.resolve(MANIFEST);
            if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifest) || Files.size(manifest) > 4 * 1024 * 1024)
                throw new IOException("This folder is not an editor-managed export. Choose a new folder.");
            try {
                JsonObject state = JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
                if (!state.has("workspace") || !state.get("workspace").isJsonPrimitive() || !state.has("files") || !state.get("files").isJsonObject())
                    throw new IOException("Invalid managed export manifest");
                if (!workspace.directory().toString().equals(state.get("workspace").getAsString())) throw new IOException("This export belongs to another workspace");
                for (var entry : state.getAsJsonObject("files").entrySet()) previous.put(AuthoringWorkspace.safeRelativePath(entry.getKey()), entry.getValue().getAsString());
            } catch (com.google.gson.JsonParseException | IllegalArgumentException | IllegalStateException exception) {
                throw new IOException("Invalid managed export manifest", exception);
            }
            for (var entry : previous.entrySet()) {
                Path file = target.resolve(entry.getKey());
                rejectLinks(target, file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !hash(file).equals(entry.getValue()))
                    throw new IOException("Export contains an external edit: " + entry.getKey() + ". Choose a new folder to preserve it.");
            }
        }
        if (target.getParent() == null) throw new IOException("Choose an export folder below a parent directory");
        Files.createDirectories(target.getParent());
        Path staging = Files.createTempDirectory(target.getParent(), ".vr-managed-");
        Path generated = staging.resolve("pack");
        Path backup = null;
        try {
            workspace.exportPack(generated);
            JsonObject manifest = new JsonObject();
            manifest.addProperty("workspace", workspace.directory().toString());
            JsonObject files = new JsonObject();
            try (var entries = Files.walk(generated)) {
                for (Path file : entries.filter(Files::isRegularFile).toList()) {
                    String name = generated.relativize(file).toString().replace('\\', '/');
                    if (existing && Files.exists(target.resolve(name), LinkOption.NOFOLLOW_LINKS) && !previous.containsKey(name))
                        throw new IOException("Export would overwrite an unmanaged file: " + name);
                    files.addProperty(name, hash(file));
                }
            }
            manifest.add("files", files);
            if (existing) {
                try (var entries = Files.walk(target)) {
                    for (Path file : entries.toList()) {
                        if (file.equals(target)) continue;
                        rejectLinks(target, file);
                        String name = target.relativize(file).toString().replace('\\', '/');
                        if (Files.isDirectory(file)) continue;
                        if (name.equals(MANIFEST) || previous.containsKey(name)) continue;
                        Path copy = generated.resolve(AuthoringWorkspace.safeRelativePath(name));
                        Files.createDirectories(copy.getParent());
                        Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                }
            }
            Files.writeString(generated.resolve(MANIFEST), AuthoringDocument.JSON.toJson(manifest));
            if (existing) {
                backup = target.resolveSibling(target.getFileName() + ".backup-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().substring(0, 8));
                move(target, backup);
            }
            try {
                move(generated, target);
            } catch (IOException exception) {
                if (backup != null && !Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    try { move(backup, target); }
                    catch (IOException restore) { exception.addSuppressed(restore); }
                }
                throw exception;
            }
            return backup;
        } finally {
            if (Files.exists(staging)) try (var entries = Files.walk(staging)) {
                for (Path file : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }

    public static void restore(Path destination, Path backup) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path previous = backup.toAbsolutePath().normalize();
        if (!java.util.Objects.equals(target.getParent(), previous.getParent())
                || !previous.getFileName().toString().startsWith(target.getFileName() + ".backup-")
                || !Files.isRegularFile(previous.resolve(MANIFEST), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("The backup does not belong to this managed export");
        rejectLinks(target.getParent(), target);
        rejectLinks(previous.getParent(), previous);
        Path rejected = target.resolveSibling(target.getFileName() + ".rejected-" + java.util.UUID.randomUUID());
        move(target, rejected);
        try { move(previous, target); }
        catch (IOException failure) {
            try { move(rejected, target); } catch (IOException recovery) { failure.addSuppressed(recovery); }
            throw failure;
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        for (int attempt = 0; ; attempt++) {
            try {
                Files.move(source, destination);
                return;
            } catch (AccessDeniedException exception) {
                if (attempt == 5) throw exception;
                try { Thread.sleep(100L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting to move the export folder", interrupted);
                }
            }
        }
    }

    private static void rejectLinks(Path root, Path file) throws IOException {
        for (Path path = file; path != null && path.startsWith(root); path = path.getParent())
            if (Files.isSymbolicLink(path)) throw new IOException("Export cannot traverse symbolic links: " + path);
    }

    private static String hash(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) != -1;) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
