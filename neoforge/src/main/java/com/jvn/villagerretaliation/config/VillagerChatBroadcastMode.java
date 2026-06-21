package com.jvn.villagerretaliation.config;

public enum VillagerChatBroadcastMode {
    OFF("Off"),
    LOCAL("Local Radius"),
    DIMENSION("Current Dimension");

    private final String label;

    VillagerChatBroadcastMode(String label) {
        this.label = label;
    }

    public boolean enabled() {
        return this != OFF;
    }

    public boolean usesDistance() {
        return this == LOCAL;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
