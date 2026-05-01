package com.jvn.commonfolk.client.pose;

import com.jvn.commonfolk.util.CommonfolkVillagerCombatUtil;
import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

public interface VillagerPoseProvider<T extends AbstractVillager> {
    VillagerArmPose getArmPose(T villager, float attackTime);

    boolean shouldUseCombatModel(T villager);

    default boolean shouldRenderHeldItem(T villager) {
        return !villager.getMainHandItem().isEmpty() || !villager.getOffhandItem().isEmpty();
    }

    default boolean hasUsableWeapon(T villager) {
        return CommonfolkVillagerWeapons.hasUsableWeapon(villager);
    }

    default boolean isInCombat(T villager) {
        return CommonfolkVillagerCombatUtil.isInCombat(villager);
    }

    default boolean isHoldingRangedWeapon(T villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isRangedWeapon);
    }

    default boolean isHoldingCrossbow(T villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isCrossbowWeapon);
    }

    default boolean isHoldingBow(T villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isBowWeapon);
    }

    default boolean isHoldingTrident(T villager) {
        return villager.isHolding(CommonfolkVillagerWeapons::isTridentWeapon);
    }

    default boolean isHoldingChargedCrossbow(T villager) {
        return isChargedCrossbow(villager.getMainHandItem()) || isChargedCrossbow(villager.getOffhandItem());
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return CommonfolkVillagerWeapons.isCrossbowWeapon(stack)
                && stack.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(stack);
    }
}
