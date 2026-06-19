package com.jvn.villagerretaliation.quest;

public record QuestTriggerDispatchTrace(
        int candidateTriggers,
        int evaluatedTriggers,
        int matchedTriggers,
        int ranTriggers
) {
    public static final QuestTriggerDispatchTrace EMPTY = new QuestTriggerDispatchTrace(0, 0, 0, 0);

    public QuestTriggerDispatchTrace {
        candidateTriggers = Math.max(0, candidateTriggers);
        evaluatedTriggers = Math.max(0, evaluatedTriggers);
        matchedTriggers = Math.max(0, matchedTriggers);
        ranTriggers = Math.max(0, ranTriggers);
    }
}
