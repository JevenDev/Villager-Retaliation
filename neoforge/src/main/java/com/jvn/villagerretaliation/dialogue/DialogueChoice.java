package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;

public record DialogueChoice(boolean showUnavailable, String unavailableText, String unavailableKey) {
    public static final DialogueChoice HIDDEN = new DialogueChoice(false, "", "");
    public static final DialogueChoice DISABLED = new DialogueChoice(true, "", "");

    public static DialogueChoice read(JsonObject data, DialogueChoice fallback) {
        boolean show = fallback.showUnavailable();
        if (data.has("unavailable_behavior")) {
            String behavior = data.get("unavailable_behavior").getAsString();
            if (!behavior.equals("hide") && !behavior.equals("disable"))
                throw new IllegalArgumentException("unavailable_behavior must be hide or disable");
            show = behavior.equals("disable");
        }
        String text = "", key = "";
        if (data.has("unavailable")) {
            var value = data.get("unavailable");
            if (value.isJsonObject()) key = value.getAsJsonObject().get("key").getAsString();
            else text = value.getAsString();
        }
        return new DialogueChoice(show, text, key);
    }

    public String reason(DialogueContext context) {
        String fallback = unavailableText.isBlank()
                ? VillagerDialogueResources.message(context, "dialogue.choice.unavailable")
                        .orElse("This response's requirements are not currently met.")
                : unavailableText;
        String text = unavailableKey.isBlank() ? fallback
                : VillagerDialogueResources.message(context, unavailableKey).orElse(fallback);
        return VillagerDialogueResources.resolveTemplate(text,
                com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.base(context));
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
