package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.preview.PlaytestDrafts;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EditorTestSnapshot {
    private final Map<String, JsonObject> content;
    private final Map<String, String> supportingFiles;
    private AuthoringWorkspace owner;
    private List<AuthoringDocument> documents = List.of();
    private long[] revisions = new long[0];
    private Map<String, String> hashes = Map.of();
    private boolean currentContent, currentLogic;
    private static final String ASSOCIATION = "authoring-workspace.json";

    EditorTestSnapshot(Map<String, JsonObject> content, Map<String, String> supportingFiles) {
        var definitions = new LinkedHashMap<String, JsonObject>();
        content.forEach((path, json) -> definitions.put(path, json.deepCopy()));
        this.content = Map.copyOf(definitions);
        this.supportingFiles = Map.copyOf(supportingFiles);
    }

    Map<String, JsonObject> content() { return content; }

    static EditorTestSnapshot capture(AuthoringWorkspace workspace) {
        var content = new LinkedHashMap<String, JsonObject>();
        workspace.documents().forEach(document -> content.put(document.resourcePath(), document.json()));
        return new EditorTestSnapshot(content, workspace.supportingFileHashes());
    }

    boolean current(AuthoringWorkspace workspace, boolean logicOnly) {
        var latest = workspace.documents();
        var supporting = workspace.supportingFileHashes();
        boolean changed = owner != workspace || !latest.equals(documents) || hashes != supporting;
        if (!changed) for (int i = 0; i < latest.size(); i++) {
            if (revisions[i] != latest.get(i).revision()) { changed = true; break; }
        }
        if (changed) {
            owner = workspace;
            documents = latest;
            hashes = supporting;
            revisions = new long[latest.size()];
            for (int i = 0; i < latest.size(); i++) revisions[i] = latest.get(i).revision();
            currentContent = false;
            currentLogic = false;
            if (supportingFiles.equals(supporting)) {
                var draft = new LinkedHashMap<String, JsonObject>();
                latest.forEach(document -> draft.put(document.resourcePath(), document.json()));
                currentContent = content.equals(draft);
                currentLogic = currentContent || PlaytestDrafts.sameLogic(content, draft);
            }
        }
        return logicOnly ? currentLogic : currentContent;
    }

    static void checkAssociation(Path world, Path workspace) throws IOException {
        Path marker = world.resolve(ASSOCIATION);
        rejectLinks(marker);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS) && !belongsTo(readObject(marker, 4096), workspace))
            throw new IOException("Quest testing in this world belongs to another workspace");
    }

    static void remember(Path world, Path workspace) throws IOException {
        checkAssociation(world, workspace);
        Path marker = world.resolve(ASSOCIATION);
        if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return;
        JsonObject association = new JsonObject();
        association.addProperty("version", 1);
        association.addProperty("workspace", workspace.toAbsolutePath().normalize().toString());
        Path temporary = Files.createTempFile(world, ".vr-workspace-", ".tmp");
        try {
            Files.writeString(temporary, association.toString());
            try { Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, marker); }
        } finally { Files.deleteIfExists(temporary); }
    }

    static EditorTestSnapshot restore(Path world, Path workspace) throws IOException {
        Path marker = world.resolve(ASSOCIATION);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!belongsTo(readObject(marker, 4096), workspace)) return null;
        return readPack(world.resolve("datapacks/test_content"), workspace);
    }

    private static boolean belongsTo(JsonObject association, Path workspace) throws IOException {
        try {
            if (!association.has("version") || !association.get("version").isJsonPrimitive()
                    || !association.getAsJsonPrimitive("version").isNumber()
                    || association.get("version").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE) != 0
                    || !association.has("workspace") || !association.get("workspace").isJsonPrimitive()
                    || !association.getAsJsonPrimitive("workspace").isString()) throw new IOException("Invalid test world workspace association");
            Path owner = Path.of(association.get("workspace").getAsString());
            if (!owner.isAbsolute()) throw new IOException("Test world workspace path must be absolute");
            return owner.normalize().equals(workspace.toAbsolutePath().normalize());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("Invalid test world workspace association", exception);
        }
    }

    static EditorTestSnapshot readPack(Path pack, Path workspace) throws IOException {
        JsonObject manifest = readObject(pack.resolve(".vr-authoring.json"), 4 * 1024 * 1024);
        try {
            if (!manifest.has("workspace") || !manifest.get("workspace").isJsonPrimitive() || !manifest.getAsJsonPrimitive("workspace").isString()
                    || !workspace.toAbsolutePath().normalize().toString().equals(manifest.get("workspace").getAsString())
                    || !manifest.has("files") || !manifest.get("files").isJsonObject()) throw new IOException("The test pack belongs to another workspace or has an invalid manifest");
            var content = new LinkedHashMap<String, JsonObject>();
            var supporting = new LinkedHashMap<String, String>();
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            long total = 0;
            for (var entry : manifest.getAsJsonObject("files").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                    throw new IOException("Invalid test pack file fingerprint: " + entry.getKey());
                String path = AuthoringWorkspace.safeRelativePath(entry.getKey());
                byte[] bytes = readBytes(pack.resolve(path), 32 * 1024 * 1024);
                total += bytes.length;
                if (total > 256L * 1024 * 1024) throw new IOException("Test pack exceeds the authoring size limit");
                String hash = java.util.HexFormat.of().formatHex(digest.digest(bytes));
                if (!hash.equals(entry.getValue().getAsString())) throw new IOException("Test pack has an external edit: " + path);
                if (path.startsWith("data/") && path.endsWith(".json")) {
                    var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
                    if (json.isJsonObject()) { content.put(path, json.getAsJsonObject()); continue; }
                }
                supporting.put(path, hash);
            }
            if (!supporting.containsKey("pack.mcmeta")) throw new IOException("Test pack is missing pack.mcmeta");
            return new EditorTestSnapshot(content, supporting);
        } catch (com.google.gson.JsonParseException | IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("Invalid test pack snapshot", exception);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonObject readObject(Path file, int limit) throws IOException {
        try { return JsonParser.parseString(new String(readBytes(file, limit), StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (com.google.gson.JsonParseException | IllegalStateException exception) { throw new IOException("Invalid authoring file: " + file, exception); }
    }

    private static byte[] readBytes(Path file, int limit) throws IOException {
        rejectLinks(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Missing authoring file: " + file);
        try (var input = Files.newInputStream(file)) {
            byte[] bytes = input.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Authoring file exceeds its size limit: " + file);
            return bytes;
        }
    }

    private static void rejectLinks(Path file) throws IOException {
        for (Path path = file.toAbsolutePath(); path != null; path = path.getParent())
            if (Files.isSymbolicLink(path)) throw new IOException("Test world paths cannot traverse symbolic links: " + path);
    }
}
