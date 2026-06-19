package com.jvn.villagerretaliation.quest;

import java.util.Optional;
import java.util.Set;

public interface QuestObjectiveType<C> {
    QuestObjectiveResult evaluate(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective);

    default Optional<String> validationError(QuestDefinition.Objective objective) {
        return Optional.empty();
    }

    default String trackerStepKey(QuestDefinition.Objective objective) {
        return "";
    }

    default QuestObjectiveDebugState debugState(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective,
            QuestObjectiveResult result) {
        return QuestObjectiveDebugState.EMPTY;
    }

    default boolean requiresLocatedTarget(QuestDefinition.Objective objective) {
        return false;
    }

    default boolean requiresItemHandIn(QuestDefinition.Objective objective) {
        return false;
    }

    default Set<QuestObjectiveEventKind> eventKinds(QuestDefinition.Objective objective) {
        return Set.of();
    }

    default Set<net.minecraft.resources.ResourceLocation> eventSubscriptionKeys(QuestDefinition.Objective objective) {
        return Set.of();
    }

    default boolean matchesEvent(
            QuestObjectiveEvaluationContext context,
            QuestDefinition.Objective objective,
            QuestObjectiveEvent event) {
        return false;
    }
}
