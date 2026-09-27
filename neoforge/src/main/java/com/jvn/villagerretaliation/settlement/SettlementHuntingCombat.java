package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.HuntingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mixin.LivingEntityCombatAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;

final class SettlementHuntingCombat {
    private SettlementHuntingCombat() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, Villager owner,
                     LivingEntity target, int elapsed) {
        long now = level.getGameTime();
        var state = town.huntingTargets().get(target.getUUID());
        if (target instanceof Player || state == null
                || level.getEntity(target.getUUID()) == target && level.isPositionEntityTicking(target.blockPosition())) {
            worker.status("waiting_for_native_hunting_target");
            return;
        }
        if (elapsed <= 0 || now <= worker.huntingCombatAt()) return;
        long at = Math.max(Math.max(worker.huntingCombatAt(), Math.max(worker.huntingAttackAt(), VillagerRetaliationHandler.nextMeleeAttackTick(owner))),
                Math.max(state.observedAt(), Math.max(0L, now - Math.clamp(elapsed, 0, 200))));
        long nextAttack = worker.huntingAttackAt();
        try {
            var terrain = new SettlementExperienceTerrain(level, town);
            while (at <= now && target.isAlive() && owner.isAlive()) {
                if (!HuntingWorker.isEligibleTarget(owner, target, HiredHuntingTargets.fromState(worker.settings()))) break;
                worker.huntingTimers(owner, at);
                var readiness = VillagerRetaliationHandler.prepareObservedMelee(owner, target, terrain, at);
                if (readiness != VillagerRetaliationHandler.ObservedMelee.READY) {
                    worker.status(switch (readiness) {
                        case RETREAT -> "avoiding_hunting_target";
                        case RANGED -> "waiting_for_ranged_hunting";
                        case BLOCKING -> "blocking_hunting_target";
                        case OUT_OF_REACH -> "tracking_hunting_target";
                        default -> "hunting_combat_unavailable";
                    });
                    break;
                }
                state.restoreTimers(target, at);
                ((LivingEntityCombatAccessor) owner).villagerretaliation$detectEquipmentUpdates();
                ((LivingEntityCombatAccessor) target).villagerretaliation$detectEquipmentUpdates();
                var targetBefore = target.saveWithoutId(new CompoundTag());
                var ownerBefore = owner.saveWithoutId(new CompoundTag());
                var targetPosition = target.blockPosition().immutable();
                var ownerPosition = owner.blockPosition().immutable();
                try (var victimCapture = new SettlementDeathCapture(target);
                     var ownerCapture = new SettlementDeathCapture(owner)) {
                    VillagerRetaliationHandler.performMeleeStrike(owner, target, at);
                    HiredJobInventory.synchronizeEquipmentDurability(owner, EquipmentSlot.MAINHAND);
                    HiredJobInventory.synchronizeEquipmentDurability(owner, EquipmentSlot.OFFHAND);
                    nextAttack = Math.max(at + 1L, VillagerRetaliationHandler.nextMeleeAttackTick(owner));
                    boolean killed = outcome(level, town, worker, owner, target, targetPosition, victimCapture, at);
                    outcome(level, town, worker, owner, owner, ownerPosition, ownerCapture, at);
                    if (!killed) state.interacted(target, targetBefore, at);
                    var care = town.residents().get(worker.id());
                    if (care != null) care.combat(owner);
                    worker.huntingInjury(owner, ownerBefore, at);
                    worker.status(killed ? "killed_hunting_target" : "attacking_hunting_target");
                    if (killed) {
                        town.removeHuntingTarget(target.getUUID());
                        town.removeLivestock(target.getUUID());
                        worker.huntingTarget(null);
                        break;
                    }
                }
                at = nextAttack;
            }
        } catch (SettlementExperienceTerrain.Unobserved unavailable) {
            worker.status("hunting_visibility_unobserved");
        } finally {
            worker.huntingCombat(now, nextAttack);
            town.changed();
        }
    }

    private static boolean outcome(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
                                    Villager owner, LivingEntity victim, BlockPos nativePosition,
                                    SettlementDeathCapture capture, long now) {
        boolean killed = !victim.isAlive() && capture.accepted();
        for (var item : capture.drops()) {
            if (item.isRemoved() || item.getItem().isEmpty()) continue;
            var drop = SettlementItemDrop.create(item, worker.area(), now);
            drop.access(worker, owner.distanceToSqr(item) <= 2.25D ? 0 : -1);
            town.recordItemDrop(drop);
        }
        if (killed || !capture.experience().isEmpty()) {
            town.recordEntityDeath(SettlementEntityDeath.create(victim, nativePosition, worker, capture.experience(), now, killed));
        }
        if (killed) {
            if (victim.getUUID().equals(worker.id())) {
                town.removeWorker(worker.id());
                town.removeResident(worker.id());
                town.removeDuty(worker.id());
            }
            var birth = town.animalBirths().get(victim.getUUID());
            if (birth != null) birth.applyChild();
        }
        return killed;
    }
}
