package com.jvn.villagerretaliation.reputation.runtime;

import java.util.UUID;

public interface ReputationStore<E extends ReputationState> {
    E get(UUID entityId, UUID playerId);
    E getOrCreate(UUID entityId, UUID playerId);
    void changed();
}
