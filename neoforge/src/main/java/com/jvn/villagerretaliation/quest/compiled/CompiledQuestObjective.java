package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;

public record CompiledQuestObjective(
        String id,
        int index,
        QuestDefinition.Objective definition,
        QuestSourcePointer source
) {
    public CompiledQuestObjective {
        id = id == null || id.isBlank()
                ? definition == null ? "objective" : definition.id()
                : id;
        index = Math.max(0, index);
    }
}
