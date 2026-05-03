package com.jvn.villagerretaliation.client.reputation;

import com.jvn.villagerretaliation.network.FearedVillagerPulsePayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class FearedVillagerAnimationClientCache {
    private static final Map<Integer, Integer> FEARED_SHAKE_TICKS = new HashMap<>();

    private FearedVillagerAnimationClientCache() {
    }

    public static void accept(FearedVillagerPulsePayload payload) {
        FEARED_SHAKE_TICKS.merge(payload.entityId(), payload.ticks(), Math::max);
    }

    public static boolean isShaking(AbstractVillager villager) {
        return FEARED_SHAKE_TICKS.getOrDefault(villager.getId(), 0) > 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            FEARED_SHAKE_TICKS.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> iterator = FEARED_SHAKE_TICKS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                iterator.remove();
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }
}
