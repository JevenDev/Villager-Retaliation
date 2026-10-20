package com.jvn.villagerretaliation.dialogue;

import com.jvn.villagerretaliation.dialogue.conditions.ConditionContext;
import com.jvn.villagerretaliation.dialogue.conditions.EntityConditionAccess;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public record EntityDialogueContext(
        ServerLevel level,
        ServerPlayer player,
        LivingEntity speaker,
        String locale,
        String groupKey,
        com.jvn.villagerretaliation.action.NarrativeActions actions
) implements DialogueRuntimeContext {
    public EntityDialogueContext(ServerLevel level, ServerPlayer player, LivingEntity speaker, String locale, String groupKey) {
        this(level, player, speaker, locale, groupKey, com.jvn.villagerretaliation.action.NarrativeActions.common());
    }

    public EntityDialogueContext {
        Objects.requireNonNull(level, "dialogue level");
        Objects.requireNonNull(player, "dialogue player");
        if (player.serverLevel() != level || speaker != null && speaker.level() != level) {
            throw new IllegalArgumentException("dialogue participants must be in the dialogue level");
        }
        locale = locale == null || locale.isBlank() ? "en_us" : locale;
        groupKey = groupKey == null ? "" : groupKey;
        actions = actions == null ? com.jvn.villagerretaliation.action.NarrativeActions.common() : actions;
    }

    @Override
    public ConditionContext conditions() {
        return EntityConditionAccess.forDialogue(this);
    }
}
