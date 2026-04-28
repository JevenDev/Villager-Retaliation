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
        // Mirror vanilla mob equipment-drop wear roll (Mob#dropCustomDeathLoot).
        int damage = maxDamage - random.nextInt(1 + random.nextInt(Math.max(maxDamage - 3, 1)));
        stack.setDamageValue(damage);
        return stack;
    }
}
