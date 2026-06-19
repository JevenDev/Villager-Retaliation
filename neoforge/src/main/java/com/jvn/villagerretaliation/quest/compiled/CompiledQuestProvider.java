package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;

public record CompiledQuestProvider(QuestDefinition.Offer offer) {
    public CompiledQuestProvider {
        offer = offer == null ? QuestDefinition.Offer.any() : offer;
    }
}
