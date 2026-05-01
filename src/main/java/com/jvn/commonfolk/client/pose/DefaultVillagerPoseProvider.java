package com.jvn.commonfolk.client.pose;

import com.jvn.commonfolk.combat.CommonfolkPotionUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import net.minecraft.world.entity.npc.Villager;
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
        if (hasUsableWeapon(villager)) {
            return isInCombat(villager) ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingRangedWeapon(villager)) {
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.NONE;
        }
        if (isHoldingPotionItem(villager)) {
            if (isHoldingDrinkableCombatConsumable(villager)) {
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
                && (CommonfolkVillagerWeapons.isCrossbowWeapon(villager.getUseItem())
                || CommonfolkPotionUtil.isDrinkableCombatConsumable(villager.getUseItem()))) {
            return true;
        }

        return isInCombat(villager)
                || hasUsableWeapon(villager)
                || isHoldingChargedCrossbow(villager)
                || isHoldingPotionItem(villager);
    }

    private static boolean isHoldingPotionItem(Villager villager) {
        return villager.isHolding(stack -> CommonfolkPotionUtil.isPotion(stack) || stack.is(Items.MILK_BUCKET));
    }

    private static boolean isHoldingDrinkableCombatConsumable(Villager villager) {
        return villager.isHolding(CommonfolkPotionUtil::isDrinkableCombatConsumable);
    }
}
