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
    private JsonObject orphaned = new JsonObject();

    java.util.List<String> orphanedPaths() { return java.util.List.copyOf(orphaned.keySet()); }
    String orphanedInput(String path) { return AuthoringDocument.JSON.toJson(orphaned.get(path)); }
    void discardOrphan(String path) { orphaned.remove(path); }

    void reattach(AuthoringWorkspace workspace, String path) throws IOException {
        if (drafts.keySet().stream().anyMatch(document -> document.resourcePath().equals(path) && pending(document)))
            throw new IllegalStateException("Resolve the existing draft before reattaching recovered input");
        if (workspace.documents().stream().noneMatch(document -> document.resourcePath().equals(path)))
            throw new IllegalStateException("Restore the file before reattaching its draft");
        var document = workspace.documents().stream().filter(value -> value.resourcePath().equals(path)).findFirst().orElseThrow();
        if (!orphaned.has(path)) throw new IllegalStateException("No retained draft exists for this file");
        drafts.put(document, readDraft(document, orphaned.getAsJsonObject(path)));
        orphaned.remove(path);
    }

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

    boolean pending() { return !orphaned.isEmpty() || drafts.values().stream().anyMatch(Draft::pending); }
    boolean pending(AuthoringDocument document) { return drafts.containsKey(document) && drafts.get(document).pending(); }
    boolean pending(String path) { return orphaned.has(path) || drafts.entrySet().stream().anyMatch(entry -> entry.getKey().resourcePath().equals(path) && entry.getValue().pending()); }
    void clear() { drafts.clear(); orphaned = new JsonObject(); }

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
        orphaned.keySet().forEach(path -> issues.put(path, "Recovered input belongs to a missing file. Restore the file or copy the retained draft"));
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
        if (!orphaned.isEmpty()) result.add("_orphaned", orphaned.deepCopy());
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
        var missing = state.has("_orphaned") ? state.getAsJsonObject("_orphaned").deepCopy() : new JsonObject();
        for (String path : missing.keySet()) AuthoringWorkspace.safeRelativePath(path);
        for (var entry : state.entrySet()) {
            if (entry.getKey().equals("_orphaned")) continue;
            var document = workspace.documents().stream().filter(value -> value.resourcePath().equals(entry.getKey())).findFirst().orElse(null);
            if (document == null) {
                AuthoringWorkspace.safeRelativePath(entry.getKey());
                missing.add(entry.getKey(), entry.getValue().deepCopy());
                continue;
            }
            restored.put(document, readDraft(document, entry.getValue().getAsJsonObject()));
        }
        drafts.clear(); drafts.putAll(restored); orphaned = missing;
    }

    private static Draft readDraft(AuthoringDocument document, JsonObject value) {
        Draft draft = new Draft();
        if (value.has("fields")) draft.fields.restore(value.getAsJsonObject("fields"));
        if (value.has("raw")) {
            draft.raw = value.get("raw").getAsString();
            draft.baseline = value.has("baseline") ? value.getAsJsonObject("baseline").deepCopy() : document.json();
        }
        return draft;
    }
}
