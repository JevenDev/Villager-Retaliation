package com.jvn.villagerretaliation.interaction;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class VillagerConversationSession extends InteractionSession {
    public VillagerConversationSession(UUID playerId, UUID villagerId, int villagerEntityId, ResourceKey<Level> dimension,
            BlockPos startPosition, long gameTime, boolean forced) {
        super(playerId, villagerId, villagerEntityId, dimension, startPosition, gameTime, forced);
    }

    public UUID villagerId() { return actorId(); }
    public int villagerEntityId() { return entityId(); }
}
