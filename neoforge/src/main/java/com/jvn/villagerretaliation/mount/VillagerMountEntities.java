package com.jvn.villagerretaliation.mount;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class VillagerMountEntities {
    private VillagerMountEntities() {
    }

    static Entity loaded(MinecraftServer server, UUID entityId) {
        if (server == null || entityId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }
}
