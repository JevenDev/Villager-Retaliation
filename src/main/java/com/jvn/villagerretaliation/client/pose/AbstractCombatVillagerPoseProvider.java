package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.ItemStack;

abstract class AbstractCombatVillagerPoseProvider<T extends AbstractVillager> implements VillagerPoseProvider<T> {
    @Override
    public VillagerArmPose getArmPose(T villager, float attackTime) {
        if (villager.isUsingItem()) {
            ItemStack useItem = villager.getUseItem();
            if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(useItem)) {
                return VillagerArmPose.CROSSBOW_CHARGE;
            }
            if (VillagerRetaliationVillagerWeapons.isBowWeapon(useItem)) {
                return VillagerArmPose.BOW_AND_ARROW;
            }
            return usingItemPose(villager);
        }

        if (isHoldingChargedCrossbow(villager)) {
            return VillagerArmPose.CROSSBOW_HOLD;
        }
        if (isHoldingCrossbow(villager) && isInCombat(villager)) {
            return VillagerArmPose.CROSSBOW_HOLD;
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
        if (hasCustomHeldItemPose(villager)) {
            return heldItemPose(villager, attackTime);
        }
        if (villager.swinging || isAggressivelyPostured(villager)) {
            return VillagerArmPose.MELEE_WEAPON;
        }
        return VillagerArmPose.NONE;
    }

    @Override
    public boolean shouldUseCombatModel(T villager) {
        if (villager.isUsingItem() && shouldUseCombatModelWhileUsingItem(villager, villager.getUseItem())) {
            return true;
        }

        return isInCombat(villager)
                || hasUsableWeapon(villager)
                || isHoldingChargedCrossbow(villager)
                || shouldUseCombatModelForHeldItem(villager);
    }

    protected VillagerArmPose usingItemPose(T villager) {
        return VillagerArmPose.HOLDING_ITEM;
    }

    protected boolean hasCustomHeldItemPose(T villager) {
        return false;
    }

    protected VillagerArmPose heldItemPose(T villager, float attackTime) {
        return VillagerArmPose.HOLDING_ITEM;
    }

    protected boolean shouldUseCombatModelWhileUsingItem(T villager, ItemStack useItem) {
        return VillagerRetaliationVillagerWeapons.isCrossbowWeapon(useItem);
    }

    protected boolean shouldUseCombatModelForHeldItem(T villager) {
        return false;
    }

    protected boolean isAggressivelyPostured(T villager) {
        return villager.isAggressive() || villager.getTarget() != null;
    }
}
