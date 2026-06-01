package com.jvn.villagerretaliation.interaction;

import java.util.Locale;

public enum HiredVillagerRole {
    COMBAT("combat"),
    MINING("mining"),
    LOGGING("logging"),
    FARMING("farming"),
    BREWING("brewing"),
    NAVIGATION("navigation"),
    ANIMAL_HANDLING("animal_handling");

    private final String serializedName;

    HiredVillagerRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String label() {
        String[] words = this.serializedName.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }

    public static HiredVillagerRole bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (HiredVillagerRole role : values()) {
            if (role.serializedName.equals(normalized) || role.name().equalsIgnoreCase(normalized)) {
                return role;
            }
        }
        return null;
    }
}
