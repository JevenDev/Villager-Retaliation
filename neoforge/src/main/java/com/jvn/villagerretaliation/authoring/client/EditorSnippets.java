package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.DialogueGraph;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import imgui.ImGui;
import imgui.type.ImString;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

final class EditorSnippets {
    private static final Set<String> COLLECTIONS = Set.of("conditions", "available_when", "actions", "on_enter", "on_exit", "entry_actions", "exit_actions", "rewards", "objectives", "effects");
    private final ImString name = new ImString(256);
    private String selected = "";
    private final ImString search = new ImString(256), category = new ImString(256);
    private String categoryFilter = "";

    static void validate(JsonObject snippet) {
        String type = string(snippet, "type");
        if (snippet.has("category")) string(snippet, "category");
        if (type.equals("nodes")) {
            JsonObject payload = object(snippet, "payload");
            string(payload, "kind"); string(payload, "path"); object(payload, "source"); object(payload, "positions");
            JsonObject translations = object(payload, "translations");
            for (var entry : translations.entrySet()) if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid snippet wording: " + entry.getKey());
            if (!payload.has("selection") || !payload.get("selection").isJsonArray() || payload.getAsJsonArray("selection").isEmpty())
                throw new IllegalArgumentException("A node snippet requires selected nodes");
            for (var id : payload.getAsJsonArray("selection")) if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid snippet node ID");
            new EditorGraphClipboard().importSnippet(payload);
        } else if (type.equals("block")) {
            DefinitionKind.valueOf(string(snippet, "kind")); object(snippet, "payload");
            if (!COLLECTIONS.contains(string(snippet, "collection"))) throw new IllegalArgumentException("Unsupported snippet collection");
        } else throw new IllegalArgumentException("Unsupported snippet type: " + type);
    }

    private static String string(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Snippet requires text field " + field);
        return value.getAsString();
    }

    private static JsonObject object(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Snippet requires object field " + field);
        return value.getAsJsonObject();
    }

    static JsonObject nodes(AuthoringWorkspace workspace, AuthoringDocument document, Set<String> selection) {
        var clipboard = new EditorGraphClipboard(); clipboard.copy(workspace, document, selection);
        JsonObject result = new JsonObject(); result.addProperty("type", "nodes"); result.add("payload", clipboard.exportSnippet());
        return result;
    }

    static JsonObject block(AuthoringDocument document, String pointer) {
        String collection = collection(pointer);
        JsonElement value = JsonPointers.get(document.json(), pointer);
        if (!COLLECTIONS.contains(collection) || !value.isJsonObject())
            throw new IllegalArgumentException("Select one condition, reward, action or objective inside a list");
        JsonObject result = new JsonObject(); result.addProperty("type", "block"); result.addProperty("kind", document.kind().name());
        result.addProperty("collection", collection); result.add("payload", value.deepCopy());
        return result;
    }

    private static String collection(String pointer) {
        int split = pointer.lastIndexOf('/');
        if (split < 0 || !pointer.substring(split + 1).matches("[0-9]+")) return "";
        String parent = pointer.substring(0, split);
        return parent.substring(parent.lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
    }

    static List<String> insert(AuthoringWorkspace workspace, AuthoringDocument document, String pointer, JsonObject snippet) {
        validate(snippet);
        if (snippet.get("type").getAsString().equals("nodes")) {
            var clipboard = new EditorGraphClipboard(); clipboard.importSnippet(snippet.getAsJsonObject("payload"));
            return clipboard.paste(workspace, document);
        }
        if (!document.kind().name().equals(snippet.get("kind").getAsString())) throw new IllegalArgumentException("Insert this block into the same content type");
        JsonObject changed = document.json();
        String target = pointer;
        JsonElement owner = JsonPointers.get(changed, target);
        if (owner.isJsonObject() && !collection(target).isEmpty()) { target = target.substring(0, target.lastIndexOf('/')); owner = JsonPointers.get(changed, target); }
        String field = target.substring(target.lastIndexOf('/') + 1).replace("~1", "/").replace("~0", "~");
        if (!owner.isJsonArray() || !field.equals(snippet.get("collection").getAsString()))
            throw new IllegalArgumentException("Select the " + snippet.get("collection").getAsString() + " list, or an entry in it");
        JsonObject value = snippet.getAsJsonObject("payload").deepCopy();
        if (document.kind() == DefinitionKind.QUEST && field.equals("objectives")) {
            String destination = target;
            var stage = DialogueGraph.nodes(document).stream().filter(node -> (node.pointer() + "/objectives").equals(destination)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Select a quest stage's objectives list"));
            var draft = new AuthoringDocument(document.kind(), document.resourcePath(), document.json());
            EditorContentTools.appendObjective(draft, stage.id(), value);
            EditorMutation.validate(workspace, document, draft.json());
            document.edit("Insert objective snippet", root -> {
                root.keySet().clear(); draft.json().entrySet().forEach(entry -> root.add(entry.getKey(), entry.getValue()));
            });
            return List.of();
        }
        if (value.has("id")) {
            Set<String> used = new HashSet<>();
            if (field.equals("effects")) effectIds(changed, used);
            owner.getAsJsonArray().forEach(child -> { if (child.isJsonObject() && child.getAsJsonObject().has("id")) used.add(child.getAsJsonObject().get("id").getAsString()); });
            String id = value.get("id").getAsString();
            while (used.contains(id)) id += "_copy";
            value.addProperty("id", id);
        }
        owner.getAsJsonArray().add(value);
        EditorMutation.validate(workspace, document, changed);
        String destination = target;
        document.edit("Insert snippet", root -> JsonPointers.get(root, destination).getAsJsonArray().add(value.deepCopy()));
        return List.of();
    }

    private static void effectIds(JsonElement value, Set<String> used) {
        if (value.isJsonObject()) {
            var object = value.getAsJsonObject();
            for (var effect : DialogueGraph.array(object, "effects")) if (effect.isJsonObject() && effect.getAsJsonObject().has("id")) used.add(effect.getAsJsonObject().get("id").getAsString());
            object.entrySet().stream().filter(entry -> !entry.getKey().equals("metadata")).forEach(entry -> effectIds(entry.getValue(), used));
        } else if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> effectIds(child, used));
    }

    void render(EditorLibrary library, AuthoringWorkspace workspace, AuthoringDocument document, Set<String> nodes, String pointer,
                Consumer<ImGuiAuthoringEditor.EditorOperation> run, Consumer<List<String>> inserted) {
        if (!ImGui.collapsingHeader("Reusable snippets")) return;
        ImGui.inputText("Snippet name", name);
        ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED || nodes.isEmpty() || document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest());
        if (ImGui.button("Save selected nodes as snippet")) run.accept(() -> library.saveSnippet(name.get(), nodes(workspace, document, nodes)));
        ImGui.endDisabled();
        if (ImGui.button("Save selected block as snippet")) run.accept(() -> library.saveSnippet(name.get(), block(document, pointer)));
        ImGui.inputTextWithHint("##snippet_search", "Search snippets...", search);
        var categories = library.snippets().stream().map(entry -> {
            var saved = library.snippet(entry);
            return saved.has("category") ? saved.get("category").getAsString() : "";
        }).filter(value -> !value.isEmpty()).distinct().sorted().toList();
        if (ImGui.beginCombo("Category filter", categoryFilter.isEmpty() ? "All categories" : categoryFilter)) {
            if (ImGui.selectable("All categories", categoryFilter.isEmpty())) categoryFilter = "";
            for (String value : categories) if (ImGui.selectable(value, categoryFilter.equals(value))) categoryFilter = value;
            ImGui.endCombo();
        }
        if (ImGui.button("Import snippet library")) EditorFileDialog.choose("JSON import snippets", "", path ->
                net.minecraft.client.Minecraft.getInstance().execute(() -> run.accept(() -> library.importSnippets(java.nio.file.Path.of(path)))));
        ImGui.sameLine();
        if (ImGui.button("Export snippet library")) EditorFileDialog.choose("JSON export snippets", "snippets.json", path ->
                net.minecraft.client.Minecraft.getInstance().execute(() -> run.accept(() -> library.exportSnippets(java.nio.file.Path.of(path)))));
        ImGui.textDisabled("Import keeps existing snippets. Export needs a new file name.");
        if (ImGui.beginCombo("Saved snippets", selected.isEmpty() ? "Choose a snippet..." : selected)) {
            for (String entry : library.snippets(search.get(), categoryFilter)) if (ImGui.selectable(entry, selected.equals(entry))) {
                selected = entry; name.set(entry);
                var saved = library.snippet(entry);
                category.set(saved.has("category") ? saved.get("category").getAsString() : "");
            }
            ImGui.endCombo();
        }
        if (!library.snippets().contains(selected)) return;
        JsonObject snippet = library.snippet(selected);
        ImGui.textWrapped(snippet.get("type").getAsString().equals("nodes") ? "Graph nodes with their connected dependencies. Paste creates new IDs."
                : "Append to the selected " + snippet.get("collection").getAsString() + " list.");
        if (ImGui.collapsingHeader("Snippet contents")) ImGui.textWrapped(AuthoringDocument.JSON.toJson(snippet.get("payload")));
        if (ImGui.button("Rename snippet")) run.accept(() -> { library.renameSnippet(selected, name.get()); selected = name.get().strip(); });
        ImGui.inputText("Snippet category", category);
        if (ImGui.smallButton("Save category")) run.accept(() -> library.categorizeSnippet(selected, category.get()));
        boolean graphSnippet = snippet.get("type").getAsString().equals("nodes");
        ImGui.beginDisabled(graphSnippet && (nodes.isEmpty() || document.kind() == DefinitionKind.FORCED));
        if (ImGui.button("Update snippet from selection")) run.accept(() ->
                library.updateSnippet(selected, graphSnippet ? nodes(workspace, document, nodes) : block(document, pointer)));
        ImGui.endDisabled();
        if (ImGui.button("Insert snippet")) run.accept(() -> inserted.accept(insert(workspace, document, pointer, snippet)));
        ImGui.sameLine();
        if (ImGui.button("Remove snippet")) run.accept(() -> { library.removeSnippet(selected); selected = ""; });
    }
}
