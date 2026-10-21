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

    default QuestJournalDataSource dataSource() {
        QuestJournalSource legacy = this;
        return new QuestJournalDataSource() {
            @Override public List<com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry> entries() {
                return legacy.entries().stream().map(VrQuestJournalData::shared).toList();
            }
            @Override public List<com.jvn.villagerretaliation.quest.tracking.QuestJournalData.QuestlineNode> questlineNodes() {
                return legacy.questlineNodes().stream().map(VrQuestJournalData::shared).toList();
            }
            @Override public int trackedIndex(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                return legacy.trackedIndex(VrQuestJournalData.legacy(entry));
            }
            @Override public void toggleTracking(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                legacy.toggleTracking(VrQuestJournalData.legacy(entry));
            }
            @Override public void acknowledgeQuestUpdate(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                legacy.acknowledgeQuestUpdate(VrQuestJournalData.legacy(entry));
            }
            @Override public void request(String questId, QuestJournalDataSource.Request request) {
                legacy.request(questId, QuestJournalSource.Request.valueOf(request.name()));
            }
            @Override public void dismissFlash() { legacy.dismissFlash(); }
            @Override public boolean isJournalKey(int keyCode, int scanCode) { return legacy.isJournalKey(keyCode, scanCode); }
            @Override public void ignorePendingToggle() { legacy.ignorePendingToggle(); }
            @Override public boolean showTags() { return legacy.showTags(); }
            @Override public boolean cinematicBars() { return legacy.cinematicBars(); }
            @Override public boolean confirmAbandonment() { return legacy.confirmAbandonment(); }
            @Override public void confirmAbandonment(boolean confirm) { legacy.confirmAbandonment(confirm); }
            @Override public Screen campaignScreen(Screen parent) { return legacy.campaignScreen(parent); }
        };
    }

    enum Request { ACCEPT, ABANDON }
}
