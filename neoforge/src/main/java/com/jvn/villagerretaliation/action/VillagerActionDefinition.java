package com.jvn.villagerretaliation.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import com.jvn.villagerretaliation.village.VillageEventMemory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record VillagerActionDefinition(
        Kind kind,
        ResourceLocation questId,
        QuestAction questAction,
        int amount,
        ResourceLocation memoryTag,
        ResourceLocation lootTable,
        String notificationTrigger,
        String text,
        String forcedDialogue,
        boolean flashTracker,
        Map<String, List<String>> linesByStatus) {
    public VillagerActionDefinition {
        kind = kind == null ? Kind.NONE : kind;
        questAction = questAction == null ? QuestAction.NONE : questAction;
        notificationTrigger = notificationTrigger == null ? "" : notificationTrigger;
        text = text == null ? "" : text;
        forcedDialogue = forcedDialogue == null ? "" : forcedDialogue;
        linesByStatus = linesByStatus == null ? Map.of() : copyLines(linesByStatus);
    }

    public static List<VillagerActionDefinition> readList(ResourceLocation location, String context, JsonObject entry) {
        return readList(location, context, entry, null);
    }

    public static List<VillagerActionDefinition> readList(
            ResourceLocation location,
            String context,
            JsonObject entry,
            ResourceLocation defaultQuestId) {
        JsonElement element = entry.get("actions");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "actions must be an array.");
            return List.of();
        }

        List<VillagerActionDefinition> actions = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                read(location, context + ".actions[" + index + "]", child.getAsJsonObject(), defaultQuestId).ifPresent(actions::add);
            }
            index++;
        }
        return List.copyOf(actions);
    }

    public static List<VillagerActionDefinition> readListOrInline(ResourceLocation location, String context, JsonObject entry) {
        return readListOrInline(location, context, entry, null);
    }

    public static List<VillagerActionDefinition> readListOrInline(
            ResourceLocation location,
            String context,
            JsonObject entry,
            ResourceLocation defaultQuestId) {
        List<VillagerActionDefinition> actions = readList(location, context, entry, defaultQuestId);
        if (!actions.isEmpty() || entry.has("actions") || !hasInlineAction(entry)) {
            return actions;
        }
        return read(location, context + ".action", entry, defaultQuestId).map(List::of).orElse(List.of());
    }

    public static boolean hasInlineAction(JsonObject entry) {
        return entry.has("type")
                || entry.has("notification")
                || entry.has("trigger")
                || entry.has("text")
                || entry.has("forced_dialogue")
                || entry.has("flash_tracker")
                || entry.has("quest")
                || entry.has("quest_id")
                || entry.has("action")
                || entry.has("experience")
                || entry.has("reputation")
                || entry.has("gossip")
                || entry.has("gossip_reputation")
                || entry.has("memory_event")
                || entry.has("loot_table");
    }

    private static java.util.Optional<VillagerActionDefinition> read(
            ResourceLocation location,
            String context,
            JsonObject entry,
            ResourceLocation defaultQuestId) {
        Kind kind = inferKind(entry, defaultQuestId);
        if (kind == Kind.NONE) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action must define a supported type.");
            return java.util.Optional.empty();
        }

        boolean hasExplicitQuestId = hasQuestIdField(entry);
        ResourceLocation questId = readQuestId(location, entry);
        if (questId == null && kind == Kind.QUEST && !hasExplicitQuestId) {
            questId = defaultQuestId;
        }
        QuestAction questAction = QuestAction.bySerializedName(
                DatapackJsonReader.readString(entry, "action"));
        int amount = readAmount(kind, entry);
        ResourceLocation memoryTag = readMemoryTag(location, context, entry);
        ResourceLocation lootTable = DatapackJsonReader.readResourceLocation(entry, "loot_table").orElse(null);
        String notificationTrigger = firstNonBlank(
                DatapackJsonReader.readString(entry, "notification"),
                DatapackJsonReader.readString(entry, "trigger"));
        String text = DatapackJsonReader.readString(entry, "text");
        String forcedDialogue = DatapackJsonReader.readString(entry, "forced_dialogue");
        boolean flashTracker = DatapackJsonReader.readBoolean(entry, "flash_tracker", true);

        if (!hasRequiredFields(location, context, kind, questId, questAction, memoryTag, lootTable, notificationTrigger, text, forcedDialogue)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new VillagerActionDefinition(
                kind,
                questId,
                questAction,
                amount,
                memoryTag,
                lootTable,
                notificationTrigger,
                text,
                forcedDialogue,
                flashTracker,
                readLinesByStatus(entry)));
    }

    private static Kind inferKind(JsonObject entry, ResourceLocation defaultQuestId) {
        Kind explicit = Kind.bySerializedName(DatapackJsonReader.readString(entry, "type"));
        if (explicit != Kind.NONE) {
            return explicit;
        }
        if (hasQuestIdField(entry)) {
            return Kind.QUEST;
        }
        if (defaultQuestId != null && entry.has("action")) {
            return Kind.QUEST;
        }
        if (entry.has("forced_dialogue")) {
            return Kind.FORCED_DIALOGUE;
        }
        if (entry.has("loot_table")) {
            return Kind.LOOT;
        }
        if (entry.has("memory_event")) {
            return Kind.MEMORY;
        }
        if (entry.has("experience")) {
            return Kind.EXPERIENCE;
        }
        if (entry.has("reputation")) {
            return Kind.REPUTATION;
        }
        if (entry.has("gossip") || entry.has("gossip_reputation")) {
            return Kind.GOSSIP;
        }
        if (entry.has("flash_tracker")) {
            return Kind.TRACKER;
        }
        if (entry.has("notification") || entry.has("trigger") || entry.has("text")) {
            return Kind.NOTIFICATION;
        }
        return Kind.NONE;
    }

    private static boolean hasQuestIdField(JsonObject entry) {
        return entry.has("quest") || entry.has("quest_id") || entry.has("id");
    }

    private static int readAmount(Kind kind, JsonObject entry) {
        Integer explicitAmount = DatapackJsonReader.readNullableInt(entry, "amount");
        if (explicitAmount != null) {
            return explicitAmount;
        }
        return switch (kind) {
            case EXPERIENCE -> DatapackJsonReader.readInt(entry, "experience", 0);
            case REPUTATION -> DatapackJsonReader.readInt(entry, "reputation", 0);
            case GOSSIP -> DatapackJsonReader.readInt(
                    entry,
                    "gossip",
                    DatapackJsonReader.readInt(entry, "gossip_reputation", 0));
            default -> 0;
        };
    }

    private static ResourceLocation readQuestId(ResourceLocation location, JsonObject entry) {
        for (String key : List.of("quest", "quest_id", "id")) {
            String value = DatapackJsonReader.readString(entry, key);
            if (!value.isBlank()) {
                return QuestIds.parse(value, location);
            }
        }
        return null;
    }

    private static boolean hasRequiredFields(
            ResourceLocation location,
            String context,
            Kind kind,
            ResourceLocation questId,
            QuestAction questAction,
            ResourceLocation memoryTag,
            ResourceLocation lootTable,
            String notificationTrigger,
            String text,
            String forcedDialogue) {
        boolean valid = switch (kind) {
            case QUEST -> questId != null && questAction != QuestAction.NONE;
            case MEMORY -> memoryTag != null;
            case LOOT -> lootTable != null;
            case FORCED_DIALOGUE -> !forcedDialogue.isBlank();
            case NOTIFICATION -> !notificationTrigger.isBlank() || !text.isBlank();
            case TRACKER, EXPERIENCE, REPUTATION, GOSSIP -> true;
            case NONE -> false;
        };
        if (!valid) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action is missing required fields for type \"" + kind.serializedName() + "\".");
        }
        return valid;
    }

    private static ResourceLocation readMemoryTag(ResourceLocation location, String context, JsonObject entry) {
        String value = DatapackJsonReader.readString(entry, "memory_event");
        if (value.isBlank()) {
            return null;
        }
        ResourceLocation tagId = VillageEventMemory.parseTagId(value).orElse(null);
        if (tagId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "memory action uses invalid tag \"" + value + "\".");
        }
        return tagId;
    }

    private static Map<String, List<String>> readLinesByStatus(JsonObject entry) {
        JsonObject lines = DatapackJsonReader.readObject(entry, "lines");
        if (lines == null) {
            return Map.of();
        }

        Map<String, List<String>> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> child : lines.entrySet()) {
            List<String> variants = readTextVariants(child.getValue());
            if (!variants.isEmpty()) {
                values.put(normalizeStatus(child.getKey()), variants);
            }
        }
        return Map.copyOf(values);
    }

    private static List<String> readTextVariants(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (element.isJsonPrimitive()) {
            String value = element.getAsString().trim();
            return value.isBlank() ? List.of() : List.of(value);
        }
        if (!element.isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonPrimitive()) {
                String value = child.getAsString().trim();
                if (!value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return List.copyOf(values);
    }

    public List<String> linesForStatus(String status) {
        return this.linesByStatus.getOrDefault(normalizeStatus(status), List.of());
    }

    private static String normalizeStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static Map<String, List<String>> copyLines(Map<String, List<String>> linesByStatus) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : linesByStatus.entrySet()) {
            copy.put(normalizeStatus(entry.getKey()), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    public enum Kind {
        NONE("none"),
        NOTIFICATION("notification"),
        TRACKER("tracker"),
        FORCED_DIALOGUE("forced_dialogue"),
        QUEST("quest"),
        EXPERIENCE("experience"),
        REPUTATION("reputation"),
        GOSSIP("gossip"),
        MEMORY("memory"),
        LOOT("loot");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static Kind bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "notification", "notify", "hud", "message" -> NOTIFICATION;
                case "tracker", "quest_tracker", "flash_tracker" -> TRACKER;
                case "forced_dialogue", "force_dialogue", "dialogue" -> FORCED_DIALOGUE;
                case "quest", "quest_action" -> QUEST;
                case "experience", "xp" -> EXPERIENCE;
                case "reputation", "rep" -> REPUTATION;
                case "gossip", "gossip_reputation" -> GOSSIP;
                case "memory", "memory_event" -> MEMORY;
                case "loot", "loot_table" -> LOOT;
                default -> NONE;
            };
        }
    }

    public enum QuestAction {
        NONE,
        START,
        REMIND,
        TURN_IN,
        ABANDON;

        public static QuestAction bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "start", "accept", "begin" -> START;
                case "remind", "reminder", "details" -> REMIND;
                case "turn_in", "turnin", "complete", "claim" -> TURN_IN;
                case "abandon", "drop", "cancel", "remove" -> ABANDON;
                default -> NONE;
            };
        }
    }
}
