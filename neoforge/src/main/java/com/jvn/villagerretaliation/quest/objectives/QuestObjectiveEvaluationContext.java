package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record QuestObjectiveEvaluationContext(
        ServerPlayer player,
        DialogueRuntimeContext runtimeContext,
        ServerLevel level,
        QuestDefinition definition,
        com.jvn.villagerretaliation.quest.runtime.QuestProgressModel model,
        ToIntFunction<QuestDefinition.Objective> itemCounter,
        BiPredicate<QuestDefinition.Objective, ItemStack> itemMatcher,
        ToIntFunction<QuestDefinition.Objective> reputationValue,
        Predicate<QuestDefinition.Objective> factMatcher,
        Predicate<QuestDefinition.Objective> conditionMatcher,
        Function<QuestDefinition.Objective, QuestObjectiveDebugState> debugState
) {
    public QuestObjectiveEvaluationContext(
        ServerPlayer player,
        DialogueRuntimeContext runtimeContext,
        ServerLevel level,
        QuestDefinition definition,
        com.jvn.villagerretaliation.quest.runtime.QuestProgressState state,
        ToIntFunction<QuestDefinition.Objective> itemCounter,
        BiPredicate<QuestDefinition.Objective, ItemStack> itemMatcher,
        ToIntFunction<QuestDefinition.Objective> reputationValue,
        Predicate<QuestDefinition.Objective> factMatcher,
        Predicate<QuestDefinition.Objective> conditionMatcher,
        Function<QuestDefinition.Objective, QuestObjectiveDebugState> debugState
    ) {
        this(player, runtimeContext, level, definition, (com.jvn.villagerretaliation.quest.runtime.QuestProgressModel) state, itemCounter, itemMatcher, reputationValue, factMatcher, conditionMatcher, debugState);
    }

    public com.jvn.villagerretaliation.quest.runtime.QuestProgressState state() {
        return this.model instanceof com.jvn.villagerretaliation.quest.runtime.QuestProgressState legacy ? legacy : null;
    }

    public com.jvn.villagerretaliation.dialogue.DialogueContext dialogueContext() {
        return this.runtimeContext instanceof com.jvn.villagerretaliation.dialogue.DialogueContext context ? context : null;
    }

    public VillagerQuestSavedData.QuestProgress progress() {
        return this.model instanceof VillagerQuestSavedData.QuestProgress progress ? progress : null;
    }

    public QuestObjectiveEvaluationContext(ServerPlayer player, com.jvn.villagerretaliation.dialogue.DialogueContext context,
            ServerLevel level, QuestDefinition definition, VillagerQuestSavedData.QuestProgress progress,
            ToIntFunction<QuestDefinition.Objective> itemCounter, BiPredicate<QuestDefinition.Objective, ItemStack> itemMatcher,
            ToIntFunction<QuestDefinition.Objective> reputationValue, Predicate<QuestDefinition.Objective> factMatcher,
            Predicate<QuestDefinition.Objective> conditionMatcher,
            Function<QuestDefinition.Objective, QuestObjectiveDebugState> debugState) {
        this(player, (DialogueRuntimeContext) context, level, definition,
                (com.jvn.villagerretaliation.quest.runtime.QuestProgressState) progress, itemCounter,
                itemMatcher, reputationValue, factMatcher, conditionMatcher, debugState);
    }

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
