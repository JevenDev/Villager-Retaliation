package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.Set;

public final class PlaytestDrafts {
    private static final Set<String> PRESENTATION = Set.of("text", "text_key", "text_keys", "line", "line_key", "line_keys",
            "label", "label_key", "title", "title_key", "description", "description_key", "text_effects", "camera", "voices", "speaker", "response", "response_key", "response_keys");

    private PlaytestDrafts() { }

    public static JsonElement logic(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> {
                if (!PRESENTATION.contains(entry.getKey())) {
                    var projected = entry.getKey().equals("lines") || entry.getKey().equals("variants") || entry.getKey().equals("responses")
                            ? lineLogic(entry.getValue()) : logic(entry.getValue());
                    if (!(entry.getKey().equals("ui") || entry.getKey().equals("display")) || !projected.isJsonObject() || !projected.getAsJsonObject().isEmpty())
                        result.add(entry.getKey(), projected);
                }
            });
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(logic(child)));
            return result;
        }
        return value.deepCopy();
    }

    private static JsonElement lineLogic(JsonElement value) {
        if (!value.isJsonArray()) return logic(value);
        var result = new JsonArray();
        value.getAsJsonArray().forEach(child -> result.add(child.isJsonPrimitive() ? JsonNull.INSTANCE : logic(child)));
        return result;
    }

    public static boolean sameLogic(Map<String, JsonObject> applied, Map<String, JsonObject> draft) {
        var old = new java.util.TreeMap<String, JsonElement>();
        var next = new java.util.TreeMap<String, JsonElement>();
        applied.forEach((path, root) -> addLogic(old, path, root));
        draft.forEach((path, root) -> addLogic(next, path, root));
        return old.equals(next);
    }

    private static void addLogic(Map<String, JsonElement> target, String path, JsonObject root) {
        if (!locale(path)) { target.put(path, logic(root)); return; }
        var projected = localeLogic(root);
        if (!projected.isJsonNull() && (!projected.isJsonObject() || !projected.getAsJsonObject().isEmpty())) target.put(path, projected);
    }

    private static JsonElement localeLogic(JsonElement value) {
        if (value.isJsonObject()) {
            var result = new JsonObject();
            value.getAsJsonObject().entrySet().forEach(entry -> {
                var projected = entry.getKey().equals("conditions") || entry.getKey().equals("when") ? entry.getValue().deepCopy() : localeLogic(entry.getValue());
                if (!projected.isJsonNull() && (!projected.isJsonObject() || !projected.getAsJsonObject().isEmpty())) result.add(entry.getKey(), projected);
            });
            return result;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray();
            value.getAsJsonArray().forEach(child -> result.add(localeLogic(child)));
            return result;
        }
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() ? JsonNull.INSTANCE : value.deepCopy();
    }

    private static boolean locale(String path) { return path.contains("/locales/") || path.contains("/lang/") || path.contains("/messages/"); }

    public static JsonObject presentation(JsonObject applied, JsonObject draft) {
        return merge(applied, draft, "").getAsJsonObject();
    }

    private static JsonElement merge(JsonElement applied, JsonElement draft, String field) {
        if (PRESENTATION.contains(field)) return draft.deepCopy();
        if (applied.isJsonObject() && draft.isJsonObject()) {
            var result = applied.getAsJsonObject().deepCopy();
            for (var entry : draft.getAsJsonObject().entrySet()) {
                String key = entry.getKey();
                if (PRESENTATION.contains(key)) result.add(key, entry.getValue().deepCopy());
                else if (result.has(key)) result.add(key, merge(result.get(key), entry.getValue(), key));
                else if (key.equals("ui") || key.equals("display")) result.add(key, merge(new JsonObject(), entry.getValue(), key));
            }
            for (String key : PRESENTATION) if (!draft.getAsJsonObject().has(key)) result.remove(key);
            return result;
        }
        if (applied.isJsonArray() && draft.isJsonArray()) {
            var result = applied.getAsJsonArray().deepCopy();
            for (int index = 0; index < Math.min(result.size(), draft.getAsJsonArray().size()); index++) {
                var old = result.get(index);
                var next = draft.getAsJsonArray().get(index);
                if (old.isJsonObject() && old.getAsJsonObject().has("id")) {
                    var id = old.getAsJsonObject().get("id");
                    next = draft.getAsJsonArray().asList().stream().filter(candidate -> candidate.isJsonObject()
                            && id.equals(candidate.getAsJsonObject().get("id"))).findFirst().orElse(old);
                }
                result.set(index, (field.equals("lines") || field.equals("variants") || field.equals("responses")) && old.isJsonPrimitive() && next.isJsonPrimitive()
                        ? next.deepCopy() : merge(old, next, ""));
            }
            return result;
        }
        return applied.deepCopy();
    }
}
