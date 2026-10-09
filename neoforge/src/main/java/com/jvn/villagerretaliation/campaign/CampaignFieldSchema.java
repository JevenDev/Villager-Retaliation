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

    static JsonObject reference(String definition) {
        var value = new JsonObject();
        value.addProperty("$ref", "#/$defs/" + definition);
        return value;
    }

    static JsonObject nestedDefinition(String definition, Map<String, JsonObject> types) {
        var rules = new com.google.gson.JsonArray();
        var object = new JsonObject();
        object.add("additionalProperties", reference(definition));
        var values = new com.google.gson.JsonArray();
        types.forEach((type, schema) -> values.add(typeRule(type, schema)));
        object.add("allOf", values);
        rules.add(shapeRule("object", object));
        var array = new JsonObject(); array.add("items", reference(definition));
        rules.add(shapeRule("array", array));
        var result = new JsonObject(); result.add("allOf", rules);
        return result;
    }

    private static JsonObject shapeRule(String type, JsonObject consequence) {
        var condition = new JsonObject(); condition.addProperty("type", type);
        var rule = new JsonObject(); rule.add("if", condition); rule.add("then", consequence);
        return rule;
    }

    private static JsonObject typeRule(String type, JsonObject consequence) {
        var condition = new JsonObject();
        var pattern = new StringBuilder("^[\\x00-\\x20]*");
        for (char letter : type.toCharArray()) pattern.append(Character.isLetter(letter)
                ? "[" + letter + Character.toUpperCase(letter) + "]" : Character.toString(letter));
        pattern.append("[\\x00-\\x20]*$");
        var properties = new JsonObject(); var name = new JsonObject();
        name.addProperty("type", "string"); name.addProperty("pattern", pattern.toString()); properties.add("type", name);
        condition.add("properties", properties);
        var required = new com.google.gson.JsonArray(); required.add("type"); condition.add("required", required);
        var rule = new JsonObject(); rule.add("if", condition); rule.add("then", consequence);
        return rule;
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
