package com.jvn.villagerretaliation.interaction.work;

public record WorkResult(String status, boolean progressed, boolean completed) {
    public static WorkResult idle(String status) {
        return new WorkResult(status, false, false);
    }

    public static WorkResult progressed(String status) {
        return new WorkResult(status, true, false);
    }

    public static WorkResult completed(String status) {
        return new WorkResult(status, true, true);
    }
}
