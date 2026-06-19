package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public record QuestObjectiveEvaluationContext(
        ServerPlayer player,
        DialogueContext dialogueContext,
        ServerLevel level,
        QuestDefinition definition,
        VillagerQuestSavedData.QuestProgress progress,
        ToIntFunction<QuestDefinition.Objective> itemCounter
) {
    public int itemCount(QuestDefinition.Objective objective) {
        return this.itemCounter == null || objective == null ? 0 : this.itemCounter.applyAsInt(objective);
    }
}
