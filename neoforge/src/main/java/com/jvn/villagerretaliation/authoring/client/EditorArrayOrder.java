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
        document.edit("Reorder list", root -> {
            JsonArray values = JsonPointers.get(root, pointer).getAsJsonArray();
            if (from == to) return;
            var reordered = new java.util.ArrayList<>(values.asList());
            var entry = reordered.remove(from);
            reordered.add(to, entry);
            for (int i = 0; i < reordered.size(); i++) values.set(i, reordered.get(i));
        });
    }
}
