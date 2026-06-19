package com.jvn.villagerretaliation.quest;

public record QuestObjectiveDebugState(
        Integer itemCount,
        String message
) {
    public static final QuestObjectiveDebugState EMPTY = new QuestObjectiveDebugState(null, "");

    public QuestObjectiveDebugState {
        message = message == null ? "" : message;
    }

    public int itemCountOr(int fallback) {
        return this.itemCount == null ? fallback : this.itemCount;
    }
}
