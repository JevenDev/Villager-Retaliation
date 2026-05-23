package com.jvn.villagerretaliation.loot;

import com.jvn.villagerretaliation.combat.VillagerRetaliationCombatWeaponFactory;
import com.jvn.villagerretaliation.combat.VillagerCombatRoles;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.toucanlib.util.ToucanItemStacks;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.toucanlib.neoforge.loot.ToucanLivingDrops;
import com.jvn.toucanlib.util.ToucanRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

public final class VillagerLootHandler {
    private VillagerLootHandler() {
    }

    public static void addDrops(Villager villager, LivingDropsEvent event) {
        VillagerInventoryAccess.dropAllInventoryAndEquipment(villager, event);

        if (!VillagerRetaliationConfig.ENABLE_VILLAGER_DROPS.get()
                || villager.isBaby() && !VillagerRetaliationConfig.BABY_VILLAGERS_DROP_LOOT.get()) {
            return;
        }

        RandomSource random = villager.getRandom();
        if (ToucanRandom.chance(random, VillagerRetaliationConfig.VILLAGER_EMERALD_DROP_CHANCE.get())) {
            ToucanLivingDrops.addDrop(event, new ItemStack(Items.EMERALD, ToucanRandom.betweenInclusive(random, 1, 5)));
        }

        if (ToucanRandom.chance(random, VillagerRetaliationConfig.VILLAGER_BREAD_DROP_CHANCE.get())) {
            ToucanLivingDrops.addDrop(event, new ItemStack(Items.BREAD, ToucanRandom.betweenInclusive(random, 1, 3)));
        }

        if (VillagerRetaliationConfig.REQUIRE_PLAYER_KILL_FOR_PROFESSION_LOOT.get() && !(event.getSource().getEntity() instanceof Player)) {
            return;
        }

        if (!ToucanRandom.chance(random, VillagerRetaliationConfig.PROFESSION_DROP_CHANCE.get())) {
            return;
        }

        rollOutOfCombatWeaponDrop(villager, event, random);

        for (ItemStack stack : ProfessionLootResources.roll(villager, event, random)) {
            ToucanLivingDrops.addDrop(event, stack);
        }
    }

    private static void rollOutOfCombatWeaponDrop(Villager villager, LivingDropsEvent event, RandomSource random) {
        if (isInCombat(villager) || !ToucanRandom.chance(random, VillagerRetaliationConfig.COMBAT_WEAPON_DROP_CHANCE.get())) {
            return;
        }

        ItemStack weapon = VillagerCombatRoles.preferredLootWeapon(villager);
        if (weapon.isEmpty()) {
            return;
        }

        ItemStack drop = resolveOutOfCombatWeaponDrop(villager, weapon, random);
        ToucanLivingDrops.addDropIfNoMatchingItem(event, drop);
    }

    private static ItemStack resolveOutOfCombatWeaponDrop(Villager villager, ItemStack preferredWeapon, RandomSource random) {
        ItemStack mainHand = villager.getMainHandItem();
        if (!mainHand.isEmpty() && ItemStack.isSameItem(mainHand, preferredWeapon)) {
            return mainHand.copy();
        }

        return maybeEnchantLootWeapon(villager, ToucanItemStacks.withRandomMobDropDamage(preferredWeapon.copy(), random), random);
    }

    private static ItemStack maybeEnchantLootWeapon(Villager villager, ItemStack weapon, RandomSource random) {
        return VillagerRetaliationCombatWeaponFactory.maybeEnchantCombatWeapon(villager, weapon, random);
    }

    private static boolean isInCombat(Villager villager) {
        return VillagerRetaliationVillagerCombatUtil.isInCombat(villager);
    }
}
