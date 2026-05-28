package com.jvn.villagerretaliation.profile;

import java.util.Locale;

public enum VillagerSocialAttribute {
    KNOWLEDGE("knowledge"),
    GUTS("guts"),
    PROFICIENCY("proficiency"),
    KINDNESS("kindness"),
    CHARM("charm");

    private final String serializedName;

    VillagerSocialAttribute(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "villagerretaliation.profile.attribute." + this.serializedName;
    }

    public static VillagerSocialAttribute bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("intellect") || normalized.equals("intelligence")) {
            return KNOWLEDGE;
        }
        for (VillagerSocialAttribute attribute : values()) {
            if (attribute.serializedName.equals(normalized)
                    || attribute.name().equalsIgnoreCase(normalized)) {
                return attribute;
            }
        }
        return null;
    }
}
