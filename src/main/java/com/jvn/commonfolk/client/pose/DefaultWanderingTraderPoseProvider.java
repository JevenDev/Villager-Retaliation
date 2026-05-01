package com.jvn.commonfolk.client.pose;

import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;

public final class DefaultWanderingTraderPoseProvider implements VillagerPoseProvider<WanderingTrader> {
    public static final DefaultWanderingTraderPoseProvider INSTANCE = new DefaultWanderingTraderPoseProvider();

    private DefaultWanderingTraderPoseProvider() {
    }

    @Override
    public VillagerArmPose getArmPose(WanderingTrader trader, float attackTime) {
        if (trader.isUsingItem()) {
            ItemStack useItem = trader.getUseItem();
            if (CommonfolkVillagerWeapons.isCrossbowWeapon(useItem)) {
                return VillagerArmPose.CROSSBOW_CHARGE;
            }
            if (CommonfolkVillagerWeapons.isBowWeapon(useItem)) {
                return VillagerArmPose.BOW_AND_ARROW;
            }
            return VillagerArmPose.HOLDING_ITEM;
        }

        if (isHoldingChargedCrossbow(trader)) {
            return VillagerArmPose.CROSSBOW_HOLD;
        }
        if (isHoldingCrossbow(trader) && isInCombat(trader)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingBow(trader) && isInCombat(trader)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingTrident(trader) && isInCombat(trader)) {
            return trader.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
        }
        if (hasUsableWeapon(trader)) {
            return isInCombat(trader) ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
        }
        if (isHoldingRangedWeapon(trader)) {
            return trader.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.NONE;
        }
        if (trader.swinging || trader.isAggressive() || trader.getTarget() != null) {
            return VillagerArmPose.MELEE_WEAPON;
        }
        return VillagerArmPose.NONE;
    }

    @Override
    public boolean shouldUseCombatModel(WanderingTrader trader) {
        return isInCombat(trader) || hasUsableWeapon(trader) || isHoldingChargedCrossbow(trader);
    }
}
