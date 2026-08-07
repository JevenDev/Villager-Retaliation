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

    @Override
    public String toString() {
        return this.label;
    }
}
