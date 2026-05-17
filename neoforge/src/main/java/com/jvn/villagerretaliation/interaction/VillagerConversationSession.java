package com.jvn.villagerretaliation.interaction;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class VillagerConversationSession {
    private final UUID playerId;
    private final UUID villagerId;
    private final int villagerEntityId;
    private final ResourceKey<Level> dimension;
    private final BlockPos startPosition;
    private long lastInteractionGameTime;
    private boolean active = true;

    public VillagerConversationSession(UUID playerId, UUID villagerId, int villagerEntityId, ResourceKey<Level> dimension,
            BlockPos startPosition, long gameTime) {
        this.playerId = playerId;
        this.villagerId = villagerId;
        this.villagerEntityId = villagerEntityId;
        this.dimension = dimension;
        this.startPosition = startPosition;
        this.lastInteractionGameTime = gameTime;
    }

    public UUID playerId() {
        return this.playerId;
    }

    public UUID villagerId() {
        return this.villagerId;
    }

    public int villagerEntityId() {
        return this.villagerEntityId;
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos startPosition() {
        return this.startPosition;
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
