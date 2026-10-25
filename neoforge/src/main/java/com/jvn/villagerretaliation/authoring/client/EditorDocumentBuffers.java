package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class EditorDocumentBuffers {
    private static final class Draft {
        final EditorFieldEdits fields = new EditorFieldEdits();
        JsonObject baseline;
        String raw;
        boolean pending() { return raw != null || fields.pending(); }
    }

    private final Map<AuthoringDocument, Draft> drafts = new LinkedHashMap<>();

    EditorFieldEdits fields(AuthoringDocument document) {
        return document == null ? new EditorFieldEdits() : drafts.computeIfAbsent(document, ignored -> new Draft()).fields;
    }

    String raw(AuthoringDocument document) {
        Draft draft = drafts.get(document);
        return draft == null ? null : draft.raw;
    }

    void raw(AuthoringDocument document, String text) {
        if (document == null) throw new IllegalStateException("Select a file before editing JSON");
        Draft draft = drafts.computeIfAbsent(document, ignored -> new Draft());
        if (draft.raw == null) draft.baseline = document.json();
        draft.raw = text;
    }

    void discardRaw(AuthoringDocument document) {
        Draft draft = drafts.get(document);
        if (draft != null) { draft.raw = null; draft.baseline = null; }
    }

    boolean pending() { return drafts.values().stream().anyMatch(Draft::pending); }
    boolean pending(AuthoringDocument document) { return drafts.containsKey(document) && drafts.get(document).pending(); }
    boolean pending(String path) { return drafts.entrySet().stream().anyMatch(entry -> entry.getKey().resourcePath().equals(path) && entry.getValue().pending()); }
    void clear() { drafts.clear(); }

    void applyRaw(AuthoringDocument document) {
        Draft draft = drafts.get(document);
        if (draft == null || draft.raw == null) return;
        if (draft.fields.pending()) throw new IllegalStateException("Apply or discard the field edits before applying full JSON");
        if (!document.json().equals(draft.baseline)) throw new IllegalStateException("This file changed after its JSON draft was opened. Review the current file before applying the draft");
        document.replaceJson(draft.raw);
        discardRaw(document);
    }

    Map<String, String> applyValid(AuthoringWorkspace workspace) {
        var issues = new LinkedHashMap<String, String>();
        for (var entry : drafts.entrySet()) {
            if (!entry.getValue().pending()) continue;
            AuthoringDocument document = entry.getKey();
            try {
                if (!workspace.documents().contains(document)) throw new IllegalStateException("The file was removed. Its unfinished draft is still retained");
                if (entry.getValue().raw != null) applyRaw(document);
                else entry.getValue().fields.apply(document);
            } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
                issues.put(document.resourcePath(), exception.getMessage());
            }
        }
        return issues;
    }

    JsonObject snapshot() {
        JsonObject result = new JsonObject();
        drafts.forEach((document, draft) -> {
            if (!draft.pending()) return;
            JsonObject state = new JsonObject();
            state.add("fields", draft.fields.snapshot());
            if (draft.raw != null) {
                state.addProperty("raw", draft.raw);
                state.add("baseline", draft.baseline.deepCopy());
            }
            result.add(document.resourcePath(), state);
        });
        return result;
    }

    void restore(AuthoringWorkspace workspace, JsonObject state) throws IOException {
        var restored = new LinkedHashMap<AuthoringDocument, Draft>();
        for (var entry : state.entrySet()) {
            var document = workspace.documents().stream().filter(value -> value.resourcePath().equals(entry.getKey())).findFirst().orElse(null);
            if (document == null) throw new IOException("The file for a recovered draft is missing: " + entry.getKey());
            Draft draft = new Draft();
            JsonObject value = entry.getValue().getAsJsonObject();
            if (value.has("fields")) draft.fields.restore(value.getAsJsonObject("fields"));
            if (value.has("raw")) {
                draft.raw = value.get("raw").getAsString();
                draft.baseline = value.has("baseline") ? value.getAsJsonObject("baseline").deepCopy() : document.json();
            }
            restored.put(document, draft);
        }
        drafts.clear(); drafts.putAll(restored);
    }
}
