package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;

public enum VillagerReputationLevel {
    ROYALTY(4,
            150,
            "A villager now treats you like royalty.",
            "A villager no longer sees you as royalty."),
    REVERED(3,
            100,
            "A villager now reveres you.",
            "A villager's reverence for you has faded."),
    RESPECTED(2,
            70,
            "You feel yourself earning a villager's deep respect.",
            "You feel a villager's deep respect for you slipping away."),
    TRUSTED(1,
            35,
            "You feel yourself gaining a villager's trust.",
            "You feel a villager's trust in you weaken."),
    NEUTRAL(0,
            0,
            "A villager seems to feel neutral toward you again.",
            "A villager seems to feel neutral toward you again."),
    SUSPICIOUS(-1,
            -20,
            "A villager seems less suspicious of you.",
            "You feel a villager becoming suspicious of you."),
    HOSTILE(-2,
            -50,
            "A villager no longer sees you as completely unforgivable.",
            "You feel a villager becoming hostile toward you."),
    DESPISED(-3,
            0,
            "A villager's hatred for you has softened.",
            "You feel a villager come to despise you."),
    FEARED(-4,
            0,
            "A villager no longer fears you completely.",
            "A villager now fears you.");

    private final int trustRank;
    private final int tradeReputationEquivalent;
    private final String improvingMessage;
    private final String worseningMessage;

    VillagerReputationLevel(int trustRank, int tradeReputationEquivalent, String improvingMessage, String worseningMessage) {
        this.trustRank = trustRank;
        this.tradeReputationEquivalent = tradeReputationEquivalent;
        this.improvingMessage = improvingMessage;
        this.worseningMessage = worseningMessage;
    }

    public static final com.jvn.villagerretaliation.reputation.runtime.ReputationProfile<VillagerReputationLevel> PROFILE =
            new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile<>(NEUTRAL, java.util.List.of(
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(ROYALTY, () -> VillagerRetaliationConfig.ROYALTY_THRESHOLD.get(), true),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(REVERED, () -> VillagerRetaliationConfig.REVERED_THRESHOLD.get(), true),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(RESPECTED, () -> VillagerRetaliationConfig.RESPECTED_THRESHOLD.get(), true),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(TRUSTED, () -> VillagerRetaliationConfig.TRUSTED_THRESHOLD.get(), true),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(FEARED, () -> VillagerRetaliationConfig.FEARED_THRESHOLD.get(), false),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(DESPISED, () -> VillagerRetaliationConfig.DESPISED_THRESHOLD.get(), false),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(HOSTILE, () -> VillagerRetaliationConfig.HOSTILE_THRESHOLD.get(), false),
                    new com.jvn.villagerretaliation.reputation.runtime.ReputationProfile.Tier<>(SUSPICIOUS, () -> VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get(), false)));

    public static VillagerReputationLevel fromReputation(int reputation) {
        return PROFILE.tier(reputation);
    }

    public boolean isMoreTrustedThan(VillagerReputationLevel other) {
        return this.trustRank > other.trustRank;
    }

    public int trustRank() {
        return this.trustRank;
    }

    public int tradeReputationEquivalent() {
        return this.tradeReputationEquivalent;
    }

    public String transitionMessageFrom(VillagerReputationLevel previous) {
        return isMoreTrustedThan(previous) ? this.improvingMessage : this.worseningMessage;
    }
}
