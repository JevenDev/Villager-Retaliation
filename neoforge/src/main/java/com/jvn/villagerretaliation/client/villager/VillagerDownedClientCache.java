package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerDownedStatePayload;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;

public final class VillagerDownedClientCache {
    private static final Set<Integer> DOWNED_ENTITY_IDS = new HashSet<>();

    private VillagerDownedClientCache() {
    }

    public static void accept(VillagerDownedStatePayload payload) {
        if (payload.downed()) {
            DOWNED_ENTITY_IDS.add(payload.entityId());
        } else {
            DOWNED_ENTITY_IDS.remove(payload.entityId());
        }
    }

    public static boolean isDowned(Entity entity) {
        return entity != null && DOWNED_ENTITY_IDS.contains(entity.getId());
    }

    public static void clear() {
        DOWNED_ENTITY_IDS.clear();
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
        DOWNED_ENTITY_IDS.removeIf(id -> minecraft.level.getEntity(id) == null);
    }
}
