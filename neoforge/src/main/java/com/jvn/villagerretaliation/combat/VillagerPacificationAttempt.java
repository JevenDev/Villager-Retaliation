package com.jvn.villagerretaliation.combat;

import javax.annotation.Nullable;

public record VillagerPacificationAttempt(
        VillagerPacificationResult result,
        @Nullable PacifyPaymentOffer payment) {
    public static VillagerPacificationAttempt notApplicable() {
        return new VillagerPacificationAttempt(VillagerPacificationResult.NOT_APPLICABLE, null);
    }

    public static VillagerPacificationAttempt of(VillagerPacificationResult result, PacifyPaymentOffer payment) {
        return new VillagerPacificationAttempt(result, payment);
    }

    public boolean handled() {
        return this.result.handled();
    }
}
