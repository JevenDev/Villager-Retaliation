package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.party.PartyQuickCommandService;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Chooses which kind of AI intent currently owns a hired villager's attention. This class only
 * arbitrates; it deliberately does not mutate the villager's Brain or its memories.
 */
public final class VillagerAiArbitration {
    private VillagerAiArbitration() {
    }

    public static Priority currentPriority(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        if (isImmediateDanger(villager, brain)) {
            return Priority.IMMEDIATE_DANGER;
        }
        if (isCombatOrSupportAction(villager)) {
            return Priority.COMBAT_OR_SUPPORT_ACTION;
        }
        if (villager.isTrading() || VillagerConversationService.isConversing(villager)) {
            return Priority.TRADING_OR_CONVERSATION;
        }
        if (villager.isSleeping() || brain.isActive(Activity.REST)) {
            return Priority.SLEEP;
        }

        VillagerAssignmentSnapshot assignment = VillagerAssignmentStore.snapshot(villager);
        if (assignment.state() == VillagerAssignmentState.HIRED
                && (assignment.command() == VillagerAssignmentCommand.WORK
                || assignment.command() == VillagerAssignmentCommand.GUARD)) {
            return Priority.HIRED_ROLE_TASK;
        }
        if (VillagerAssignmentStore.commandOwner(villager).isPresent()) {
            return Priority.FOLLOW_OR_RETURN_MOVEMENT;
        }
        return Priority.VANILLA_SCHEDULE_OR_IDLE;
    }

    private static boolean isImmediateDanger(Villager villager, Brain<Villager> brain) {
        return VillagerRecoveryService.isForcingRecovery(villager)
                || villager.isOnFire()
                || brain.isActive(Activity.PANIC)
                || brain.isActive(Activity.HIDE)
                || brain.isActive(Activity.PRE_RAID)
                || brain.isActive(Activity.RAID)
                || villager.getTarget() == null && hasLiveUnansweredThreat(brain);
    }

    private static boolean hasLiveUnansweredThreat(Brain<Villager> brain) {
        LivingEntity attacker = brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).orElse(null);
        if (attacker != null && attacker.isAlive()) {
            return true;
        }
        LivingEntity hostile = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null);
        return hostile != null && hostile.isAlive();
    }

    private static boolean isCombatOrSupportAction(Villager villager) {
        LivingEntity target = villager.getTarget();
        return target != null && target.isAlive()
                || PartyQuickCommandService.overridesRecruitmentMovement(villager);
    }

    public enum Priority {
        IMMEDIATE_DANGER,
        COMBAT_OR_SUPPORT_ACTION,
        TRADING_OR_CONVERSATION,
        SLEEP,
        HIRED_ROLE_TASK,
        FOLLOW_OR_RETURN_MOVEMENT,
        VANILLA_SCHEDULE_OR_IDLE;

        public boolean yieldsCommandMovement() {
            return ordinal() < FOLLOW_OR_RETURN_MOVEMENT.ordinal();
        }
    }
}
