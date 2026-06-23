package com.jvn.villagerretaliation.quest.objectives;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.Set;

public record QuestObjectiveTypeDescriptor<C>(
        String id,
        QuestDefinition.ObjectiveType objectiveType,
        Set<String> aliases,
        Set<QuestObjectiveRequirement> requirements,
        QuestObjectiveType<C> implementation
) {
    public QuestObjectiveTypeDescriptor {
        id = QuestObjectiveRegistry.normalizeType(id);
        objectiveType = objectiveType == null ? QuestDefinition.ObjectiveType.CONDITION : objectiveType;
        aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
        requirements = requirements == null ? Set.of() : Set.copyOf(requirements);
    }

    public boolean hasImplementation() {
        return this.implementation != null;
    }
}
