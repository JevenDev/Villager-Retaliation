package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

final class CampaignFactFields {
    private CampaignFactFields() {
    }

    static JsonObject schema(boolean action) {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        var fields = new JsonObject();
        fields.add("fact", string(CampaignOutcomeAction.ID_PATTERN, 256));
        fields.add("value", string("", 4096));
        var required = new JsonArray();
        required.add("fact");
        required.add("value");
        if (action) {
            fields.add("milestone", string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("operation_id", string(CampaignOutcomeAction.ID_PATTERN, 256));
            fields.add("mutation", enumeration("set", "add"));
            var requiredFlag = new JsonObject();
            requiredFlag.addProperty("const", true);
            fields.add("required", requiredFlag);
            required.add("operation_id");
        } else {
            var type = new JsonObject();
            type.addProperty("const", "campaign_fact");
            fields.add("type", type);
            schema.addProperty("additionalProperties", false);
            fields.add("campaign", string("[a-z0-9_.-]+:[a-z0-9_./-]+", 256));
            fields.add("comparison", enumeration("equals", "not_equals", "at_least", "at_most"));
            required.add("campaign");
        }
        schema.add("required", required);
        schema.add("properties", fields);
        return schema;
    }

    static Map<String, String> issues(JsonObject object, boolean action) {
        var schema = schema(action);
        var issues = new LinkedHashMap<String, String>();
        if (!action) {
            object.keySet().stream().filter(key -> !schema.getAsJsonObject("properties").has(key))
                    .forEach(key -> issues.put(key, "unknown campaign fact condition field"));
        }
        schema.getAsJsonArray("required").forEach(key -> {
            if (!object.has(key.getAsString())) issues.put(key.getAsString(), "campaign fact field is required");
        });
        schema.getAsJsonObject("properties").entrySet().forEach(entry -> {
            var value = object.get(entry.getKey());
            if (value == null) return;
            var rule = entry.getValue().getAsJsonObject();
            boolean valid;
            if (rule.has("const")) valid = rule.get("const").equals(value);
            else valid = value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && (!rule.has("pattern") || value.getAsString().matches(rule.get("pattern").getAsString()))
                    && (!rule.has("maxLength") || value.getAsString().length() <= rule.get("maxLength").getAsInt())
                    && (!rule.has("enum") || rule.getAsJsonArray("enum").contains(value));
            if (!valid) issues.put(entry.getKey(), "campaign fact field does not match its registered schema");
        });
        return issues;
    }

    static boolean validId(String id) { return id != null && id.matches(CampaignOutcomeAction.ID_PATTERN); }

    private static JsonObject string(String pattern, int maximum) {
        var field = new JsonObject();
        field.addProperty("type", "string");
        field.addProperty("maxLength", maximum);
        if (!pattern.isBlank()) field.addProperty("pattern", "^" + pattern + "$");
        return field;
    }

    private static JsonObject enumeration(String... values) {
        var field = new JsonObject();
        field.addProperty("type", "string");
        var choices = new JsonArray();
        for (String value : values) choices.add(value);
        field.add("enum", choices);
        return field;
    }
}
