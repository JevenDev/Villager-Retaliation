package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class EditorChanges {
    private SupportingRevert supportingRevert;
    private AuthoringWorkspace owner;
    private long revision = -1;
    private List<FileChange> files = List.of();

    static final class FileChange {
        final String path;
        final byte[] before, after;
        final AuthoringDocument document;
        private JsonElement current;
        private List<Change> changes;
        private List<FieldChange> rows;
        private String beforeText, afterText;

        FileChange(String path, byte[] before, byte[] after, AuthoringDocument document) {
            this.path = path; this.before = before; this.after = after; this.document = document;
        }
        String kind() { return before == null ? "Added" : after == null ? "Deleted" : "Modified"; }
        JsonElement current() {
            if (current == null && after != null) current = parse(after);
            return current;
        }
        List<Change> changes() {
            if (changes == null) changes = compare(before == null ? null : parse(before), current());
            return changes;
        }
        List<FieldChange> rows() {
            if (rows == null) rows = changes().stream().map(change -> new FieldChange(change, display(change.before()), display(change.after()))).toList();
            return rows;
        }
        String beforeText() {
            if (beforeText == null) beforeText = before == null ? "(absent)" : new String(before, StandardCharsets.UTF_8);
            return beforeText;
        }
        String afterText() {
            if (afterText == null) afterText = after == null ? "(absent)" : new String(after, StandardCharsets.UTF_8);
            return afterText;
        }
    }
    private record FieldChange(Change change, String before, String after) { }

    List<FileChange> files(AuthoringWorkspace workspace) throws IOException {
        long version = workspace.revision();
        if (owner == workspace && revision == version) return files;
        boolean sameOwner = owner == workspace;
        var previous = sameOwner ? files.stream().collect(java.util.stream.Collectors.toMap(file -> file.path, file -> file)) : java.util.Map.<String, FileChange>of();
        var current = new java.util.HashSet<>(workspace.filePaths());
        var documents = workspace.documents().stream().collect(java.util.stream.Collectors.toMap(AuthoringDocument::resourcePath, document -> document));
        var next = new ArrayList<FileChange>();
        for (String path : workspace.changedFilePaths()) {
            byte[] before = workspace.savedFile(path), after = current.contains(path) ? workspace.readFile(path) : null;
            var old = previous.get(path);
            next.add(old != null && old.document == documents.get(path) && Arrays.equals(old.before, before) && Arrays.equals(old.after, after)
                    ? old : new FileChange(path, before, after, documents.get(path)));
        }
        if (!sameOwner) supportingRevert = null;
        owner = workspace; revision = version; files = List.copyOf(next);
        return files;
    }
    record Change(String pointer, JsonElement before, JsonElement after) { }
    private record SupportingRevert(String path, String before, String after) { }

    static List<Change> compare(JsonElement before, JsonElement after) {
        var changes = new ArrayList<Change>(); collect(before, after, "", changes); return List.copyOf(changes);
    }

    private static void collect(JsonElement before, JsonElement after, String path, List<Change> changes) {
        if (Objects.equals(before, after)) return;
        if (before != null && after != null && before.isJsonObject() && after.isJsonObject()) {
            var keys = new LinkedHashSet<>(before.getAsJsonObject().keySet()); keys.addAll(after.getAsJsonObject().keySet());
            for (String key : keys) collect(before.getAsJsonObject().get(key), after.getAsJsonObject().get(key), path + "/" + JsonPointers.escape(key), changes);
        } else if (sameArrayEntries(before, after)) {
            for (int i = 0; i < before.getAsJsonArray().size(); i++) collect(before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), path + "/" + i, changes);
        } else changes.add(new Change(path, before == null ? null : before.deepCopy(), after == null ? null : after.deepCopy()));
    }

    private static boolean sameArrayEntries(JsonElement before, JsonElement after) {
        if (before == null || after == null || !before.isJsonArray() || !after.isJsonArray() || before.getAsJsonArray().size() != after.getAsJsonArray().size()) return false;
        for (int i = 0; i < before.getAsJsonArray().size(); i++) {
            var a = before.getAsJsonArray().get(i); var b = after.getAsJsonArray().get(i);
            if (a.isJsonObject() && b.isJsonObject() && !Objects.equals(a.getAsJsonObject().get("id"), b.getAsJsonObject().get("id"))) return false;
        }
        return true;
    }

    static void revert(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject baseline, Change change) {
        if (!document.json().equals(baseline)) throw new IllegalStateException("The draft changed. Review changes again before reverting");
        if (change.pointer().isEmpty()) throw new IllegalArgumentException("Use file tools to remove a new document");
        JsonObject changed = baseline.deepCopy();
        if (change.before() == null) JsonPointers.remove(changed, change.pointer());
        else JsonPointers.set(changed, change.pointer(), change.before());
        EditorMutation.validate(workspace, document, changed);
        document.edit("Revert saved field", root -> {
            if (change.before() == null) JsonPointers.remove(root, change.pointer());
            else JsonPointers.set(root, change.pointer(), change.before());
        });
    }

    void render(AuthoringWorkspace workspace, Consumer<ImGuiAuthoringEditor.EditorOperation> run, Consumer<String> open, Consumer<AuthoringDocument> changed) throws IOException {
        if (!EditorWidgets.section("Changes since last save")) return;
        ImGui.textWrapped("Apply pending field or JSON edits to include them in this review.");
        var review = files(workspace);
        if (review.isEmpty()) ImGui.textWrapped("No pack file changes. Editor layout and other editor settings are saved separately.");
        for (var file : review) {
            String path = file.path;
            byte[] before = file.before, after = file.after;
            ImGui.pushID(path);
            if (ImGui.treeNode(file.kind() + ": " + path)) {
                if (after != null && ImGui.smallButton("Open file")) open.accept(path);
                boolean text = EditorPackFiles.textFile(path);
                if (text && (path.endsWith(".json") || path.equals("pack.mcmeta")) && after != null) {
                    JsonElement current = file.current();
                    var document = file.document;
                    for (var row : file.rows()) {
                        var change = row.change();
                        ImGui.pushID(change.pointer());
                        ImGui.textWrapped(change.pointer().isEmpty() ? "Whole file" : change.pointer());
                        ImGui.textDisabled("Saved"); ImGui.textWrapped(row.before());
                        ImGui.textDisabled("Draft"); ImGui.textWrapped(row.after());
                        if (document != null && !change.pointer().isEmpty() && ImGui.smallButton("Revert this field"))
                            run.accept(() -> { revert(workspace, document, current.getAsJsonObject(), change); changed.accept(document); });
                        ImGui.popID();
                    }
                } else if (text) {
                    ImGui.textDisabled("Saved"); ImGui.textWrapped(file.beforeText());
                    ImGui.textDisabled("Draft"); ImGui.textWrapped(file.afterText());
                } else ImGui.textWrapped("Saved: " + (before == null ? 0 : before.length) + " bytes. Draft: " + (after == null ? 0 : after.length) + " bytes.");
                if (text && before != null && after != null && file.document == null
                        && ImGui.smallButton("Restore saved file")) run.accept(() -> {
                    if (!Arrays.equals(after, workspace.readFile(path))) throw new IllegalStateException("The draft changed. Review changes again");
                    String saved = new String(before, StandardCharsets.UTF_8), draft = new String(after, StandardCharsets.UTF_8);
                    workspace.writeTextFile(path, saved); supportingRevert = new SupportingRevert(path, saved, draft); changed.accept(null);
                });
                ImGui.treePop();
            }
            ImGui.popID();
        }
        if (supportingRevert != null && ImGui.button("Undo supporting file restore")) run.accept(() -> {
            var last = supportingRevert;
            if (!new String(workspace.readFile(last.path()), StandardCharsets.UTF_8).equals(last.before())) throw new IllegalStateException("The supporting file has newer edits");
            workspace.writeTextFile(last.path(), last.after()); supportingRevert = null; changed.accept(null);
        });
    }

    private static JsonElement parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        try { return JsonParser.parseString(text); }
        catch (com.google.gson.JsonParseException exception) { return new com.google.gson.JsonPrimitive(text); }
    }

    private static String display(JsonElement value) { return value == null ? "(absent)" : AuthoringDocument.JSON.toJson(value); }
}
