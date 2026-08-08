package com.jvn.villagerretaliation.social;

import com.jvn.villagerretaliation.combat.VillagerRetaliationHandler;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.util.VillagerRetaliationVillagerCombatUtil;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

/** Central, server-side policy for villager breeding participation. */
public final class VillagerBreedingPolicy {
    private static final List<ParentEvaluator> EVALUATORS = List.of(
            VillagerBreedingPolicy::evaluateLifeState,
            VillagerBreedingPolicy::evaluateHireState,
            VillagerBreedingPolicy::evaluatePartyState,
            VillagerBreedingPolicy::evaluateCommandState,
            VillagerBreedingPolicy::evaluateDownedState,
            VillagerBreedingPolicy::evaluateWorkState,
            VillagerBreedingPolicy::evaluateCombatState,
            VillagerBreedingPolicy::evaluateSceneState,
            VillagerBreedingPolicy::evaluateVanillaBreedingSetting);

    private VillagerBreedingPolicy() {
    }

    public static BreedingDecision evaluateParent(ServerLevel level, Villager villager) {
        if (level == null || villager == null || villager.level() != level) {
            return BreedingDecision.block(BreedingBlockReason.DEAD);
        }
        for (ParentEvaluator evaluator : EVALUATORS) {
            BreedingBlockReason reason = evaluator.evaluate(level, villager);
            if (reason != BreedingBlockReason.NONE) {
                return BreedingDecision.block(reason);
            }
        }
        return BreedingDecision.allow();
    }

    public static BreedingDecision evaluatePair(ServerLevel level, Villager first, Villager second) {
        BreedingDecision firstDecision = evaluateParent(level, first);
        if (!firstDecision.allowed()) return firstDecision;
        BreedingDecision secondDecision = evaluateParent(level, second);
        if (!secondDecision.allowed()) return secondDecision;

        if (VillagerRetaliationConfig.ENABLE_VILLAGER_SOCIAL_GRAPH.get()
                && VillagerRetaliationConfig.ENABLE_FAMILY_BREEDING_RULES.get()) {
            VillagerSocialGraphSavedData.BreedingValidation family =
                    VillagerSocialGraphSavedData.get(level).validateBreedingPair(level, first, second);
            if (!family.allowed()) {
                return BreedingDecision.block(BreedingBlockReason.INVALID_FAMILY_PAIR);
            }
        }
        return BreedingDecision.allow();
    }

    public static boolean canBreed(ServerLevel level, Villager villager) {
        return evaluateParent(level, villager).allowed();
    }

    /** Idempotently clears only memories owned by a current villager courtship. */
    public static void cancelActiveAttempt(ServerLevel level, Villager villager) {
        if (level == null || villager == null || villager.level() != level) return;
        Villager partner = breedingPartner(villager);
        if (partner == null) return;

        clearAttemptWith(villager, partner);
        if (partner.level() == level && breedingPartner(partner) == villager) {
            clearAttemptWith(partner, villager);
        }
    }

    private static Villager breedingPartner(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.BREED_TARGET)
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .orElse(null);
    }

    private static void clearAttemptWith(Villager villager, Villager partner) {
        Brain<Villager> brain = villager.getBrain();
        boolean ownedWalkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(WalkTarget::getTarget)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(partner::equals)
                .isPresent();
        boolean ownedLookTarget = brain.getMemory(MemoryModuleType.LOOK_TARGET)
                .filter(EntityTracker.class::isInstance)
                .map(EntityTracker.class::cast)
                .map(EntityTracker::getEntity)
                .filter(partner::equals)
                .isPresent();

        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        if (ownedLookTarget) brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        if (ownedWalkTarget) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            villager.getNavigation().stop();
        }
    }

    private static BreedingBlockReason evaluateLifeState(ServerLevel level, Villager villager) {
        if (!villager.isAlive() || villager.isRemoved()) return BreedingBlockReason.DEAD;
        return villager.isBaby() ? BreedingBlockReason.BABY : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateHireState(ServerLevel level, Villager villager) {
        return VillagerBehaviorSuppressionPolicy.state(villager)
                == VillagerBehaviorSuppressionPolicy.ControlState.HIRED
                ? BreedingBlockReason.HIRED : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluatePartyState(ServerLevel level, Villager villager) {
        return VillagerBehaviorSuppressionPolicy.state(villager)
                == VillagerBehaviorSuppressionPolicy.ControlState.PARTIED
                ? BreedingBlockReason.PARTY_MEMBER : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateCommandState(ServerLevel level, Villager villager) {
        if (VillagerRecruitmentService.isActivelyFollowingAnyPlayer(villager)) {
            return BreedingBlockReason.FOLLOWING_PLAYER;
        }
        return VillagerRecruitmentService.isOrderedToStay(villager)
                ? BreedingBlockReason.ORDERED_TO_STAY : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateDownedState(ServerLevel level, Villager villager) {
        return VillagerBehaviorSuppressionPolicy.state(villager)
                == VillagerBehaviorSuppressionPolicy.ControlState.DOWNED
                ? BreedingBlockReason.DOWNED : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateWorkState(ServerLevel level, Villager villager) {
        return HiredVillagerWorkService.isActivelyWorking(villager)
                ? BreedingBlockReason.WORKING : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateCombatState(ServerLevel level, Villager villager) {
        if (VillagerRetaliationHandler.hasActiveRetaliationTarget(villager)
                || VillagerRetaliationVillagerCombatUtil.isInCombat(villager)) {
            return BreedingBlockReason.IN_COMBAT;
        }
        return VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, villager.getBrain())
                ? BreedingBlockReason.PANICKING : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateSceneState(ServerLevel level, Villager villager) {
        return SceneSavedData.get(level).hasActiveActor(villager.getUUID())
                ? BreedingBlockReason.SCENE_CONTROLLED : BreedingBlockReason.NONE;
    }

    private static BreedingBlockReason evaluateVanillaBreedingSetting(ServerLevel level, Villager villager) {
        return VillagerRetaliationConfig.ENABLE_VANILLA_VILLAGER_BREEDING.get()
                ? BreedingBlockReason.NONE : BreedingBlockReason.BREEDING_DISABLED;
    }

    @FunctionalInterface
    private interface ParentEvaluator {
        BreedingBlockReason evaluate(ServerLevel level, Villager villager);
    }
}
