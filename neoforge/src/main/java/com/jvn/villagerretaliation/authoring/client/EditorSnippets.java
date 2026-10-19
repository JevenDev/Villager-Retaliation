package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
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
        if (value.has("id")) {
            Set<String> used = new HashSet<>();
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

    void render(EditorLibrary library, AuthoringWorkspace workspace, AuthoringDocument document, Set<String> nodes, String pointer,
                Consumer<ImGuiAuthoringEditor.EditorOperation> run, Consumer<List<String>> inserted) {
        if (!ImGui.collapsingHeader("Reusable snippets")) return;
        ImGui.inputText("Snippet name", name);
        ImGui.beginDisabled(document.kind() == DefinitionKind.FORCED || nodes.isEmpty() || document.kind() == DefinitionKind.RESOURCE || document.kind().campaignManifest());
        if (ImGui.button("Save selected nodes as snippet")) run.accept(() -> library.saveSnippet(name.get(), nodes(workspace, document, nodes)));
        ImGui.endDisabled();
        if (ImGui.button("Save selected block as snippet")) run.accept(() -> library.saveSnippet(name.get(), block(document, pointer)));
        if (ImGui.beginCombo("Saved snippets", selected.isEmpty() ? "Choose a snippet..." : selected)) {
            for (String entry : library.snippets()) if (ImGui.selectable(entry, selected.equals(entry))) selected = entry;
            ImGui.endCombo();
        }
        if (!library.snippets().contains(selected)) return;
        JsonObject snippet = library.snippet(selected);
        ImGui.textWrapped(snippet.get("type").getAsString().equals("nodes") ? "Graph nodes with their connected dependencies. Paste creates new IDs."
                : "Append to the selected " + snippet.get("collection").getAsString() + " list.");
        if (ImGui.collapsingHeader("Snippet contents")) ImGui.textWrapped(AuthoringDocument.JSON.toJson(snippet.get("payload")));
        if (ImGui.button("Insert snippet")) run.accept(() -> inserted.accept(insert(workspace, document, pointer, snippet)));
        ImGui.sameLine();
        if (ImGui.button("Remove snippet")) run.accept(() -> { library.removeSnippet(selected); selected = ""; });
    }
}
