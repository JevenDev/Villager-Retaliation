package com.jvn.villagerretaliation.quest.objectives;

public record QuestObjectiveResult(
        boolean complete,
        float progress,
        String message
) {
    public QuestObjectiveResult {
        progress = Math.max(0.0F, Math.min(1.0F, progress));
        message = message == null ? "" : message;
    }

    public static QuestObjectiveResult complete(String message) {
        return new QuestObjectiveResult(true, 1.0F, message);
    }

    public static QuestObjectiveResult incomplete(float progress, String message) {
        return new QuestObjectiveResult(false, progress, message);
    }
}
