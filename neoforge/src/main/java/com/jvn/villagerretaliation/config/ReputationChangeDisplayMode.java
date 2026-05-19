package com.jvn.villagerretaliation.config;

public enum ReputationChangeDisplayMode {
    HUD("HUD"),
    CHAT("Chat"),
    OFF("Off");

    private final String label;

    ReputationChangeDisplayMode(String label) {
        this.label = label;
    }

    public boolean showsHudNotification() {
        return this == HUD;
    }

    public boolean showsChatMessage() {
        return this == CHAT;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
