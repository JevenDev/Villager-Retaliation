package com.jvn.commonfolk.client.pose;

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
            if (useItem.is(Items.CROSSBOW)) {
                return VillagerArmPose.CROSSBOW_CHARGE;
            }
            if (useItem.is(Items.BOW)) {
                return VillagerArmPose.BOW_AND_ARROW;
            }
            return VillagerPoseRegistry.itemUsePose(villager).orElse(VillagerArmPose.HOLDING_ITEM);
        }

        if (isHoldingChargedCrossbow(villager)) {
            return VillagerArmPose.CROSSBOW_HOLD;
        }
        if (isHoldingRangedWeapon(villager)) {
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.MELEE_WEAPON : VillagerArmPose.NONE;
        }
        if (isHoldingPotionItem(villager)) {
            return villager.swinging || attackTime > 0.0F ? VillagerArmPose.THROWING_ITEM : VillagerArmPose.HOLDING_ITEM;
        }
        if (villager.swinging || villager.isAggressive() || villager.isChasing() || villager.getTarget() != null) {
            return VillagerArmPose.MELEE_WEAPON;
        }
        return VillagerArmPose.NONE;
    }

    @Override
    public boolean shouldUseCombatModel(Villager villager) {
        if (villager.isUsingItem() && (villager.getUseItem().is(Items.CROSSBOW) || villager.getUseItem().is(Items.POTION))) {
            return true;
        }

        return villager.isAggressive()
                || villager.isChasing()
                || villager.getTarget() != null
                || villager.swinging
                || isHoldingChargedCrossbow(villager)
                || isHoldingPotionItem(villager);
    }

    @Override
    public boolean shouldRenderHeldItem(Villager villager) {
        return !villager.getMainHandItem().isEmpty() || !villager.getOffhandItem().isEmpty();
    }

    private static boolean isHoldingRangedWeapon(Villager villager) {
        return villager.isHolding(stack -> stack.is(Items.BOW) || stack.is(Items.CROSSBOW));
    }

    private static boolean isHoldingChargedCrossbow(Villager villager) {
        ItemStack mainHand = villager.getMainHandItem();
        if (mainHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(mainHand)) {
            return true;
        }

        ItemStack offHand = villager.getOffhandItem();
        return offHand.is(Items.CROSSBOW) && CrossbowItem.isCharged(offHand);
    }

    private static boolean isHoldingPotionItem(Villager villager) {
        return villager.isHolding(stack -> stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION));
    }
}
