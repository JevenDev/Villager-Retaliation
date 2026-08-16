package com.jvn.villagerretaliation.quest.objectives;

public record QuestObjectiveDebugState(
        Integer itemCount,
        String message,
        String factScopeKey,
        String conditionState
) {
    public static final QuestObjectiveDebugState EMPTY = new QuestObjectiveDebugState(null, "", "", "");

    public QuestObjectiveDebugState {
        message = message == null ? "" : message;
        factScopeKey = factScopeKey == null ? "" : factScopeKey;
        conditionState = conditionState == null ? "" : conditionState;
    }

    public QuestObjectiveDebugState(Integer itemCount, String message) {
        this(itemCount, message, "", "");
    }

    public int itemCountOr(int fallback) {
        return this.itemCount == null ? fallback : this.itemCount;
    }

    public String factScopeKeyOr(String fallback) {
        return this.factScopeKey.isBlank() ? fallback : this.factScopeKey;
    }

    public String conditionStateOr(String fallback) {
        return this.conditionState.isBlank() ? fallback : this.conditionState;
    }
}
