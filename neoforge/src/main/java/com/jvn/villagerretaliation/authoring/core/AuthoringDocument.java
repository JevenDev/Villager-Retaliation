package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Consumer;

public final class AuthoringDocument {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final int HISTORY_LIMIT = 200;
    private final DefinitionKind kind;
    private String resourcePath;
    private JsonObject json;
    private JsonObject metadata = new JsonObject();
    private State saved;
    private long revision;
    private long dirtyRevision = -1;
    private boolean dirty;
    private final Deque<HistoryEntry> undo = new ArrayDeque<>();
    private final Deque<HistoryEntry> redo = new ArrayDeque<>();

    public AuthoringDocument(DefinitionKind kind, String resourcePath, JsonObject json) {
        this.kind = Objects.requireNonNull(kind);
        this.resourcePath = AuthoringWorkspace.safeRelativePath(resourcePath);
        this.json = Objects.requireNonNull(json).deepCopy();
    }

    public DefinitionKind kind() { return kind; }
    public String resourcePath() { return resourcePath; }
    public JsonObject json() { return json.deepCopy(); }
    public JsonElement value(String pointer) { return JsonPointers.get(json, pointer).deepCopy(); }
    public JsonObject metadata() { return metadata.deepCopy(); }
    public String id() {
        if (json.has("id") && json.get("id").isJsonPrimitive()) return json.get("id").getAsString();
        if (kind == DefinitionKind.TREE) {
            String[] parts = DefinitionKind.unscopedPath(resourcePath).split("/", 5);
            if (parts.length == 5 && parts[2].equals("dialogue_trees") && parts[4].endsWith(".json")) {
                return parts[1] + ":" + parts[4].substring(0, parts[4].length() - 5);
            }
        }
        return resourcePath;
    }
    public boolean canUndo() { return !undo.isEmpty(); }
    public boolean canRedo() { return !redo.isEmpty(); }
    public long revision() { return revision; }
    public java.util.List<AuthoringDocument> historyDocuments(boolean redoing) {
        var history = redoing ? redo : undo;
        return history.isEmpty() || history.peek().group() == null ? java.util.List.of(this) : history.peek().group().documents();
    }
    public boolean dirty() {
        if (dirtyRevision != revision) {
            dirty = saved == null || !resourcePath.equals(saved.path()) || !json.equals(saved.json()) || !metadata.equals(saved.metadata());
            dirtyRevision = revision;
        }
        return dirty;
    }
    public String undoLabel() { return undo.isEmpty() ? "" : undo.peek().label(); }
    public String redoLabel() { return redo.isEmpty() ? "" : redo.peek().label(); }
    public java.util.List<String> undoLabels() { return undo.stream().map(HistoryEntry::label).toList(); }
    public java.util.List<String> redoLabels() { return redo.stream().map(HistoryEntry::label).toList(); }

    public void edit(String label, Consumer<JsonObject> edit) {
        JsonObject changed = json.deepCopy();
        edit.accept(changed);
        if (changed.equals(json)) return;
        remember(label);
        json = changed.deepCopy();
    }

    public void edit(String label, Consumer<JsonObject> edit, Consumer<JsonObject> editorEdit) {
        JsonObject changed = json.deepCopy();
        JsonObject changedMetadata = metadata.deepCopy();
        edit.accept(changed);
        editorEdit.accept(changedMetadata);
        if (changed.equals(json) && changedMetadata.equals(metadata)) return;
        remember(label);
        json = changed.deepCopy();
        metadata = changedMetadata.deepCopy();
    }

    public void replaceJson(String text) {
        JsonObject changed = JsonParser.parseString(text).getAsJsonObject();
        edit("Edit JSON", root -> {
            root.keySet().clear();
            changed.entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue().deepCopy()));
        });
    }

    public void editMetadata(String label, Consumer<JsonObject> edit) {
        JsonObject changed = metadata.deepCopy();
        edit.accept(changed);
        if (changed.equals(metadata)) return;
        remember(label);
        metadata = changed.deepCopy();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        if (undo.peek().group() != null) return undo.peek().group().undo();
        redo.push(new HistoryEntry(state(), null, undo.peek().label()));
        restore(undo.pop().state());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        if (redo.peek().group() != null) return redo.peek().group().redo();
        undo.push(new HistoryEntry(state(), null, redo.peek().label()));
        restore(redo.pop().state());
        return true;
    }

    void rename(String path, String id) {
        remember("Rename file");
        resourcePath = AuthoringWorkspace.safeRelativePath(path);
        if (kind != DefinitionKind.RESOURCE && kind != DefinitionKind.FORCED) json.addProperty("id", id);
    }

    void relocate(String path, JsonObject definition) {
        String safePath = AuthoringWorkspace.safeRelativePath(path);
        remember("Move file");
        resourcePath = safePath;
        json = definition.deepCopy();
    }

    void loadMetadata(JsonObject value) { metadata = value.deepCopy(); revision++; }
    void markSaved() { saved = state(); dirty = false; dirtyRevision = revision; }
    String savedPath() { return saved == null ? null : saved.path(); }
    JsonObject savedJson() { return saved == null ? null : saved.json().deepCopy(); }

    private void remember(String label) {
        undo.push(new HistoryEntry(state(), null, label == null || label.isBlank() ? "Edit document" : label));
        while (undo.size() > HISTORY_LIMIT) undo.removeLast();
        redo.clear();
        revision++;
    }

    private State state() { return new State(resourcePath, json.deepCopy(), metadata.deepCopy()); }
    private void restore(State state) {
        resourcePath = state.path();
        json = state.json().deepCopy();
        metadata = state.metadata().deepCopy();
        revision++;
    }
    static void groupLatestChanges(java.util.List<AuthoringDocument> documents, Runnable beforeUndo, Runnable beforeRedo) {
        HistoryGroup group = new HistoryGroup(java.util.List.copyOf(documents), beforeUndo, beforeRedo);
        for (AuthoringDocument document : documents) {
            HistoryEntry latest = document.undo.pop();
            document.undo.push(new HistoryEntry(latest.state(), group, latest.label()));
        }
    }

    private record State(String path, JsonObject json, JsonObject metadata) {
    }

    private record HistoryEntry(State state, HistoryGroup group, String label) {
    }

    private record HistoryGroup(java.util.List<AuthoringDocument> documents, Runnable beforeUndo, Runnable beforeRedo) {
        boolean undo() {
            if (documents.stream().anyMatch(document -> document.undo.isEmpty() || document.undo.peek().group() != this)) {
                throw new IllegalStateException("Undo newer edits in the other affected documents before undoing this grouped edit");
            }
            beforeUndo.run();
            for (AuthoringDocument document : documents) {
                document.redo.push(new HistoryEntry(document.state(), this, document.undo.peek().label()));
                document.restore(document.undo.pop().state());
            }
            return true;
        }

        boolean redo() {
            if (documents.stream().anyMatch(document -> document.redo.isEmpty() || document.redo.peek().group() != this)) {
                throw new IllegalStateException("This grouped edit cannot be redone because another affected document has newer edits");
            }
            beforeRedo.run();
            for (AuthoringDocument document : documents) {
                document.undo.push(new HistoryEntry(document.state(), this, document.redo.peek().label()));
                document.restore(document.redo.pop().state());
            }
            return true;
        }
    }
}
