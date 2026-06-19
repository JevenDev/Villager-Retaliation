package com.jvn.villagerretaliation.quest;

public record QuestTriggerDispatchResult(
        boolean dirty,
        QuestTriggerDispatchTrace trace
) {
    public QuestTriggerDispatchResult {
        trace = trace == null ? QuestTriggerDispatchTrace.EMPTY : trace;
    }

    public static QuestTriggerDispatchResult empty() {
        return new QuestTriggerDispatchResult(false, QuestTriggerDispatchTrace.EMPTY);
    }
}
