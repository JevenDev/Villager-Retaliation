package com.jvn.villagerretaliation.quest.compiled;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.quest.QuestIds;
import com.jvn.villagerretaliation.util.DatapackJsonReader;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record CompiledQuestTransition(
        ResourceLocation questId,
        String stageId,
        String scenePath,
        String responseId,
        Target target,
        String targetStage,
        String sourcePointer
) {
    public static final CompiledQuestTransition EMPTY =
            new CompiledQuestTransition(null, "", "", "", Target.NONE, "", "");

    public CompiledQuestTransition {
        stageId = normalize(stageId);
        scenePath = normalize(scenePath);
        responseId = normalize(responseId);
        target = target == null ? Target.NONE : target;
        targetStage = normalize(targetStage);
        sourcePointer = normalize(sourcePointer);
    }

    public static CompiledQuestTransition read(
            ResourceLocation location,
            JsonObject object,
            ResourceLocation defaultQuestId) {
        if (object == null) {
            return EMPTY;
        }
        ResourceLocation questId = readQuestId(location, object, defaultQuestId);
        String targetStage = firstNonBlank(
                DatapackJsonReader.readString(object, "target_stage"),
                firstNonBlank(
                        DatapackJsonReader.readString(object, "next_stage"),
                        DatapackJsonReader.readString(object, "next")));
        Target target = Target.bySerializedName(DatapackJsonReader.readString(object, "target"));
        if (target == Target.NONE && !targetStage.isBlank()) {
            target = Target.STAGE;
        } else if (target == Target.NONE && DatapackJsonReader.readBoolean(object, "complete", false)) {
            target = Target.COMPLETE;
        } else if (target == Target.NONE && DatapackJsonReader.readBoolean(object, "abandon", false)) {
            target = Target.ABANDON;
        } else if (target == Target.NONE && DatapackJsonReader.readBoolean(object, "fail", false)) {
            target = Target.FAIL;
        }
        return new CompiledQuestTransition(
                questId,
                firstNonBlank(
                        DatapackJsonReader.readString(object, "from_stage"),
                        DatapackJsonReader.readString(object, "stage_id")),
                firstNonBlank(
                        DatapackJsonReader.readString(object, "scene_path"),
                        DatapackJsonReader.readString(object, "scene")),
                firstNonBlank(
                        DatapackJsonReader.readString(object, "response_id"),
                        DatapackJsonReader.readString(object, "response")),
                target,
                targetStage,
                DatapackJsonReader.readString(object, "source_pointer"));
    }

    public boolean isEmpty() {
        return this.questId == null
                || this.stageId.isBlank()
                || this.responseId.isBlank()
                || this.target == Target.NONE
                || this.target == Target.STAGE && this.targetStage.isBlank();
    }

    public boolean isTerminal() {
        return this.target == Target.COMPLETE || this.target == Target.ABANDON || this.target == Target.FAIL;
    }

    private static ResourceLocation readQuestId(
            ResourceLocation location,
            JsonObject object,
            ResourceLocation defaultQuestId) {
        for (String key : java.util.List.of("quest", "quest_id", "id")) {
            String value = DatapackJsonReader.readString(object, key);
            if (!value.isBlank()) {
                return QuestIds.parse(value, location);
            }
        }
        return defaultQuestId;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    public enum Target {
        NONE,
        STAGE,
        COMPLETE,
        ABANDON,
        FAIL;

        public static Target bySerializedName(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "stage", "next", "set_stage" -> STAGE;
                case "complete", "completed", "turn_in", "turnin" -> COMPLETE;
                case "abandon", "abandoned", "drop" -> ABANDON;
                case "fail", "failed" -> FAIL;
                default -> NONE;
            };
        }
    }
}
