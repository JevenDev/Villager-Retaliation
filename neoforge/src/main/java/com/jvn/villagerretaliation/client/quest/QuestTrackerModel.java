package com.jvn.villagerretaliation.client.quest;

import com.jvn.villagerretaliation.network.QuestTrackerSyncPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class QuestTrackerModel {
    private List<QuestTrackerSyncPayload.Entry> entries = List.of();
    private List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes = List.of();
    private List<String> trackedIds = List.of();
    private final Set<String> questUpdateQuestIds = new HashSet<>();
    private final Map<String, QuestTrackerSyncPayload.Entry> completedEntryCache = new LinkedHashMap<>();
    private final Map<String, UUID> entityIndicators = new LinkedHashMap<>();

    public void accept(List<QuestTrackerSyncPayload.Entry> source, List<String> trackedIds,
            List<QuestTrackerSyncPayload.QuestlineNode> questlines, Map<String, UUID> indicators) {
        var visible = source.stream().filter(entry -> !entry.journal().hidden()).toList();
        this.entityIndicators.putAll(indicators);
        for (var entry : visible) {
            if (entry.questUpdate() && !entry.questAvailable() && !entry.questId().isBlank()) {
                this.questUpdateQuestIds.add(entry.questId());
            }
        }
        Set<String> accepted = new HashSet<>();
        for (var entry : visible) {
            if (!entry.questAvailable() && !entry.questId().isBlank()) accepted.add(entry.questId());
        }
        this.questUpdateQuestIds.removeIf(questId -> !accepted.contains(questId));
        this.entries = applyQuestUpdateCache(mergeCompletedEntries(visible));
        this.entityIndicators.keySet().removeIf(questId -> !accepted.contains(questId));
        this.questlineNodes = List.copyOf(questlines);
        this.trackedIds = this.entries.isEmpty() ? List.of() : List.copyOf(trackedIds);
    }

    public List<QuestTrackerSyncPayload.Entry> entries() { return this.entries; }
    public List<QuestTrackerSyncPayload.QuestlineNode> questlineNodes() { return this.questlineNodes; }
    public List<QuestTrackerSyncPayload.Entry> trackedEntries() { return QuestTrackingSelection.entries(this.entries, this.trackedIds); }
    public int trackedIndex(QuestTrackerSyncPayload.Entry entry) { return QuestTrackingSelection.index(entry, this.trackedIds); }
    public boolean hasIndicator(UUID entity) { return entity != null && this.entityIndicators.containsValue(entity); }

    public void acknowledge(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null || entry.questAvailable() || entry.questId().isBlank()) return;
        if (this.questUpdateQuestIds.remove(entry.questId())) this.entries = applyQuestUpdateCache(this.entries);
        this.entityIndicators.remove(entry.questId());
    }

    public void reset() {
        this.entries = List.of();
        this.questlineNodes = List.of();
        this.trackedIds = List.of();
        this.questUpdateQuestIds.clear();
        this.completedEntryCache.clear();
        this.entityIndicators.clear();
    }

    private List<QuestTrackerSyncPayload.Entry> applyQuestUpdateCache(List<QuestTrackerSyncPayload.Entry> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .map(entry -> entry.withQuestUpdate(!entry.questAvailable() && questUpdateQuestIds.contains(entry.questId())))
                .toList();
    }

    private List<QuestTrackerSyncPayload.Entry> mergeCompletedEntries(List<QuestTrackerSyncPayload.Entry> source) {
        if (source != null) {
            for (QuestTrackerSyncPayload.Entry entry : source) {
                if (isCompletedEntry(entry) && !entry.questId().isBlank()) {
                    completedEntryCache.remove(entry.questId());
                    completedEntryCache.put(entry.questId(), entry);
                }
            }
        }
        if (completedEntryCache.isEmpty()) {
            return source == null ? List.of() : List.copyOf(source);
        }
        List<QuestTrackerSyncPayload.Entry> merged = new ArrayList<>();
        Set<String> includedCompleted = new HashSet<>();
        if (source != null) {
            for (QuestTrackerSyncPayload.Entry entry : source) {
                merged.add(entry);
                if (isCompletedEntry(entry) && !entry.questId().isBlank()) {
                    includedCompleted.add(entry.questId());
                }
            }
        }
        for (QuestTrackerSyncPayload.Entry entry : completedEntryCache.values()) {
            if (!includedCompleted.contains(entry.questId())) {
                merged.add(entry);
            }
        }
        return List.copyOf(merged);
    }

    private boolean isCompletedEntry(QuestTrackerSyncPayload.Entry entry) {
        if (entry == null || entry.questAvailable()) {
            return false;
        }
        return switch (entry.state().trim().toLowerCase(Locale.ROOT)) {
            case "completed", "complete", "done" -> true;
            default -> false;
        };
    }

}
