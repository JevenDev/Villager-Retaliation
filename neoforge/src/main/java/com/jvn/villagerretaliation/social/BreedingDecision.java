package com.jvn.villagerretaliation.social;

import java.util.Locale;

public record BreedingDecision(boolean allowed, BreedingBlockReason reason, String messageKey) {
    private static final BreedingDecision ALLOWED = new BreedingDecision(true, BreedingBlockReason.NONE, "");

    public static BreedingDecision allow() {
        return ALLOWED;
    }

    public static BreedingDecision block(BreedingBlockReason reason) {
        return new BreedingDecision(
                false,
                reason,
                "villagerretaliation.breeding.blocked." + reason.name().toLowerCase(Locale.ROOT));
    }
}
