package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;

public record DialogueChoice(boolean showUnavailable, String unavailableText, String unavailableKey) {
    public static final DialogueChoice HIDDEN = new DialogueChoice(false, "", "");
    public static final DialogueChoice DISABLED = new DialogueChoice(true, "", "");

    public static DialogueChoice read(JsonObject data, DialogueChoice fallback) {
        boolean show = fallback.showUnavailable();
        if (data.has("unavailable_behavior")) {
            if (!data.get("unavailable_behavior").isJsonPrimitive()
                    || !data.getAsJsonPrimitive("unavailable_behavior").isString())
                throw new IllegalArgumentException("unavailable_behavior must be hide or disable");
            String behavior = data.get("unavailable_behavior").getAsString();
            if (!behavior.equals("hide") && !behavior.equals("disable"))
                throw new IllegalArgumentException("unavailable_behavior must be hide or disable");
            show = behavior.equals("disable");
        }
        String text = "", key = "";
        if (data.has("unavailable")) {
            var value = data.get("unavailable");
            if (value.isJsonObject() && value.getAsJsonObject().keySet().equals(java.util.Set.of("key"))
                    && value.getAsJsonObject().get("key").isJsonPrimitive()
                    && value.getAsJsonObject().getAsJsonPrimitive("key").isString()
                    && !value.getAsJsonObject().get("key").getAsString().isBlank()) {
                key = value.getAsJsonObject().get("key").getAsString();
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                text = value.getAsString();
            } else throw new IllegalArgumentException("unavailable must be text or a localized reference");
        }
        return new DialogueChoice(show, text, key);
    }

    public String reason(DialogueContext context) {
        return reason((DialogueRuntimeContext) context);
    }

    public String reason(DialogueRuntimeContext context) {
        String fallback = unavailableText.isBlank()
                ? context.message("dialogue.choice.unavailable")
                        .orElse("This response's requirements are not currently met.")
                : unavailableText;
        String text = unavailableKey.isBlank() ? fallback
                : context.message(unavailableKey).orElse(fallback);
        return DialogueTemplates.resolveTemplate(text,
                com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.base(context), context);
    }

    public static JsonObject behaviorSchema() {
        var schema = new JsonObject();
        schema.addProperty("type", "string");
        var values = new com.google.gson.JsonArray();
        values.add("hide");
        values.add("disable");
        schema.add("enum", values);
        return schema;
    }
}
