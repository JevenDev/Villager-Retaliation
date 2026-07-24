package com.jvn.villagerretaliation.combat;

import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Shared combat decisions for retaliation, party combat, and structured duels. */
public final class VillagerCombatBehavior {
    private VillagerCombatBehavior() {
    }

    public static boolean prepareAndIsRanged(Villager villager, LivingEntity target, double distanceSqr) {
        VillagerCombatStateMachine.prepare(villager, target, distanceSqr);
        return VillagerCombatStateMachine.isUsingRangedMode(villager);
    }

    public static boolean handleShieldTactics(
            Villager villager,
            LivingEntity target,
            double distanceSqr,
            long gameTime,
            boolean meleeAttackReady) {
        return VillagerArmorerCombatTactics.handleCombatTactics(
                villager, target, distanceSqr, gameTime, meleeAttackReady);
    }

    public static boolean tryBlockStructuredCombatDamage(
            Villager villager,
            LivingIncomingDamageEvent event) {
        return VillagerArmorerCombatTactics.tryHandleShieldBlock(
                villager, event, (ignoredVillager, ignoredAttacker) -> {
                }, (ignoredVillager, ignoredAttacker, ignoredRadius) -> {
                });
    }

    public static double movementSpeedFactor(Villager villager) {
        return VillagerArmorerCombatTactics.movementSpeedFactor(villager);
    }

    public static boolean canRaiseShield(Villager villager) {
        return !VillagerArmorerCombatTactics.isShieldDisabled(
                villager, villager.level().getGameTime());
    }

    public static boolean canMeleeHit(Villager villager, LivingEntity target) {
        return VillagerRetaliationRetaliationUtil.canMeleeHit(villager, target);
    }

    public static boolean tryBreakTargetShield(Villager villager, LivingEntity target) {
        return VillagerCombatStateMachine.tryBreakTargetShield(villager, target);
    }

    public static void onMeleeAttackCommitted(Villager villager, LivingEntity target) {
        VillagerArmorerCombatTactics.onMeleeAttackCommitted(villager);
    }

    public static void reset(Villager villager) {
        VillagerInventoryAccess.returnBorrowedCombatWeapon(villager);
        VillagerCombatStateMachine.clearState(villager);
        VillagerArmorerCombatTactics.resetState(villager);
    }
}
