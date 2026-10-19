package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.util.ArrayList;
import java.util.List;

final class EditorOpenFiles {
    record Entry(AuthoringDocument document, String path, boolean pinned) {
        String currentPath() { return document == null ? path : document.resourcePath(); }
    }

    private AuthoringWorkspace workspace;
    private final List<Entry> files = new ArrayList<>();

    List<Entry> entries(AuthoringWorkspace owner) {
        if (workspace != owner) { workspace = owner; files.clear(); }
        var paths = owner.filePaths();
        files.removeIf(entry -> !paths.contains(entry.currentPath()) || entry.document() != null && !owner.documents().contains(entry.document()));
        return List.copyOf(files);
    }

    void open(AuthoringWorkspace owner, String path) {
        entries(owner);
        if (!owner.filePaths().contains(path) || files.stream().anyMatch(entry -> entry.currentPath().equals(path))) return;
        var document = owner.documents().stream().filter(candidate -> candidate.resourcePath().equals(path)).findFirst().orElse(null);
        files.add(new Entry(document, path, false));
        if (files.size() > 32) files.stream().filter(entry -> !entry.pinned()).findFirst().ifPresent(files::remove);
    }

    void pin(AuthoringWorkspace owner, String path) {
        open(owner, path);
        for (int i = 0; i < files.size(); i++) {
            var entry = files.get(i);
            if (entry.currentPath().equals(path)) { files.set(i, new Entry(entry.document(), entry.path(), !entry.pinned())); return; }
        }
    }

    void close(AuthoringWorkspace owner, String path) { entries(owner); files.removeIf(entry -> entry.currentPath().equals(path)); }

    String cycle(AuthoringWorkspace owner, String current, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("Choose previous or next file");
        var open = entries(owner);
        if (open.isEmpty()) return "";
        int index = -1;
        for (int i = 0; i < open.size(); i++) if (open.get(i).currentPath().equals(current)) index = i;
        return open.get(index < 0 ? direction < 0 ? open.size() - 1 : 0 : Math.floorMod(index + direction, open.size())).currentPath();
    }

    JsonArray snapshot(AuthoringWorkspace owner) {
        JsonArray result = new JsonArray();
        for (var entry : entries(owner)) {
            JsonObject file = new JsonObject();
            file.addProperty("path", entry.currentPath()); file.addProperty("pinned", entry.pinned()); result.add(file);
        }
        return result;
    }

    void restore(AuthoringWorkspace owner, JsonArray saved) {
        workspace = owner; files.clear();
        for (var value : saved) {
            if (files.size() >= 32) break;
            var file = value.getAsJsonObject();
            if (!file.has("path") || !file.get("path").isJsonPrimitive() || !file.getAsJsonPrimitive("path").isString())
                throw new IllegalArgumentException("Saved open file must contain a path");
            String path = file.get("path").getAsString();
            open(owner, path);
            if (file.has("pinned") && file.get("pinned").getAsBoolean() && owner.filePaths().contains(path)) pin(owner, path);
        }
    }
}
