package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record QuestObjectiveEvaluationContext(
        ServerPlayer player,
        DialogueContext dialogueContext,
        ServerLevel level,
        QuestDefinition definition,
        VillagerQuestSavedData.QuestProgress progress,
        ToIntFunction<QuestDefinition.Objective> itemCounter,
        BiPredicate<QuestDefinition.Objective, ItemStack> itemMatcher,
        ToIntFunction<QuestDefinition.Objective> reputationValue
) {
    public int itemCount(QuestDefinition.Objective objective) {
        return this.itemCounter == null || objective == null ? 0 : this.itemCounter.applyAsInt(objective);
    }

    public boolean matchesItem(QuestDefinition.Objective objective, ItemStack stack) {
        return this.itemMatcher != null
                && objective != null
                && stack != null
                && this.itemMatcher.test(objective, stack);
    }

    public int reputationValue(QuestDefinition.Objective objective) {
        return this.reputationValue == null || objective == null ? 0 : this.reputationValue.applyAsInt(objective);
    }
}
