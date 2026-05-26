package com.jvn.villagerretaliation.skill;

import java.util.Locale;

public enum VillagerSkill {
    FARMING("farming"),
    FISHING("fishing"),
    SMITHING("smithing"),
    CRAFTING("crafting"),
    TRADING("trading"),
    MEDICINE("medicine"),
    ARCHERY("archery"),
    GUARDING("guarding"),
    COOKING("cooking"),
    ANIMAL_HANDLING("animal_handling"),
    CARTOGRAPHY("cartography"),
    SCHOLARSHIP("scholarship"),
    GATHERING("gathering"),
    MASONRY("masonry"),
    MINING("mining"),
    LEATHERWORKING("leatherworking"),
    DIPLOMACY("diplomacy"),
    SURVIVAL("survival");

    private final String serializedName;

    VillagerSkill(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "villagerretaliation.profile.skill." + this.serializedName;
    }

    public String descriptionTranslationKey() {
        return translationKey() + ".description";
    }

    public static VillagerSkill bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (VillagerSkill skill : values()) {
            if (skill.serializedName.equals(normalized)
                    || skill.name().equalsIgnoreCase(normalized)) {
                return skill;
            }
        }
        return null;
    }
}
