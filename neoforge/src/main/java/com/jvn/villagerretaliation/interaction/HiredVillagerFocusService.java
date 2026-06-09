package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
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
        if (state.getBoolean("Enabled")) {
            suppressIdleAttentionBehavior(villager);
        }
    }

    public static boolean shouldSuppressVanillaBrainTick(ServerLevel level, Villager villager) {
        if (shouldSkipHiredFocus(level, villager)) {
            return false;
        }

        CompoundTag state = HiredVillagerWorkService.state(villager);
        HiredVillagerWorkService.initializeDefaults(state, villager);
        return state.getBoolean("Enabled")
                && !VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain());
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
        suppressIdleAttentionBehavior(villager);
        suppressProfessionJobSiteBehavior(level, villager, context);
    }

    private static void suppressIdleAttentionBehavior(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(brain)) {
            return;
        }

        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT);
        brain.eraseMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
        brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
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
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL);
        boolean workAreaReturnNavigation = worker.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA;
        boolean blockTargetNavigation = worker.taskState().keepsBlockTarget()
                && worker.targetPos() != null
                && worker.taskState() != HiredWorkerTaskState.FAILED_COOLDOWN;
        boolean hiredNavigation = storageNavigation || workAreaReturnNavigation || blockTargetNavigation;
        boolean stopNavigation = navigationTarget != null
                && ((!hiredNavigation && !context.isInsideWorkArea(navigationTarget))
                || !workAreaReturnNavigation && navigationTarget.equals(jobSite));
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
        if (brain.isActive(Activity.WORK)) {
            brain.setDefaultActivity(Activity.IDLE);
            brain.setActiveActivityIfPossible(Activity.IDLE);
        }
    }
}
