package com.jvn.villagerretaliation.config;

public enum InteractionChatPosition {
    BOTTOM_LEFT("Bottom Left"),
    MID_LEFT("Mid Left"),
    TOP_LEFT("Top Left"),
    TOP_MID("Top Mid"),
    TOP_RIGHT("Top Right"),
    MID_RIGHT("Mid Right");

    private final String label;

    InteractionChatPosition(String label) {
        this.label = label;
    }

    public boolean anchorsRight() {
        return this == TOP_RIGHT || this == MID_RIGHT;
    }

    public boolean anchorsCenter() {
        return this == TOP_MID;
    }

    public boolean anchorsTop() {
        return this == TOP_LEFT || this == TOP_MID || this == TOP_RIGHT;
    }

    public boolean anchorsMiddle() {
        return this == MID_LEFT || this == MID_RIGHT;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
