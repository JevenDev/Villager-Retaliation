package com.jvn.villagerretaliation.quest.inventory;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressModel;
import java.util.LinkedHashMap;
import java.util.List;

public final class QuestSubmissionRequirements {
    private QuestSubmissionRequirements() { }

    public static List<QuestDefinition.Objective> required(QuestDefinition definition, QuestProgressModel progress) {
        if (definition == null) return List.of();
        var objectives = new LinkedHashMap<String, QuestDefinition.Objective>();
        for (var objective : QuestObjectiveQuery.requiredObjectives(definition, progress)) {
            objectives.put(objective.id(), objective);
        }
        if (progress != null) {
            for (var objective : definition.objectives()) {
                if (!objective.optional() && objective.type() == QuestDefinition.ObjectiveType.ITEM_CHECK
                        && progress.objectiveComplete(objective.id())) {
                    objectives.putIfAbsent(objective.id(), objective);
                }
            }
        }
        return List.copyOf(objectives.values());
    }
}
