package com.jvn.villagerretaliation.util;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Resolves loaded villagers without duplicating dimension scans across services. */
public final class VillagerEntityResolver {
    private VillagerEntityResolver() {
    }

    public static Villager loaded(ServerLevel level, UUID villagerId) {
        if (level == null || villagerId == null) {
            return null;
        }
        Entity entity = level.getEntity(villagerId);
        return entity instanceof Villager villager ? villager : null;
    }

    public static Villager loaded(MinecraftServer server, UUID villagerId) {
        if (server == null || villagerId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Villager villager = loaded(level, villagerId);
            if (villager != null) {
                return villager;
            }
        }
        return null;
    }

    public static Villager active(ServerLevel level, UUID villagerId) {
        Villager villager = loaded(level, villagerId);
        return villager != null && villager.isAlive() ? villager : null;
    }

    public static Villager active(MinecraftServer server, UUID villagerId) {
        Villager villager = loaded(server, villagerId);
        return villager != null && villager.isAlive() ? villager : null;
    }
}
