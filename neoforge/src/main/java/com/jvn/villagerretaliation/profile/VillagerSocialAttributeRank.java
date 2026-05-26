package com.jvn.villagerretaliation.profile;

public enum VillagerSocialAttributeRank {
    POOR("poor", 1, 19),
    MODEST("modest", 20, 39),
    AVERAGE("average", 40, 59),
    STRONG("strong", 60, 79),
    EXCEPTIONAL("exceptional", 80, 100);

    private final String serializedName;
    private final int minInclusive;
    private final int maxInclusive;

    VillagerSocialAttributeRank(String serializedName, int minInclusive, int maxInclusive) {
        this.serializedName = serializedName;
        this.minInclusive = minInclusive;
        this.maxInclusive = maxInclusive;
    }

    public String serializedName() {
        return this.serializedName;
    }

    public String translationKey() {
        return "villagerretaliation.profile.rank." + this.serializedName;
    }

    public int minInclusive() {
        return this.minInclusive;
    }

    public int maxInclusive() {
        return this.maxInclusive;
    }

    public static VillagerSocialAttributeRank fromValue(int value) {
        int clamped = VillagerSocialAttributes.clamp(value);
        for (VillagerSocialAttributeRank rank : values()) {
            if (clamped >= rank.minInclusive && clamped <= rank.maxInclusive) {
                return rank;
            }
        }
        return AVERAGE;
    }
}
