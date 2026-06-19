package com.jvn.villagerretaliation.quest.compiled;

import com.jvn.villagerretaliation.quest.QuestDefinition;

public record CompiledQuestUi(
        QuestDefinition.Tracker tracker,
        QuestDefinition.Dialogue dialogue,
        QuestDefinition.Links links
) {
    public CompiledQuestUi {
        tracker = tracker == null ? QuestDefinition.Tracker.EMPTY : tracker;
        dialogue = dialogue == null ? QuestDefinition.Dialogue.EMPTY : dialogue;
        links = links == null ? QuestDefinition.Links.EMPTY : links;
    }
}
