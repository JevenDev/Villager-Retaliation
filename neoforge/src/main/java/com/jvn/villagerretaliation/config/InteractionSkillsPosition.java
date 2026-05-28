package com.jvn.villagerretaliation.config;

public enum InteractionSkillsPosition {
    TOP_RIGHT("Top Right"),
    TOP_MID("Top Mid"),
    TOP_LEFT("Top Left"),
    MID_LEFT("Mid Left"),
    BOTTOM_LEFT("Bottom Left");

    private final String label;

    InteractionSkillsPosition(String label) {
        this.label = label;
    }

    public boolean anchorsRight() {
        return this == TOP_RIGHT;
    }

    public boolean anchorsCenter() {
        return this == TOP_MID;
    }

    public boolean anchorsTop() {
        return this == TOP_RIGHT || this == TOP_MID || this == TOP_LEFT;
    }

    public boolean anchorsMiddle() {
        return this == MID_LEFT;
    }

    public boolean anchorsBottom() {
        return this == BOTTOM_LEFT;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
