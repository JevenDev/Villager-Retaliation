package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerHungerSyncPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class VillagerHungerClientCache {
    private static final Map<Integer, Integer> HUNGER = new HashMap<>();

    private VillagerHungerClientCache() {
    }

    public static void accept(VillagerHungerSyncPayload payload) {
        HUNGER.put(payload.entityId(), Math.clamp(payload.hunger(), 0, 20));
    }

    public static int hunger(Entity villager) {
        return villager == null ? 20 : HUNGER.getOrDefault(villager.getId(), 20);
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HUNGER.clear();
    }
}
