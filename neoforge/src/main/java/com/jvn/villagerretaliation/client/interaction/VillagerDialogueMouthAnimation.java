package com.jvn.villagerretaliation.client.interaction;

import net.minecraft.Util;
import net.minecraft.world.entity.npc.AbstractVillager;

public final class VillagerDialogueMouthAnimation {
    private static final long ACTIVE_TIMEOUT_MILLIS = 250L;
    private static int activeVillagerEntityId = -1;
    private static boolean active;
    private static long lastUpdateMillis;

    private VillagerDialogueMouthAnimation() {
    }

    public static void update(int villagerEntityId, boolean talking) {
        activeVillagerEntityId = villagerEntityId;
        active = talking;
        lastUpdateMillis = Util.getMillis();
    }

    public static void clear(int villagerEntityId) {
        if (activeVillagerEntityId == villagerEntityId) {
            clear();
        }
    }

    public static void clear() {
        activeVillagerEntityId = -1;
        active = false;
        lastUpdateMillis = 0L;
    }

    public static boolean isTalking(AbstractVillager villager) {
        return villager != null
                && active
                && villager.getId() == activeVillagerEntityId
                && Util.getMillis() - lastUpdateMillis <= ACTIVE_TIMEOUT_MILLIS;
    }
}
