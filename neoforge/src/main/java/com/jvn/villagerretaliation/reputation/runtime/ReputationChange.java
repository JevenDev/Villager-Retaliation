package com.jvn.villagerretaliation.reputation.runtime;

import java.util.UUID;
import net.minecraft.core.BlockPos;

public record ReputationChange<T>(UUID entityId, UUID playerId, int previousScore, int score,
        T previousTier, T tier, long gameTime, BlockPos position) {
    public ReputationChange {
        position = position == null ? null : position.immutable();
    }

    public boolean changed() { return this.previousScore != this.score; }
    public boolean tierChanged() { return !this.previousTier.equals(this.tier); }
}
