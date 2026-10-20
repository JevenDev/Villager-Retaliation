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

    @Override public void request(String questId, Request request) {
        PacketDistributor.sendToServer(new QuestTrackerRequestPayload(questId, switch (request) {
            case ACCEPT -> QuestTrackerRequestPayload.Action.ACCEPT;
            case ABANDON -> QuestTrackerRequestPayload.Action.ABANDON;
        }));
    }
}
