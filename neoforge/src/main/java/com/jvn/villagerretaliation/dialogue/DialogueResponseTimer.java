package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;

public record DialogueResponseTimer(int durationTicks, String response, String next) {
    public DialogueResponseTimer {
        if (durationTicks < 1 || durationTicks > 72000 || response == null || next == null
                || response.isBlank() == next.isBlank())
            throw new IllegalArgumentException("Response timers require a duration and exactly one response or silence destination.");
    }

    public static DialogueResponseTimer read(JsonElement raw) {
        if (raw == null || raw.isJsonNull()) return null;
        var issues = QuestJsonSchema.validate(raw, schema());
        if (!issues.isEmpty()) throw new IllegalArgumentException("Invalid dialogue response timer: " + issues);
        var value = raw.getAsJsonObject();
        return new DialogueResponseTimer(value.get("duration_ticks").getAsInt(),
                value.has("response") ? value.get("response").getAsString() : "",
                value.has("next") ? value.get("next").getAsString() : "");
    }

    public static JsonObject schema() {
        var result = new JsonObject();
        result.addProperty("type", "object");
        result.addProperty("additionalProperties", false);
        var fields = new JsonObject();
        var ticks = new JsonObject();
        ticks.addProperty("type", "integer");
        ticks.addProperty("minimum", 1);
        ticks.addProperty("maximum", 72000);
        fields.add("duration_ticks", ticks);
        for (String name : java.util.List.of("response", "next")) {
            var id = new JsonObject();
            id.addProperty("type", "string");
            id.addProperty("minLength", 1);
            id.addProperty("maxLength", 256);
            id.addProperty("pattern", "^[a-z0-9][a-z0-9_./-]*$");
            fields.add(name, id);
        }
        result.add("properties", fields);
        var required = new com.google.gson.JsonArray();
        required.add("duration_ticks");
        result.add("required", required);
        var alternatives = new com.google.gson.JsonArray();
        for (String name : java.util.List.of("response", "next")) {
            var alternative = new JsonObject();
            var names = new com.google.gson.JsonArray();
            names.add(name);
            alternative.add("required", names);
            alternatives.add(alternative);
        }
        result.add("oneOf", alternatives);
        return result;
    }
}
