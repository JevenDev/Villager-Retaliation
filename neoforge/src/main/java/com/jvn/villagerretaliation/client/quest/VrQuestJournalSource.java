package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.client.config.VillagerRetaliationClientPreferences;
import com.jvn.villagerretaliation.client.interaction.CampaignJournalScreen;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.network.QuestTrackerRequestPayload;
import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;

public final class VrQuestJournalSource implements QuestJournalSource {
    public static final VrQuestJournalSource INSTANCE = new VrQuestJournalSource();
    private VrQuestJournalSource() { }

    @Override public List<QuestTrackerSyncPayload.Entry> entries() { return VillagerQuestTrackerOverlay.entries(); }
    @Override public List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes() { return VillagerQuestTrackerOverlay.questlineNodes(); }
    @Override public int trackedIndex(QuestTrackerSyncPayload.Entry entry) { return VillagerQuestTrackerOverlay.trackedIndex(entry); }
    @Override public void toggleTracking(QuestTrackerSyncPayload.Entry entry) { VillagerQuestTrackerOverlay.toggleTracking(entry); }
    @Override public void acknowledgeQuestUpdate(QuestTrackerSyncPayload.Entry entry) { VillagerQuestTrackerOverlay.acknowledgeQuestUpdate(entry); }
    @Override public void dismissFlash() { VillagerQuestTrackerOverlay.dismissJournalFlash(); }
    @Override public boolean isJournalKey(int keyCode, int scanCode) { return VillagerQuestKeyMappings.OPEN_JOURNAL.matches(keyCode, scanCode); }
    @Override public void ignorePendingToggle() { VillagerQuestTrackerOverlay.ignorePendingJournalToggle(); }
    @Override public boolean cinematicBars() { return com.jvn.villagerretaliation.client.interaction.ClientVillagerConversationState.active(); }
    @Override public boolean showTags() { return VillagerRetaliationConfig.SHOW_QUEST_JOURNAL_TAGS.get(); }
    @Override public boolean confirmAbandonment() { return VillagerRetaliationClientPreferences.confirmQuestAbandonment(); }
    @Override public void confirmAbandonment(boolean confirm) { VillagerRetaliationClientPreferences.confirmQuestAbandonment(confirm); }
    @Override public Screen campaignScreen(Screen parent) { return new CampaignJournalScreen(parent); }

    @Override public QuestJournalDataSource dataSource() {
        return new QuestJournalDataSource() {
            @Override public QuestPresentation presentation() { return VrQuestJournalSource.this.presentation(); }
            @Override public List<com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry> entries() {
                return VillagerQuestTrackerOverlay.journalEntries();
            }
            @Override public List<com.jvn.villagerretaliation.quest.tracking.QuestJournalData.QuestlineNode> questlineNodes() {
                return VillagerQuestTrackerOverlay.journalQuestlineNodes();
            }
            @Override public int trackedIndex(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                return VrQuestJournalSource.this.trackedIndex(VrQuestJournalData.legacy(entry));
            }
            @Override public void toggleTracking(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                VrQuestJournalSource.this.toggleTracking(VrQuestJournalData.legacy(entry));
            }
            @Override public void acknowledgeQuestUpdate(com.jvn.villagerretaliation.quest.tracking.QuestJournalData.Entry entry) {
                VrQuestJournalSource.this.acknowledgeQuestUpdate(VrQuestJournalData.legacy(entry));
            }
            @Override public void request(String questId, QuestJournalDataSource.Request request) {
                VrQuestJournalSource.this.request(questId, QuestJournalSource.Request.valueOf(request.name()));
            }
            @Override public void dismissFlash() { VrQuestJournalSource.this.dismissFlash(); }
            @Override public boolean isJournalKey(int keyCode, int scanCode) { return VrQuestJournalSource.this.isJournalKey(keyCode, scanCode); }
            @Override public void ignorePendingToggle() { VrQuestJournalSource.this.ignorePendingToggle(); }
            @Override public boolean showTags() { return VrQuestJournalSource.this.showTags(); }
            @Override public boolean cinematicBars() { return VrQuestJournalSource.this.cinematicBars(); }
            @Override public boolean confirmAbandonment() { return VrQuestJournalSource.this.confirmAbandonment(); }
            @Override public void confirmAbandonment(boolean confirm) { VrQuestJournalSource.this.confirmAbandonment(confirm); }
            @Override public Screen campaignScreen(Screen parent) { return VrQuestJournalSource.this.campaignScreen(parent); }
        };
    }

    @Override public void request(String questId, Request request) {
        PacketDistributor.sendToServer(new QuestTrackerRequestPayload(questId, switch (request) {
            case ACCEPT -> QuestTrackerRequestPayload.Action.ACCEPT;
            case ABANDON -> QuestTrackerRequestPayload.Action.ABANDON;
        }));
    }
}
