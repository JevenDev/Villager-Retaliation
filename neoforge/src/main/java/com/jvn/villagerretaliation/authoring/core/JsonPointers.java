package com.jvn.villagerretaliation.authoring.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class JsonPointers {
    private JsonPointers() {
    }

    public static String escape(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    public static JsonElement get(JsonElement root, String pointer) {
        if (pointer.isEmpty()) return root;
        if (!pointer.startsWith("/")) throw new IllegalArgumentException("Invalid JSON pointer " + pointer);
        JsonElement current = root;
        for (String token : pointer.substring(1).split("/", -1)) {
            String key = token.replace("~1", "/").replace("~0", "~");
            if (current == null) throw new IllegalArgumentException("Missing JSON field " + pointer);
            current = current.isJsonArray() ? current.getAsJsonArray().get(Integer.parseInt(key))
                    : current.getAsJsonObject().get(key);
        }
        if (current == null) throw new IllegalArgumentException("Missing JSON field " + pointer);
        return current;
    }

    public static void set(JsonObject root, String pointer, JsonElement value) {
        int split = pointer.lastIndexOf('/');
        if (split < 0) throw new IllegalArgumentException("Replace the document to edit its root");
        JsonElement parent = get(root, pointer.substring(0, split));
        String key = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        if (parent.isJsonArray()) parent.getAsJsonArray().set(Integer.parseInt(key), value.deepCopy());
        else parent.getAsJsonObject().add(key, value.deepCopy());
    }

    public static void remove(JsonObject root, String pointer) {
        int split = pointer.lastIndexOf('/');
        if (split < 0) throw new IllegalArgumentException("Cannot remove the document root");
        JsonElement parent = get(root, pointer.substring(0, split));
        String key = pointer.substring(split + 1).replace("~1", "/").replace("~0", "~");
        if (parent.isJsonArray()) parent.getAsJsonArray().remove(Integer.parseInt(key));
        else parent.getAsJsonObject().remove(key);
    }
}
