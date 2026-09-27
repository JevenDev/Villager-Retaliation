package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.mount.VillagerMountSpeedPolicy;
import com.jvn.villagerretaliation.allegiance.VillageCombatAuthorizationService;
import com.jvn.villagerretaliation.interaction.work.HiredRangedAmmo;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerWeapons;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.Unit;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
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

public final class VillagerRangedCombatHelper {
    private static final String STATE_TAG = "VillagerRetaliationRangedCombat";
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

    public static boolean tryDuelAttack(Villager villager, LivingEntity target, ServerLevel level, double distanceSqr) {
        return tryAttack(villager, target, level, distanceSqr);
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
        if (villager instanceof Villager mountedVillager) {
            movementSpeed = VillagerMountSpeedPolicy.toward(mountedVillager, target, movementSpeed);
        }
        ItemStack rangedWeapon = VillagerRetaliationVillagerWeapons.getPrimaryWeapon(villager);
        if (rangedWeapon.isEmpty() || !VillagerRetaliationVillagerWeapons.isRangedWeapon(rangedWeapon)) {
            return false;
        }

        if (VillagerRetaliationVillagerWeapons.isTridentWeapon(rangedWeapon)) {
            return tryTridentAttack(villager, target, level, distanceSqr, movementSpeed);
        }
        if (!HiredRangedAmmo.canUseRangedAttack(villager, rangedWeapon)
                && !(VillagerRetaliationVillagerWeapons.isCrossbowWeapon(rangedWeapon)
                && (hasLoadedCrossbowProjectile(villager) || hasActiveCrossbowCycle(villager)))) {
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

    public static void clearDuelState(AbstractVillager villager) {
        clearState(villager);
    }

    static void clearState(AbstractVillager villager) {
        refundReservedCrossbowProjectile(villager);
        villager.getPersistentData().remove(STATE_TAG);
        if (villager.isUsingItem()) {
            ItemStack using = villager.getUseItem();
            if (VillagerRetaliationVillagerWeapons.isBowWeapon(using) || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(using)) {
                villager.stopUsingItem();
            }
        }
    }

    static void cancelForWeaponSwitch(AbstractVillager villager) {
        clearState(villager);
    }

    static boolean hasState(AbstractVillager villager) {
        var state = readState(villager);
        return state.contains("SeeTime") || state.contains("AttackDelay")
                || state.contains("CrossbowState") || state.contains("PathDelay");
    }

    static boolean hasLoadedCrossbowProjectile(AbstractVillager villager) {
        ItemStack projectile = loadedProjectile(villager);
        return projectile != null && !projectile.isEmpty();
    }

    static boolean hasActiveCrossbowCycle(AbstractVillager villager) {
        CrossbowState state = crossbowState(villager);
        return state == CrossbowState.CHARGING
                || state == CrossbowState.CHARGED
                || state == CrossbowState.READY_TO_ATTACK;
    }

    /** Advances only the recovery timer while Stand Guard temporarily stows the weapon. */
    static boolean tickGuardInterval(AbstractVillager villager) {
        if (villager.isUsingItem()) {
            return false;
        }
        CrossbowState crossbowState = crossbowState(villager);
        if (crossbowState == CrossbowState.CHARGING || crossbowState == CrossbowState.READY_TO_ATTACK) {
            return false;
        }
        int delay = readState(villager).getInt("AttackDelay");
        if (delay <= 0) {
            return false;
        }
        state(villager).putInt("AttackDelay", delay - 1);
        return true;
    }

    static void seedInitialAttackDelay(AbstractVillager villager, ItemStack equippedWeapon) {
        if (VillagerRetaliationVillagerWeapons.isBowWeapon(equippedWeapon)) {
            state(villager).putInt("AttackDelay", INITIAL_RANGED_WINDUP_TICKS);
        } else if (VillagerRetaliationVillagerWeapons.isCrossbowWeapon(equippedWeapon)) {
            if (hasLoadedCrossbowProjectile(villager)) {
                return;
            }
            readState(villager).remove("AttackDelay");
            readState(villager).remove("CrossbowState");
            readState(villager).remove("LoadedProjectile");
            readState(villager).remove("PathDelay");
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

        int attackDelay = readState(villager).getInt("AttackDelay");
        if (attackDelay > 0) {
            state(villager).putInt("AttackDelay", attackDelay - 1);
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

        ItemStack thrownStack = heldTrident.copyWithCount(1);
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
                VillagerCombatSkillBehavior.adjustRangedInaccuracy(
                        level, villager, (float) (14 - level.getDifficulty().getId() * 4))
        );
        level.addFreshEntity(thrownTrident);
        VillageCombatAuthorizationService.associateProjectile(thrownTrident, villager, target);

        ItemStack undamagedTrident = heldTrident.copy();
        heldTrident.hurtAndBreak(1, villager, LivingEntity.getSlotForHand(hand));
        syncRangedWeaponStack(villager, hand, heldTrident, undamagedTrident);
        villager.swing(hand, true);
        villager.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        state(villager).putInt("AttackDelay", attackRecoveryTicks(villager, TRIDENT_ATTACK_INTERVAL_TICKS));
        return true;
    }

    private static int updateSeeTime(AbstractVillager villager, boolean hasLineOfSight) {
        int seeTime = readState(villager).getInt("SeeTime");
        boolean couldSeeLastTick = seeTime > 0;
        if (hasLineOfSight != couldSeeLastTick) {
            seeTime = 0;
        }

        seeTime += hasLineOfSight ? 1 : -1;
        state(villager).putInt("SeeTime", seeTime);
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
                        state(villager).putInt("AttackDelay", attackRecoveryTicks(villager, BOW_ATTACK_INTERVAL_TICKS));
                    } else {
                        state(villager).putInt("AttackDelay", INITIAL_RANGED_WINDUP_TICKS);
                    }
                }
            }
            return;
        }

        int attackDelay = readState(villager).getInt("AttackDelay");
        if (attackDelay > 0) {
            state(villager).putInt("AttackDelay", attackDelay - 1);
            return;
        }

        if (seeTime >= -60) {
            villager.startUsingItem(VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isBowWeapon));
        }
    }

    static boolean fireBowLikeIllusioner(AbstractVillager villager, LivingEntity target, ServerLevel level, float power) {
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
                VillagerCombatSkillBehavior.adjustRangedInaccuracy(
                        level, villager, (float) (14 - level.getDifficulty().getId() * 4))
        );
        villager.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (villager.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
        VillageCombatAuthorizationService.associateProjectile(arrow, villager, target);
        ItemStack undamagedBow = bowStack.copy();
        bowStack.hurtAndBreak(1, villager, LivingEntity.getSlotForHand(hand));
        syncRangedWeaponStack(villager, hand, bowStack, undamagedBow);
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
        CrossbowState state = crossbowState(villager);
        if (state == null) state = CrossbowItem.isCharged(rangedWeapon) ? CrossbowState.CHARGED : CrossbowState.UNCHARGED;
        if ((state == CrossbowState.CHARGED || state == CrossbowState.READY_TO_ATTACK)
                && !CrossbowItem.isCharged(rangedWeapon)
                && !restoreLoadedCrossbowProjectile(villager)) {
            stopCrossbowAttack(villager);
            return;
        }
        boolean shouldPathToTarget = (!isWithinCrossbowAttackRange(villager, target, rangedWeapon)
                || seeTime < CROSSBOW_MINIMUM_SEE_TIME)
                && readState(villager).getInt("AttackDelay") == 0;
        if (shouldPathToTarget) {
            tickCrossbowPathing(villager, target, movementSpeed, state == CrossbowState.UNCHARGED);
        } else {
            readState(villager).remove("PathDelay");
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
            VillagerRetaliationRetaliationUtil.clearPathingState(villager);
        }

        villager.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (state == CrossbowState.UNCHARGED) {
            if (shouldPathToTarget) {
                return;
            }
            ItemStack reservedProjectile = loadedProjectile(villager);
            if (reservedProjectile == null || reservedProjectile.isEmpty()) {
                reservedProjectile = resolveDefaultCrossbowProjectile(villager, rangedWeapon);
                if (reservedProjectile.isEmpty()) {
                    stopCrossbowAttack(villager);
                    return;
                }
                loadedProjectile(villager, reservedProjectile);
            }
            villager.startUsingItem(VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon));
            crossbowState(villager, CrossbowState.CHARGING);
            return;
        }

        if (state == CrossbowState.CHARGING) {
            if (!villager.isUsingItem()) {
                crossbowState(villager, CrossbowState.UNCHARGED);
                return;
            }

            ItemStack using = villager.getUseItem();
            if (!(using.getItem() instanceof CrossbowItem)) {
                stopCrossbowAttack(villager);
                return;
            }

            int chargeTicks = villager.getTicksUsingItem();
            if (chargeTicks >= CrossbowItem.getChargeDuration(using, villager)) {
                if (!ensureCrossbowMarkedCharged(villager, using)) {
                    stopCrossbowAttack(villager);
                    return;
                }
                // The active-use stack can be a detached equipment copy. Finish loading
                // against the authoritative hand stack, then end the use animation without
                // asking vanilla to mutate and consume from the detached copy again.
                villager.stopUsingItem();
                crossbowState(villager, CrossbowState.CHARGED);
                state(villager).putInt("AttackDelay", nextCrossbowPostLoadDelay(villager));
            }
            return;
        }

        if (state == CrossbowState.CHARGED) {
            int attackDelay = readState(villager).getInt("AttackDelay");
            if (attackDelay > 0) {
                attackDelay--;
                state(villager).putInt("AttackDelay", attackDelay);
                if (attackDelay > 0) {
                    return;
                }
            }
            crossbowState(villager, CrossbowState.READY_TO_ATTACK);
            return;
        }

        if (state == CrossbowState.READY_TO_ATTACK) {
            if (!hasLineOfSight) {
                return;
            }
            if (fireCrossbowLikePillager(villager, target, level)) {
                readState(villager).remove("AttackDelay");
                readState(villager).remove("LoadedProjectile");
                crossbowState(villager, CrossbowState.UNCHARGED);
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
        int updatePathDelay = readState(villager).getInt("PathDelay") - 1;
        if (updatePathDelay <= 0) {
            VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
            villager.getNavigation().moveTo(target, canRun ? movementSpeed : movementSpeed * 0.5D);
            updatePathDelay = CROSSBOW_PATHFINDING_DELAY_RANGE.sample(villager.getRandom());
        }
        state(villager).putInt("PathDelay", updatePathDelay);
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
                VillagerCombatSkillBehavior.adjustRangedInaccuracy(
                        level, villager, (float) (14 - level.getDifficulty().getId() * 4)),
                target
        );
        syncRangedWeaponStack(villager, hand, weapon);
        return true;
    }

    private static int nextCrossbowPostLoadDelay(AbstractVillager villager) {
        int normalTicks = CROSSBOW_POST_LOAD_DELAY_BASE_TICKS
                + villager.getRandom().nextInt(CROSSBOW_POST_LOAD_DELAY_RANDOM_TICKS);
        return attackRecoveryTicks(villager, normalTicks);
    }

    private static int attackRecoveryTicks(AbstractVillager villager, int normalTicks) {
        return villager instanceof Villager villageResident
                ? VillagerCombatRoles.rangedAttackRecoveryTicks(villageResident, normalTicks)
                : VillagerCombatSkillBehavior.adjustRangedRecoveryTicks(
                        (ServerLevel) villager.level(), villager, normalTicks);
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
        refundReservedCrossbowProjectile(villager, true);
        clearState(villager);
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
            syncRangedWeaponStack(villager, hand, weapon);
        }
    }

    private static boolean ensureCrossbowMarkedCharged(AbstractVillager villager, ItemStack activeUseItem) {
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(villager, VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (!(weapon.getItem() instanceof CrossbowItem)) {
            return false;
        }
        if (!CrossbowItem.isCharged(weapon)) {
            ChargedProjectiles releasedProjectiles = activeUseItem.getOrDefault(
                    DataComponents.CHARGED_PROJECTILES,
                    ChargedProjectiles.EMPTY);
            if (!releasedProjectiles.isEmpty()) {
                weapon.set(
                        DataComponents.CHARGED_PROJECTILES,
                        ChargedProjectiles.of(releasedProjectiles.getItems()));
            } else {
                ItemStack projectile = loadedProjectile(villager);
                if (projectile.isEmpty()) {
                    projectile = resolveDefaultCrossbowProjectile(villager, weapon);
                }
                if (projectile.isEmpty()) {
                    return false;
                }
                weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(projectile)));
            }
        }
        ChargedProjectiles chargedProjectiles = weapon.getOrDefault(
                DataComponents.CHARGED_PROJECTILES,
                ChargedProjectiles.EMPTY);
        if (chargedProjectiles.isEmpty()) {
            return false;
        }
        // The job inventory and equipped stack are synchronized independently. Preserve
        // vanilla-loaded ammunition so a stale uncharged copy can be repaired next tick.
        loadedProjectile(villager, chargedProjectiles.getItems().getFirst());
        syncRangedWeaponStack(villager, hand, weapon);
        return CrossbowItem.isCharged(weapon);
    }

    private static boolean restoreLoadedCrossbowProjectile(AbstractVillager villager) {
        ItemStack projectile = loadedProjectile(villager);
        if (projectile == null || projectile.isEmpty()) {
            return false;
        }
        InteractionHand hand = VillagerRetaliationVillagerWeapons.getHoldingHand(
                villager,
                VillagerRetaliationVillagerWeapons::isCrossbowWeapon);
        ItemStack weapon = villager.getItemInHand(hand);
        if (!(weapon.getItem() instanceof CrossbowItem)) {
            return false;
        }
        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(List.of(projectile.copy())));
        syncRangedWeaponStack(villager, hand, weapon);
        return true;
    }

    private static void syncRangedWeaponStack(AbstractVillager villager, InteractionHand hand, ItemStack weapon) {
        syncRangedWeaponStack(villager, hand, weapon, weapon);
    }

    private static void syncRangedWeaponStack(
            AbstractVillager villager,
            InteractionHand hand,
            ItemStack weapon,
            ItemStack previousWeapon) {
        ItemStack updated = weapon.copy();
        if (villager instanceof Villager regular
                && !DuelService.isParticipant(regular)
                && HiredJobInventory.isJobInventoryAvailable(regular)) {
            HiredJobInventory inventory = HiredJobInventory.getJobInventory(regular);
            int slot = hand == InteractionHand.MAIN_HAND
                    ? HiredJobInventory.MAINHAND_SLOT
                    : HiredJobInventory.OFFHAND_SLOT;
            ItemStack expected = updated.isEmpty() ? previousWeapon : updated;
            if (ItemStack.isSameItem(inventory.getItem(slot), expected)) {
                inventory.setItem(slot, updated.copy());
            }
        }
        EquipmentSlot slot = hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        VillagerRetaliationVillagerEquipment.setInventoryEquipment(villager, slot, updated);
    }

    private static void refundReservedCrossbowProjectile(AbstractVillager villager) {
        refundReservedCrossbowProjectile(villager, false);
    }

    private static void refundReservedCrossbowProjectile(AbstractVillager villager, boolean refundChargedCycle) {
        CrossbowState state = crossbowState(villager);
        if (!refundChargedCycle && (state == CrossbowState.CHARGED || state == CrossbowState.READY_TO_ATTACK)) {
            return;
        }

        ItemStack reservedProjectile = loadedProjectile(villager);
        if (reservedProjectile == null
                || !HiredRangedAmmo.clearConsumedCrossbowProjectileMarker(reservedProjectile)) {
            return;
        }

        readState(villager).remove("LoadedProjectile");
        reservedProjectile.remove(DataComponents.INTANGIBLE_PROJECTILE);
        if (villager instanceof Villager regular) {
            ItemStack remainder = reservedProjectile.copy();
            if (!DuelService.isParticipant(regular)
                    && HiredJobInventory.isJobInventoryAvailable(regular)) {
                remainder = HiredJobInventory.getJobInventory(regular).insertSupply(remainder);
            }
            remainder = VillagerInventoryAccess.addItem(regular, remainder);
            if (!remainder.isEmpty()) {
                villager.spawnAtLocation(remainder);
            }
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

    public static void captureUse(AbstractVillager villager) {
        var item = villager.getUseItem();
        if (villager.isUsingItem() && (VillagerRetaliationVillagerWeapons.isBowWeapon(item)
                || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(item))) {
            var use = new CompoundTag();
            use.putInt("Hand", villager.getUsedItemHand() == InteractionHand.MAIN_HAND ? 0 : 1);
            use.putInt("Remaining", villager.getUseItemRemainingTicks());
            use.put("Item", item.save(villager.registryAccess()));
            state(villager).put("Use", use);
        } else {
            readState(villager).remove("Use");
        }
    }

    public static void restoreUse(AbstractVillager villager) {
        var use = readState(villager).getCompound("Use");
        var item = ItemStack.parseOptional(villager.registryAccess(), use.getCompound("Item"));
        int handValue = use.getInt("Hand");
        var hand = handValue == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean valid = handValue >= 0 && handValue <= 1 && use.contains("Remaining", net.minecraft.nbt.Tag.TAG_INT)
                && use.getInt("Remaining") > 0 && ItemStack.isSameItem(item, villager.getItemInHand(hand))
                && (VillagerRetaliationVillagerWeapons.isBowWeapon(item) || VillagerRetaliationVillagerWeapons.isCrossbowWeapon(item));
        var current = villager.getUseItem();
        if (!valid && !VillagerRetaliationVillagerWeapons.isBowWeapon(current)
                && !VillagerRetaliationVillagerWeapons.isCrossbowWeapon(current)) return;
        var access = (com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor) villager;
        access.villagerretaliation$setUseItem(valid ? item : ItemStack.EMPTY);
        access.villagerretaliation$setUseItemRemaining(valid ? use.getInt("Remaining") : 0);
        access.villagerretaliation$setLivingEntityFlag(1, valid);
        if (valid) access.villagerretaliation$setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
    }

    private static CompoundTag readState(AbstractVillager villager) {
        return villager.getPersistentData().getCompound(STATE_TAG);
    }

    private static CompoundTag state(AbstractVillager villager) {
        var persistent = villager.getPersistentData();
        if (!persistent.contains(STATE_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) persistent.put(STATE_TAG, new CompoundTag());
        return persistent.getCompound(STATE_TAG);
    }

    private static CrossbowState crossbowState(AbstractVillager villager) {
        return switch (readState(villager).getString("CrossbowState")) {
            case "UNCHARGED" -> CrossbowState.UNCHARGED;
            case "CHARGING" -> CrossbowState.CHARGING;
            case "CHARGED" -> CrossbowState.CHARGED;
            case "READY_TO_ATTACK" -> CrossbowState.READY_TO_ATTACK;
            default -> null;
        };
    }

    private static void crossbowState(AbstractVillager villager, CrossbowState phase) {
        state(villager).putString("CrossbowState", phase.name());
    }

    private static ItemStack loadedProjectile(AbstractVillager villager) {
        return ItemStack.parseOptional(villager.registryAccess(), readState(villager).getCompound("LoadedProjectile"));
    }

    private static void loadedProjectile(AbstractVillager villager, ItemStack projectile) {
        if (projectile.isEmpty()) readState(villager).remove("LoadedProjectile");
        else state(villager).put("LoadedProjectile", projectile.save(villager.registryAccess()));
    }

    private enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}
