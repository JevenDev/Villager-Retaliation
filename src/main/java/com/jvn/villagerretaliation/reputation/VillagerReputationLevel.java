package com.jvn.villagerretaliation.reputation;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;

public enum VillagerReputationLevel {
    REVERED(3,
            "A villager now reveres you.",
            "A villager's reverence for you has faded."),
    RESPECTED(2,
            "You feel yourself earning a villager's deep respect.",
            "You feel a villager's deep respect for you slipping away."),
    TRUSTED(1,
            "You feel yourself gaining a villager's trust.",
            "You feel a villager's trust in you weaken."),
    NEUTRAL(0,
            "A villager seems to feel neutral toward you again.",
            "A villager seems to feel neutral toward you again."),
    SUSPICIOUS(-1,
            "A villager seems less suspicious of you.",
            "You feel a villager becoming suspicious of you."),
    HOSTILE(-2,
            "A villager no longer sees you as completely unforgivable.",
            "You feel a villager becoming hostile toward you."),
    DESPISED(-3,
            "A villager's hatred for you has softened.",
            "You feel a villager come to despise you.");

    private final int trustRank;
    private final String improvingMessage;
    private final String worseningMessage;

    VillagerReputationLevel(int trustRank, String improvingMessage, String worseningMessage) {
        this.trustRank = trustRank;
        this.improvingMessage = improvingMessage;
        this.worseningMessage = worseningMessage;
    }

    public static VillagerReputationLevel fromReputation(int reputation) {
        if (reputation >= VillagerRetaliationConfig.REVERED_THRESHOLD.get()) {
            return REVERED;
        }
        if (reputation >= VillagerRetaliationConfig.RESPECTED_THRESHOLD.get()) {
            return RESPECTED;
        }
        if (reputation >= VillagerRetaliationConfig.TRUSTED_THRESHOLD.get()) {
            return TRUSTED;
        }
        if (reputation <= VillagerRetaliationConfig.DESPISED_THRESHOLD.get()) {
            return DESPISED;
        }
        if (reputation <= VillagerRetaliationConfig.HOSTILE_THRESHOLD.get()) {
            return HOSTILE;
        }
        if (reputation <= VillagerRetaliationConfig.SUSPICIOUS_THRESHOLD.get()) {
            return SUSPICIOUS;
        }
        return NEUTRAL;
    }

    public boolean isMoreTrustedThan(VillagerReputationLevel other) {
        return this.trustRank > other.trustRank;
    }

    public String transitionMessageFrom(VillagerReputationLevel previous) {
        return isMoreTrustedThan(previous) ? this.improvingMessage : this.worseningMessage;
    }
}
