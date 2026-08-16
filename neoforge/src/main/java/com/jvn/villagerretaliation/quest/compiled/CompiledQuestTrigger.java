package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;

public record CompiledQuestTrigger(
        String id,
        int index,
        QuestDefinition.Trigger definition,
        QuestSourcePointer source
) {
    public CompiledQuestTrigger {
        id = id == null || id.isBlank()
                ? definition == null ? "trigger" : definition.id()
                : id;
        index = Math.max(0, index);
    }
}
