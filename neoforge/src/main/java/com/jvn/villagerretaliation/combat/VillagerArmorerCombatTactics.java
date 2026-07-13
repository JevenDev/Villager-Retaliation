package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerEquipment;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

final class VillagerArmorerCombatTactics {
    private static final long SHIELD_AXE_BREAK_TICKS = 100L;
    private static final int COUNTER_SWINGS_AFTER_BLOCK = 1;
    private static final int COUNTER_ATTACK_DELAY_MIN_TICKS = 10;
    private static final int COUNTER_ATTACK_DELAY_MAX_TICKS = 30;
    private static final double BLOCKING_SPEED_FACTOR = 0.45D;
    private static final double SHIELD_TRIGGER_RANGE = 7.0D;
    private static final double SHIELD_TRIGGER_RANGE_SQR = SHIELD_TRIGGER_RANGE * SHIELD_TRIGGER_RANGE;
    private static final String PERSISTENT_SHIELD_ROLLED_TAG = "VillagerRetaliationArmorerShieldRolled";

    private static final Map<UUID, Long> SHIELD_DISABLED_UNTIL_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> PENDING_COUNTER_SWINGS = new HashMap<>();
    private static final Map<UUID, Long> COUNTER_ATTACK_READY_TICKS = new HashMap<>();

    private VillagerArmorerCombatTactics() {
    }

    static boolean tryHandleShieldBlock(
            Villager villager,
            LivingIncomingDamageEvent event,
            AngerCallback angerCallback,
            NearbyAngerCallback nearbyAngerCallback) {
        if (!canUseShieldTactics(villager) || !isActivelyBlocking(villager)) {
            return false;
        }

        DamageSource source = event.getSource();
        if (!villager.isDamageSourceBlocked(source)) {
            return false;
        }

        float incomingDamage = event.getAmount();
        event.setCanceled(true);
        event.setAmount(0.0F);
        villager.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + villager.getRandom().nextFloat() * 0.4F);

        boolean shieldBroke = applyShieldDurabilityDamage(villager, incomingDamage);
        boolean disabledByAxe = false;
        Optional<LivingEntity> attacker = VillagerRetaliationVillagerCombatUtil.resolveAttacker(villager, source);
        if (attacker.isPresent()) {
            LivingEntity resolvedAttacker = attacker.get();
            angerCallback.anger(villager, resolvedAttacker);
            if (!VillagerRetaliationConfig.ATTACK_AGGROS_ONLY_HIT_VILLAGER.get()) {
                nearbyAngerCallback.angerNearby(
                        villager,
                        resolvedAttacker,
                        VillagerRetaliationConfig.VILLAGER_KILL_AGGRO_RADIUS.get());
            }

            if (!shieldBroke && isAxeAttacker(resolvedAttacker)) {
                disabledByAxe = true;
                breakShieldGuard(villager);
            }
        }
        if (!shieldBroke && !disabledByAxe) {
            UUID villagerId = villager.getUUID();
            PENDING_COUNTER_SWINGS.put(villagerId, COUNTER_SWINGS_AFTER_BLOCK);
            COUNTER_ATTACK_READY_TICKS.put(villagerId, villager.level().getGameTime() + nextCounterAttackDelayTicks(villager));
            ensureBlocking(villager);
        }
        return true;
    }

    static boolean handleCombatTactics(
            Villager villager,
            LivingEntity target,
            double distanceSqr,
            long gameTime,
            boolean meleeAttackReady) {
        if (!canUseShieldTactics(villager)
                || !isHardMode(villager)
                || VillagerRetaliationRetaliationUtil.isUsingRangedCombatMode(villager)) {
            clearTacticState(villager, true);
            return true;
        }

        if (!hasShield(villager)) {
            clearTacticState(villager, true);
            return true;
        }

        boolean inMeleeRange = VillagerRetaliationRetaliationUtil.canUseMeleeCombatMode(villager)
                && VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target);
        boolean inShieldTriggerRange = distanceSqr <= SHIELD_TRIGGER_RANGE_SQR;

        UUID villagerId = villager.getUUID();
        long shieldDisabledUntil = SHIELD_DISABLED_UNTIL_TICKS.getOrDefault(villagerId, 0L);
        if (gameTime < shieldDisabledUntil) {
            stopBlocking(villager);
            return true;
        }
        if (shieldDisabledUntil != 0L) {
            SHIELD_DISABLED_UNTIL_TICKS.remove(villagerId);
        }

        int pendingCounterSwings = PENDING_COUNTER_SWINGS.getOrDefault(villagerId, 0);
        if (pendingCounterSwings > 0) {
            long counterAttackReadyTick = COUNTER_ATTACK_READY_TICKS.getOrDefault(villagerId, gameTime);
            if (gameTime < counterAttackReadyTick || !meleeAttackReady || !inMeleeRange) {
                if (!inShieldTriggerRange) {
                    stopBlocking(villager);
                    return true;
                }
                ensureBlocking(villager);
                return false;
            }
            stopBlocking(villager);
            return true;
        }

        if (inMeleeRange && meleeAttackReady) {
            stopBlocking(villager);
            return true;
        }

        if (!inShieldTriggerRange) {
            stopBlocking(villager);
            return true;
        }

        ensureBlocking(villager);
        return false;
    }

    static void onMeleeAttackCommitted(Villager villager) {
        if (!VillagerCombatRoles.isArmorer(villager) || !hasShield(villager)) {
            return;
        }

        UUID villagerId = villager.getUUID();
        int pendingCounterSwings = PENDING_COUNTER_SWINGS.getOrDefault(villagerId, 0);
        if (pendingCounterSwings > 0) {
            PENDING_COUNTER_SWINGS.put(villagerId, pendingCounterSwings - 1);
            COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        }
        stopBlocking(villager);
    }

    static void ensureSpawnShieldRoll(Villager villager) {
        if (villager.isBaby()
                || !canUseShieldTactics(villager)
                || !isHardMode(villager)) {
            return;
        }

        rollSpawnShield(villager);
    }

    static void resetStateIfActive(Villager villager) {
        if (VillagerCombatRoles.isArmorer(villager) || isActivelyBlocking(villager) || hasTacticState(villager)) {
            resetState(villager);
        }
    }

    static void resetState(Villager villager) {
        stopBlocking(villager);
        clearTacticState(villager, false);
    }

    static void clearRuntimeState() {
        SHIELD_DISABLED_UNTIL_TICKS.clear();
        PENDING_COUNTER_SWINGS.clear();
        COUNTER_ATTACK_READY_TICKS.clear();
    }

    static double movementSpeedFactor(Villager villager) {
        return isActivelyBlocking(villager) ? BLOCKING_SPEED_FACTOR : 1.0D;
    }

    private static boolean applyShieldDurabilityDamage(Villager villager, float blockedDamage) {
        ItemStack shield = villager.getOffhandItem();
        if (!shield.is(Items.SHIELD)) {
            return false;
        }

        int durabilityLoss = blockedDamage >= 3.0F ? 1 + Mth.floor(blockedDamage) : 1;
        shield.hurtAndBreak(durabilityLoss, villager, EquipmentSlot.OFFHAND);
        return !villager.getOffhandItem().is(Items.SHIELD);
    }

    private static boolean isAxeAttacker(LivingEntity attacker) {
        return attacker.getMainHandItem().getItem() instanceof AxeItem
                || attacker.getOffhandItem().getItem() instanceof AxeItem;
    }

    private static void breakShieldGuard(Villager villager) {
        long gameTime = villager.level().getGameTime();
        UUID villagerId = villager.getUUID();
        SHIELD_DISABLED_UNTIL_TICKS.put(villagerId, gameTime + SHIELD_AXE_BREAK_TICKS);
        PENDING_COUNTER_SWINGS.remove(villagerId);
        COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        stopBlocking(villager);
        villager.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + villager.getRandom().nextFloat() * 0.4F);
    }

    private static void ensureBlocking(Villager villager) {
        if (!isActivelyBlocking(villager) && hasShield(villager)) {
            villager.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private static void stopBlocking(Villager villager) {
        if (villager.isUsingItem() && villager.getUsedItemHand() == InteractionHand.OFF_HAND) {
            villager.stopUsingItem();
        }
    }

    private static void clearTacticState(Villager villager, boolean stopBlocking) {
        UUID villagerId = villager.getUUID();
        SHIELD_DISABLED_UNTIL_TICKS.remove(villagerId);
        PENDING_COUNTER_SWINGS.remove(villagerId);
        COUNTER_ATTACK_READY_TICKS.remove(villagerId);
        if (stopBlocking) {
            stopBlocking(villager);
        }
    }

    private static boolean isActivelyBlocking(Villager villager) {
        return villager.isUsingItem()
                && villager.getUsedItemHand() == InteractionHand.OFF_HAND
                && hasShield(villager);
    }

    private static int nextCounterAttackDelayTicks(Villager villager) {
        return COUNTER_ATTACK_DELAY_MIN_TICKS
                + villager.getRandom().nextInt(COUNTER_ATTACK_DELAY_MAX_TICKS - COUNTER_ATTACK_DELAY_MIN_TICKS + 1);
    }

    private static void rollSpawnShield(Villager villager) {
        var persistentData = villager.getPersistentData();
        if (persistentData.getBoolean(PERSISTENT_SHIELD_ROLLED_TAG)) {
            return;
        }
        persistentData.putBoolean(PERSISTENT_SHIELD_ROLLED_TAG, true);

        if (villager.getOffhandItem().isEmpty()
                && villager.getRandom().nextDouble() < VillagerRetaliationConfig.ARMORER_SHIELD_CHANCE_HARD.get()) {
            VillagerRetaliationVillagerEquipment.setRoleEquipment(villager, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
    }

    private static boolean canUseShieldTactics(Villager villager) {
        return VillagerCombatRoles.isArmorer(villager)
                && VillagerRetaliationConfig.ARMORERS_FIGHT_BACK.get();
    }

    private static boolean hasShield(Villager villager) {
        return villager.getOffhandItem().is(Items.SHIELD);
    }

    private static boolean hasTacticState(Villager villager) {
        UUID villagerId = villager.getUUID();
        return SHIELD_DISABLED_UNTIL_TICKS.containsKey(villagerId)
                || PENDING_COUNTER_SWINGS.containsKey(villagerId)
                || COUNTER_ATTACK_READY_TICKS.containsKey(villagerId);
    }

    private static boolean isHardMode(Villager villager) {
        return villager.level().getDifficulty() == Difficulty.HARD;
    }

    @FunctionalInterface
    interface AngerCallback {
        void anger(Villager villager, LivingEntity attacker);
    }

    @FunctionalInterface
    interface NearbyAngerCallback {
        void angerNearby(Villager source, LivingEntity attacker, double radius);
    }
}
