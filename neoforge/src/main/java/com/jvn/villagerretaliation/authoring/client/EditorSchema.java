package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EditorSchema {
    private final JsonObject root;

    EditorSchema(JsonObject root) {
        this.root = root;
    }

    static EditorSchema forDocument(AuthoringDocument document) {
        JsonObject root = switch (document.kind()) {
            case QUEST -> com.jvn.villagerretaliation.quest.schema.v3.QuestV3Schema.export();
            case CONVERSATION -> com.jvn.villagerretaliation.dialogue.conversation.ConversationSchema.export();
            case WORLD_CAMPAIGN -> com.jvn.villagerretaliation.campaign.selection.WorldCampaignSchema.export();
            case CAMPAIGN_ADDON -> com.jvn.villagerretaliation.campaign.selection.WorldCampaignAddon.schema();
            default -> new JsonObject();
        };
        if (!root.has("$defs")) root.add("$defs", com.jvn.villagerretaliation.quest.schema.ContentSchemaDefinitions.definitions());
        return new EditorSchema(root);
    }

    JsonObject at(JsonObject document, String pointer) {
        JsonElement value = document;
        JsonObject rule = root;
        if (pointer.isEmpty()) return effective(rule, value);
        for (String token : pointer.substring(1).split("/")) {
            String key = token.replace("~1", "/").replace("~0", "~");
            rule = effective(rule, value);
            if (value.isJsonArray()) {
                rule = object(rule.get("items"));
                value = value.getAsJsonArray().get(Integer.parseInt(key));
            } else {
                JsonObject properties = object(rule.get("properties"));
                rule = properties.has(key) ? object(properties.get(key)) : object(rule.get("additionalProperties"));
                if (rule.isEmpty()) rule = fallback(key);
                value = value.getAsJsonObject().get(key);
            }
            if (value == null) value = JsonNull.INSTANCE;
        }
        return effective(rule, value);
    }

    JsonObject effective(JsonObject rule, JsonElement value) {
        return effective(rule, value, 0);
    }

    private JsonObject effective(JsonObject rule, JsonElement value, int depth) {
        if (depth > 24) return new JsonObject();
        JsonObject result = new JsonObject();
        if (rule.has("$ref")) merge(result, effective(object(JsonPointers.get(root,
                rule.get("$ref").getAsString().substring(1))), value, depth + 1));
        for (var entry : rule.entrySet()) {
            if (!List.of("$ref", "allOf", "anyOf", "oneOf", "if", "then", "else", "$defs").contains(entry.getKey()))
                result.add(entry.getKey(), entry.getValue().deepCopy());
        }
        if (rule.has("allOf")) for (var branch : rule.getAsJsonArray("allOf"))
            merge(result, effective(object(branch), value, depth + 1));
        if (rule.has("if")) {
            String branch = issues(value, object(rule.get("if"))) == 0 ? "then" : "else";
            merge(result, effective(object(rule.get(branch)), value, depth + 1));
        }
        for (String keyword : List.of("oneOf", "anyOf")) {
            if (!rule.has(keyword)) continue;
            JsonObject best = null;
            int score = Integer.MAX_VALUE;
            for (var branch : rule.getAsJsonArray(keyword)) {
                int current = issues(value, object(branch));
                if (current < score) { best = object(branch); score = current; }
            }
            if (best != null) merge(result, effective(best, value, depth + 1));
        }
        return result;
    }

    Map<String, JsonElement> choices(JsonObject rule) {
        Map<String, JsonElement> result = new LinkedHashMap<>();
        if (rule.isEmpty()) {
            result.put("Text", new JsonPrimitive("")); result.put("Number", new JsonPrimitive(0)); result.put("Toggle", new JsonPrimitive(false));
            result.put("Object", new JsonObject()); result.put("List", new JsonArray());
            return result;
        }
        alternatives(rule, result, 0);
        if (result.isEmpty()) result.put("New entry", seed(rule));
        return result;
    }

    private void alternatives(JsonObject rule, Map<String, JsonElement> result, int depth) {
        if (depth > 16) return;
        if (rule.has("$ref")) {
            alternatives(object(JsonPointers.get(root, rule.get("$ref").getAsString().substring(1))), result, depth + 1);
            return;
        }
        for (String keyword : List.of("anyOf", "oneOf")) if (rule.has(keyword)) {
            for (var branch : rule.getAsJsonArray(keyword)) alternatives(object(branch), result, depth + 1);
            return;
        }
        JsonObject fields = object(rule.get("properties"));
        JsonObject discriminator = object(fields.get("type"));
        if (discriminator.has("enum")) {
            boolean conditionTypes = discriminator.getAsJsonArray("enum").contains(new JsonPrimitive("player_dimension_visited"));
            for (var type : discriminator.getAsJsonArray("enum")) {
                JsonObject value = new JsonObject();
                value.add("type", type.deepCopy());
                JsonObject resolved = effective(rule, value);
                fillRequired(value, resolved, 0);
                JsonObject progress = conditionTypes ? EditorProgressConditions.seed(type.getAsString()) : null;
                if (progress != null) value = progress;
                String name = conditionTypes ? EditorProgressConditions.label(type.getAsString()) : type.getAsString();
                result.put(name.replace('_', ' '), value);
            }
        } else if (rule.has("enum")) {
            for (var value : rule.getAsJsonArray("enum")) result.put(value.getAsString(), value.deepCopy());
        } else {
            JsonElement value = seed(rule);
            String name = rule.has("title") ? rule.get("title").getAsString()
                    : value.isJsonObject() ? "Object" : value.isJsonArray() ? "List"
                    : value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? "Toggle"
                    : value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? "Number" : "Text";
            if (value.isJsonObject() && !value.getAsJsonObject().isEmpty()) {
                JsonObject object = value.getAsJsonObject();
                name = object.has("type") && object.get("type").isJsonPrimitive() ? object.get("type").getAsString()
                        : "Object (" + String.join(", ", object.keySet()) + ")";
            }
            String unique = name;
            int number = 2;
            while (result.containsKey(unique)) unique = name + " " + number++;
            result.put(unique, value);
        }
    }

    JsonElement seed(JsonObject rule) {
        return seed(rule, 0);
    }

    private JsonElement seed(JsonObject rule, int depth) {
        if (depth > 12) return JsonNull.INSTANCE;
        JsonObject resolved = effective(rule, JsonNull.INSTANCE);
        for (String field : List.of("default", "const")) if (resolved.has(field)) return resolved.get(field).deepCopy();
        if (resolved.has("enum") && !resolved.getAsJsonArray("enum").isEmpty()) return resolved.getAsJsonArray("enum").get(0).deepCopy();
        String type = resolved.has("type") ? resolved.get("type").getAsString()
                : resolved.has("properties") ? "object" : "string";
        return switch (type) {
            case "object" -> {
                JsonObject value = new JsonObject();
                fillRequired(value, resolved, depth + 1);
                yield value;
            }
            case "array" -> {
                JsonArray values = new JsonArray();
                int count = resolved.has("minItems") ? resolved.get("minItems").getAsInt() : 0;
                for (int i = 0; i < Math.min(count, 32); i++) values.add(seed(object(resolved.get("items")), depth + 1));
                yield values;
            }
            case "boolean" -> new JsonPrimitive(false);
            case "integer", "number" -> resolved.has("minimum") ? resolved.get("minimum").deepCopy() : new JsonPrimitive(0);
            case "null" -> JsonNull.INSTANCE;
            default -> new JsonPrimitive("");
        };
    }

    private void fillRequired(JsonObject value, JsonObject rule, int depth) {
        JsonObject fields = object(rule.get("properties"));
        if (rule.has("required")) for (var required : rule.getAsJsonArray("required")) {
            String name = required.getAsString();
            if (!value.has(name)) value.add(name, seed(object(fields.get(name)), depth + 1));
        }
    }

    List<String> required(JsonObject rule) {
        List<String> result = new ArrayList<>();
        if (rule.has("required")) rule.getAsJsonArray("required").forEach(value -> result.add(value.getAsString()));
        return result;
    }

    private int issues(JsonElement value, JsonObject rule) {
        JsonObject schema = rule.deepCopy();
        schema.add("$defs", root.get("$defs"));
        return QuestJsonSchema.validate(value, schema).size();
    }

    private JsonObject fallback(String field) {
        String definition = switch (field) {
            case "conditions", "available_when" -> "condition";
            case "actions", "on_enter", "on_exit", "entry_actions", "exit_actions" -> "action";
            case "objectives" -> "objective";
            default -> "";
        };
        if (!definition.isEmpty()) {
            JsonObject result = new JsonObject();
            result.addProperty("type", "array");
            JsonObject item = new JsonObject();
            item.addProperty("$ref", "#/$defs/" + definition);
            result.add("items", item);
            return result;
        }
        if (field.equals("camera")) return com.jvn.villagerretaliation.dialogue.DialogueCamera.schema();
        return new JsonObject();
    }

    static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
    }

    private static void merge(JsonObject target, JsonObject source) {
        for (var entry : source.entrySet()) {
            String name = entry.getKey();
            if (name.equals("properties") && target.has(name)) {
                entry.getValue().getAsJsonObject().entrySet().forEach(field -> target.getAsJsonObject(name).add(field.getKey(), field.getValue().deepCopy()));
            } else if (name.equals("required") && target.has(name)) {
                for (var field : entry.getValue().getAsJsonArray()) if (!target.getAsJsonArray(name).contains(field)) target.getAsJsonArray(name).add(field.deepCopy());
            } else target.add(name, entry.getValue().deepCopy());
        }
    }
}
