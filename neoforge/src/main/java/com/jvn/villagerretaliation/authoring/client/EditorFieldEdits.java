package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EditorFieldEdits {
    private final Map<String, Edit> edits = new LinkedHashMap<>();

    public boolean pending() { return !edits.isEmpty(); }

    public void clear() { edits.clear(); }

    public JsonObject snapshot() {
        JsonObject values = new JsonObject();
        edits.forEach((path, edit) -> {
            JsonObject value = new JsonObject();
            value.add("original", edit.original().deepCopy());
            value.addProperty("text", edit.text());
            values.add(path, value);
        });
        return values;
    }

    public void restore(JsonObject values) {
        clear();
        values.entrySet().forEach(entry -> update(entry.getKey(), entry.getValue().getAsJsonObject().get("original"),
                entry.getValue().getAsJsonObject().get("text").getAsString()));
    }

    public void update(String path, JsonElement original, String text) {
        if (edits.containsKey(path)) original = edits.get(path).original();
        boolean string = original.isJsonPrimitive() && original.getAsJsonPrimitive().isString();
        String unchanged = string ? original.getAsString() : original.toString();
        if (unchanged.equals(text)) edits.remove(path);
        else edits.put(path, new Edit(original.deepCopy(), text, string));
    }

    public boolean apply(AuthoringDocument document) {
        if (edits.isEmpty()) return false;
        if (document == null) throw new IllegalStateException("No definition selected for the pending fields.");
        document.edit("Edit properties", this::applyTo);
        clear();
        return true;
    }

    public void validate(AuthoringDocument document) {
        if (!edits.isEmpty()) applyTo(document.json());
    }

    public Map<String, String> issues(AuthoringDocument document) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : edits.entrySet()) {
            try { replacement(document.value(entry.getKey()), entry.getKey(), entry.getValue()); }
            catch (IllegalArgumentException | IllegalStateException exception) { result.put(entry.getKey(), exception.getMessage()); }
        }
        return result;
    }

    public boolean contains(String path) { return edits.containsKey(path); }
    public String text(String path, String fallback) { return edits.containsKey(path) ? edits.get(path).text() : fallback; }

    public String issue(AuthoringDocument document, String path) {
        if (!edits.containsKey(path)) return "";
        try { replacement(document.value(path), path, edits.get(path)); return ""; }
        catch (IllegalArgumentException | IllegalStateException exception) { return exception.getMessage(); }
    }

    public JsonElement candidate(AuthoringDocument document, String path) {
        JsonElement current = document.value(path);
        return edits.containsKey(path) ? replacement(current, path, edits.get(path)) : current;
    }

    private void applyTo(JsonObject root) {
        edits.forEach((path, edit) -> applyField(root, path, edit));
    }

    private static void applyField(JsonObject root, String path, Edit edit) {
        JsonPointers.set(root, path, replacement(JsonPointers.get(root, path), path, edit));
    }

    private static JsonElement replacement(JsonElement current, String path, Edit edit) {
        if (!current.equals(edit.original()))
            throw new IllegalStateException("The field changed since editing began: " + path);
        JsonElement value;
        try {
            value = edit.string() ? new JsonPrimitive(edit.text()) : JsonParser.parseString(edit.text());
        } catch (com.google.gson.JsonParseException exception) {
            throw new IllegalArgumentException("Invalid JSON at " + path + ": " + exception.getMessage(), exception);
        }
        if (!edit.string() && !value.isJsonPrimitive() && !value.isJsonNull())
            throw new IllegalArgumentException("Use advanced JSON to change the scalar field at " + path + " into an object or array.");
        if (edit.original().isJsonPrimitive() && edit.original().getAsJsonPrimitive().isNumber()
                && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()))
            throw new IllegalArgumentException("Expected a number at " + path);
        if (path.isEmpty()) throw new IllegalArgumentException("Replace the document to edit its root");
        return value;
    }

    private record Edit(JsonElement original, String text, boolean string) { }
}
