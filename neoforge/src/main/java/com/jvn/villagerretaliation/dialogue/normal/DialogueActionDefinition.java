package com.jvn.villagerretaliation.dialogue.normal;

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

public record DialogueActionDefinition(
        Kind kind,
        ResourceLocation questId,
        DialogueQuestAction.Action questAction,
        int amount,
        ResourceLocation lootTable,
        VillageEventMemory.EventTag memoryEvent,
        Map<String, List<String>> linesByStatus
) {
    public DialogueActionDefinition {
        kind = kind == null ? Kind.NONE : kind;
        questAction = questAction == null ? DialogueQuestAction.Action.NONE : questAction;
        linesByStatus = linesByStatus == null ? Map.of() : copyLines(linesByStatus);
    }

    public static List<DialogueActionDefinition> readList(ResourceLocation location, String context, JsonObject entry) {
        JsonElement element = entry.get("actions");
        if (element == null || element.isJsonNull()) {
            return List.of();
        }
        if (!element.isJsonArray()) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "actions must be an array.");
            return List.of();
        }

        List<DialogueActionDefinition> actions = new ArrayList<>();
        int index = 0;
        for (JsonElement child : element.getAsJsonArray()) {
            if (child.isJsonObject()) {
                DialogueActionDefinition action = read(location, context + ".actions[" + index + "]", child.getAsJsonObject());
                if (action.kind() != Kind.NONE) {
                    actions.add(action);
                }
            }
            index++;
        }
        return List.copyOf(actions);
    }

    private static DialogueActionDefinition read(ResourceLocation location, String context, JsonObject entry) {
        Kind kind = Kind.bySerializedName(DatapackJsonReader.readString(entry, "type", "kind"));
        if (kind == Kind.NONE && entry.has("quest")) {
            kind = Kind.QUEST;
        }
        if (kind == Kind.NONE) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action must define a supported type.");
            return empty();
        }

        ResourceLocation questId = readQuestId(location, entry);
        DialogueQuestAction.Action questAction = DialogueQuestAction.Action.bySerializedName(
                DatapackJsonReader.readString(entry, "action", "quest_action"));
        int amount = DatapackJsonReader.readInt(entry, "amount", DatapackJsonReader.readInt(entry, "value", 0));
        ResourceLocation lootTable = DatapackJsonReader.readResourceLocation(entry, "loot")
                .or(() -> DatapackJsonReader.readResourceLocation(entry, "loot_table"))
                .orElse(null);
        return new DialogueActionDefinition(
                kind,
                questId,
                questAction,
                amount,
                lootTable,
                readMemoryEvent(entry),
                readLinesByStatus(entry)
        );
    }

    private static DialogueActionDefinition empty() {
        return new DialogueActionDefinition(Kind.NONE, null, DialogueQuestAction.Action.NONE, 0, null, null, Map.of());
    }

    private static ResourceLocation readQuestId(ResourceLocation location, JsonObject entry) {
        for (String key : List.of("quest", "quest_id")) {
            ResourceLocation questId = QuestIds.parse(DatapackJsonReader.readString(entry, key), location);
            if (questId != null) {
                return questId;
            }
        }
        return null;
    }

    private static VillageEventMemory.EventTag readMemoryEvent(JsonObject entry) {
        String value = DatapackJsonReader.readString(entry, "memory", "memory_event", "tag");
        if (value.isBlank()) {
            return null;
        }
        try {
            return VillageEventMemory.EventTag.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
        NONE,
        QUEST,
        EXPERIENCE,
        REPUTATION,
        GOSSIP,
        MEMORY,
        LOOT;

        public static Kind bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
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
}
