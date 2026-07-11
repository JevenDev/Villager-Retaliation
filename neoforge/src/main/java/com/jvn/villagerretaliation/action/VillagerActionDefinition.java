package com.jvn.villagerretaliation.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.quest.QuestFactScope;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestTransition;
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
        QuestFactScope factScope,
        ResourceLocation factTag,
        String factKey,
        String factValue,
        Map<String, List<String>> linesByStatus,
        CompiledQuestTransition questTransition,
        ResourceLocation sceneId,
        String sceneOperationId,
        boolean waitForScene,
        boolean required) {
    public VillagerActionDefinition {
        kind = kind == null ? Kind.NONE : kind;
        questAction = questAction == null ? QuestAction.NONE : questAction;
        notificationTrigger = notificationTrigger == null ? "" : notificationTrigger;
        text = text == null ? "" : text;
        forcedDialogue = forcedDialogue == null ? "" : forcedDialogue;
        factScope = factScope == null ? QuestFactScope.PLAYER : factScope;
        factKey = factKey == null ? "" : factKey;
        factValue = factValue == null ? "" : factValue;
        linesByStatus = linesByStatus == null ? Map.of() : copyLines(linesByStatus);
        questTransition = questTransition == null ? CompiledQuestTransition.EMPTY : questTransition;
        sceneOperationId = sceneOperationId == null ? "" : sceneOperationId.trim();
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
                || entry.has("set_tag")
                || entry.has("clear_tag")
                || entry.has("fact_tag")
                || entry.has("quest_tag")
                || entry.has("variable")
                || entry.has("stage")
                || entry.has("counter")
                || entry.has("increment_counter")
                || entry.has("experience")
                || entry.has("reputation")
                || entry.has("gossip")
                || entry.has("gossip_reputation")
                || entry.has("memory_event")
                || entry.has("loot_table")
                || entry.has("target_stage")
                || entry.has("from_stage")
                || entry.has("scene_path")
                || entry.has("scene_id")
                || entry.has("start_scene")
                || entry.has("source_pointer");
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
        if (questId == null && (kind == Kind.QUEST || kind == Kind.QUEST_TRANSITION || kind.isQuestFact()) && !hasExplicitQuestId) {
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
        QuestFactScope factScope = readFactScope(entry, kind, questId);
        ResourceLocation factTag = readFactTag(location, context, entry, kind);
        String factKey = readFactKey(entry, kind);
        String factValue = readFactValue(entry, kind);
        CompiledQuestTransition questTransition = kind == Kind.QUEST_TRANSITION
                ? CompiledQuestTransition.read(location, entry, questId)
                : CompiledQuestTransition.EMPTY;
        ResourceLocation sceneId = kind == Kind.START_SCENE ? readSceneId(location, entry) : null;
        String sceneOperationId = DatapackJsonReader.readString(entry, "operation_id", "scene_operation_id");
        boolean waitForScene = DatapackJsonReader.readBoolean(entry, "wait_for_result", false);
        boolean required = DatapackJsonReader.readBoolean(entry, "required", false);

        if (!hasRequiredFields(
                location,
                context,
                kind,
                questId,
                questAction,
                memoryTag,
                lootTable,
                notificationTrigger,
                text,
                forcedDialogue,
                factTag,
                factKey,
                factValue,
                questTransition,
                sceneId,
                sceneOperationId)) {
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
                factScope,
                factTag,
                factKey,
                factValue,
                readLinesByStatus(entry),
                questTransition,
                sceneId,
                sceneOperationId,
                waitForScene,
                required));
    }

    private static Kind inferKind(JsonObject entry, ResourceLocation defaultQuestId) {
        Kind explicit = Kind.bySerializedName(DatapackJsonReader.readString(entry, "type"));
        if (explicit != Kind.NONE) {
            return explicit;
        }
        if (entry.has("set_tag") || entry.has("fact_tag") || entry.has("quest_tag")) {
            return Kind.SET_TAG;
        }
        if (entry.has("clear_tag")) {
            return Kind.CLEAR_TAG;
        }
        if (entry.has("variable") || (entry.has("key") && entry.has("value"))) {
            return Kind.SET_VARIABLE;
        }
        if (entry.has("stage")) {
            return Kind.SET_VARIABLE;
        }
        if (entry.has("target_stage") || entry.has("from_stage") || entry.has("scene_path") || entry.has("source_pointer")) {
            return Kind.QUEST_TRANSITION;
        }
        if (entry.has("scene_id") || entry.has("start_scene")) {
            return Kind.START_SCENE;
        }
        if (entry.has("counter") || entry.has("increment_counter")) {
            return Kind.COUNTER;
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
            case COUNTER -> {
                Integer by = DatapackJsonReader.readNullableInt(entry, "by");
                if (by == null) {
                    by = DatapackJsonReader.readNullableInt(entry, "delta");
                }
                yield by == null ? 1 : by;
            }
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
            String forcedDialogue,
            ResourceLocation factTag,
            String factKey,
            String factValue,
            CompiledQuestTransition questTransition,
            ResourceLocation sceneId,
            String sceneOperationId) {
        boolean valid = switch (kind) {
            case QUEST -> questId != null && questAction != QuestAction.NONE;
            case QUEST_TRANSITION -> questTransition != null && !questTransition.isEmpty();
            case MEMORY -> memoryTag != null;
            case LOOT -> lootTable != null;
            case FORCED_DIALOGUE -> !forcedDialogue.isBlank();
            case NOTIFICATION -> !notificationTrigger.isBlank() || !text.isBlank();
            case SET_TAG, CLEAR_TAG -> factTag != null;
            case SET_VARIABLE -> !factKey.isBlank() && !factValue.isBlank();
            case COUNTER -> !factKey.isBlank();
            case START_SCENE -> sceneId != null && !sceneOperationId.isBlank();
            case TRACKER, EXPERIENCE, REPUTATION, GOSSIP -> true;
            case NONE -> false;
        };
        if (!valid) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "action is missing required fields for type \"" + kind.serializedName() + "\".");
        }
        return valid;
    }

    private static ResourceLocation readSceneId(ResourceLocation location, JsonObject entry) {
        String value = firstNonBlank(DatapackJsonReader.readString(entry, "scene_id"),
                DatapackJsonReader.readString(entry, "start_scene"));
        if (value.isBlank()) return null;
        ResourceLocation parsed = value.contains(":") ? ResourceLocation.tryParse(value)
                : ResourceLocation.fromNamespaceAndPath(location.getNamespace(), value);
        if (parsed == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, "scene action", "invalid scene id \"" + value + "\"");
        }
        return parsed;
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

    private static QuestFactScope readFactScope(JsonObject entry, Kind kind, ResourceLocation questId) {
        QuestFactScope fallback = kind.isQuestFact() && questId != null ? QuestFactScope.QUEST : QuestFactScope.PLAYER;
        return QuestFactScope.bySerializedName(DatapackJsonReader.readString(entry, "scope", "fact_scope"), fallback);
    }

    private static ResourceLocation readFactTag(ResourceLocation location, String context, JsonObject entry, Kind kind) {
        String value = switch (kind) {
            case SET_TAG -> firstNonBlank(
                    DatapackJsonReader.readString(entry, "set_tag"),
                    firstNonBlank(
                            DatapackJsonReader.readString(entry, "fact_tag"),
                            firstNonBlank(
                                    DatapackJsonReader.readString(entry, "quest_tag"),
                                    DatapackJsonReader.readString(entry, "tag"))));
            case CLEAR_TAG -> firstNonBlank(
                    DatapackJsonReader.readString(entry, "clear_tag"),
                    firstNonBlank(
                            DatapackJsonReader.readString(entry, "fact_tag"),
                            firstNonBlank(
                                    DatapackJsonReader.readString(entry, "quest_tag"),
                                    DatapackJsonReader.readString(entry, "tag"))));
            default -> "";
        };
        if (value.isBlank()) {
            return null;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(value);
        if (tagId == null) {
            DatapackDiagnostics.warnInvalidDialogueCondition(location, context, "quest fact tag \"" + value + "\" is not a valid resource location.");
        }
        return tagId;
    }

    private static String readFactKey(JsonObject entry, Kind kind) {
        return switch (kind) {
            case SET_VARIABLE -> {
                String key = firstNonBlank(
                        DatapackJsonReader.readString(entry, "variable"),
                        firstNonBlank(
                                DatapackJsonReader.readString(entry, "key"),
                                DatapackJsonReader.readString(entry, "fact")));
                yield key.isBlank() && entry.has("stage") ? "stage" : key;
            }
            case COUNTER -> firstNonBlank(
                    DatapackJsonReader.readString(entry, "counter"),
                    firstNonBlank(
                            DatapackJsonReader.readString(entry, "increment_counter"),
                            firstNonBlank(
                                    DatapackJsonReader.readString(entry, "key"),
                                    DatapackJsonReader.readString(entry, "fact"))));
            default -> "";
        };
    }

    private static String readFactValue(JsonObject entry, Kind kind) {
        return kind == Kind.SET_VARIABLE
                ? firstNonBlank(
                        DatapackJsonReader.readString(entry, "value"),
                        DatapackJsonReader.readString(entry, "stage"))
                : "";
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
        QUEST_TRANSITION("quest_transition"),
        EXPERIENCE("experience"),
        REPUTATION("reputation"),
        GOSSIP("gossip"),
        MEMORY("memory"),
        LOOT("loot"),
        SET_TAG("set_tag"),
        CLEAR_TAG("clear_tag"),
        SET_VARIABLE("set_variable"),
        COUNTER("counter"),
        START_SCENE("start_scene");

        private final String serializedName;

        Kind(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static Kind bySerializedName(String value) {
            return VillagerActionRegistry.kindBySerializedName(value);
        }

        public boolean isQuestFact() {
            return this == SET_TAG || this == CLEAR_TAG || this == SET_VARIABLE || this == COUNTER;
        }
    }

    public enum QuestAction {
        NONE,
        START,
        REMIND,
        TURN_IN,
        FAIL,
        ABANDON,
        BLOCK;

        public static QuestAction bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "start", "accept", "begin" -> START;
                case "remind", "reminder", "details" -> REMIND;
                case "turn_in", "turnin", "complete", "claim" -> TURN_IN;
                case "fail", "failed" -> FAIL;
                case "abandon", "drop", "cancel", "remove" -> ABANDON;
                case "block", "lock", "consume", "close", "close_branch", "branch_lock" -> BLOCK;
                default -> NONE;
            };
        }
    }
}
