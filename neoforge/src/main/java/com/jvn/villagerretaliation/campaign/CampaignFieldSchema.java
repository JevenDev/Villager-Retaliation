package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

final class CampaignFieldSchema {
    private CampaignFieldSchema() {
    }

    static Map<String, String> issues(JsonObject object, JsonObject schema, String label) {
        var issues = new LinkedHashMap<String, String>();
        if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
            object.keySet().stream().filter(key -> !schema.getAsJsonObject("properties").has(key))
                    .forEach(key -> issues.put(key, "unknown " + label + " field"));
        }
        schema.getAsJsonArray("required").forEach(key -> {
            if (!object.has(key.getAsString())) issues.put(key.getAsString(), label + " field is required");
        });
        schema.getAsJsonObject("properties").entrySet().forEach(entry -> {
            var value = object.get(entry.getKey());
            if (value == null) return;
            var rule = entry.getValue().getAsJsonObject();
            boolean valid;
            if (rule.has("const")) valid = rule.get("const").equals(value);
            else if (rule.has("type") && rule.get("type").getAsString().equals("integer")) valid = integer(value, rule);
            else valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && (!rule.has("pattern") || value.getAsString().matches(rule.get("pattern").getAsString()))
                    && (!rule.has("maxLength") || value.getAsString().length() <= rule.get("maxLength").getAsInt())
                    && (!rule.has("enum") || rule.getAsJsonArray("enum").contains(value));
            if (!valid) issues.put(entry.getKey(), label + " field does not match its registered schema");
        });
        return issues;
    }

    private static boolean integer(com.google.gson.JsonElement value, JsonObject rule) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return false;
        try {
            var number = value.getAsBigDecimal();
            return number.stripTrailingZeros().scale() <= 0
                    && (!rule.has("minimum") || number.compareTo(rule.get("minimum").getAsBigDecimal()) >= 0)
                    && (!rule.has("maximum") || number.compareTo(rule.get("maximum").getAsBigDecimal()) <= 0);
        } catch (NumberFormatException exception) { return false; }
    }

    static JsonObject string(String pattern, int maximum) {
        var field = new JsonObject();
        field.addProperty("type", "string");
        field.addProperty("maxLength", maximum);
        if (!pattern.isBlank()) field.addProperty("pattern", "^" + pattern + "$");
        return field;
    }

    static JsonObject enumeration(String... values) {
        var field = new JsonObject();
        field.addProperty("type", "string");
        var choices = new com.google.gson.JsonArray();
        for (String value : values) choices.add(value);
        field.add("enum", choices);
        return field;
    }
}
