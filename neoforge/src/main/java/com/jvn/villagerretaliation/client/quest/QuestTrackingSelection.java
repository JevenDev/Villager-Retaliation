package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.ArrayList;
import java.util.List;

public final class QuestTrackingSelection {
    private QuestTrackingSelection() { }

    public static int index(QuestTrackerSyncPayload.Entry entry, List<String> trackedIds) {
        return entry == null || !entry.trackable() ? -1 : trackedIds.indexOf(entry.questId());
    }

    public static List<QuestTrackerSyncPayload.Entry> entries(
            List<QuestTrackerSyncPayload.Entry> entries, List<String> trackedIds) {
        List<QuestTrackerSyncPayload.Entry> selected = new ArrayList<>();
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
