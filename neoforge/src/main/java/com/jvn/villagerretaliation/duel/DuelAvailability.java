package com.jvn.villagerretaliation.duel;

public record DuelAvailability(
        boolean visible,
        boolean available,
        DuelAvailabilityReason reason,
        int villagerWins,
        int villagerLosses,
        int consecutiveLosses,
        long cooldownTicksRemaining,
        int playerCurrency,
        int villagerCurrency) {
    public static DuelAvailability hidden() {
        return new DuelAvailability(false, false, DuelAvailabilityReason.LOW_GUTS, 0, 0, 0, 0L, 0, 0);
    }

    public boolean showChallengeButton() {
        return this.visible && this.available;
    }

    public int maximumStake() {
        return Math.max(0, Math.min(this.playerCurrency, this.villagerCurrency));
    }
}
