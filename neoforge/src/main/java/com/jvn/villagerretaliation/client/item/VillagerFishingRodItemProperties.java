package com.jvn.villagerretaliation.client.item;

import com.jvn.villagerretaliation.entity.VillagerFishingHook;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ItemAbilities;

public final class VillagerFishingRodItemProperties {
    private static final ResourceLocation CAST_PROPERTY = ResourceLocation.withDefaultNamespace("cast");

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
        for (Entity nearby : level.entitiesForRendering()) {
            if (nearby instanceof VillagerFishingHook hook && hook.isAlive() && hook.getOwner() == entity) {
                return true;
            }
        }
        return false;
    }
}
