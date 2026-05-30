package com.jvn.villagerretaliation.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
                read(location, context + ".actions[" + index + "]", child.getAsJsonObject()).ifPresent(actions::add);
            }
            index++;
        }
        return List.copyOf(actions);
    }

    public static List<VillagerActionDefinition> readListOrInline(ResourceLocation location, String context, JsonObject entry) {
        List<VillagerActionDefinition> actions = readList(location, context, entry);
        if (!actions.isEmpty() || entry.has("actions") || !hasInlineAction(entry)) {
            return actions;
        }
        return read(location, context + ".action", entry).map(List::of).orElse(List.of());
    }

    public static boolean hasInlineAction(JsonObject entry) {
        return entry.has("type")
                || entry.has("kind")
                || entry.has("action")
                || entry.has("notification")
                || entry.has("notification_trigger")
                || entry.has("trigger")
                || entry.has("message")
                || entry.has("message_key")
                || entry.has("text")
                || entry.has("fallback")
                || entry.has("fallback_text")
                || entry.has("forced_dialogue")
                || entry.has("forced_dialogue_id")
                || entry.has("dialogue")
                || entry.has("dialogue_id")
                || entry.has("tracker")
                || entry.has("flash_tracker")
                || entry.has("quest")
                || entry.has("quest_id")
                || entry.has("xp")
                || entry.has("experience")
                || entry.has("reputation")
                || entry.has("gossip")
                || entry.has("gossip_reputation")
                || entry.has("memory")
                || entry.has("memory_event")
                || entry.has("tag")
                || entry.has("loot")
                || entry.has("loot_table");
    }

    private static java.util.Optional<VillagerActionDefinition> read(ResourceLocation location, String context, JsonObject entry) {
        Kind kind = inferKind(entry);
        if (kind == Kind.NONE) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action must define a supported type.");
            return java.util.Optional.empty();
        }

        ResourceLocation questId = DatapackJsonReader.readResourceLocation(entry, "quest")
                .or(() -> DatapackJsonReader.readResourceLocation(entry, "quest_id"))
                .orElse(null);
        QuestAction questAction = QuestAction.bySerializedName(
                DatapackJsonReader.readString(entry, "action", "quest_action"));
        int amount = DatapackJsonReader.readInt(entry, "amount", DatapackJsonReader.readInt(entry, "value", 0));
        if (amount == 0) {
            amount = DatapackJsonReader.readInt(entry, kind == Kind.EXPERIENCE ? "xp" : kind.serializedName(), 0);
        }
        if (amount == 0 && kind == Kind.GOSSIP) {
            amount = DatapackJsonReader.readInt(entry, "gossip_reputation", 0);
        }
        ResourceLocation memoryTag = readMemoryTag(location, context, entry);
        ResourceLocation lootTable = DatapackJsonReader.readResourceLocation(entry, "loot")
                .or(() -> DatapackJsonReader.readResourceLocation(entry, "loot_table"))
                .orElse(null);
        String notificationTrigger = firstNonBlank(
                DatapackJsonReader.readString(entry, "notification", "notification_trigger"),
                DatapackJsonReader.readString(entry, "message", "message_key"));
        notificationTrigger = firstNonBlank(notificationTrigger, DatapackJsonReader.readString(entry, "trigger"));
        String text = DatapackJsonReader.readString(entry, "text", "fallback", "fallback_text");
        String forcedDialogue = DatapackJsonReader.readString(entry, "forced_dialogue", "forced_dialogue_id", "dialogue", "dialogue_id");
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

    private static Kind inferKind(JsonObject entry) {
        Kind explicit = Kind.bySerializedName(DatapackJsonReader.readString(entry, "type", "kind"));
        if (explicit != Kind.NONE) {
            return explicit;
        }
        Kind actionKind = Kind.bySerializedName(DatapackJsonReader.readString(entry, "action"));
        if (actionKind != Kind.NONE) {
            return actionKind;
        }
        String action = DatapackJsonReader.readString(entry, "action");
        if (!action.isBlank() && entry.has("quest")) {
            return Kind.QUEST;
        }
        if (!DatapackJsonReader.readString(entry, "forced_dialogue", "forced_dialogue_id", "dialogue", "dialogue_id").isBlank()) {
            return Kind.FORCED_DIALOGUE;
        }
        if (entry.has("flash_tracker") || entry.has("tracker")) {
            return Kind.TRACKER;
        }
        if (entry.has("quest") || entry.has("quest_id")) {
            return Kind.QUEST;
        }
        if (entry.has("xp") || entry.has("experience")) {
            return Kind.EXPERIENCE;
        }
        if (entry.has("gossip") || entry.has("gossip_reputation")) {
            return Kind.GOSSIP;
        }
        if (entry.has("reputation")) {
            return Kind.REPUTATION;
        }
        if (entry.has("memory") || entry.has("memory_event") || entry.has("tag")) {
            return Kind.MEMORY;
        }
        if (entry.has("loot") || entry.has("loot_table")) {
            return Kind.LOOT;
        }
        if (entry.has("notification") || entry.has("notification_trigger") || entry.has("message")
                || entry.has("message_key") || entry.has("text") || entry.has("fallback") || entry.has("fallback_text")) {
            return Kind.NOTIFICATION;
        }
        return Kind.NONE;
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
        String value = DatapackJsonReader.readString(entry, "memory", "memory_event", "tag");
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

    private static Map<String, List<String>> copyLines(Map<String, List<String>> linesByStatus) {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : linesByStatus.entrySet()) {
            copy.put(normalizeStatus(entry.getKey()), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
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
                case "notification", "notify", "message" -> NOTIFICATION;
                case "tracker", "quest_tracker", "flash_tracker" -> TRACKER;
                case "forced_dialogue", "dialogue", "forced" -> FORCED_DIALOGUE;
                case "quest" -> QUEST;
                case "xp", "experience" -> EXPERIENCE;
                case "reputation" -> REPUTATION;
                case "gossip", "gossip_reputation" -> GOSSIP;
                case "memory", "village_memory" -> MEMORY;
                case "loot", "give_loot", "loot_table" -> LOOT;
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
