package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuestTrackerModel {
    private final QuestJournalModel shared = new QuestJournalModel();
    private List<QuestJournalData.Entry> entrySource;
    private List<QuestTrackerSyncPayload.Entry> entries = List.of();
    private List<QuestJournalData.QuestlineNode> nodeSource;
    private List<QuestTrackerSyncPayload.QuestlineNode> nodes = List.of();

    public QuestJournalModel shared() { return this.shared; }

    public void accept(List<QuestTrackerSyncPayload.Entry> source, List<String> trackedIds,
            List<QuestTrackerSyncPayload.QuestlineNode> questlines, Map<String, UUID> indicators) {
        this.shared.accept(source.stream().map(VrQuestJournalData::shared).toList(), trackedIds,
                questlines.stream().map(VrQuestJournalData::shared).toList(), indicators);
    }

    public List<QuestTrackerSyncPayload.Entry> entries() {
        if (this.entrySource != this.shared.entries()) {
            this.entrySource = this.shared.entries();
            this.entries = this.entrySource.stream().map(VrQuestJournalData::legacy).toList();
        }
        return this.entries;
    }

    public List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes() {
        if (this.nodeSource != this.shared.questlineNodes()) {
            this.nodeSource = this.shared.questlineNodes();
            this.nodes = this.nodeSource.stream().map(VrQuestJournalData::legacy).toList();
        }
        return this.nodes;
    }

    public List<QuestTrackerSyncPayload.Entry> trackedEntries() {
        return this.shared.trackedEntries().stream().map(VrQuestJournalData::legacy).toList();
    }

    public int trackedIndex(QuestTrackerSyncPayload.Entry entry) { return this.shared.trackedIndex(VrQuestJournalData.shared(entry)); }
    public boolean hasIndicator(UUID entity) { return this.shared.hasIndicator(entity); }
    public void acknowledge(QuestTrackerSyncPayload.Entry entry) { this.shared.acknowledge(VrQuestJournalData.shared(entry)); }
    public void reset() { this.shared.reset(); }
}
