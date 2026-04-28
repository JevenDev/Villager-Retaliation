package com.jvn.commonfolk.loot;

import com.jvn.commonfolk.combat.VillagerCombatRoles;
import com.jvn.commonfolk.config.CommonfolkConfig;
import com.jvn.commonfolk.util.CommonfolkLootUtil;
import com.jvn.commonfolk.util.CommonfolkRandomUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerLootHandler {
    private VillagerLootHandler() {
    }

    public static void addDrops(Villager villager, LivingDropsEvent event) {
        if (!CommonfolkConfig.ENABLE_VILLAGER_DROPS.get() || villager.isBaby()) {
            return;
        }

        RandomSource random = villager.getRandom();
        if (CommonfolkRandomUtil.chance(random, CommonfolkConfig.VILLAGER_EMERALD_DROP_CHANCE.get())) {
            CommonfolkLootUtil.addDrop(event, new ItemStack(Items.EMERALD, CommonfolkRandomUtil.between(random, 1, 5)));
        }

        if (CommonfolkRandomUtil.chance(random, CommonfolkConfig.VILLAGER_BREAD_DROP_CHANCE.get())) {
            CommonfolkLootUtil.addDrop(event, new ItemStack(Items.BREAD, CommonfolkRandomUtil.between(random, 1, 3)));
        }

        if (CommonfolkConfig.REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT.get() && !(event.getSource().getEntity() instanceof Player)) {
            return;
        }

        if (!CommonfolkRandomUtil.chance(random, CommonfolkConfig.PROFESSION_DROP_CHANCE.get())) {
            return;
        }

        rollOutOfCombatWeaponDrop(villager, event, random);

        for (ItemStack stack : ProfessionLootPools.roll(villager, random)) {
            CommonfolkLootUtil.addDrop(event, stack);
        }
    }

    private static void rollOutOfCombatWeaponDrop(Villager villager, LivingDropsEvent event, RandomSource random) {
        if (isInCombat(villager) || !CommonfolkRandomUtil.chance(random, CommonfolkConfig.COMBAT_WEAPON_DROP_CHANCE.get())) {
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredLootWeapon(villager, random);
        if (weapon.isEmpty()) {
            return;
        }

        ItemStack drop = maybeEnchantLootWeapon(villager, weapon.copy(), random);
        CommonfolkLootUtil.addDrop(event, drop);
    }

    private static ItemStack maybeEnchantLootWeapon(Villager villager, ItemStack weapon, RandomSource random) {
        if (!(villager.level() instanceof ServerLevel level)
                || level.getDifficulty() != Difficulty.HARD
                || !CommonfolkRandomUtil.chance(random, CommonfolkConfig.COMBAT_WEAPON_ENCHANT_CHANCE.get())) {
            return weapon;
        }

        DifficultyInstance difficulty = level.getCurrentDifficultyAt(villager.blockPosition());
        EnchantmentHelper.enchantItemFromProvider(
                weapon,
                level.registryAccess(),
                VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT,
                difficulty,
                random
        );
        return weapon;
    }

    private static boolean isInCombat(Villager villager) {
        return villager.isAggressive() || villager.isChasing() || villager.getTarget() != null || villager.swinging;
    }
}
