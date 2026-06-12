package com.jvn.villagerretaliation.client.renderer;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

public final class VillagerRenderEquipmentState {
    private static final int MAIN_HAND_GRACE_TICKS = 4;
    private static final int MAX_CACHED_VILLAGERS = 512;
    private static final Map<UUID, CachedMainHand> CACHED_MAIN_HANDS = new HashMap<>();

    private VillagerRenderEquipmentState() {
    }

    public static ItemStack visibleMainHand(AbstractVillager villager) {
        ItemStack visibleMainHand = VillagerRetaliationVillagerEquipment.visibleMainHand(villager);
        long gameTime = villager.level().getGameTime();
        UUID villagerId = villager.getUUID();
        if (!visibleMainHand.isEmpty()) {
            remember(villagerId, visibleMainHand, gameTime);
            return visibleMainHand;
        }

        CachedMainHand cached = CACHED_MAIN_HANDS.get(villagerId);
        if (cached == null) {
            return ItemStack.EMPTY;
        }
        long age = gameTime - cached.lastSeenGameTime();
        if (!villager.isAlive() || age < 0L || age > MAIN_HAND_GRACE_TICKS) {
            CACHED_MAIN_HANDS.remove(villagerId);
            return ItemStack.EMPTY;
        }
        return cached.stack();
    }

    private static void remember(UUID villagerId, ItemStack stack, long gameTime) {
        CACHED_MAIN_HANDS.put(villagerId, new CachedMainHand(stack.copy(), gameTime));
        if (CACHED_MAIN_HANDS.size() > MAX_CACHED_VILLAGERS) {
            prune(gameTime);
        }
    }

    private static void prune(long gameTime) {
        Iterator<Map.Entry<UUID, CachedMainHand>> iterator = CACHED_MAIN_HANDS.entrySet().iterator();
        while (iterator.hasNext()) {
            CachedMainHand cached = iterator.next().getValue();
            long age = gameTime - cached.lastSeenGameTime();
            if (age < 0L || age > MAIN_HAND_GRACE_TICKS) {
                iterator.remove();
            }
        }
    }

    private record CachedMainHand(ItemStack stack, long lastSeenGameTime) {
    }
}
