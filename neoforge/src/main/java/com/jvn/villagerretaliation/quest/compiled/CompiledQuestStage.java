package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import java.util.List;

public record CompiledQuestStage(
        String id,
        int index,
        QuestDefinition.Stage definition,
        List<CompiledQuestObjective> objectives,
        QuestSourcePointer source
) {
    public CompiledQuestStage {
        id = id == null || id.isBlank()
                ? definition == null ? "" : definition.id()
                : id;
        index = Math.max(0, index);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }
}
