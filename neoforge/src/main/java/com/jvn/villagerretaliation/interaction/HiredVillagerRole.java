package com.jvn.villagerretaliation.interaction;

import java.util.Locale;

public enum HiredVillagerRole {
    COMBAT("combat"),
    HUNTING("hunting"),
    MINING("mining"),
    LOGGING("logging"),
    FARMING("farming"),
    FISHING("fishing"),
    BREWING("brewing"),
    CRAFTSMAN("craftsman"),
    BUILDER("builder"),
    ANIMAL_HANDLING("animal_handling"),
    NITWIT("nitwit"),
    COOK("cook"),
    SMELTER("smelter"),
    COURIER("courier");

    private static final java.util.Map<String, HiredVillagerRole> BY_NAME = names();
    private final String serializedName;

    private static java.util.Map<String, HiredVillagerRole> names() {
        var names = new java.util.HashMap<String, HiredVillagerRole>();
        for (var value : values()) {
            names.put(value.serializedName, value);
            names.put(value.name().toLowerCase(Locale.ROOT), value);
        }
        return java.util.Map.copyOf(names);
    }

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
        var known = BY_NAME.get(normalized);
        if (known != null) return known;
        for (var candidate : values()) if (candidate.name().equalsIgnoreCase(normalized)) return candidate;
        return null;
    }
}
