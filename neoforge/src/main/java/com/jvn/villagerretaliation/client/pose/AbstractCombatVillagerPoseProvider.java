package com.jvn.villagerretaliation.client.pose;

import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

abstract class AbstractCombatVillagerPoseProvider<T extends AbstractVillager> implements VillagerPoseProvider<T> {
    private static final long SHIELD_BLOCK_POSE_GRACE_TICKS = 4L;
    private static final Map<UUID, Long> RECENT_SHIELD_BLOCK_TICKS = new HashMap<>();

    @Override
    public VillagerArmPose getArmPose(T villager, float attackTime) {
        if (villager.isUsingItem()) {
            ItemStack useItem = villager.getUseItem();
            if (useItem.is(Items.SHIELD)) {
                rememberShieldBlockTick(villager);
                return VillagerArmPose.SHIELD_BLOCK;
            }
            if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(useItem)) {
                return VillagerArmPose.CROSSBOW_CHARGE;
            }
            if (VillagerRetaliationVillagerWeapons.isBowWeapon(useItem)) {
                return VillagerArmPose.BOW_AND_ARROW;
            }
            return usingItemPose(villager);
        }
        VillagerArmPose shieldPose = resolveShieldPose(villager, attackTime);
        if (shieldPose != null) {
            return shieldPose;
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
                || shouldUseCombatModelForHeldItem(villager)
                || resolveShieldPose(villager, 0.0F) != null;
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

    protected VillagerArmPose resolveShieldPose(T villager, float attackTime) {
        if (!isHoldingShield(villager) || !isShieldCombatPosture(villager, attackTime)) {
            clearRecentShieldBlockTick(villager);
            return null;
        }

        Long lastShieldBlockTick = RECENT_SHIELD_BLOCK_TICKS.get(villager.getUUID());
        boolean withinShieldBlockGraceWindow = lastShieldBlockTick != null
                && villager.level().getGameTime() - lastShieldBlockTick <= SHIELD_BLOCK_POSE_GRACE_TICKS;
        return withinShieldBlockGraceWindow ? VillagerArmPose.SHIELD_BLOCK : VillagerArmPose.SHIELD_LOWERED;
    }

    private static void rememberShieldBlockTick(AbstractVillager villager) {
        RECENT_SHIELD_BLOCK_TICKS.put(villager.getUUID(), villager.level().getGameTime());
    }

    private static void clearRecentShieldBlockTick(AbstractVillager villager) {
        RECENT_SHIELD_BLOCK_TICKS.remove(villager.getUUID());
    }

    private static boolean isHoldingShield(AbstractVillager villager) {
        return villager.getOffhandItem().is(Items.SHIELD);
    }

    private boolean isShieldCombatPosture(T villager, float attackTime) {
        return VillagerRetaliationVillagerCombatUtil.isThreatened(villager)
                && !villager.swinging
                && attackTime <= 0.0F
                && !isHoldingCrossbow(villager)
                && !isHoldingBow(villager)
                && !isHoldingTrident(villager);
    }

    protected boolean isAggressivelyPostured(T villager) {
        return villager.isAggressive() || villager.getTarget() != null;
    }
}
