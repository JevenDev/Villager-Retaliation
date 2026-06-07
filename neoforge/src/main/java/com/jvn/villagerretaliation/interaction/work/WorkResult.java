package com.jvn.villagerretaliation.interaction.work;

public record WorkResult(String status, boolean progressed, boolean completed, boolean awardsSkillGrowth) {
    public static WorkResult idle(String status) {
        return new WorkResult(status, false, false, false);
    }

    public static WorkResult progressed(String status) {
        return new WorkResult(status, true, false, false);
    }

    public static WorkResult skilledProgress(String status) {
        return new WorkResult(status, true, false, true);
    }

    public static WorkResult completed(String status) {
        return new WorkResult(status, true, true, true);
    }
}
