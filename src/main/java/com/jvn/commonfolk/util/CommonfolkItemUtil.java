package com.jvn.commonfolk.util;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public final class CommonfolkItemUtil {
    private CommonfolkItemUtil() {
    }

    public static ItemStack withRandomDamage(ItemStack stack, RandomSource random) {
        if (!stack.isDamageableItem()) {
            return stack;
        }

        int maxDamage = stack.getMaxDamage();
        int damage = CommonfolkRandomUtil.between(random, maxDamage / 5, Math.max(maxDamage / 5, maxDamage - 1));
        stack.setDamageValue(damage);
        return stack;
    }
}
