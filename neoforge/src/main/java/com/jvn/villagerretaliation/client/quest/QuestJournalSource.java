package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;

public interface QuestJournalSource {
    List<QuestTrackerSyncPayload.Entry> entries();
    List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes();
    int trackedIndex(QuestTrackerSyncPayload.Entry entry);
    void toggleTracking(QuestTrackerSyncPayload.Entry entry);
    void acknowledgeQuestUpdate(QuestTrackerSyncPayload.Entry entry);
    void request(String questId, Request request);
    void dismissFlash();
    boolean isJournalKey(int keyCode, int scanCode);
    void ignorePendingToggle();
    boolean showTags();
    boolean cinematicBars();
    boolean confirmAbandonment();
    void confirmAbandonment(boolean confirm);
    Screen campaignScreen(Screen parent);

    default boolean isTracked(QuestTrackerSyncPayload.Entry entry) {
        return trackedIndex(entry) >= 0;
    }

    enum Request { ACCEPT, ABANDON }
}
