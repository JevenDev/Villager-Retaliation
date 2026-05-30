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
                || entry.has("notification")
                || entry.has("text")
                || entry.has("forced_dialogue")
                || entry.has("flash_tracker")
                || entry.has("quest")
                || entry.has("experience")
                || entry.has("reputation")
                || entry.has("gossip")
                || entry.has("memory_event")
                || entry.has("loot_table");
    }

    private static java.util.Optional<VillagerActionDefinition> read(ResourceLocation location, String context, JsonObject entry) {
        Kind kind = inferKind(entry);
        if (kind == Kind.NONE) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action must define a supported type.");
            return java.util.Optional.empty();
        }

        ResourceLocation questId = readQuestId(location, entry);
        QuestAction questAction = QuestAction.bySerializedName(
                DatapackJsonReader.readString(entry, "action"));
        int amount = DatapackJsonReader.readInt(entry, "amount", 0);
        ResourceLocation memoryTag = readMemoryTag(location, context, entry);
        ResourceLocation lootTable = DatapackJsonReader.readResourceLocation(entry, "loot_table").orElse(null);
        String notificationTrigger = DatapackJsonReader.readString(entry, "notification");
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

    private static Kind inferKind(JsonObject entry) {
        Kind explicit = Kind.bySerializedName(DatapackJsonReader.readString(entry, "type"));
        if (explicit != Kind.NONE) {
            return explicit;
        }
        return Kind.NONE;
    }

    private static ResourceLocation readQuestId(ResourceLocation location, JsonObject entry) {
        return QuestIds.parse(DatapackJsonReader.readString(entry, "quest"), location);
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
                case "notification" -> NOTIFICATION;
                case "tracker" -> TRACKER;
                case "forced_dialogue" -> FORCED_DIALOGUE;
                case "quest" -> QUEST;
                case "experience" -> EXPERIENCE;
                case "reputation" -> REPUTATION;
                case "gossip" -> GOSSIP;
                case "memory" -> MEMORY;
                case "loot" -> LOOT;
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
                case "start" -> START;
                case "remind" -> REMIND;
                case "turn_in" -> TURN_IN;
                case "abandon" -> ABANDON;
                default -> NONE;
            };
        }
    }
}
