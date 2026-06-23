package com.jvn.villagerretaliation.dialogue.normal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record DialogueQuestAction(ResourceLocation questId, Action action) {
    public static final DialogueQuestAction EMPTY = new DialogueQuestAction(null, Action.NONE);

    public boolean isEmpty() {
        return this.questId == null || this.action == Action.NONE;
    }

    public static DialogueQuestAction read(ResourceLocation location, String context, JsonObject option) {
        JsonElement element = option.get("quest_action");
        if (element != null && !element.isJsonNull()) {
            return readLegacyQuestAction(location, context, element);
        }
        return readSharedQuestAction(location, context, option);
    }

    private static DialogueQuestAction readLegacyQuestAction(ResourceLocation location, String context, JsonElement element) {
        if (!element.isJsonObject()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "quest_action must be an object.");
            return EMPTY;
        }

        JsonObject actionObject = element.getAsJsonObject();
        ResourceLocation questId = readQuestId(location, actionObject);
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

    private static DialogueQuestAction readSharedQuestAction(ResourceLocation location, String context, JsonObject option) {
        if (!option.has("actions") && !hasInlineQuestAction(option)) {
            return EMPTY;
        }
        List<VillagerActionDefinition> actions = VillagerActionDefinition.readListOrInline(location, context, option);
        for (VillagerActionDefinition action : actions) {
            if (action.kind() != VillagerActionDefinition.Kind.QUEST) {
                continue;
            }
            Action questAction = Action.fromShared(action.questAction());
            if (action.questId() != null && questAction != Action.NONE) {
                return new DialogueQuestAction(action.questId(), questAction);
            }
        }
        return EMPTY;
    }

    private static boolean hasInlineQuestAction(JsonObject option) {
        VillagerActionDefinition.Kind explicit = VillagerActionDefinition.Kind.bySerializedName(readString(option, "type"));
        return explicit == VillagerActionDefinition.Kind.QUEST
                || option.has("quest")
                || option.has("quest_id");
    }

    private static ResourceLocation readQuestId(ResourceLocation location, JsonObject object) {
        for (String key : new String[] { "quest", "quest_id", "id" }) {
            String value = readString(object, key);
            if (!value.isBlank()) {
                return QuestIds.parse(value, location);
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
        TURN_IN,
        ABANDON,
        BLOCK;

        public static Action bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "start", "accept", "begin" -> START;
                case "remind", "reminder", "details" -> REMIND;
                case "turn_in", "turnin", "complete", "claim" -> TURN_IN;
                case "abandon", "drop", "cancel", "remove" -> ABANDON;
                case "block", "lock", "consume", "close", "close_branch", "branch_lock" -> BLOCK;
                default -> NONE;
            };
        }

        private static Action fromShared(VillagerActionDefinition.QuestAction action) {
            if (action == null) {
                return NONE;
            }
            return switch (action) {
                case START -> START;
                case REMIND -> REMIND;
                case TURN_IN -> TURN_IN;
                case ABANDON -> ABANDON;
                case BLOCK -> BLOCK;
                case NONE -> NONE;
            };
        }
    }
}
