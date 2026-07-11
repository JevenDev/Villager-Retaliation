package com.jvn.villagerretaliation.scene.runtime;

public enum SceneState {
    PENDING,
    RUNNING,
    WAITING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED,
    CLEANING_UP;

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public static SceneState byName(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException exception) { return BLOCKED; }
    }
}
