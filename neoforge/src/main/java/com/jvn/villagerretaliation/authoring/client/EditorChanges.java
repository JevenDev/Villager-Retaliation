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
        if (owner != workspace) { owner = workspace; supportingRevert = null; }
        if (!EditorWidgets.section("Changes since last save")) return;
        ImGui.textWrapped("Apply pending field or JSON edits to include them in this review.");
        var paths = workspace.changedFilePaths();
        if (paths.isEmpty()) ImGui.textWrapped("No pack file changes. Editor layout and other editor settings are saved separately.");
        for (String path : paths) {
            byte[] before = workspace.savedFile(path), after = workspace.filePaths().contains(path) ? workspace.readFile(path) : null;
            ImGui.pushID(path);
            String kind = before == null ? "Added" : after == null ? "Deleted" : "Modified";
            if (ImGui.treeNode(kind + ": " + path)) {
                if (after != null && ImGui.smallButton("Open file")) open.accept(path);
                boolean text = EditorPackFiles.textFile(path);
                if (text && (path.endsWith(".json") || path.equals("pack.mcmeta")) && after != null) {
                    JsonElement previous = before == null ? null : parse(before);
                    JsonElement current = parse(after);
                    var document = workspace.documents().stream().filter(value -> value.resourcePath().equals(path)).findFirst().orElse(null);
                    for (var change : compare(previous, current)) {
                        ImGui.pushID(change.pointer());
                        ImGui.textWrapped(change.pointer().isEmpty() ? "Whole file" : change.pointer());
                        ImGui.textDisabled("Saved"); ImGui.textWrapped(display(change.before()));
                        ImGui.textDisabled("Draft"); ImGui.textWrapped(display(change.after()));
                        if (document != null && !change.pointer().isEmpty() && ImGui.smallButton("Revert this field"))
                            run.accept(() -> { revert(workspace, document, current.getAsJsonObject(), change); changed.accept(document); });
                        ImGui.popID();
                    }
                } else if (text) {
                    ImGui.textDisabled("Saved"); ImGui.textWrapped(before == null ? "(absent)" : new String(before, StandardCharsets.UTF_8));
                    ImGui.textDisabled("Draft"); ImGui.textWrapped(after == null ? "(absent)" : new String(after, StandardCharsets.UTF_8));
                } else ImGui.textWrapped("Saved: " + (before == null ? 0 : before.length) + " bytes. Draft: " + (after == null ? 0 : after.length) + " bytes.");
                if (text && before != null && after != null && workspace.documents().stream().noneMatch(value -> value.resourcePath().equals(path))
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
