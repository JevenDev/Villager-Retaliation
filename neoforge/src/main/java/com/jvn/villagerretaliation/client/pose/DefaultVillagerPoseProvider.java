package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.client.inventory.VillagerInventoryScreen;
import com.jvn.villagerretaliation.combat.VillagerRetaliationPotionUtil;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;

public final class DefaultVillagerPoseProvider extends AbstractCombatVillagerPoseProvider<Villager> {
    public static final DefaultVillagerPoseProvider INSTANCE = new DefaultVillagerPoseProvider();

    private DefaultVillagerPoseProvider() {
    }

    @Override
    protected VillagerArmPose usingItemPose(Villager villager) {
        return VillagerPoseRegistry.itemUsePose(villager).orElse(VillagerArmPose.HOLDING_ITEM);
    }

    @Override
    protected boolean hasCustomHeldItemPose(Villager villager) {
        return isHoldingPotionItem(villager);
    }

    @Override
    protected VillagerArmPose heldItemPose(Villager villager, float attackTime) {
        if (isHoldingDrinkableCombatConsumable(villager)) {
            return VillagerArmPose.CASTING_OR_POTION;
        }
        return villager.swinging || attackTime > 0.0F ? VillagerArmPose.THROWING_ITEM : VillagerArmPose.HOLDING_ITEM;
    }

    @Override
    protected boolean shouldUseCombatModelWhileUsingItem(Villager villager, net.minecraft.world.item.ItemStack useItem) {
        return super.shouldUseCombatModelWhileUsingItem(villager, useItem)
                || useItem.is(Items.SHIELD)
                || VillagerRetaliationPotionUtil.isDrinkableCombatConsumable(useItem);
    }

    @Override
    protected boolean shouldUseCombatModelForHeldItem(Villager villager) {
        return isHoldingPotionItem(villager);
    }

    @Override
    protected boolean isAggressivelyPostured(Villager villager) {
        return villager.isAggressive() || villager.isChasing() || villager.getTarget() != null;
    }

    @Override
    public VillagerArmPose getArmPose(Villager villager, float attackTime) {
        if (VillagerInventoryScreen.isRenderingInventoryPreview(villager)
                && shouldRenderHeldItem(villager)) {
            return VillagerArmPose.HOLDING_ITEM;
        }
        return super.getArmPose(villager, attackTime);
    }

    private static boolean isHoldingPotionItem(Villager villager) {
        return villager.isHolding(stack -> VillagerRetaliationPotionUtil.isPotion(stack) || stack.is(Items.MILK_BUCKET));
    }

    private static boolean isHoldingDrinkableCombatConsumable(Villager villager) {
        return villager.isHolding(VillagerRetaliationPotionUtil::isDrinkableCombatConsumable);
    }
}
