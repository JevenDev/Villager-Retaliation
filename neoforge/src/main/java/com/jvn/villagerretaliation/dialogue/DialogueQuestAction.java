package com.jvn.villagerretaliation.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record DialogueQuestAction(ResourceLocation questId, Action action) {
    public static final DialogueQuestAction EMPTY = new DialogueQuestAction(null, Action.NONE);

    public boolean isEmpty() {
        return this.questId == null || this.action == Action.NONE;
    }

    public static DialogueQuestAction read(ResourceLocation location, String context, JsonObject option) {
        JsonElement element = option.get("quest_action");
        if (element == null || element.isJsonNull()) {
            return EMPTY;
        }
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "quest_action must be an object.");
            return EMPTY;
        }

        JsonObject actionObject = element.getAsJsonObject();
        ResourceLocation questId = readQuestId(actionObject);
        if (questId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "quest_action must define quest or quest_id.");
            return EMPTY;
        }

        Action action = Action.bySerializedName(readString(actionObject, "action"));
        if (action == Action.NONE) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "quest_action uses an unknown action.");
            return EMPTY;
        }
        return new DialogueQuestAction(questId, action);
    }

    private static ResourceLocation readQuestId(JsonObject object) {
        for (String key : new String[] { "quest", "quest_id", "id" }) {
            String value = readString(object, key);
            if (!value.isBlank()) {
                return ResourceLocation.tryParse(value);
            }
        }
        return null;
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString().trim();
    }

    public enum Action {
        NONE,
        START,
        REMIND,
        TURN_IN;

        public static Action bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "start", "accept", "begin" -> START;
                case "remind", "reminder", "details" -> REMIND;
                case "turn_in", "turnin", "complete", "claim" -> TURN_IN;
                default -> NONE;
            };
        }
    }
}
