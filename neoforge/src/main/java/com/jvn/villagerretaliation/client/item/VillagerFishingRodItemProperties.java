package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ItemAbilities;

public final class VillagerFishingRodItemProperties {
    private static final ResourceLocation CAST_PROPERTY = ResourceLocation.withDefaultNamespace("cast");
    private static final double ACTIVE_HOOK_LOOKUP_RADIUS = 32.0D;
    private static final Map<Integer, Boolean> ACTIVE_HOOK_CACHE = new HashMap<>();
    private static ClientLevel cachedLevel;
    private static long cachedGameTime = Long.MIN_VALUE;

    private VillagerFishingRodItemProperties() {
    }

    public static void register() {
        ItemProperties.register(Items.FISHING_ROD, CAST_PROPERTY, VillagerFishingRodItemProperties::castState);
    }

    private static float castState(ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        if (entity == null || !isHeldFishingRod(stack, entity)) {
            return 0.0F;
        }
        if (entity instanceof Player player && player.fishing != null) {
            return 1.0F;
        }
        return hasActiveVillagerFishingHook(level, entity) ? 1.0F : 0.0F;
    }

    private static boolean isHeldFishingRod(ItemStack stack, LivingEntity entity) {
        return stack.canPerformAction(ItemAbilities.FISHING_ROD_CAST)
                && (stack == entity.getMainHandItem() || stack == entity.getOffhandItem());
    }

    private static boolean hasActiveVillagerFishingHook(ClientLevel level, LivingEntity entity) {
        if (level == null) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (level != cachedLevel || gameTime != cachedGameTime) {
            ACTIVE_HOOK_CACHE.clear();
            cachedLevel = level;
            cachedGameTime = gameTime;
        }
        Boolean cached = ACTIVE_HOOK_CACHE.get(entity.getId());
        if (cached != null) {
            return cached;
        }
        boolean active = false;
        for (VillagerFishingHook hook : level.getEntitiesOfClass(
                VillagerFishingHook.class,
                entity.getBoundingBox().inflate(ACTIVE_HOOK_LOOKUP_RADIUS),
                hook -> hook.isAlive() && hook.getOwner() == entity)) {
            active = true;
            break;
        }
        ACTIVE_HOOK_CACHE.put(entity.getId(), active);
        return active;
    }
}
