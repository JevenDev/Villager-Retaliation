package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.UniformInt;
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
import net.minecraft.world.phys.AABB;

final class VillagerRangedCombatHelper {
    private static final Map<UUID, Integer> SEE_TIME = new HashMap<>();
    private static final Map<UUID, Integer> ATTACK_DELAY = new HashMap<>();
    private static final Map<UUID, CrossbowState> CROSSBOW_STATE = new HashMap<>();
    private static final Map<UUID, Integer> CROSSBOW_UPDATE_PATH_DELAY = new HashMap<>();
    private static final double MAX_BOW_DISTANCE_SQR = 225.0D;
    private static final int BOW_DRAW_TICKS = 20;
    private static final int BOW_ATTACK_INTERVAL_TICKS = 20;
    private static final int INITIAL_RANGED_WINDUP_TICKS = 2;
    private static final UniformInt CROSSBOW_PATHFINDING_DELAY_RANGE = TimeUtil.rangeOfSeconds(1, 2);
    private static final int CROSSBOW_MINIMUM_SEE_TIME = 5;
    private static final int CROSSBOW_POST_LOAD_DELAY_BASE_TICKS = 20;
    private static final int CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS = 20;
    private static final double TRIDENT_MAX_DISTANCE_SQR = 144.0D;
    private static final int TRIDENT_ATTACK_INTERVAL_TICKS = 40;
    private static final double POINT_BLANK_RANGED_EDGE_REACH_SQR = 1.0D;

    private VillagerRangedCombatHelper() {
    }

    static boolean tryAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        if (VillagerRetaliationPotionUtil.shouldSuppressCombatWhileUsingPotion(villager)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
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
        if (!HiredRangedAmmo.canUseRangedAttack(villager, rangedWeapon)) {
            stopAmmoBlockedRangedUse(villager, rangedWeapon);
            return true;
        }

        boolean hasLineOfSight = hasRangedLineOfSight(villager, target, villager.hasLineOfSight(target));
        if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(rangedWeapon)) {
            hasLineOfSight = hasRangedLineOfSight(villager, target, villager.getSensing().hasLineOfSight(target));
            int seeTime = updateSeeTime(villager, hasLineOfSight);
            handleCrossbowAttack(villager, target, level, rangedWeapon, hasLineOfSight, seeTime, movementSpeed);
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
        CROSSBOW_UPDATE_PATH_DELAY.remove(villager.getUUID());
        if (villager.isUsingItem()) {
            ItemStack using = villager.getUseItem();
            if (VillagerRetaliationVillagerWeapons.isBowWeapon(using) || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(using)) {
                villager.stopUsingItem();
            }
        }
    }

    static void clearRuntimeState() {
        SEE_TIME.clear();
        ATTACK_DELAY.clear();
        CROSSBOW_STATE.clear();
        CROSSBOW_UPDATE_PATH_DELAY.clear();
    }

    static boolean hasState(AbstractVillager villager) {
        UUID villagerId = villager.getUUID();
        return SEE_TIME.containsKey(villagerId)
                || ATTACK_DELAY.containsKey(villagerId)
                || CROSSBOW_STATE.containsKey(villagerId)
                || CROSSBOW_UPDATE_PATH_DELAY.containsKey(villagerId);
    }

    static void seedInitialAttackDelay(AbstractVillager villager, ItemStack equippedWeapon) {
        if (VillagerRetaliationVillagerWeapons.isBowWeapon(equippedWeapon)) {
            ATTACK_DELAY.put(villager.getUUID(), INITIAL_RANGED_WINDUP_TICKS);
        } else if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(equippedWeapon)) {
            ATTACK_DELAY.remove(villager.getUUID());
            CROSSBOW_STATE.remove(villager.getUUID());
            CROSSBOW_UPDATE_PATH_DELAY.remove(villager.getUUID());
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
        VillageCombatAuthorizationService.associateProjectile(thrownTrident, villager, target);

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
        if (hasLineOfSight && (villager.isUsingItem() || distanceSqr <= 100.0D && seeTime >= 5)) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            VillagerRetaliationRetaliationUtil.clearPathingState(villager);
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
                    if (fireBowLikeIllusioner(villager, target, level, BowItem.getPowerForTime(drawTicks))) {
                        ATTACK_DELAY.put(villager.getUUID(), BOW_ATTACK_INTERVAL_TICKS);
                    } else {
                        ATTACK_DELAY.put(villager.getUUID(), INITIAL_RANGED_WINDUP_TICKS);
                    }
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

    private static boolean fireBowLikeIllusioner(AbstractVillager villager, LivingEntity target, ServerLevel level, float power) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isBowWeapon);
        ItemStack bowStack = villager.getItemInHand(hand);
        ItemStack ammo = resolveBowProjectile(villager, bowStack);
        if (ammo.isEmpty()) {
            return false;
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
        VillageCombatAuthorizationService.associateProjectile(arrow, villager, target);
        return true;
    }

    private static void handleCrossbowAttack(
            AbstractVillager villager,
            LivingEntity target,
            ServerLevel level,
            ItemStack rangedWeapon,
            boolean hasLineOfSight,
            int seeTime,
            double movementSpeed
    ) {
        UUID villagerId = villager.getUUID();
        CrossbowState state = CROSSBOW_STATE.getOrDefault(
                villagerId,
                CrossbowItem.isCharged(rangedWeapon) ? CrossbowState.CHARGED : CrossbowState.UNCHARGED);
        boolean shouldPathToTarget = (!isWithinCrossbowAttackRange(villager, target, rangedWeapon)
                || seeTime < CROSSBOW_MINIMUM_SEE_TIME)
                && ATTACK_DELAY.getOrDefault(villagerId, 0) == 0;
        if (shouldPathToTarget) {
            tickCrossbowPathing(villager, target, movementSpeed, state == CrossbowState.UNCHARGED);
        } else {
            CROSSBOW_UPDATE_PATH_DELAY.remove(villagerId);
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            VillagerRetaliationRetaliationUtil.clearPathingState(villager);
        }

        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (state == CrossbowState.UNCHARGED) {
            if (shouldPathToTarget) {
                return;
            }
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
                if (!ensureCrossbowMarkedCharged(villager)) {
                    stopCrossbowAttack(villager);
                    return;
                }
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
            if (!hasLineOfSight) {
                return;
            }
            if (fireCrossbowLikePillager(villager, target, level)) {
                ATTACK_DELAY.remove(villagerId);
                CROSSBOW_STATE.put(villagerId, CrossbowState.UNCHARGED);
            } else {
                stopCrossbowAttack(villager);
            }
        }
    }

    private static boolean hasRangedLineOfSight(
            AbstractVillager villager,
            LivingEntity target,
            boolean vanillaLineOfSight) {
        return vanillaLineOfSight || hasPointBlankRangedShot(villager, target);
    }

    static boolean hasPointBlankRangedShot(AbstractVillager villager, LivingEntity target) {
        return isPointBlankRange(villager, target)
                && VillagerRetaliationRetaliationUtil.hasClearLineOfSight(villager, target);
    }

    private static boolean isPointBlankRange(AbstractVillager villager, LivingEntity target) {
        AABB villagerBox = villager.getBoundingBox();
        AABB targetBox = target.getBoundingBox();
        double xGap = Math.max(0.0D, Math.max(villagerBox.minX - targetBox.maxX, targetBox.minX - villagerBox.maxX));
        double zGap = Math.max(0.0D, Math.max(villagerBox.minZ - targetBox.maxZ, targetBox.minZ - villagerBox.maxZ));
        return villagerBox.maxY > targetBox.minY
                && targetBox.maxY > villagerBox.minY
                && xGap * xGap + zGap * zGap <= POINT_BLANK_RANGED_EDGE_REACH_SQR;
    }

    private static void tickCrossbowPathing(
            AbstractVillager villager,
            LivingEntity target,
            double movementSpeed,
            boolean canRun
    ) {
        UUID villagerId = villager.getUUID();
        int updatePathDelay = CROSSBOW_UPDATE_PATH_DELAY.getOrDefault(villagerId, 0) - 1;
        if (updatePathDelay <= 0) {
            VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
            villager.getNavigation().moveTo(target, canRun ? movementSpeed : movementSpeed * 0.5D);
            updatePathDelay = CROSSBOW_PATHFINDING_DELAY_RANGE.sample(villager.getRandom());
        }
        CROSSBOW_UPDATE_PATH_DELAY.put(villagerId, updatePathDelay);
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

        crossbowItem.performShooting(
                level,
                villager,
                hand,
                weapon,
                1.6F,
                (float) (14 - level.getDifficulty().getId() * 4),
                target
        );
        syncCrossbowStack(villager, hand, weapon);
        return true;
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
        CROSSBOW_UPDATE_PATH_DELAY.remove(villager.getUUID());
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
            syncCrossbowStack(villager, hand, weapon);
        }
    }

    private static boolean ensureCrossbowMarkedCharged(AbstractVillager villager) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (weapon.getItem() instanceof CrossbowItem && !CrossbowItem.isCharged(weapon)) {
            ItemStack projectile = resolveDefaultCrossbowProjectile(villager, weapon);
            if (projectile.isEmpty()) {
                return false;
            }
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(projectile)));
            syncCrossbowStack(villager, hand, weapon);
        }
        return true;
    }

    private static void syncCrossbowStack(AbstractVillager villager, InteractionHand hand, ItemStack weapon) {
        ItemStack updated = weapon.copy();
        villager.setItemInHand(hand, updated);
        if (!(villager instanceof Villager regular) || !HiredJobInventory.isJobInventoryAvailable(regular)) {
            return;
        }
        HiredJobInventory inventory = HiredJobInventory.getJobInventory(regular);
        int slot = hand == InteractionHand.MAIN_HAND
                ? HiredJobInventory.MAINHAND_SLOT
                : HiredJobInventory.OFFHAND_SLOT;
        if (ItemStack.isSameItem(inventory.getItem(slot), updated)) {
            inventory.setItem(slot, updated.copy());
        }
    }

    private static ItemStack resolveDefaultCrossbowProjectile(AbstractVillager villager, ItemStack crossbow) {
        boolean consumesAmmo = HiredRangedAmmo.requiresAmmo(villager, crossbow);
        ItemStack projectile = consumesAmmo
                ? HiredRangedAmmo.consumeAmmo(villager)
                : defaultMobProjectile(villager, crossbow);
        if (projectile.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (consumesAmmo) {
            HiredRangedAmmo.markConsumedCrossbowProjectile(projectile);
        }
        projectile.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        return projectile;
    }

    private static ItemStack resolveBowProjectile(AbstractVillager villager, ItemStack bow) {
        if (HiredRangedAmmo.requiresAmmo(villager, bow)) {
            return HiredRangedAmmo.consumeAmmo(villager);
        }
        return defaultMobProjectile(villager, bow);
    }

    private static ItemStack defaultMobProjectile(AbstractVillager villager, ItemStack weapon) {
        ItemStack projectile = villager.getProjectile(weapon);
        return projectile.isEmpty() ? new ItemStack(Items.ARROW) : projectile.copyWithCount(1);
    }

    private static void stopAmmoBlockedRangedUse(AbstractVillager villager, ItemStack weapon) {
        if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(weapon)) {
            stopCrossbowAttack(villager);
        } else if (villager.isUsingItem() && VillagerRetaliationVillagerWeapons.isBowWeapon(villager.getUseItem())) {
            villager.stopUsingItem();
        }
    }

    private enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}
