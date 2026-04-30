package com.jvn.commonfolk.client.pose;

import com.jvn.commonfolk.combat.CommonfolkPotionUtil;
import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class DefaultVillagerPoseProvider implements VillagerPoseProvider<Villager> {
    public static final DefaultVillagerPoseProvider INSTANCE = new DefaultVillagerPoseProvider();

    private DefaultVillagerPoseProvider() {
    }

    @Override
    public VillagerArmPose getArmPose(Villager villager, float attackTime) {
        if (villager.isUsingItem()) {
            ItemStack useItem = villager.getUseItem();
            if (CommonfolkVillagerWeapons.isCrossbowWeapon(useItem)) {
                return VillagerArmPose.CROSSBOW_CHARGE;
            }
            if (CommonfolkVillagerWeapons.isBowWeapon(useItem)) {
                return VillagerArmPose.BOW_AND_ARROW;
            }
            return VillagerPoseRegistry.itemUsePose(villager).orElse(VillagerArmPose.HOLDING_ITEM);
        }

        if (isHoldingChargedCrossbow(villager)) {
            return VillagerArmPose.CROSSBOW_HOLD;
        }
        if (isHoldingCrossbow(villager) && isInCombat(villager)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingBow(villager) && isInCombat(villager)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingTrident(villager) && isInCombat(villager)) {
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
        }
        if (CommonfolkVillagerWeapons.hasUsableWeapon(villager)) {
            return isInCombat(villager) ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingRangedWeapon(villager)) {
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.NONE;
        }
        if (isHoldingPotionItem(villager)) {
            if (isHoldingDrinkablePotion(villager)) {
                return VillagerArmPose.CASTING_OR_POTION;
            }
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.THROWING_ITEM : VillagerArmPose.HOLDING_ITEM;
        }
        if (villager.swinging || villager.isAggressive() || villager.isChasing() || villager.getTarget() != null) {
            return VillagerArmPose.MELEE_WEAPON;
        }
        return VillagerArmPose.NONE;
    }

    @Override
    public boolean shouldUseCombatModel(Villager villager) {
        if (villager.isUsingItem()
                && (CommonfolkVillagerWeapons.isCrossbowWeapon(villager.getUseItem()) || villager.getUseItem().is(Items.POTION))) {
            return true;
        }

        return CommonfolkVillagerCombatUtil.isInCombat(villager)
                || CommonfolkVillagerWeapons.hasUsableWeapon(villager)
                || isHoldingChargedCrossbow(villager)
                || isHoldingPotionItem(villager);
    }

    @Override
    public boolean shouldRenderHeldItem(Villager villager) {
        return !villager.getMainHandItem().isEmpty() || !villager.getOffhandItem().isEmpty();
    }

    private static boolean isHoldingRangedWeapon(Villager villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isRangedWeapon);
    }

    private static boolean isHoldingCrossbow(Villager villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isCrossbowWeapon);
    }

    private static boolean isHoldingBow(Villager villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isBowWeapon);
    }

    private static boolean isHoldingTrident(Villager villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isTridentWeapon);
    }

    private static boolean isHoldingChargedCrossbow(Villager villager) {
        ItemStack mainHand = villager.getMainHandItem();
        if (CommonfolkVillagerWeapons.isCrossbowWeapon(mainHand) && mainHand.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(mainHand)) {
            return true;
        }

        ItemStack offHand = villager.getOffhandItem();
        return CommonfolkVillagerWeapons.isCrossbowWeapon(offHand)
                && offHand.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(offHand);
    }

    private static boolean isHoldingPotionItem(Villager villager) {
        return villager.isHolding(CommonfolkPotionUtil::isPotion);
    }

    private static boolean isHoldingDrinkablePotion(Villager villager) {
        return villager.isHolding(CommonfolkPotionUtil::isDrinkablePotion);
    }

    private static boolean isInCombat(Villager villager) {
        return CommonfolkVillagerCombatUtil.isInCombat(villager);
    }
}
