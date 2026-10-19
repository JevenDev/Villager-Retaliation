package com.jvn.villagerretaliation.authoring.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.JsonPointers;
import java.util.ArrayList;
import java.util.List;

final class EditorGraphInline {
    record Field(AuthoringDocument document, String pointer, String label, JsonElement value, boolean multiline, boolean editable, List<String> options) {
        Field(AuthoringDocument document, String pointer, String label, JsonElement value, boolean multiline, boolean editable) {
            this(document, pointer, label, value, multiline, editable, List.of());
        }
        float height() { return multiline ? 104 : 54; }
        String bufferId(AuthoringDocument selected) {
            return document == selected ? pointer : document.resourcePath() + pointer;
        }
    }

    private EditorGraphInline() { }

    static List<Field> fields(AuthoringWorkspace workspace, AuthoringDocument document, JsonObject root,
                              EditorGraph.Block block, String locale, EditorSchema schema) {
        var fields = new ArrayList<Field>();
        JsonElement value = JsonPointers.get(root, block.pointer());
        if (List.of("Say", "Say one of", "Conversation").contains(block.title())) {
            text(fields, workspace, document, block.pointer(), value, "Dialogue text", true, locale, 0);
        } else if (block.title().equals("Dialogue") && value.isJsonObject()) {
            for (var slot : value.getAsJsonObject().entrySet())
                text(fields, workspace, document, block.pointer() + "/" + JsonPointers.escape(slot.getKey()), slot.getValue(), label(slot.getKey()), true, locale, 0);
        } else if (block.title().equals("Scenes") && value.isJsonArray()) {
            text(fields, workspace, document, block.pointer(), value, "Scene text", true, locale, 0);
        } else if (List.of("Choice", "End choice").contains(block.title()) && value.isJsonObject()) {
            JsonElement label = value.getAsJsonObject().get("label");
            if (label != null) text(fields, workspace, document, block.pointer() + "/label", label, "Player response", true, locale, 0);
        } else if (List.of("Do", "On entry", "On exit", "Only if", "Choice only if", "Choice does", "Objectives").contains(block.title())
                && value.isJsonObject()) {
            String path = block.pointer();
            JsonObject object = value.getAsJsonObject();
            if (object.has("action") && object.get("action").isJsonObject()) {
                object = object.getAsJsonObject("action"); path += "/action";
            }
            for (var entry : object.entrySet()) {
                String name = entry.getKey();
                JsonElement scalar = entry.getValue();
                if (List.of("text", "line", "label").contains(name))
                    text(fields, workspace, document, path + "/" + name, scalar, label(name), true, locale, 0);
                else if (scalar.isJsonPrimitive() && List.of("amount", "min", "max", "count", "item", "tag", "entity", "quest",
                        "state", "time", "weather", "enabled", "repeatable", "all", "radius", "chance", "duration", "seconds").contains(name))
                    fields.add(scalar(document, path + "/" + name, label(name), scalar, root, schema));
            }
        }
        return List.copyOf(fields);
    }

    private static Field scalar(AuthoringDocument document, String path, String label, JsonElement value, JsonObject root, EditorSchema schema) {
        JsonObject rule = value.getAsJsonPrimitive().isString() ? schema.at(root, path) : new JsonObject();
        var options = rule.has("enum") && rule.get("enum").isJsonArray()
                ? rule.getAsJsonArray("enum").asList().stream().map(JsonElement::getAsString).toList() : List.<String>of();
        return new Field(document, path, label, value, false, true, options);
    }

    private static void text(List<Field> fields, AuthoringWorkspace workspace, AuthoringDocument document, String path,
                             JsonElement value, String label, boolean editable, String locale, int depth) {
        if (value == null || depth > 16) return;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            fields.add(new Field(document, path, label, value, true, editable));
        } else if (value.isJsonArray()) {
            var array = value.getAsJsonArray();
            for (int i = 0; i < array.size(); i++)
                text(fields, workspace, document, path + "/" + i, array.get(i),
                        array.size() == 1 ? label : label + " " + (i + 1), editable, locale, depth + 1);
        } else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            for (String field : List.of("text", "line", "lines", "variants")) {
                if (!object.has(field)) continue;
                text(fields, workspace, document, path + "/" + field, object.get(field), label, editable, locale, depth + 1);
                return;
            }
            if (workspace == null || !object.has("key") || !object.get("key").isJsonPrimitive()) return;
            var location = workspace.textLocation(document, object.get("key").getAsString(), locale);
            if (location == null) {
                fields.add(new Field(document, path, "Missing translation", new JsonPrimitive(workspace.resolveText(document, value, locale)), true, false));
                return;
            }
            boolean fallback = !locale.equals("en_us") && !location.document().resourcePath().endsWith("/locales/" + locale + ".json")
                    && !location.document().resourcePath().contains("/dialogue/" + locale + "/");
            text(fields, workspace, location.document(), location.pointer(), location.document().value(location.pointer()),
                    fallback ? label + " (English fallback)" : label, editable && !fallback, locale, depth + 1);
        }
    }

    private static String label(String name) {
        return switch (name) {
            case "min" -> "Minimum";
            case "max" -> "Maximum";
            case "all" -> "Require all";
            default -> Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
        };
    }
}
