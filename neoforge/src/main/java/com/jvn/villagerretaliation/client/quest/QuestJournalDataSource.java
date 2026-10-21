package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

public interface QuestJournalDataSource {
    List<QuestJournalData.Entry> entries();
    List<QuestJournalData.QuestlineNode> questlineNodes();
    int trackedIndex(QuestJournalData.Entry entry);
    void toggleTracking(QuestJournalData.Entry entry);
    void acknowledgeQuestUpdate(QuestJournalData.Entry entry);
    void request(String questId, Request request);
    void dismissFlash();
    boolean isJournalKey(int keyCode, int scanCode);
    void ignorePendingToggle();
    boolean showTags();
    boolean cinematicBars();
    boolean confirmAbandonment();
    void confirmAbandonment(boolean confirm);
    Screen campaignScreen(Screen parent);

    default boolean isTracked(QuestJournalData.Entry entry) {
        return trackedIndex(entry) >= 0;
    }

    enum Request { ACCEPT, ABANDON }
}
