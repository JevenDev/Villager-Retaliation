package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class EditorLibrary {
    private final Path path;
    private JsonObject data = new JsonObject();
    private boolean writable = true;

    EditorLibrary(Path path) { this.path = path.toAbsolutePath(); }

    void load() throws IOException {
        if (!Files.exists(path)) return;
        writable = false;
        try {
            JsonObject loaded = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (String field : List.of("resources", "snippets", "shortcuts"))
                if (loaded.has(field) && !loaded.get(field).isJsonObject()) throw new IllegalArgumentException("Invalid " + field);
            for (var group : EditorSchema.object(loaded.get("resources")).entrySet()) {
                JsonObject values = group.getValue().getAsJsonObject();
                for (String field : List.of("favorites", "recent")) if (values.has(field))
                    for (var id : values.getAsJsonArray(field))
                        if (!id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Invalid resource ID");
            }
            for (var snippet : EditorSchema.object(loaded.get("snippets")).entrySet()) {
                if (!snippet.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid snippet " + snippet.getKey());
                EditorSnippets.validate(snippet.getValue().getAsJsonObject());
            }
            for (var shortcut : EditorSchema.object(loaded.get("shortcuts")).entrySet()) {
                if (!shortcut.getValue().isJsonPrimitive() || !shortcut.getValue().getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Invalid shortcut");
                EditorShortcuts.parse(shortcut.getValue().getAsString());
            }
            data = loaded; writable = true;
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new IOException("Cannot read editor library: " + exception.getMessage(), exception);
        }
    }

    List<String> resources(String group, boolean favorites) {
        JsonObject groups = EditorSchema.object(data.get("resources"));
        JsonObject values = EditorSchema.object(groups.get(group));
        JsonElement list = values.get(favorites ? "favorites" : "recent");
        return list == null ? List.of() : list.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
    }

    boolean favorite(String group, String id) { return resources(group, true).contains(id); }

    void resource(String group, String id, boolean toggleFavorite) throws IOException {
        var values = new ArrayList<>(resources(group, toggleFavorite));
        if (toggleFavorite) { if (!values.remove(id)) values.add(id); }
        else { values.remove(id); values.addFirst(id); }
        JsonArray list = new JsonArray();
        values.stream().limit(toggleFavorite ? Long.MAX_VALUE : 16).forEach(list::add);
        update(root -> {
            if (!root.has("resources")) root.add("resources", new JsonObject());
            JsonObject groups = root.getAsJsonObject("resources");
            if (!groups.has(group)) groups.add(group, new JsonObject());
            groups.getAsJsonObject(group).add(toggleFavorite ? "favorites" : "recent", list);
        });
    }

    List<String> snippets() { return List.copyOf(EditorSchema.object(data.get("snippets")).keySet()); }

    JsonObject snippet(String name) {
        JsonElement value = EditorSchema.object(data.get("snippets")).get(name);
        if (value == null) throw new IllegalArgumentException("Snippet no longer exists");
        return value.getAsJsonObject().deepCopy();
    }

    void saveSnippet(String name, JsonObject value) throws IOException {
        String key = name.strip();
        if (key.isEmpty()) throw new IllegalArgumentException("Enter a snippet name");
        if (snippets().contains(key)) throw new IllegalArgumentException("Choose a new name or remove the existing snippet first");
        EditorSnippets.validate(value);
        update(root -> {
            if (!root.has("snippets")) root.add("snippets", new JsonObject());
            root.getAsJsonObject("snippets").add(key, value.deepCopy());
        });
    }

    void renameSnippet(String name, String replacement) throws IOException {
        snippet(name);
        String key = replacement.strip();
        if (key.isEmpty()) throw new IllegalArgumentException("Enter a snippet name");
        if (name.equals(key)) return;
        if (snippets().contains(key)) throw new IllegalArgumentException("A snippet already uses that name");
        update(root -> {
            var snippets = root.getAsJsonObject("snippets");
            snippets.add(key, snippets.remove(name));
        });
    }

    void updateSnippet(String name, JsonObject value) throws IOException {
        JsonObject previous = snippet(name);
        EditorSnippets.validate(value);
        if (previous.has("category")) value.add("category", previous.get("category").deepCopy());
        update(root -> root.getAsJsonObject("snippets").add(name, value.deepCopy()));
    }

    void categorizeSnippet(String name, String category) throws IOException {
        snippet(name);
        update(root -> {
            var snippet = root.getAsJsonObject("snippets").getAsJsonObject(name);
            if (category.isBlank()) snippet.remove("category");
            else snippet.addProperty("category", category.strip());
        });
    }

    List<String> snippets(String query, String category) {
        String term = query.strip().toLowerCase(java.util.Locale.ROOT);
        return snippets().stream().filter(name -> {
            var value = snippet(name);
            String group = value.has("category") ? value.get("category").getAsString() : "";
            return (category.isEmpty() || category.equals(group))
                    && (name + " " + group).toLowerCase(java.util.Locale.ROOT).contains(term);
        }).toList();
    }

    void exportSnippets(Path destination) throws IOException {
        JsonObject exported = new JsonObject(); exported.addProperty("version", 1);
        exported.add("snippets", EditorSchema.object(data.get("snippets")).deepCopy());
        Files.writeString(destination, AuthoringDocument.JSON.toJson(exported), java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    int importSnippets(Path source) throws IOException {
        JsonObject imported;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(source)).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1 || !root.has("snippets") || !root.get("snippets").isJsonObject())
                throw new IllegalArgumentException("Expected an exported snippet library");
            imported = root.getAsJsonObject("snippets");
            for (var entry : imported.entrySet()) {
                if (entry.getKey().isBlank() || !entry.getValue().isJsonObject()) throw new IllegalArgumentException("Invalid snippet name or payload");
                EditorSnippets.validate(entry.getValue().getAsJsonObject());
            }
        } catch (IllegalArgumentException | IllegalStateException | com.google.gson.JsonParseException exception) {
            throw new IOException("Cannot import snippet library: " + exception.getMessage(), exception);
        }
        update(root -> {
            if (!root.has("snippets")) root.add("snippets", new JsonObject());
            var snippets = root.getAsJsonObject("snippets");
            imported.entrySet().forEach(entry -> {
                String name = entry.getKey(); int suffix = 2;
                while (snippets.has(name)) name = entry.getKey() + " (imported " + suffix++ + ")";
                snippets.add(name, entry.getValue().deepCopy());
            });
        });
        return imported.size();
    }

    java.util.Map<String, String> shortcuts() {
        var result = new java.util.LinkedHashMap<String, String>();
        EditorSchema.object(data.get("shortcuts")).entrySet().forEach(entry -> result.put(entry.getKey(), entry.getValue().getAsString()));
        return java.util.Map.copyOf(result);
    }

    void shortcut(String command, String binding) throws IOException {
        EditorShortcuts.parse(binding);
        update(root -> {
            if (!root.has("shortcuts")) root.add("shortcuts", new JsonObject());
            root.getAsJsonObject("shortcuts").addProperty(command, binding);
        });
    }

    void resetShortcuts() throws IOException { update(root -> root.remove("shortcuts")); }

    void removeSnippet(String name) throws IOException {
        update(root -> EditorSchema.object(root.get("snippets")).remove(name));
    }

    private void update(Consumer<JsonObject> edit) throws IOException {
        if (!writable) throw new IOException("Repair the editor library file before saving favorites or snippets: " + path);
        JsonObject changed = data.deepCopy(); edit.accept(changed);
        if (changed.equals(data)) return;
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "editor-library-", ".tmp");
        try {
            Files.writeString(temporary, AuthoringDocument.JSON.toJson(changed));
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            data = changed;
        } finally { Files.deleteIfExists(temporary); }
    }
}
