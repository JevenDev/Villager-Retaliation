package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;

final class EditorArrayOrder {
    record Drag(AuthoringDocument document, String pointer, int index, JsonArray baseline) { }

    private EditorArrayOrder() { }

    static void move(AuthoringDocument document, String pointer, int from, int to, JsonArray baseline) {
        if (from < 0 || to < 0 || from >= baseline.size() || to >= baseline.size()) throw new IllegalArgumentException("Choose an existing list entry");
        if (!JsonPointers.get(document.json(), pointer).equals(baseline)) throw new IllegalStateException("This list changed. Drag the entry again");
        edit(document, pointer, "Reorder list", values -> {
            if (from == to) return;
            var reordered = new java.util.ArrayList<>(values.asList());
            var entry = reordered.remove(from);
            reordered.add(to, entry);
            for (int i = 0; i < reordered.size(); i++) values.set(i, reordered.get(i));
        });
    }

    static void edit(AuthoringDocument document, String pointer, String label, java.util.function.Consumer<JsonArray> edit) {
        var paths = new java.util.LinkedHashMap<String, java.util.List<String>>();
        document.edit(label, root -> {
            JsonArray values = JsonPointers.get(root, pointer).getAsJsonArray();
            var before = new java.util.ArrayList<>(values.asList());
            edit.accept(values);
            for (int from = 0; from < before.size(); from++) paths.put(pointer + "/" + from, new java.util.ArrayList<>());
            for (int to = 0; to < values.size(); to++) {
                int source = -1;
                for (int from = 0; from < before.size(); from++) if (before.get(from) == values.get(to)) { source = from; break; }
                if (source < 0) source = before.indexOf(values.get(to));
                if (source >= 0) paths.get(pointer + "/" + source).add(pointer + "/" + to);
            }
        }, metadata -> {
            if (!metadata.has("camera_thumbnails")) return;
            var thumbnails = metadata.getAsJsonObject("camera_thumbnails");
            var before = thumbnails.deepCopy();
            for (var entry : before.entrySet()) {
                if (paths.keySet().stream().anyMatch(path -> entry.getKey().equals(path) || entry.getKey().startsWith(path + "/"))) thumbnails.remove(entry.getKey());
            }
            for (var path : paths.entrySet()) for (var entry : before.entrySet()) {
                if (!entry.getKey().equals(path.getKey()) && !entry.getKey().startsWith(path.getKey() + "/")) continue;
                for (String destination : path.getValue()) thumbnails.add(destination + entry.getKey().substring(path.getKey().length()), entry.getValue().deepCopy());
            }
        });
    }
}
