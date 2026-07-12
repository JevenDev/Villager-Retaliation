package com.jvn.villagerretaliation.scene.model;

import com.google.gson.JsonObject;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/** Canonical typed payload for the quest_transition scene step. */
public record SceneQuestTransition(ResourceLocation questId, Target target, String targetStage) {
    public SceneQuestTransition {
        if (questId == null || target == null) {
            throw new IllegalArgumentException("quest_transition requires a quest and target");
        }
        targetStage = targetStage == null ? "" : targetStage.trim();
        if (target == Target.STAGE && targetStage.isBlank()) {
            throw new IllegalArgumentException("quest_transition target stage requires target_stage");
        }
        if (target != Target.STAGE && !targetStage.isBlank()) {
            throw new IllegalArgumentException("quest_transition terminal target cannot also define target_stage");
        }
    }

    public static SceneQuestTransition parse(JsonObject data) {
        if (data == null) throw new IllegalArgumentException("quest_transition data is required");
        for (String key : data.keySet()) {
            if (!java.util.Set.of("quest", "quest_id", "target", "target_stage").contains(key)) {
                throw new IllegalArgumentException("unknown quest_transition field " + key);
            }
        }
        String questValue = string(data, "quest", "quest_id");
        ResourceLocation questId = ResourceLocation.tryParse(questValue);
        if (questId == null) throw new IllegalArgumentException("quest_transition requires a namespaced quest id");
        String targetStage = string(data, "target_stage");
        String rawTarget = string(data, "target").toLowerCase(Locale.ROOT);
        Target target;
        if (rawTarget.isBlank() && !targetStage.isBlank()) {
            target = Target.STAGE;
        } else {
            target = switch (rawTarget) {
                case "stage" -> Target.STAGE;
                case "complete" -> Target.COMPLETE;
                case "fail" -> Target.FAIL;
                case "abandon" -> Target.ABANDON;
                default -> throw new IllegalArgumentException("unknown quest_transition target " + rawTarget);
            };
        }
        return new SceneQuestTransition(questId, target, targetStage);
    }

    private static String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString().trim();
        }
        return "";
    }

    public enum Target {
        STAGE,
        COMPLETE,
        FAIL,
        ABANDON
    }
}
