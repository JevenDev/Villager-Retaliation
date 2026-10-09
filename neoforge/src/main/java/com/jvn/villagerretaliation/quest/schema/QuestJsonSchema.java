package com.jvn.villagerretaliation.quest.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class QuestJsonSchema {
    private static final int MAX_DEPTH = 128;
    private static final int MAX_ISSUES = 128;
    private static final Set<String> KEYWORDS = Set.of(
            "$schema", "$id", "$defs", "$ref", "title", "description", "default", "examples",
            "type", "const", "enum", "allOf", "anyOf", "oneOf", "not", "if", "then", "else",
            "properties", "propertyNames", "minProperties", "maxProperties", "required", "additionalProperties", "items", "minItems", "maxItems",
            "uniqueItems", "minLength", "maxLength", "pattern", "minimum", "maximum");

    private QuestJsonSchema() {
    }

    public static List<Issue> validate(JsonElement value, JsonObject schema) {
        checkSchema(schema, schema, new HashSet<>());
        List<Issue> issues = new ArrayList<>();
        try {
            validate(value, schema, schema, "", 0, new Budget(), issues);
        } catch (ValidationLimit limit) {
            return List.of(new Issue(limit.pointer, limit.getMessage()));
        }
        return List.copyOf(issues.subList(0, Math.min(issues.size(), MAX_ISSUES)));
    }

    private static void checkSchema(JsonElement value, JsonObject root, Set<String> references) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return;
        if (!value.isJsonObject()) throw new IllegalArgumentException("schema must be an object or boolean");
        JsonObject schema = value.getAsJsonObject();
        for (String key : schema.keySet()) {
            if (!KEYWORDS.contains(key)) throw new IllegalArgumentException("unsupported schema keyword " + key);
        }
        if (schema.has("$ref")) {
            String reference = schema.get("$ref").getAsString();
            JsonElement resolved = resolve(root, reference);
            if (references.add(reference)) checkSchema(resolved, root, references);
        }
        for (String key : List.of("$defs", "properties")) {
            if (schema.has(key)) schema.getAsJsonObject(key).entrySet().forEach(entry -> checkSchema(entry.getValue(), root, references));
        }
        for (String key : List.of("allOf", "anyOf", "oneOf")) {
            if (schema.has(key)) for (JsonElement child : schema.getAsJsonArray(key)) checkSchema(child, root, references);
        }
        for (String key : List.of("not", "if", "then", "else", "items", "additionalProperties", "propertyNames")) {
            if (schema.has(key)) checkSchema(schema.get(key), root, references);
        }
    }

    private static void validate(JsonElement value, JsonElement rule, JsonObject root,
            String pointer, int depth, Budget budget, List<Issue> issues) {
        if (issues.size() >= MAX_ISSUES) return;
        if (depth > MAX_DEPTH) throw new ValidationLimit(pointer, "content exceeds the supported nesting depth");
        if (--budget.remaining < 0) throw new ValidationLimit(pointer, "content exceeds the validation work limit");
        if (rule.isJsonPrimitive()) {
            if (!rule.getAsBoolean()) issues.add(new Issue(pointer, "unsupported value"));
            return;
        }
        JsonObject schema = rule.getAsJsonObject();
        if (schema.has("$ref")) validate(value, resolve(root, schema.get("$ref").getAsString()), root, pointer, depth + 1, budget, issues);
        if (schema.has("type") && !matchesType(value, schema.get("type").getAsString())) {
            issues.add(new Issue(pointer, "expected " + schema.get("type").getAsString()));
            return;
        }
        if (schema.has("const") && !schema.get("const").equals(value)) issues.add(new Issue(pointer, "unsupported value " + value));
        if (schema.has("enum")) {
            boolean accepted = false;
            for (JsonElement candidate : schema.getAsJsonArray("enum")) accepted |= candidate.equals(value);
            if (!accepted) issues.add(new Issue(pointer, "unsupported value " + value));
        }
        if (schema.has("allOf")) {
            for (JsonElement child : schema.getAsJsonArray("allOf")) validate(value, child, root, pointer, depth + 1, budget, issues);
        }
        for (String key : List.of("anyOf", "oneOf")) {
            if (!schema.has(key)) continue;
            int matches = 0;
            List<Issue> closest = null;
            for (JsonElement child : schema.getAsJsonArray(key)) {
                List<Issue> branch = new ArrayList<>();
                validate(value, child, root, pointer, depth + 1, budget, branch);
                if (branch.isEmpty()) matches++;
                else if (closest == null || branch.size() < closest.size()) closest = branch;
            }
            if (matches == 0) {
                if (closest != null) issues.addAll(closest.subList(0, Math.min(closest.size(), Math.max(0, MAX_ISSUES - issues.size()))));
                else issues.add(new Issue(pointer, "no supported alternative"));
            } else if (key.equals("oneOf") && matches != 1) {
                issues.add(new Issue(pointer, "expected exactly one supported alternative"));
            }
        }
        if (schema.has("not") && matches(value, schema.get("not"), root, pointer, depth, budget)) {
            issues.add(new Issue(pointer, "unsupported combination of fields"));
        }
        if (schema.has("if")) {
            String branch = matches(value, schema.get("if"), root, pointer, depth, budget) ? "then" : "else";
            if (schema.has(branch)) validate(value, schema.get(branch), root, pointer, depth + 1, budget, issues);
        }
        if (value == null || value.isJsonNull()) return;
        if (value.isJsonObject()) validateObject(value.getAsJsonObject(), schema, root, pointer, depth, budget, issues);
        if (value.isJsonArray()) validateArray(value.getAsJsonArray(), schema, root, pointer, depth, budget, issues);
        if (!value.isJsonPrimitive()) return;
        if (value.getAsJsonPrimitive().isString()) {
            String text = value.getAsString();
            int length = text.codePointCount(0, text.length());
            if (schema.has("minLength") && length < schema.get("minLength").getAsInt()
                    || schema.has("maxLength") && length > schema.get("maxLength").getAsInt()) {
                issues.add(new Issue(pointer, "text length is outside the supported range"));
            }
            if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").getAsString()).matcher(text).find()) {
                issues.add(new Issue(pointer, "text does not match " + schema.get("pattern").getAsString()));
            }
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            BigDecimal number;
            try {
                number = value.getAsBigDecimal();
            } catch (NumberFormatException exception) {
                issues.add(new Issue(pointer, "expected a finite number"));
                return;
            }
            if (schema.has("minimum") && number.compareTo(schema.get("minimum").getAsBigDecimal()) < 0
                    || schema.has("maximum") && number.compareTo(schema.get("maximum").getAsBigDecimal()) > 0) {
                issues.add(new Issue(pointer, "number is outside the supported range"));
            }
        }
    }

    private static void validateObject(JsonObject value, JsonObject schema, JsonObject root,
            String pointer, int depth, Budget budget, List<Issue> issues) {
        if (schema.has("minProperties") && value.size() < schema.get("minProperties").getAsInt())
            issues.add(new Issue(pointer, "object requires at least " + schema.get("minProperties") + " fields"));
        if (schema.has("maxProperties") && value.size() > schema.get("maxProperties").getAsInt()) {
            issues.add(new Issue(pointer, "object exceeds " + schema.get("maxProperties") + " fields"));
            return;
        }
        if (schema.has("required")) for (JsonElement required : schema.getAsJsonArray("required")) {
            if (issues.size() >= MAX_ISSUES) return;
            if (!value.has(required.getAsString())) issues.add(new Issue(child(pointer, required.getAsString()), "required field is missing"));
        }
        JsonObject properties = schema.has("properties") ? schema.getAsJsonObject("properties") : new JsonObject();
        for (var entry : value.entrySet()) {
            if (issues.size() >= MAX_ISSUES) return;
            if (schema.has("propertyNames"))
                validate(new com.google.gson.JsonPrimitive(entry.getKey()), schema.get("propertyNames"), root,
                        child(pointer, entry.getKey()), depth + 1, budget, issues);
            if (properties.has(entry.getKey())) {
                validate(entry.getValue(), properties.get(entry.getKey()), root, child(pointer, entry.getKey()), depth + 1, budget, issues);
            } else if (schema.has("additionalProperties")) {
                JsonElement additional = schema.get("additionalProperties");
                if (additional.isJsonPrimitive() && !additional.getAsBoolean()) {
                    issues.add(new Issue(child(pointer, entry.getKey()), "unsupported field"));
                } else {
                    validate(entry.getValue(), additional, root, child(pointer, entry.getKey()), depth + 1, budget, issues);
                }
            }
        }
    }

    private static void validateArray(JsonArray value, JsonObject schema, JsonObject root,
            String pointer, int depth, Budget budget, List<Issue> issues) {
        if (schema.has("minItems") && value.size() < schema.get("minItems").getAsInt()) {
            issues.add(new Issue(pointer, "array requires at least " + schema.get("minItems") + " entries"));
        }
        if (schema.has("maxItems") && value.size() > schema.get("maxItems").getAsInt()) {
            issues.add(new Issue(pointer, "array exceeds " + schema.get("maxItems") + " entries"));
            return;
        }
        Set<JsonElement> seen = new HashSet<>();
        for (int index = 0; index < value.size(); index++) {
            if (issues.size() >= MAX_ISSUES) return;
            if (schema.has("uniqueItems") && schema.get("uniqueItems").getAsBoolean() && !seen.add(value.get(index))) {
                issues.add(new Issue(pointer + "/" + index, "duplicate array entry"));
            }
            if (schema.has("items")) validate(value.get(index), schema.get("items"), root, pointer + "/" + index, depth + 1, budget, issues);
        }
    }

    private static boolean matches(JsonElement value, JsonElement schema, JsonObject root, String pointer, int depth, Budget budget) {
        List<Issue> issues = new ArrayList<>();
        validate(value, schema, root, pointer, depth + 1, budget, issues);
        return issues.isEmpty();
    }

    private static boolean matchesType(JsonElement value, String type) {
        return switch (type) {
            case "null" -> value == null || value.isJsonNull();
            case "object" -> value != null && value.isJsonObject();
            case "array" -> value != null && value.isJsonArray();
            case "string" -> value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case "boolean" -> value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case "number", "integer" -> {
                if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) yield false;
                try {
                    yield !type.equals("integer") || value.getAsBigDecimal().stripTrailingZeros().scale() <= 0;
                } catch (NumberFormatException exception) {
                    yield false;
                }
            }
            default -> throw new IllegalArgumentException("unsupported schema type " + type);
        };
    }

    private static JsonElement resolve(JsonObject root, String reference) {
        if (!reference.startsWith("#/")) throw new IllegalArgumentException("unsupported schema reference " + reference);
        JsonElement value = root;
        for (String token : reference.substring(2).split("/")) {
            String key = token.replace("~1", "/").replace("~0", "~");
            if (!value.isJsonObject() || !value.getAsJsonObject().has(key)) {
                throw new IllegalArgumentException("unresolved schema reference " + reference);
            }
            value = value.getAsJsonObject().get(key);
        }
        return value;
    }

    private static String child(String pointer, String key) {
        return pointer + "/" + key.replace("~", "~0").replace("/", "~1");
    }

    private static final class Budget {
        private int remaining = 100_000;
    }

    private static final class ValidationLimit extends RuntimeException {
        private final String pointer;

        private ValidationLimit(String pointer, String message) {
            super(message);
            this.pointer = pointer;
        }
    }

    public record Issue(String pointer, String message) {
    }
}
