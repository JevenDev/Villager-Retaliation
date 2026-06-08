package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

public interface VillagerPoseProvider<T extends AbstractVillager> {
    VillagerArmPose getArmPose(T villager, float attackTime);

    boolean shouldUseCombatModel(T villager);

    default boolean shouldRenderHeldItem(T villager) {
        return !VillagerRetaliationVillagerEquipment.visibleMainHand(villager).isEmpty()
                || !villager.getOffhandItem().isEmpty();
    }

    default boolean hasUsableWeapon(T villager) {
        return VillagerRetaliationVillagerWeapons.hasUsableWeapon(villager);
    }

    default boolean isInCombat(T villager) {
        return VillagerRetaliationVillagerCombatUtil.isInCombat(villager);
    }

    default boolean isHoldingRangedWeapon(T villager) {
        return villager.isHolding(VillagerRetaliationVillagerWeapons::isRangedWeapon);
    }

    default boolean isHoldingCrossbow(T villager) {
        return villager.isHolding(VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
    }

    default boolean isHoldingBow(T villager) {
        return villager.isHolding(VillagerRetaliationVillagerWeapons::isBowWeapon);
    }

    default boolean isHoldingTrident(T villager) {
        return villager.isHolding(VillagerRetaliationVillagerWeapons::isTridentWeapon);
    }

    default boolean isHoldingChargedCrossbow(T villager) {
        return isChargedCrossbow(villager.getMainHandItem()) || isChargedCrossbow(villager.getOffhandItem());
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return VillagerRetaliationVillagerWeapons.isCrossbowWeapon(stack)
                && stack.getItem() instanceof CrossbowItem
                && CrossbowItem.isCharged(stack);
    }
}
