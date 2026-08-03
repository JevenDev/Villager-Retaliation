package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.debug.HiredStressGridService;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

public final class HiredVillagerFocusService {
    private static final String NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG = "NextProfessionSuppressionGameTime";
    private static final int PROFESSION_SUPPRESSION_INTERVAL_TICKS = 20;

    private HiredVillagerFocusService() {
    }

    public static void onVillagerTickPre(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }

        HiredVillagerContractService.expireHireContractIfNeeded(level, villager);
        suppressInteractionsWithBusyHiredVillagers(level, villager);
        if (shouldSkipHiredFocus(level, villager)) {
            return;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = activeWorkerRole(level, villager, state);
        if (role == null) {
            return;
        }
        suppressClaimedJobSiteBlockNavigation(level, villager, state, role);
        if (shouldSuppressSocialDistractionsForHiredJob(level, villager, state, role)) {
            suppressIdleAttentionBehavior(villager, HiredWorkerBrain.snapshot(state, level.getGameTime()));
        }
    }

    public static boolean shouldSuppressVanillaBrainTick(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return false;
        }
        if (shouldUseVanillaRest(level, villager)) {
            return false;
        }

        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = activeWorkerRole(level, villager, state);
        return role != null && shouldSuppressForActiveHiredJob(level, villager, state, role);
    }

    public static boolean shouldAllowCompatibleVanillaFarmerBrain(ServerLevel level, Villager villager) {
        if (level == null || villager == null || villager.isBaby() || !villager.isAlive()) {
            return false;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        if (!state.getBoolean("Enabled")
                || HiredVillagerContractService.isAwaitingAutoPayment(level, villager)) {
            return false;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRoleWithoutMaintenance(level, villager);
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(Activity.WORK) && scheduledActivity(level, brain) != Activity.WORK) {
            return false;
        }
        return role != null && shouldLetFarmerBrainHandleFields(
                level,
                villager,
                HiredWorkerBrain.snapshot(state, level.getGameTime()),
                role);
    }

    public static boolean shouldUseVanillaRest(ServerLevel level, Villager villager) {
        if (HiredStressGridService.isStressWorker(villager)) {
            return false;
        }
        Brain<Villager> brain = villager.getBrain();
        return isVanillaRestActive(villager)
                || scheduledActivity(level, brain) == Activity.REST;
    }

    public static boolean isVanillaRestActive(Villager villager) {
        return villager.isSleeping() || villager.getBrain().isActive(Activity.REST);
    }

    public static boolean shouldSkipHiredFocus(ServerLevel level, Villager villager) {
        boolean stressWorker = HiredStressGridService.isStressWorker(villager);
        return villager.isBaby()
                || !villager.isAlive()
                || (!stressWorker && villager.isSleeping())
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || (!stressWorker && villager.getTarget() != null)
                || villager.getLastHurtByMob() != null
                || !HiredVillagerContractService.hasActiveOrPendingContract(villager);
    }

    public static void suppressNonWorkAi(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredVillagerRole role = activeWorkerRole(level, villager, context.state());
        if (role == null) {
            return;
        }
        suppressClaimedJobSiteBlockNavigation(level, villager, context.state(), role);
        if (shouldSuppressSocialDistractionsForHiredJob(level, villager, context.state(), role)) {
            suppressIdleAttentionBehavior(villager, HiredWorkerBrain.snapshot(context.state(), level.getGameTime()));
        }
        if (!shouldSuppressForActiveHiredJob(level, villager, context.state(), role)) {
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        maintainWorkLookTarget(villager, worker);
        suppressProfessionJobSiteBehavior(level, villager, context, role);
    }

    public static boolean shouldSuppressClaimedJobSiteBlockUse(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return false;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = activeWorkerRole(level, villager, state);
        return role != null && shouldSuppressClaimedJobSiteBlockUse(level, villager, state, role);
    }

    public static void suppressClaimedJobSiteBlockNavigation(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = activeWorkerRole(level, villager, state);
        if (role != null) {
            suppressClaimedJobSiteBlockNavigation(level, villager, state, role);
        }
    }

    public static boolean isClaimedJobSitePathFloor(Villager villager, BlockPos floorPos) {
        if (floorPos == null
                || !(villager.level() instanceof ServerLevel level)
                || shouldSkipHiredFocus(level, villager)) {
            return false;
        }
        BlockPos jobSite = HiredVillagerWorkService.claimedJobSitePos(level, villager);
        return floorPos.equals(jobSite) && shouldSuppressClaimedJobSiteBlockUse(level, villager);
    }

    private static boolean shouldSuppressClaimedJobSiteBlockUse(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredVillagerRole role) {
        return role != null && HiredVillagerWorkService.hasClaimedJobSiteInLevel(level, villager);
    }

    private static void suppressClaimedJobSiteBlockNavigation(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredVillagerRole role) {
        if (!shouldSuppressClaimedJobSiteBlockUse(level, villager, state, role)) {
            return;
        }
        BlockPos jobSite = HiredVillagerWorkService.claimedJobSitePos(level, villager);
        if (jobSite == null) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        boolean clearedPathing = false;
        if (brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> isJobSiteWalkTarget(walkTarget, jobSite))
                .orElse(false)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            clearedPathing = true;
        }
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET)
                .map(lookTarget -> jobSite.equals(lookTarget.currentBlockPosition()))
                .orElse(false)) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        if (jobSite.equals(villager.getNavigation().getTargetPos())) {
            villager.getNavigation().stop();
            clearedPathing = true;
        }
        if (clearedPathing) {
            brain.eraseMemory(MemoryModuleType.PATH);
        }
    }

    private static boolean isJobSiteWalkTarget(WalkTarget walkTarget, BlockPos jobSite) {
        return walkTarget != null
                && walkTarget.getTarget() != null
                && jobSite.equals(walkTarget.getTarget().currentBlockPosition());
    }

    private static boolean shouldSuppressForActiveHiredJob(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredVillagerRole role) {
        if (role == null) {
            return false;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(state, level.getGameTime());
        return shouldSuppressForActiveHiredJob(level, villager, state, role, worker);
    }

    private static boolean shouldSuppressForActiveHiredJob(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredVillagerRole role,
            HiredWorkerBrain.Snapshot worker) {
        if (hasAssignedRoute(role, state)) {
            return true;
        }
        if (shouldLetFarmerBrainHandleFields(level, villager, worker, role)) {
            return false;
        }
        return switch (worker.taskState()) {
            case MOVING_TO_TARGET, VALIDATING_TARGET, WORKING, COLLECTING_OUTPUT, FINDING_CHAIN_TARGET ->
                    worker.targetPos() != null;
            case MOVING_TO_STORAGE, DEPOSITING, WAITING_FOR_MATERIALS ->
                    worker.storageTargetPos() != null;
            case RETURNING_TO_WORK_AREA, SELECTING_TARGET, IDLE, AWAITING_INSTRUCTION,
                    FAILED_COOLDOWN, PAUSED_FULL_INVENTORY, PAUSED_STORAGE_FULL,
                    PAUSED_OUTPUT_BACKPRESSURE, PAUSED_NO_STORAGE, NO_WORK_AREA,
                    PAUSED_MISSING_TOOL -> true;
            default -> false;
        };
    }

    private static boolean shouldSuppressSocialDistractionsForHiredJob(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            HiredVillagerRole role) {
        if (role == null) {
            return false;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(state, level.getGameTime());
        return hasAssignedRoute(role, state)
                || shouldLetFarmerBrainHandleFields(level, villager, worker, role)
                || shouldSuppressForActiveHiredJob(level, villager, state, role, worker);
    }

    private static boolean hasAssignedRoute(HiredVillagerRole role, CompoundTag state) {
        return HiredVillagerRoleSettings.supportsRoutes(role)
                && HiredRoute.fromState(state).usableForNavigation();
    }

    private static boolean shouldLetFarmerBrainHandleFields(
            ServerLevel level,
            Villager villager,
            HiredWorkerBrain.Snapshot worker,
            HiredVillagerRole role) {
        if (role != HiredVillagerRole.FARMING
                || villager.getVillagerData().getProfession() != VillagerProfession.FARMER
                || !FarmerHoeRequirement.hasHoe(villager)
                || !HiredVillagerWorkService.hasClaimedJobSiteInLevel(level, villager)) {
            return false;
        }
        return switch (worker.taskState()) {
            case IDLE, SELECTING_TARGET, VALIDATING_TARGET, MOVING_TO_TARGET,
                    WORKING, COLLECTING_OUTPUT, FINDING_CHAIN_TARGET,
                    AWAITING_INSTRUCTION -> true;
            default -> false;
        };
    }

    private static HiredVillagerRole activeWorkerRole(ServerLevel level, Villager villager, CompoundTag state) {
        if (!state.getBoolean("Enabled")
                || shouldUseVanillaRest(level, villager)
                || VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())
                || HiredVillagerContractService.isAwaitingAutoPayment(level, villager)
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            return null;
        }
        HireContractSnapshot contract = HiredVillagerContractService.snapshot(level, villager);
        UUID hirerId = contract.hirer().orElse(null);
        if (!contract.hired()
                || contract.awaitingAutoPayment()
                || hirerId == null
                || level.getServer().getPlayerList().getPlayer(hirerId) == null) {
            return null;
        }
        HiredVillagerRole role = contract.role();
        return HiredRoleWorkerRegistry.get(role) != null ? role : null;
    }

    private static Activity scheduledActivity(ServerLevel level, Brain<Villager> brain) {
        return brain.getSchedule().getActivityAt((int) (level.getDayTime() % 24000L));
    }

    private static void suppressIdleAttentionBehavior(Villager villager, HiredWorkerBrain.Snapshot worker) {
        Brain<Villager> brain = villager.getBrain();
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(brain)) {
            return;
        }

        BlockPos workLookTarget = currentWorkLookTarget(worker);
        if (workLookTarget == null) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        } else if (isActivelyWorkingAtTarget(villager, worker, workLookTarget)) {
            maintainWorkLookTarget(villager, worker);
        }
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
    }

    private static void suppressInteractionsWithBusyHiredVillagers(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        LivingEntity target = brain.getMemory(MemoryModuleType.INTERACTION_TARGET).orElse(null);
        if (!(target instanceof Villager hiredTarget)
                || hiredTarget == villager
                || !isBusyHiredSocialTarget(level, hiredTarget)) {
            return;
        }
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        BlockPos targetPos = hiredTarget.blockPosition();
        if (brain.getMemory(MemoryModuleType.LOOK_TARGET)
                .map(lookTarget -> targetPos.equals(lookTarget.currentBlockPosition()))
                .orElse(false)) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        if (brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> isSocialWalkTarget(walkTarget, targetPos))
                .orElse(false)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (navigationTarget != null && targetPos.distSqr(navigationTarget) <= 4.0D) {
            villager.getNavigation().stop();
        }
    }

    private static boolean isBusyHiredSocialTarget(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return false;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkStateStore.initializeDefaults(state, villager);
        HiredVillagerRole role = activeWorkerRole(level, villager, state);
        return role != null && shouldSuppressSocialDistractionsForHiredJob(level, villager, state, role);
    }

    private static boolean isSocialWalkTarget(WalkTarget walkTarget, BlockPos targetPos) {
        return walkTarget != null
                && walkTarget.getTarget() != null
                && targetPos.distSqr(walkTarget.getTarget().currentBlockPosition()) <= 4.0D;
    }

    public static BlockPos activeWorkLookTarget(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || !HiredWorkStateStore.isPerformingWork(villager)
                || shouldSkipHiredFocus(level, villager)) {
            return null;
        }
        CompoundTag state = HiredWorkStateStore.state(villager);
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(state, level.getGameTime());
        BlockPos target = currentWorkLookTarget(worker);
        return target != null && isActivelyWorkingAtTarget(villager, worker, target) ? target : null;
    }

    private static void maintainWorkLookTarget(Villager villager, HiredWorkerBrain.Snapshot worker) {
        BlockPos target = currentWorkLookTarget(worker);
        if (target == null) {
            return;
        }
        Brain<Villager> brain = villager.getBrain();
        boolean alreadyTracksTarget = brain.getMemory(MemoryModuleType.LOOK_TARGET)
                .map(lookTarget -> lookTarget instanceof BlockPosTracker
                        && target.equals(lookTarget.currentBlockPosition()))
                .orElse(false);
        if (!alreadyTracksTarget) {
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        }
        faceActiveWorkTarget(villager, worker, target);
    }

    private static void faceActiveWorkTarget(Villager villager, HiredWorkerBrain.Snapshot worker, BlockPos target) {
        if (!isActivelyWorkingAtTarget(villager, worker, target)) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(target);
        villager.getLookControl().setLookAt(center.x, center.y, center.z, 60.0F, 60.0F);
    }

    private static boolean isActivelyWorkingAtTarget(Villager villager, HiredWorkerBrain.Snapshot worker, BlockPos target) {
        return worker.taskState() == HiredWorkerTaskState.WORKING && !target.equals(villager.blockPosition());
    }

    private static BlockPos currentWorkLookTarget(HiredWorkerBrain.Snapshot worker) {
        if (worker == null) {
            return null;
        }
        if (worker.taskState().keepsStorageTarget() && worker.storageTargetPos() != null) {
            return worker.storageTargetPos();
        }
        if (worker.taskState().keepsBlockTarget() && worker.targetPos() != null) {
            return worker.targetPos();
        }
        return null;
    }

    private static void suppressProfessionJobSiteBehavior(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredVillagerRole role) {
        Brain<Villager> brain = villager.getBrain();
        BlockPos jobSite = brain.getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .orElse(null);
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        boolean storageNavigation = worker.storageTargetPos() != null
                && (worker.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || worker.taskState() == HiredWorkerTaskState.DEPOSITING
                || worker.taskState() == HiredWorkerTaskState.WAITING_FOR_MATERIALS
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL);
        boolean workAreaReturnNavigation = worker.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA;
        boolean blockTargetNavigation = worker.taskState().keepsBlockTarget()
                && worker.targetPos() != null
                && worker.taskState() != HiredWorkerTaskState.FAILED_COOLDOWN;
        boolean hiredNavigation = storageNavigation || workAreaReturnNavigation || blockTargetNavigation;
        boolean stopNavigation = navigationTarget != null
                && !hiredNavigation
                && (!HiredVillagerWorkService.isInsideEffectiveWorkArea(level, villager, role, context, navigationTarget)
                || navigationTarget.equals(jobSite));
        if (stopNavigation) {
            VillagerRetaliationVillagerBrainUtil.stopNavigationAndClearPathing(villager);
        }

        long gameTime = level.getGameTime();
        if (!stopNavigation
                && !brain.isActive(Activity.WORK)
                && gameTime < context.state().getLong(NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG)) {
            return;
        }
        context.state().putLong(
                NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG,
                gameTime + PROFESSION_SUPPRESSION_INTERVAL_TICKS);

        if (!hiredNavigation) {
            VillagerRetaliationVillagerBrainUtil.clearPathingMemories(villager);
        }
        brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        brain.eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        if (brain.isActive(Activity.WORK)) {
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }
}
