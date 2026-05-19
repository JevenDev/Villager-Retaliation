package com.jvn.villagerretaliation.config;

public enum ReputationChangeHudPosition {
    TOP_LEFT("Top Left"),
    MID_LEFT("Mid Left"),
    MID_TOP("Mid Top"),
    TOP_RIGHT("Top Right"),
    MID_RIGHT("Mid Right");

    private final String label;

    ReputationChangeHudPosition(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
