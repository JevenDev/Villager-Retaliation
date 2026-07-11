package com.jvn.villagerretaliation.scene.runtime;

import com.jvn.villagerretaliation.scene.model.SceneResource;
import java.util.UUID;

public record SceneOwner(SceneResource.OwnershipMode mode, UUID playerId, UUID partyId, UUID questInstanceId,
                         String worldKey) {
    public SceneOwner {
        mode = mode == null ? SceneResource.OwnershipMode.PLAYER : mode;
        worldKey = worldKey == null ? "" : worldKey;
        boolean valid = switch (mode) {
            case PLAYER -> playerId != null;
            case PARTY -> partyId != null;
            case QUEST_INSTANCE -> questInstanceId != null;
            case WORLD -> !worldKey.isBlank();
        };
        if (!valid) throw new IllegalArgumentException("scene owner lacks identity for " + mode);
    }

    public String stableKey() {
        return switch (mode) {
            case PLAYER -> "player:" + playerId;
            case PARTY -> "party:" + partyId;
            case QUEST_INSTANCE -> "quest:" + questInstanceId;
            case WORLD -> "world:" + worldKey;
        };
    }
}
