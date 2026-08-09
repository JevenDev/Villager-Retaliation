package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerHungerSyncPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class VillagerHungerClientCache {
    private static final Map<UUID, HungerEntry> HUNGER = new HashMap<>();

    private VillagerHungerClientCache() {
    }

    public static void accept(VillagerHungerSyncPayload payload) {
        HUNGER.put(payload.villagerId(),
                new HungerEntry(payload.entityId(), Math.clamp(payload.hunger(), 0, 20)));
    }

    public static int hunger(Entity villager) {
        if (villager == null) {
            return 20;
        }
        HungerEntry entry = HUNGER.get(villager.getUUID());
        return entry == null ? 20 : entry.hunger();
    }

    public static void clear() {
        HUNGER.clear();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        HUNGER.entrySet().removeIf(entry -> {
            Entity entity = minecraft.level.getEntity(entry.getValue().entityId());
            return entity == null || !entry.getKey().equals(entity.getUUID());
        });
    }

    private record HungerEntry(int entityId, int hunger) {
    }
}
