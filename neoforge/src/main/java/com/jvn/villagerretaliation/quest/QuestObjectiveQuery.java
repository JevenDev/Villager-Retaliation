package com.jvn.villagerretaliation.quest;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class QuestObjectiveQuery {
    private QuestObjectiveQuery() {
    }

    public static List<QuestDefinition.Objective> requiredObjectives(QuestDefinition definition) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        return definition.objectives().stream()
                .filter(objective -> !objective.optional())
                .toList();
    }

    public static List<QuestDefinition.Objective> requiredItemHandIns(QuestDefinition definition) {
        return requiredObjectives(definition).stream()
                .filter(QuestObjectiveRegistry::requiresItemHandIn)
                .toList();
    }

    public static Optional<QuestDefinition.Objective> firstIncompleteRequired(
            QuestDefinition definition,
            Predicate<QuestDefinition.Objective> complete) {
        if (complete == null) {
            return Optional.empty();
        }
        return requiredObjectives(definition).stream()
                .filter(objective -> !complete.test(objective))
                .findFirst();
    }

    public static List<QuestDefinition.Objective> choiceObjectives(QuestDefinition definition) {
        if (definition == null || definition.objectives().isEmpty()) {
            return List.of();
        }
        return definition.objectives().stream()
                .filter(objective -> objective.type() == QuestDefinition.ObjectiveType.CHOICE)
                .toList();
    }
}
