package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class EditorDocumentCache {
    private final Map<AuthoringDocument, Entry> entries = new WeakHashMap<>();

    void clear() { entries.clear(); }

    // renderers share these defensive snapshots and must keep them read-only
    JsonObject json(AuthoringDocument document) { return entry(document).json; }
    JsonObject metadata(AuthoringDocument document) { return entry(document).metadata; }

    List<DialogueGraph.Node> nodes(AuthoringDocument document) {
        Entry entry = entry(document);
        if (entry.nodes == null) entry.nodes = DialogueGraph.nodes(document);
        return entry.nodes;
    }

    private Entry entry(AuthoringDocument document) {
        Entry current = entries.get(document);
        if (current == null || current.revision != document.revision()) {
            current = new Entry(document);
            entries.put(document, current);
        }
        return current;
    }

    private static final class Entry {
        final long revision;
        final JsonObject json, metadata;
        List<DialogueGraph.Node> nodes;

        Entry(AuthoringDocument document) {
            revision = document.revision();
            json = document.json();
            metadata = document.metadata();
        }
    }
}
