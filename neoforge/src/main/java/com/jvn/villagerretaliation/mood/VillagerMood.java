package com.jvn.villagerretaliation.mood;

import java.util.Locale;

public enum VillagerMood {
    NEUTRAL("neutral"),
    CONTENT("content"),
    GRATEFUL("grateful"),
    AFRAID("afraid"),
    ANGRY("angry"),
    SUSPICIOUS("suspicious"),
    GRIEVING("grieving"),
    PROTECTIVE("protective"),
    HOPEFUL("hopeful"),
    STRESSED("stressed"),
    PROUD("proud"),
    LONELY("lonely");

    private final String serializedName;

    VillagerMood(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "villagerretaliation.mood." + this.serializedName;
    }

    public static VillagerMood bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return NEUTRAL;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (VillagerMood mood : values()) {
            if (mood.serializedName.equals(normalized) || mood.name().equalsIgnoreCase(normalized)) {
                return mood;
            }
        }
        return NEUTRAL;
    }
}
