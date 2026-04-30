package com.jvn.commonfolk.combat;

import com.jvn.commonfolk.villager.CommonfolkVillagerWeapons;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

final class VillagerRangedCombatHelper {
    private static final Map<UUID, Integer> SEE_TIME = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, CrossbowState> CROSSBOW_STATE = new HashMap<>();
    private static final double MAX_BOW_OR_CROSSBOW_DISTANCE_SQR = 225.0D;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int BOW_ATTACK_INTERVAL_TICKS = 20;
    private static final int INITIAL_RANGED_WINDUP_TICKS = 2;
    private static final int CROSSBOW_ATTACK_INTERVAL_TICKS = 40;
    private static final int CROSSBOW_POST_LOAD_DELAY_BASE_TICKS = 20;
    private static final int CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS = 20;
    private static final double TRIDENT_MAX_DISTANCE_SQR = 144.0D;
    private static final int TRIDENT_ATTACK_INTERVAL_TICKS = 40;

    private VillagerRangedCombatHelper() {
    }

    static boolean tryAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        ItemStack rangedWeapon = CommonfolkVillagerWeapons.getPrimaryWeapon(villager);
        if (rangedWeapon.isEmpty() || !CommonfolkVillagerWeapons.isRangedWeapon(rangedWeapon)) {
            return false;
        }

        if (CommonfolkVillagerWeapons.isTridentWeapon(rangedWeapon)) {
            return tryTridentAttack(villager, target, level, distanceSqr);
        }

        if (distanceSqr > MAX_BOW_OR_CROSSBOW_DISTANCE_SQR) {
            return false;
        }

        boolean hasLineOfSight = villager.hasLineOfSight(target);
        int seeTime = updateSeeTime(villager, hasLineOfSight);

        if (CommonfolkVillagerWeapons.isCrossbowWeapon(rangedWeapon)) {
            handleCrossbowAttack(villager, target, level, distanceSqr, hasLineOfSight, seeTime);
            return true;
        }
        if (CommonfolkVillagerWeapons.isBowWeapon(rangedWeapon)) {
            handleBowAttack(villager, target, level, distanceSqr, hasLineOfSight, seeTime);
            return true;
        }

        return false;
    }

    static void clearState(Villager villager) {
        SEE_TIME.remove(villager.getUUID());
        ATTACK_DELAY.remove(villager.getUUID());
        CROSSBOW_STATE.remove(villager.getUUID());
        if (villager.isUsingItem()) {
            villager.stopUsingItem();
        }
    }

    static void seedInitialAttackDelay(Villager villager, ItemStack equippedWeapon) {
        if (CommonfolkVillagerWeapons.isBowWeapon(equippedWeapon)
                || CommonfolkVillagerWeapons.isCrossbowWeapon(equippedWeapon)) {
            ATTACK_DELAY.put(villager.getUUID(), INITIAL_RANGED_WINDUP_TICKS);
        }
    }

    private static boolean tryTridentAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (distanceSqr > TRIDENT_MAX_DISTANCE_SQR) {
            return false;
        }

        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager) * 0.85D);
            return true;
        }

        if (!villager.hasLineOfSight(target)) {
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
            return true;
        }

        InteractionHand hand = CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isTridentWeapon);
        ItemStack heldTrident = villager.getItemInHand(hand);
        if (heldTrident.isEmpty()) {
            return false;
        }

        ItemStack thrownStack = new ItemStack(Items.TRIDENT);
        ThrownTrident thrownTrident = new ThrownTrident(level, villager, thrownStack);
        thrownTrident.pickup = AbstractArrow.Pickup.DISALLOWED;

        double dx = target.getX() - villager.getX();
        double dy = target.getY(0.3333333333333333D) - thrownTrident.getY();
        double dz = target.getZ() - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        thrownTrident.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        level.addFreshEntity(thrownTrident);

        heldTrident.hurtAndBreak(1, villager, LivingEntity.getSlotForHand(hand));
        villager.swing(hand, true);
        villager.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        ATTACK_DELAY.put(villager.getUUID(), TRIDENT_ATTACK_INTERVAL_TICKS);
        return true;
    }

    private static int updateSeeTime(Villager villager, boolean hasLineOfSight) {
        int seeTime = SEE_TIME.getOrDefault(villager.getUUID(), 0);
        boolean couldSeeLastTick = seeTime > 0;
        if (hasLineOfSight != couldSeeLastTick) {
            seeTime = 0;
        }

        seeTime += hasLineOfSight ? 1 : -1;
        SEE_TIME.put(villager.getUUID(), seeTime);
        return seeTime;
    }

    private static void handleBowAttack(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            boolean hasLineOfSight,
            int seeTime
    ) {
        if (distanceSqr <= 100.0D && seeTime >= 20) {
            villager.getNavigation().stop();
        } else {
            villager.getNavigation().moveTo(target, VillagerCombatRoles.movementSpeed(villager));
        }

        if (villager.isUsingItem()) {
            if (!hasLineOfSight && seeTime < -60) {
                villager.stopUsingItem();
                return;
            }

            if (hasLineOfSight) {
                int drawTicks = villager.getTicksUsingItem();
                if (drawTicks >= BOW_DRAW_TICKS) {
                    villager.stopUsingItem();
                    fireBowLikeIllusioner(villager, target, level, BowItem.getPowerForTime(drawTicks));
                    ATTACK_DELAY.put(villager.getUUID(), BOW_ATTACK_INTERVAL_TICKS);
                }
            }
            return;
        }

        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            return;
        }

        if (seeTime >= -60) {
            villager.startUsingItem(CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isBowWeapon));
        }
    }

    private static void fireBowLikeIllusioner(Villager villager, LivingEntity target, ServerLevel level, float power) {
        InteractionHand hand = CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isBowWeapon);
        ItemStack bowStack = villager.getItemInHand(hand);
        ItemStack ammo = villager.getProjectile(bowStack);
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }

        AbstractArrow arrow = ProjectileUtil.getMobArrow(villager, ammo, power, bowStack);
        if (bowStack.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow, ammo, bowStack);
        }

        double dx = target.getX() - villager.getX();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dz = target.getZ() - villager.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + horizontal * 0.2D, dz, 1.6F, (float) (14 - level.getDifficulty().getId() * 4));
        villager.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    private static void handleCrossbowAttack(
            Villager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            boolean hasLineOfSight,
            int seeTime
    ) {
        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            attackDelay--;
            ATTACK_DELAY.put(villager.getUUID(), attackDelay);
        }

        CrossbowState state = CROSSBOW_STATE.getOrDefault(villager.getUUID(), CrossbowState.UNCHARGED);
        if (state == CrossbowState.UNCHARGED && isHoldingChargedCrossbow(villager)) {
            state = attackDelay > 0 ? CrossbowState.CHARGED : CrossbowState.READY_TO_ATTACK;
            CROSSBOW_STATE.put(villager.getUUID(), state);
        }

        boolean shouldMove = (distanceSqr > 64.0D || seeTime < 5) && attackDelay == 0;
        if (shouldMove) {
            villager.getNavigation().moveTo(target, state == CrossbowState.UNCHARGED ? VillagerCombatRoles.movementSpeed(villager) : 0.25D);
        } else {
            villager.getNavigation().stop();
        }

        if (state == CrossbowState.UNCHARGED) {
            if (!shouldMove) {
                villager.startUsingItem(CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isCrossbowWeapon));
                CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.CHARGING);
            }
            return;
        }

        if (state == CrossbowState.CHARGING) {
            if (!villager.isUsingItem()) {
                CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.UNCHARGED);
                return;
            }

            ItemStack using = villager.getUseItem();
            if (!(using.getItem() instanceof CrossbowItem)) {
                CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.UNCHARGED);
                villager.stopUsingItem();
                return;
            }

            int chargeTicks = villager.getTicksUsingItem();
            if (chargeTicks >= CrossbowItem.getChargeDuration(using, villager)) {
                villager.releaseUsingItem();
                ensureCrossbowMarkedCharged(villager);
                CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.CHARGED);
                ATTACK_DELAY.put(villager.getUUID(), nextCrossbowPostLoadDelay(villager));
            }
            return;
        }

        if (state == CrossbowState.CHARGED) {
            if (attackDelay > 0) {
                return;
            }
            CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.READY_TO_ATTACK);
            return;
        }

        if (CROSSBOW_STATE.get(villager.getUUID()) == CrossbowState.READY_TO_ATTACK && hasLineOfSight) {
            fireCrossbowLikePillager(villager, target, level);
            ATTACK_DELAY.put(villager.getUUID(), CROSSBOW_ATTACK_INTERVAL_TICKS);
            CROSSBOW_STATE.put(villager.getUUID(), CrossbowState.UNCHARGED);
        }
    }

    private static void fireCrossbowLikePillager(Villager villager, LivingEntity target, ServerLevel level) {
        InteractionHand hand = CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (!(weapon.getItem() instanceof CrossbowItem crossbowItem)) {
            return;
        }

        if (!CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(resolveDefaultCrossbowProjectile(villager, weapon))));
            villager.setItemInHand(hand, weapon.copy());
            weapon = villager.getItemInHand(hand);
        }
        clampToSingleChargedProjectile(weapon);

        crossbowItem.performShooting(level, villager, hand, weapon, 1.6F, (float) (14 - level.getDifficulty().getId() * 4), target);
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        villager.setItemInHand(hand, weapon.copy());
    }

    private static void clampToSingleChargedProjectile(ItemStack weapon) {
        ChargedProjectiles chargedProjectiles = weapon.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        if (chargedProjectiles.isEmpty()) {
            return;
        }

        List<ItemStack> projectiles = chargedProjectiles.getItems();
        if (projectiles.size() <= 1) {
            return;
        }

        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectiles.get(0).copyWithCount(1)));
    }

    private static int nextCrossbowPostLoadDelay(Villager villager) {
        return CROSSBOW_POST_LOAD_DELAY_BASE_TICKS
                + villager.getRandom().nextInt(CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS);
    }

    private static boolean isHoldingChargedCrossbow(Villager villager) {
        InteractionHand hand = CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        return weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon);
    }

    private static void ensureCrossbowMarkedCharged(Villager villager) {
        InteractionHand hand = CommonfolkVillagerWeapons.getHoldingHand(villager, CommonfolkVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.getItem() instanceof CrossbowItem && !CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(resolveDefaultCrossbowProjectile(villager, weapon))));
            villager.setItemInHand(hand, weapon.copy());
        }
    }

    private static ItemStack resolveDefaultCrossbowProjectile(Villager villager, ItemStack crossbow) {
        ItemStack projectile = villager.getProjectile(crossbow);
        if (projectile.isEmpty()) {
            projectile = new ItemStack(Items.ARROW);
        } else {
            projectile = projectile.copyWithCount(1);
        }
        projectile.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return projectile;
    }

    private enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}
