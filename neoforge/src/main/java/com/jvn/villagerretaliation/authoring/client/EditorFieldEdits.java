package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
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

    public void update(String path, JsonElement original, String text) {
        boolean string = original.isJsonPrimitive() && original.getAsJsonPrimitive().isString();
        String unchanged = string ? original.getAsString() : original.toString();
        if (unchanged.equals(text)) edits.remove(path);
        else edits.put(path, new Edit(original.deepCopy(), text, string));
    }

    public boolean apply(AuthoringDocument document) {
        if (edits.isEmpty()) return false;
        if (document == null) throw new IllegalStateException("No definition selected for the pending fields.");
        document.edit("Edit properties", root -> {
            for (var entry : edits.entrySet()) {
                Edit edit = entry.getValue();
                if (!JsonPointers.get(root, entry.getKey()).equals(edit.original()))
                    throw new IllegalStateException("The field changed since editing began: " + entry.getKey());
                JsonElement value = edit.string() ? new JsonPrimitive(edit.text()) : JsonParser.parseString(edit.text());
                if (!edit.string() && !value.isJsonPrimitive() && !value.isJsonNull())
                    throw new IllegalArgumentException("Use advanced JSON to change a scalar field into an object or array.");
                if (edit.original().isJsonPrimitive() && edit.original().getAsJsonPrimitive().isNumber()
                        && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()))
                    throw new IllegalArgumentException("Expected a number at " + entry.getKey());
                JsonPointers.set(root, entry.getKey(), value);
            }
        });
        clear();
        return true;
    }

    private record Edit(JsonElement original, String text, boolean string) { }
}
