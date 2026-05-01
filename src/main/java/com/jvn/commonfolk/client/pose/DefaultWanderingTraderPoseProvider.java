package com.jvn.commonfolk.client.pose;

import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.CrossbowItem;
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
        if (CommonfolkVillagerWeapons.isRangedWeapon(primaryHeldItem(trader)) && isInCombat(trader)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        if (hasUsableWeapon(trader)) {
            return isInCombat(trader) ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.HOLDING_ITEM;
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

    @Override
    public boolean shouldRenderHeldItem(WanderingTrader trader) {
        return !trader.getMainHandItem().isEmpty() || !trader.getOffhandItem().isEmpty();
    }

    private static ItemStack primaryHeldItem(WanderingTrader trader) {
        ItemStack main = trader.getMainHandItem();
        if (CommonfolkVillagerWeapons.isUsableWeapon(main)) {
            return main;
        }

        ItemStack off = trader.getOffhandItem();
        if (CommonfolkVillagerWeapons.isUsableWeapon(off)) {
            return off;
        }

        return !main.isEmpty() ? main : off;
    }

    private static boolean hasUsableWeapon(WanderingTrader trader) {
        return CommonfolkVillagerWeapons.isUsableWeapon(trader.getMainHandItem())
                || CommonfolkVillagerWeapons.isUsableWeapon(trader.getOffhandItem());
    }

    private static boolean isHoldingChargedCrossbow(WanderingTrader trader) {
        ItemStack mainHand = trader.getMainHandItem();
        if (CommonfolkVillagerWeapons.isCrossbowWeapon(mainHand)
                && mainHand.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(mainHand)) {
            return true;
        }

        ItemStack offHand = trader.getOffhandItem();
        return CommonfolkVillagerWeapons.isCrossbowWeapon(offHand)
                && offHand.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(offHand);
    }

    private static boolean isInCombat(WanderingTrader trader) {
        return trader.swinging || trader.isAggressive() || trader.getTarget() != null;
    }
}
