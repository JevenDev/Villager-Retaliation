package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;

public final class VillagerRetaliationCombatWeaponFactory {
    private VillagerRetaliationCombatWeaponFactory() {
    }

    public static ItemStack prepareEquippedCombatWeapon(LivingEntity wielder, ItemStack weapon) {
        return maybeEnchantCombatWeapon(wielder, weapon, wielder.getRandom());
    }

    public static ItemStack maybeEnchantCombatWeapon(LivingEntity wielder, ItemStack weapon, RandomSource random) {
        if (!shouldRollCombatEnchant(weapon)
                || !(wielder.level() instanceof ServerLevel level)
                || level.getDifficulty() != Difficulty.HARD
                || random.nextFloat() >= currentCombatWeaponEnchantChance()) {
            return weapon;
        }

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(wielder.blockPosition());
        EnchantmentHelper.enchantItemFromProvider(
                weapon,
                level.registryAccess(),
                VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                difficulty,
                random
        );
        return weapon;
    }

    private static boolean shouldRollCombatEnchant(ItemStack weapon) {
        return !weapon.isEmpty()
                && !weapon.is(Items.BOOK)
                && !weapon.is(Items.BREAD);
    }

    private static float currentCombatWeaponEnchantChance() {
        return VillagerRetaliationConfig.COMBAT_WEAPON_ENCHANT_CHANCE.get().floatValue();
    }
}
