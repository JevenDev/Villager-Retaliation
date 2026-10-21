package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.quest.tracking.QuestJournalData;
import java.util.ArrayList;
import java.util.List;

public final class QuestJournalSelection {
    private QuestJournalSelection() { }

    public static int index(QuestJournalData.Entry entry, List<String> trackedIds) {
        return entry == null || !entry.trackable() ? -1 : trackedIds.indexOf(entry.questId());
    }

    public static List<QuestJournalData.Entry> entries(
            List<QuestJournalData.Entry> entries, List<String> trackedIds) {
        List<QuestJournalData.Entry> selected = new ArrayList<>();
        for (String questId : trackedIds) {
            for (var entry : entries) {
                if (entry.trackable() && questId.equals(entry.questId())) {
                    selected.add(entry);
                    break;
                }
            }
        }
        return List.copyOf(selected);
    }
}
