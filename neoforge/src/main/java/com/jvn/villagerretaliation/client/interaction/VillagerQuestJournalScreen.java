package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.quest.VrQuestJournalSource;

public final class VillagerQuestJournalScreen extends QuestJournalScreen {
    public VillagerQuestJournalScreen() {
        super(VrQuestJournalSource.INSTANCE);
    }
}
