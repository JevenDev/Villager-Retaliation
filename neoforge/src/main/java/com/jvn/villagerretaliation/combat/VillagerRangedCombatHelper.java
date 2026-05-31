package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;

final class VillagerRangedCombatHelper {
    private static final Map<UUID, Integer> SEE_TIME = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, CrossbowState> CROSSBOW_STATE = new HashMap<>();
    private static final double MAX_BOW_DISTANCE_SQR = 225.0D;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int BOW_ATTACK_INTERVAL_TICKS = 20;
    private static final int INITIAL_RANGED_WINDUP_TICKS = 2;
    private static final double CROSSBOW_BACK_UP_DISTANCE = 5.0D;
    private static final float CROSSBOW_BACK_UP_STRAFE_SPEED = 0.75F;
    private static final int CROSSBOW_POST_LOAD_DELAY_BASE_TICKS = 20;
    private static final int CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS = 20;
    private static final double TRIDENT_MAX_DISTANCE_SQR = 144.0D;
    private static final int TRIDENT_ATTACK_INTERVAL_TICKS = 40;

    private VillagerRangedCombatHelper() {
    }

    static boolean tryAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (VillagerRetaliationPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            villager.getNavigation().stop();
            return true;
        }
        return tryAttack(villager, target, level, distanceSqr, VillagerCombatRoles.movementSpeed(villager));
    }

    static boolean tryAttack(WanderingTrader trader, LivingEntity target, ServerLevel level, double distanceSqr) {
        return tryAttack(trader, target, level, distanceSqr, WanderingTraderCombatRoles.movementSpeed(trader));
    }

    static boolean tryAttack(AbstractVillager villager, LivingEntity target, ServerLevel level, double distanceSqr, double movementSpeed) {
        ItemStack rangedWeapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
        if (rangedWeapon.isEmpty() || !VillagerRetaliationVillagerWeapons.isRangedWeapon(rangedWeapon)) {
            return false;
        }

        if (VillagerRetaliationVillagerWeapons.isTridentWeapon(rangedWeapon)) {
            return tryTridentAttack(villager, target, level, distanceSqr, movementSpeed);
        }

        boolean hasLineOfSight = villager.hasLineOfSight(target);
        if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(rangedWeapon)) {
            handleCrossbowAttack(villager, target, level, rangedWeapon, hasLineOfSight, movementSpeed);
            return true;
        }

        if (distanceSqr > MAX_BOW_DISTANCE_SQR) {
            return false;
        }

        int seeTime = updateSeeTime(villager, hasLineOfSight);
        if (VillagerRetaliationVillagerWeapons.isBowWeapon(rangedWeapon)) {
            handleBowAttack(villager, target, level, distanceSqr, hasLineOfSight, seeTime, movementSpeed);
            return true;
        }

        return false;
    }

    static void clearState(AbstractVillager villager) {
        SEE_TIME.remove(villager.getUUID());
        ATTACK_DELAY.remove(villager.getUUID());
        CROSSBOW_STATE.remove(villager.getUUID());
        if (villager.isUsingItem()) {
            ItemStack using = villager.getUseItem();
            if (VillagerRetaliationVillagerWeapons.isBowWeapon(using) || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(using)) {
                villager.stopUsingItem();
            }
        }
        clearChargedCrossbows(villager);
    }

    static boolean hasState(AbstractVillager villager) {
        UUID villagerId = villager.getUUID();
        return SEE_TIME.containsKey(villagerId)
                || ATTACK_DELAY.containsKey(villagerId)
                || CROSSBOW_STATE.containsKey(villagerId);
    }

    static void seedInitialAttackDelay(AbstractVillager villager, ItemStack equippedWeapon) {
        if (VillagerRetaliationVillagerWeapons.isBowWeapon(equippedWeapon)) {
            ATTACK_DELAY.put(villager.getUUID(), INITIAL_RANGED_WINDUP_TICKS);
        } else if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(equippedWeapon)) {
            ATTACK_DELAY.remove(villager.getUUID());
            CROSSBOW_STATE.remove(villager.getUUID());
        }
    }

    private static boolean tryTridentAttack(
            AbstractVillager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            double movementSpeed
    ) {
        if (distanceSqr > TRIDENT_MAX_DISTANCE_SQR) {
            return false;
        }

        int attackDelay = ATTACK_DELAY.getOrDefault(villager.getUUID(), 0);
        if (attackDelay > 0) {
            ATTACK_DELAY.put(villager.getUUID(), attackDelay - 1);
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, movementSpeed * 0.85D);
            return true;
        }

        if (!villager.hasLineOfSight(target)) {
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, movementSpeed);
            return true;
        }

        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isTridentWeapon);
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
        thrownTrident.shoot(
                dx,
                dy + horizontal * 0.2D,
                dz,
                1.6F,
                VillagerSocialAttributeBehavior.adjustRangedInaccuracy(level, villager, (float) (14 - level.getDifficulty().getId() * 4))
        );
        level.addFreshEntity(thrownTrident);

        heldTrident.hurtAndBreak(1, villager, LivingEntity.getSlotForHand(hand));
        villager.swing(hand, true);
        villager.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        ATTACK_DELAY.put(villager.getUUID(), TRIDENT_ATTACK_INTERVAL_TICKS);
        return true;
    }

    private static int updateSeeTime(AbstractVillager villager, boolean hasLineOfSight) {
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
            AbstractVillager villager,
            LivingEntity target,
            ServerLevel level,
            double distanceSqr,
            boolean hasLineOfSight,
            int seeTime,
            double movementSpeed
    ) {
        if (distanceSqr <= 100.0D && seeTime >= 20) {
            villager.getNavigation().stop();
        } else {
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, movementSpeed);
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
            villager.startUsingItem(VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isBowWeapon));
        }
    }

    private static void fireBowLikeIllusioner(AbstractVillager villager, LivingEntity target, ServerLevel level, float power) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isBowWeapon);
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
        arrow.shoot(
                dx,
                dy + horizontal * 0.2D,
                dz,
                1.6F,
                VillagerSocialAttributeBehavior.adjustRangedInaccuracy(level, villager, (float) (14 - level.getDifficulty().getId() * 4))
        );
        villager.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    private static void handleCrossbowAttack(
            AbstractVillager villager,
            LivingEntity target,
            ServerLevel level,
            ItemStack rangedWeapon,
            boolean hasLineOfSight,
            double movementSpeed
    ) {
        if (!hasLineOfSight || !isWithinCrossbowAttackRange(villager, target, rangedWeapon)) {
            stopCrossbowAttack(villager);
            VillagerRetaliationRetaliationUtil.moveTowardReachableRetaliationTarget(villager, target, movementSpeed);
            return;
        }

        villager.getNavigation().stop();
        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (target.closerThan(villager, CROSSBOW_BACK_UP_DISTANCE)) {
            villager.getMoveControl().strafe(-CROSSBOW_BACK_UP_STRAFE_SPEED, 0.0F);
            villager.setYRot(Mth.rotateIfNecessary(villager.getYRot(), villager.yHeadRot, 0.0F));
        }

        UUID villagerId = villager.getUUID();
        CrossbowState state = CROSSBOW_STATE.getOrDefault(villager.getUUID(), CrossbowState.UNCHARGED);
        if (state == CrossbowState.UNCHARGED) {
            villager.startUsingItem(VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon));
            CROSSBOW_STATE.put(villagerId, CrossbowState.CHARGING);
            return;
        }

        if (state == CrossbowState.CHARGING) {
            if (!villager.isUsingItem()) {
                CROSSBOW_STATE.put(villagerId, CrossbowState.UNCHARGED);
                return;
            }

            ItemStack using = villager.getUseItem();
            if (!(using.getItem() instanceof CrossbowItem)) {
                stopCrossbowAttack(villager);
                return;
            }

            int chargeTicks = villager.getTicksUsingItem();
            if (chargeTicks >= CrossbowItem.getChargeDuration(using, villager)) {
                villager.releaseUsingItem();
                ensureCrossbowMarkedCharged(villager);
                CROSSBOW_STATE.put(villagerId, CrossbowState.CHARGED);
                ATTACK_DELAY.put(villagerId, nextCrossbowPostLoadDelay(villager));
            }
            return;
        }

        if (state == CrossbowState.CHARGED) {
            int attackDelay = ATTACK_DELAY.getOrDefault(villagerId, 0);
            if (attackDelay > 0) {
                attackDelay--;
                ATTACK_DELAY.put(villagerId, attackDelay);
                if (attackDelay > 0) {
                    return;
                }
            }
            CROSSBOW_STATE.put(villagerId, CrossbowState.READY_TO_ATTACK);
            return;
        }

        if (state == CrossbowState.READY_TO_ATTACK) {
            if (fireCrossbowLikePillager(villager, target, level)) {
                ATTACK_DELAY.remove(villagerId);
                CROSSBOW_STATE.put(villagerId, CrossbowState.UNCHARGED);
            } else {
                stopCrossbowAttack(villager);
            }
        }
    }

    private static boolean fireCrossbowLikePillager(AbstractVillager villager, LivingEntity target, ServerLevel level) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (!(weapon.getItem() instanceof CrossbowItem crossbowItem)) {
            return false;
        }

        if (!CrossbowItem.isCharged(weapon)) {
            return false;
        }
        clampToSingleChargedProjectile(weapon);

        crossbowItem.performShooting(
                level,
                villager,
                hand,
                weapon,
                1.6F,
                VillagerSocialAttributeBehavior.adjustRangedInaccuracy(level, villager, (float) (14 - level.getDifficulty().getId() * 4)),
                target
        );
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        villager.setItemInHand(hand, weapon.copy());
        return true;
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

    private static int nextCrossbowPostLoadDelay(AbstractVillager villager) {
        return CROSSBOW_POST_LOAD_DELAY_BASE_TICKS
                + villager.getRandom().nextInt(CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS);
    }

    private static boolean isWithinCrossbowAttackRange(AbstractVillager villager, LivingEntity target, ItemStack weapon) {
        if (!(weapon.getItem() instanceof CrossbowItem crossbowItem)) {
            return false;
        }

        return villager.closerThan(target, (double) crossbowItem.getDefaultProjectileRange());
    }

    private static void stopCrossbowAttack(AbstractVillager villager) {
        if (villager.isUsingItem() && VillagerRetaliationVillagerWeapons.isCrossbowWeapon(villager.getUseItem())) {
            villager.stopUsingItem();
        }
        ATTACK_DELAY.remove(villager.getUUID());
        CROSSBOW_STATE.remove(villager.getUUID());
        clearChargedCrossbows(villager);
    }

    private static void clearChargedCrossbows(AbstractVillager villager) {
        clearChargedCrossbow(villager, InteractionHand.MAIN_HAND);
        clearChargedCrossbow(villager, InteractionHand.OFF_HAND);
    }

    private static void clearChargedCrossbow(AbstractVillager villager, InteractionHand hand) {
        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            villager.setItemInHand(hand, weapon.copy());
        }
    }

    private static void ensureCrossbowMarkedCharged(AbstractVillager villager) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.getItem() instanceof CrossbowItem && !CrossbowItem.isCharged(weapon)) {
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(resolveDefaultCrossbowProjectile(villager, weapon))));
            villager.setItemInHand(hand, weapon.copy());
        }
    }

    private static ItemStack resolveDefaultCrossbowProjectile(AbstractVillager villager, ItemStack crossbow) {
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
