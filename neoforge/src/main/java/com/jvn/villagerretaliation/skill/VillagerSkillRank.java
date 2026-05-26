package com.jvn.villagerretaliation.skill;

import java.util.Locale;

public enum VillagerSkillRank {
    NOVICE("novice", 1, 19),
    APPRENTICE("apprentice", 20, 39),
    SKILLED("skilled", 40, 59),
    EXPERT("expert", 60, 79),
    MASTER("master", 80, 100);

    private final String serializedName;
    private final int minInclusive;
    private final int maxInclusive;

    VillagerSkillRank(String serializedName, int minInclusive, int maxInclusive) {
        this.serializedName = serializedName;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "villagerretaliation.profile.skill_rank." + this.serializedName;
    }

    public int minInclusive() {
        return this.minInclusive;
    }

    public int maxInclusive() {
        return this.maxInclusive;
    }

    public static VillagerSkillRank fromValue(int value) {
        int clamped = VillagerSkillSet.clamp(value);
        for (VillagerSkillRank rank : values()) {
            if (clamped >= rank.minInclusive && clamped <= rank.maxInclusive) {
                return rank;
            }
        }
        return SKILLED;
    }

    public static VillagerSkillRank bySerializedName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (VillagerSkillRank rank : values()) {
            if (rank.serializedName.equals(normalized)
                    || rank.name().equalsIgnoreCase(normalized)) {
                return rank;
            }
        }
        return null;
    }
}
