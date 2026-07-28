package com.jvn.villagerretaliation.interaction;

import java.util.Optional;
import java.util.UUID;

/** Immutable contract state for code that needs several consistent queries. */
public record HireContractSnapshot(
        boolean hired,
        Optional<UUID> hirer,
        HiredVillagerRole role,
        boolean awaitingAutoPayment,
        boolean oneOffBuilderJob) {
    public HireContractSnapshot {
        hirer = hirer == null ? Optional.empty() : hirer;
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }
    }

    public static HireContractSnapshot inactive(HiredVillagerRole defaultRole) {
        return new HireContractSnapshot(
                false, Optional.empty(), defaultRole, false, false);
    }
}
