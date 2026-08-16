package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
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
        ToIntFunction<QuestDefinition.Objective> reputationValue,
        Predicate<QuestDefinition.Objective> factMatcher,
        Predicate<QuestDefinition.Objective> conditionMatcher,
        Function<QuestDefinition.Objective, QuestObjectiveDebugState> debugState
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

    public boolean matchesFact(QuestDefinition.Objective objective) {
        return this.factMatcher != null && objective != null && this.factMatcher.test(objective);
    }

    public boolean matchesConditions(QuestDefinition.Objective objective) {
        return this.conditionMatcher != null && objective != null && this.conditionMatcher.test(objective);
    }

    public QuestObjectiveDebugState debugState(QuestDefinition.Objective objective) {
        return this.debugState == null || objective == null
                ? QuestObjectiveDebugState.EMPTY
                : this.debugState.apply(objective);
    }
}
