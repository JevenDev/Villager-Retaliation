package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;

public record CompiledQuestRewards(QuestDefinition.Rewards definition) {
    public CompiledQuestRewards {
        definition = definition == null ? QuestDefinition.Rewards.EMPTY : definition;
    }
}
