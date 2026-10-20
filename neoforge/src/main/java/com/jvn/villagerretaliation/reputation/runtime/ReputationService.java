package com.jvn.villagerretaliation.reputation.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;

public final class ReputationService<T> {
    private final ReputationStore<?> store;
    private final ReputationProfile<T> profile;
    private final Consumer<ReputationChange<T>> changes;

    public ReputationService(ReputationStore<?> store, ReputationProfile<T> profile) {
        this(store, profile, change -> { });
    }

    public ReputationService(ReputationStore<?> store, ReputationProfile<T> profile, Consumer<ReputationChange<T>> changes) {
        this.store = Objects.requireNonNull(store, "reputation store");
        this.profile = Objects.requireNonNull(profile, "reputation profile");
        this.changes = Objects.requireNonNull(changes, "reputation change listener");
    }

    public int score(UUID entityId, UUID playerId) {
        var state = this.store.get(entityId, playerId);
        return state == null ? 0 : state.reputation();
    }

    public T tier(UUID entityId, UUID playerId) {
        return this.profile.tier(score(entityId, playerId));
    }

    public ReputationChange<T> set(UUID entityId, UUID playerId, int score, long gameTime, BlockPos position) {
        return update(entityId, playerId, score, gameTime, position, false);
    }

    public ReputationChange<T> add(UUID entityId, UUID playerId, int amount, long gameTime, BlockPos position) {
        return update(entityId, playerId, score(entityId, playerId) + amount, gameTime, position, true);
    }

    private ReputationChange<T> update(UUID entityId, UUID playerId, int score, long gameTime, BlockPos position, boolean touch) {
        Objects.requireNonNull(entityId, "reputation entity");
        Objects.requireNonNull(playerId, "reputation player");
        var state = this.store.getOrCreate(entityId, playerId);
        int previous = state.reputation();
        var change = new ReputationChange<>(entityId, playerId, previous, score,
                this.profile.tier(previous), this.profile.tier(score), gameTime, position);
        if (change.changed() || touch) {
            state.setReputation(score);
            state.setLastInteractionGameTime(gameTime);
            state.setPosition(position);
            this.store.changed();
        }
        if (change.changed()) this.changes.accept(change);
        return change;
    }
}
