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

    public int anchoredLeft(int screenWidth, int groupWidth, int edgeMargin) {
        int maxLeft = Math.max(0, screenWidth - groupWidth);
        int target = switch (this) {
            case TOP_MID -> (screenWidth - groupWidth) / 2;
            case TOP_RIGHT, MID_RIGHT -> screenWidth - groupWidth - edgeMargin;
            default -> 0;
        };
        return clamp(target, 0, maxLeft);
    }

    public int anchoredTop(int screenHeight, int groupHeight, int vanillaTop, int topMargin) {
        int maxTop = Math.max(0, screenHeight - groupHeight);
        int target = switch (this) {
            case TOP_LEFT, TOP_MID, TOP_RIGHT -> topMargin;
            case MID_LEFT, MID_RIGHT -> (screenHeight - groupHeight) / 2;
            case BOTTOM_LEFT -> vanillaTop;
        };
        return clamp(target, 0, maxTop);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return this.label;
    }
}
