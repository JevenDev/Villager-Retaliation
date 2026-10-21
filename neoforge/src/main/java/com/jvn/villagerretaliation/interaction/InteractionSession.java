package com.jvn.villagerretaliation.interaction;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class InteractionSession {
    private final UUID id = UUID.randomUUID();
    private final UUID playerId;
    private final UUID actorId;
    private final int entityId;
    private final ResourceKey<Level> dimension;
    private final BlockPos startPosition;
    private final boolean forced;
    private long lastInteractionGameTime;
    private boolean active = true;

    public InteractionSession(UUID playerId, UUID actorId, int entityId, ResourceKey<Level> dimension,
            BlockPos startPosition, long gameTime, boolean forced) {
        this.playerId = playerId;
        this.actorId = actorId;
        this.entityId = entityId;
        this.dimension = dimension;
        this.startPosition = startPosition.immutable();
        this.forced = forced;
        this.lastInteractionGameTime = gameTime;
    }

    public UUID id() { return this.id; }

    public UUID playerId() {
        return this.playerId;
    }

    public UUID actorId() {
        return this.actorId;
    }

    public int entityId() {
        return this.entityId;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos startPosition() {
        return this.startPosition;
    }

    public boolean forced() {
        return this.forced;
    }

    public long lastInteractionGameTime() {
        return this.lastInteractionGameTime;
    }

    public boolean active() {
        return this.active;
    }

    public void touch(long gameTime) {
        this.lastInteractionGameTime = gameTime;
    }

    public void deactivate() {
        this.active = false;
    }
}
