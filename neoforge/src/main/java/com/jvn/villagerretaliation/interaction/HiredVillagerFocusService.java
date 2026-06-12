package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public final class HiredVillagerFocusService {
    private static final String NEXT_PROFESSION_SUPPRESSION_GAME_TIME_TAG = "NextProfessionSuppressionGameTime";
    private static final int PROFESSION_SUPPRESSION_INTERVAL_TICKS = 20;

    private HiredVillagerFocusService() {
    }

    public static void onVillagerTickPre(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || shouldSkipHiredFocus(level, villager)) {
            return;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        if (shouldSuppressForActiveHiredJob(level, villager, state)) {
            suppressIdleAttentionBehavior(villager, HiredWorkerBrain.snapshot(state, level.getGameTime()));
        }
    }

    public static boolean shouldSuppressVanillaBrainTick(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return false;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        return shouldSuppressForActiveHiredJob(level, villager, state);
    }

    public static boolean shouldSkipHiredFocus(ServerLevel level, Villager villager) {
        return villager.isBaby()
                || !villager.isAlive()
                || villager.isSleeping()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || !HiredVillagerContractService.isHired(level, villager);
    }

    public static void suppressNonWorkAi(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!shouldSuppressForActiveHiredJob(level, villager, context.state())) {
            return;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        suppressIdleAttentionBehavior(villager, worker);
        maintainWorkLookTarget(villager, worker);
        suppressProfessionJobSiteBehavior(level, villager, context);
    }

    private static boolean shouldSuppressForActiveHiredJob(ServerLevel level, Villager villager, CompoundTag state) {
        if (!state.getBoolean("Enabled")
                || !hasActiveHiredWorkerOwner(level, villager)
                || VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())) {
            return false;
        }
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(state, level.getGameTime());
        return switch (worker.taskState()) {
            case MOVING_TO_TARGET, VALIDATING_TARGET, WORKING, COLLECTING_OUTPUT, FINDING_CHAIN_TARGET ->
                    worker.targetPos() != null;
            case MOVING_TO_STORAGE, DEPOSITING, WAITING_FOR_MATERIALS, PAUSED_STORAGE_FULL ->
                    worker.storageTargetPos() != null;
            case RETURNING_TO_WORK_AREA, SELECTING_TARGET, IDLE, FAILED_COOLDOWN -> true;
            default -> false;
        };
    }

    private static boolean hasActiveHiredWorkerOwner(ServerLevel level, Villager villager) {
        if (HiredVillagerContractService.isAwaitingAutoPayment(level, villager)
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            return false;
        }
        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        if (hirerId == null || level.getServer().getPlayerList().getPlayer(hirerId) == null) {
            return false;
        }
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        return role != null && HiredRoleWorkerRegistry.get(role) != null;
    }

    private static void suppressIdleAttentionBehavior(Villager villager, HiredWorkerBrain.Snapshot worker) {
        Brain<Villager> brain = villager.getBrain();
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(brain)) {
            return;
        }

        if (currentWorkLookTarget(worker) == null) {
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
    }

    private static void maintainWorkLookTarget(Villager villager, HiredWorkerBrain.Snapshot worker) {
        BlockPos target = currentWorkLookTarget(worker);
        if (target == null) {
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
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

    private static void suppressProfessionJobSiteBehavior(ServerLevel level, Villager villager, HiredWorkContext context) {
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
                && (!context.isInsideWorkArea(navigationTarget) || navigationTarget.equals(jobSite));
        if (stopNavigation) {
            villager.getNavigation().stop();
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

        if (!hiredNavigation && brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent()) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (!hiredNavigation && brain.getMemory(MemoryModuleType.PATH).isPresent()) {
            brain.eraseMemory(MemoryModuleType.PATH);
        }
        brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        brain.eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        if (brain.isActive(Activity.WORK)) {
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }
}
