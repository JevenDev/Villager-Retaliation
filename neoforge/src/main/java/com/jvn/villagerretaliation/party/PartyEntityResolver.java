package com.jvn.villagerretaliation.party;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Resolves loaded party entities without duplicating dimension scans across services. */
final class PartyEntityResolver {
    private PartyEntityResolver() {
    }

    static Villager loadedVillager(ServerLevel level, UUID villagerId) {
        if (level == null || villagerId == null) {
            return null;
        }
        Entity entity = level.getEntity(villagerId);
        return entity instanceof Villager villager ? villager : null;
    }

    static Villager loadedVillager(MinecraftServer server, UUID villagerId) {
        if (server == null || villagerId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Villager villager = loadedVillager(level, villagerId);
            if (villager != null) {
                return villager;
            }
        }
        return null;
    }

    static Villager activeVillager(ServerLevel level, UUID villagerId) {
        Villager villager = loadedVillager(level, villagerId);
        return villager != null && villager.isAlive() ? villager : null;
    }

    static Villager activeVillager(MinecraftServer server, UUID villagerId) {
        Villager villager = loadedVillager(server, villagerId);
        return villager != null && villager.isAlive() ? villager : null;
    }
}
