package com.jvn.commonfolk.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class CommonfolkLootUtil {
    private CommonfolkLootUtil() {
    }

    public static void drop(LivingEntity entity, ItemStack stack) {
        if (!stack.isEmpty()) {
            entity.spawnAtLocation(stack);
        }
    }
}
