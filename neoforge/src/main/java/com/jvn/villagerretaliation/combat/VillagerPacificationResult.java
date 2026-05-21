package com.jvn.villagerretaliation.combat;

public enum VillagerPacificationResult {
    NOT_APPLICABLE,
    SUCCESS,
    NOT_ENOUGH_EMERALDS,
    BLOCKED_BY_REPUTATION;

    public boolean handled() {
        return this != NOT_APPLICABLE;
    }
}
