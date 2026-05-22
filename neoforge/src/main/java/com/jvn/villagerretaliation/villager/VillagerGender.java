package com.jvn.villagerretaliation.villager;

import java.util.Locale;

public enum VillagerGender {
    MALE("male", "Male"),
    FEMALE("female", "Female");

    private final String serializedName;
    private final String displayName;

    VillagerGender(String serializedName, String displayName) {
        this.serializedName = serializedName;
        this.displayName = displayName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String displayName() {
        return this.displayName;
    }

    public static VillagerGender bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (VillagerGender gender : values()) {
            if (gender.serializedName.equals(normalized) || gender.name().equalsIgnoreCase(value)) {
                return gender;
            }
        }
        return null;
    }
}
