package com.jvn.villagerretaliation.dialogue.normal;

public record DialogueReputationEffect(
        DialogueRequestType requestType,
        int reputationDelta,
        String reason,
        CooldownCategory cooldownCategory,
        boolean firstInteraction,
        boolean applied,
        boolean blockedByCooldown,
        String responseOverride
) {
    public static DialogueReputationEffect none(DialogueRequestType requestType) {
        return new DialogueReputationEffect(requestType, 0, "none", CooldownCategory.NONE, false, false, false, null);
    }

    public enum CooldownCategory {
        NONE,
        POSITIVE,
        NEGATIVE,
        JOKE
    }
}
