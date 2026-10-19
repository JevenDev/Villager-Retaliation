package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

final class EditorSession {
    private final Path path;
    private JsonObject workspaces;

    EditorSession(Path path) { this.path = path; }

    JsonObject read(AuthoringWorkspace workspace) throws IOException {
        load();
        String key = workspace.directory().toAbsolutePath().normalize().toString();
        if (!workspaces.has(key)) return new JsonObject();
        if (!workspaces.get(key).isJsonObject()) throw new IOException("Saved workspace session must be an object");
        return workspaces.getAsJsonObject(key).deepCopy();
    }

    private void load() throws IOException {
        if (workspaces != null) return;
        try { workspaces = Files.exists(path) ? JsonParser.parseString(Files.readString(path)).getAsJsonObject() : new JsonObject(); }
        catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new IOException("Cannot read editor sessions: " + exception.getMessage(), exception);
        }
    }

    void save(AuthoringWorkspace workspace, JsonObject session) throws IOException {
        load();
        String key = workspace.directory().toAbsolutePath().normalize().toString();
        if (session.equals(workspaces.get(key))) return;
        JsonObject changed = workspaces.deepCopy();
        changed.remove(key); changed.add(key, session.deepCopy());
        while (changed.size() > 8) changed.remove(changed.keySet().iterator().next());
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "authoring-session-", ".tmp");
        try {
            Files.writeString(temporary, changed.toString());
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            workspaces = changed;
        } finally { Files.deleteIfExists(temporary); }
    }

    static JsonObject snapshot(AuthoringWorkspace workspace, EditorNavigation navigation, EditorOpenFiles files,
                               EditorNavigation.Location current, boolean snapGrid) {
        JsonObject result = new JsonObject();
        result.add("current", location(current));
        JsonArray positions = new JsonArray();
        navigation.rememberedLocations(workspace).forEach(value -> positions.add(location(value)));
        result.add("positions", positions);
        JsonArray recent = new JsonArray(); navigation.recentFiles(workspace).forEach(recent::add);
        result.add("recent", recent); result.add("open_files", files.snapshot(workspace));
        result.addProperty("snap_grid", snapGrid);
        return result;
    }

    private static JsonObject location(EditorNavigation.Location value) {
        JsonObject result = new JsonObject();
        result.addProperty("path", value.currentPath()); result.addProperty("pointer", value.pointer());
        result.addProperty("view", value.view() == 3 ? 0 : value.view()); result.addProperty("files_mode", value.filesMode());
        result.addProperty("pan_x", value.panX()); result.addProperty("pan_y", value.panY()); result.addProperty("zoom", value.zoom());
        result.addProperty("scroll", value.scroll()); result.addProperty("details_scroll", value.detailsScroll());
        return result;
    }

    static EditorNavigation.Location location(AuthoringWorkspace workspace, JsonObject saved) {
        if (!saved.has("path") || !saved.get("path").isJsonPrimitive() || !saved.getAsJsonPrimitive("path").isString())
            throw new IllegalArgumentException("Saved editor position must contain a file path");
        String path = saved.get("path").getAsString();
        var document = workspace.documents().stream().filter(candidate -> candidate.resourcePath().equals(path)).findFirst().orElse(null);
        int view = saved.has("view") ? saved.get("view").getAsInt() : 0;
        if (!List.of(0, 1, 2, 4).contains(view)) view = 0;
        var value = new EditorNavigation.Location(document, path, view, !saved.has("files_mode") || saved.get("files_mode").getAsBoolean(), saved.has("pointer") ? saved.get("pointer").getAsString() : "",
                number(saved, "pan_x", 25), number(saved, "pan_y", 25), Math.clamp(number(saved, "zoom", 1), .35f, 2),
                Math.max(0, number(saved, "scroll", 0)), Math.max(0, number(saved, "details_scroll", 0)));
        if (!path.isEmpty() && document == null && !workspace.filePaths().contains(path) && !workspace.folderPaths().contains(path)) return null;
        if (document == null && view != 4 && !path.isEmpty()) return null;
        return value;
    }

    private static float number(JsonObject object, String field, float fallback) {
        if (!object.has(field)) return fallback;
        float value = object.get(field).getAsFloat();
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid editor session coordinate: " + field);
        return value;
    }

    static List<EditorNavigation.Location> positions(AuthoringWorkspace workspace, JsonObject session) {
        var result = new ArrayList<EditorNavigation.Location>();
        if (session.has("positions")) for (var value : session.getAsJsonArray("positions")) {
            if (result.size() >= 512) break;
            var position = location(workspace, value.getAsJsonObject());
            if (position != null) result.add(position);
        }
        return result;
    }
}
