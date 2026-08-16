package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.network.VillagerDownedStatePayload;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedPose;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;

public final class VillagerDownedClientCache {
    private static final Map<Integer, VillagerDownedPose> DOWNED_POSES = new HashMap<>();

    private VillagerDownedClientCache() {
    }

    public static void accept(VillagerDownedStatePayload payload) {
        boolean recovered = false;
        if (payload.downed()) {
            DOWNED_POSES.put(payload.entityId(), VillagerDownedPose.fromId(payload.pose()).orElse(VillagerDownedPose.SITTING));
        } else {
            recovered = DOWNED_POSES.remove(payload.entityId()) != null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (recovered
                && minecraft.options.keyUse.isDown()
                && minecraft.hitResult instanceof EntityHitResult hitResult
                && hitResult.getEntity().getId() == payload.entityId()) {
            minecraft.options.keyUse.setDown(false);
        }
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(payload.entityId());
            if (entity != null) {
                entity.refreshDimensions();
            }
        }
    }

    public static boolean isDowned(Entity entity) {
        return entity != null && DOWNED_POSES.containsKey(entity.getId());
    }

    public static VillagerDownedPose pose(Entity entity) {
        if (entity == null) return VillagerDownedPose.SITTING;
        return DOWNED_POSES.getOrDefault(entity.getId(), VillagerDownedPose.forVillager(entity.getUUID()));
    }

    public static void onEntitySize(EntityEvent.Size event) {
        if (isDowned(event.getEntity())) {
            event.setNewSize(pose(event.getEntity()).dimensions(event.getNewSize()));
        }
    }

    public static void clear() {
        DOWNED_POSES.clear();
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
        DOWNED_POSES.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
    }
}
