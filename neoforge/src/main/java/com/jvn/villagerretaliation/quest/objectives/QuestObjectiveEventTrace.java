package com.jvn.villagerretaliation.quest.objectives;

public record QuestObjectiveEventTrace(
        int evaluatedObjectives,
        int matchedObjectives
) {
    public QuestObjectiveEventTrace {
        evaluatedObjectives = Math.max(0, evaluatedObjectives);
        matchedObjectives = Math.max(0, matchedObjectives);
    }
}
