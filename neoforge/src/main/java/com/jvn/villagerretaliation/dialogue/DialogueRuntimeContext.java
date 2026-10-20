package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.normal.DialogueUsageSavedData;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public interface DialogueRuntimeContext {
    ServerLevel level();

    ServerPlayer player();

    LivingEntity speaker();

    String locale();

    ConditionContext conditions();

    default com.jvn.villagerretaliation.action.NarrativeActions actions() {
        return com.jvn.villagerretaliation.action.NarrativeActions.common();
    }

    default DialogueRuntimeAdapter runtime() {
        return com.jvn.villagerretaliation.dialogue.normal.EntityDialogueRuntime.INSTANCE;
    }

    default RandomSource random() {
        return speaker() == null ? player().getRandom() : speaker().getRandom();
    }

    default UUID providerId() {
        return speaker() == null ? null : speaker().getUUID();
    }

    default BlockPos origin() {
        return speaker() == null ? player().blockPosition() : speaker().blockPosition();
    }

    default String groupKey() {
        return "";
    }

    default Map<String, String> replacements() {
        return Map.of();
    }

    default Optional<String> message(String key) {
        return Optional.empty();
    }

    default String displayName(Entity entity) {
        return entity.getName().getString();
    }

    default DialogueUsageSavedData.Usage legacyUsage(String dialogueId) {
        return DialogueUsageSavedData.Usage.EMPTY;
    }
}
