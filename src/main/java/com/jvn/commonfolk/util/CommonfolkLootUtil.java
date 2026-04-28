package com.jvn.commonfolk.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class CommonfolkLootUtil {
    private CommonfolkLootUtil() {
    }

    public static void addDrop(LivingDropsEvent event, ItemStack stack) {
        LivingEntity entity = event.getEntity();
        if (!stack.isEmpty()) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), stack));
        }
    }

    public static void drop(LivingEntity entity, ItemStack stack) {
        if (!stack.isEmpty()) {
            entity.spawnAtLocation(stack);
        }
    }
}
