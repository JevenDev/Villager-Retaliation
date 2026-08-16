package com.jvn.villagerretaliation.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public final class VillagerRetaliationPotionUtil {
    private VillagerRetaliationPotionUtil() {
    }

    public static boolean isPotion(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    public static boolean isDrinkablePotion(ItemStack stack) {
        return stack.is(Items.POTION);
    }

    public static boolean isDrinkableCombatConsumable(ItemStack stack) {
        return isDrinkablePotion(stack) || stack.is(Items.MILK_BUCKET);
    }

    public static boolean isHealingPotion(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return isPotion(stack) && contents.is(Potions.HEALING);
    }

    public static boolean isHealingOrRegenerationPotion(ItemStack stack) {
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        return isPotion(stack) && (contents.is(Potions.HEALING) || contents.is(Potions.REGENERATION));
    }

    public static boolean shouldSuppressCombatWhileUsingPotion(Villager villager) {
        return VillagerClericPotionHelper.isDrinkingPotion(villager)
                || villager.isUsingItem()
                && isDrinkableCombatConsumable(villager.getUseItem())
                && (VillagerCombatRoles.isCleric(villager) || VillagerCombatRoles.isFarmer(villager));
    }
}
