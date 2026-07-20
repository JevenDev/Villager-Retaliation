package com.jvn.villagerretaliation.client.villager;

import com.jvn.villagerretaliation.interaction.work.HiredWorkAnimation;
import com.jvn.villagerretaliation.network.VillagerWorkAnimationPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class VillagerWorkAnimationClientCache {
    private static final Map<Integer, ActiveAnimation> ACTIVE = new HashMap<>();

    private VillagerWorkAnimationClientCache() {
    }

    public static void accept(VillagerWorkAnimationPayload payload) {
        int duration = Math.max(1, Math.min(payload.durationTicks(), 40));
        ACTIVE.put(payload.entityId(), new ActiveAnimation(
                payload.animation(),
                duration,
                payload.item().isEmpty() ? ItemStack.EMPTY : payload.item().copyWithCount(1)));
    }

    public static ItemStack displayItem(AbstractVillager villager) {
        ActiveAnimation animation = ACTIVE.get(villager.getId());
        return animation == null ? ItemStack.EMPTY : animation.item();
    }

    public static boolean isActive(AbstractVillager villager) {
        return ACTIVE.containsKey(villager.getId());
    }

    public static boolean isUsingItem(AbstractVillager villager) {
        ActiveAnimation animation = ACTIVE.get(villager.getId());
        return animation != null && animation.kind() == HiredWorkAnimation.USE_ITEM;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Map.Entry<Integer, ActiveAnimation>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ActiveAnimation> entry = iterator.next();
            ActiveAnimation animation = entry.getValue();
            if (animation.ticksLeft() <= 1) {
                iterator.remove();
            } else {
                entry.setValue(new ActiveAnimation(animation.kind(), animation.ticksLeft() - 1, animation.item()));
            }
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTIVE.clear();
    }

    private record ActiveAnimation(int kind, int ticksLeft, ItemStack item) {
    }
}
