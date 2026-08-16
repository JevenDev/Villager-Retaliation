package com.jvn.villagerretaliation.scene.runtime;

public enum StepExecutionStatus {
    PENDING,
    PREPARED,
    RUNNING,
    APPLIED,
    COMPLETED,
    FAILED,
    SKIPPED;

    public static StepExecutionStatus byName(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException exception) { return PENDING; }
    }
}
