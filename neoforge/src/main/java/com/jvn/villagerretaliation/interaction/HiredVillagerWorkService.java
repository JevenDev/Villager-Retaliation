package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.resources.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalCullSettings;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredHuntingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredRouteNavigator;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public final class HiredVillagerWorkService {
    private static final String TAG = "VillagerRetaliationHiredWork";
    private static final String NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG = "NextWorkAreaReturnPathGameTime";
    private static final String WORK_AREA_RETURN_TARGET_TAG = "WorkAreaReturnTarget";
    private static final String WORK_AREA_RETURN_DISTANCE_SQR_TAG = "WorkAreaReturnDistanceSqr";
    private static final String WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG = "WorkAreaReturnLastCheckGameTime";
    private static final String WORK_AREA_RETURN_STUCK_CHECKS_TAG = "WorkAreaReturnStuckChecks";
    private static final String STORAGE_FULL_NOTICE_SHOWN_TAG = "StorageFullNoticeShown";
    private static final String STATUS_REPLACEMENTS_TAG = "StatusReplacements";
    private static final String COMPLETED_TASKS_TAG = "CompletedTasks";
    private static final String VANILLA_REST_PAUSED_TAG = "VanillaRestPaused";
    private static final String DISABLED_WORK_PAUSED_TAG = "DisabledWorkPaused";
    private static final String DEFAULTS_VERSION_TAG = "DefaultsVersion";
    private static final int DEFAULTS_VERSION = 1;
    public static final String WAITING_FOR_HIRER_STATUS = "interaction.work.status.waiting_for_hirer";
    private static final String STORAGE_FULL_NOTICE = "interaction.work.status.storage_full";
    private static final String PAUSED_FOR_COMMAND_STATUS = "interaction.work.status.paused_for_command";
    private static final String TIRED_STATUS = "interaction.work.status.tired";
    private static final String SLEEPING_STATUS = "interaction.work.status.sleeping";
    private static final double HIRED_WORK_NOTICE_RADIUS = 32.0D;
    private static final double HIRED_WORK_NOTICE_RADIUS_SQR = HIRED_WORK_NOTICE_RADIUS * HIRED_WORK_NOTICE_RADIUS;
    private static final int MIN_ROUTINE_REPORT_COOLDOWN_SECONDS = 300;
    private static final int MIN_WORK_RADIUS = 4;
    private static final int SKILL_RADIUS_BASELINE = 50;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;
    private static final int HUNTING_WORK_RADIUS = 64;
    private static final int WORK_AREA_RETURN_PATH_RETRY_TICKS = 20;
    private static final int MAX_RETURN_TARGETS_TO_PATHFIND = 32;
    private static final int RETURN_INTERMEDIATE_SEARCH_RADIUS = 10;
    private static final int RETURN_INTERMEDIATE_VERTICAL_RADIUS = 3;
    private static final int MAX_RETURN_INTERMEDIATE_PATH_ATTEMPTS = 24;
    private static final int EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS = 2;
    private static final float WORK_AREA_RETURN_WALK_SPEED = 0.5F;
    private static final int WORK_AREA_RETURN_CLOSE_ENOUGH = 0;
    private static final int WORK_AREA_RETURN_INTERMEDIATE_CLOSE_ENOUGH = 2;
    private static final int EXCAVATION_RETURN_CLOSE_ENOUGH = 2;
    private static final int WORK_AREA_RETURN_STUCK_CHECK_TICKS = 20;
    private static final int WORK_AREA_RETURN_STUCK_LIMIT = 3;
    private static final double WORK_AREA_RETURN_MIN_PROGRESS_SQR = 0.20D;
    private static final int WORK_AREA_TETHER_HORIZONTAL_PADDING = 2;
    private static final int WORK_AREA_TETHER_VERTICAL_PADDING = 2;
    private static final int FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS = 10;
    private static final int FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS = 4;

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || shouldSkipHiredWorkTick(level, villager)) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        if (HiredVillagerFocusService.isVanillaRestActive(villager)) {
            pauseForVanillaRest(level, villager, session);
            return;
        }
        clearVanillaRestPause(session.state());

        if (HiredVillagerContractService.isAwaitingAutoPayment(level, villager)) {
            VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.moveInWaterTowardNavigationTarget(level, villager, WORK_AREA_RETURN_WALK_SPEED);
            return;
        }

        UUID hirerId = HiredVillagerContractService.getHirer(level, villager).orElse(null);
        if (hirerId == null || !(level.getServer().getPlayerList().getPlayer(hirerId) instanceof ServerPlayer hirer)) {
            CompoundTag waitingState = state(villager);
            initializeDefaults(waitingState, villager);
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(waitingState, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(waitingState, WAITING_FOR_HIRER_STATUS);
            return;
        }
        if (VillagerAggressionPolicy.shouldAttackOnSight(villager, hirer)) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            return;
        }
        if (VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            return;
        }

        if (session.worker() == null) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "interaction.work.status.no_routine");
            return;
        }
        if (!session.state().getBoolean("Enabled")) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            if (!session.state().getBoolean(DISABLED_WORK_PAUSED_TAG)) {
                if (session.worker() != null) {
                    session.worker().pause(level, villager, session.context());
                } else {
                    HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                }
                VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
                session.state().putBoolean(DISABLED_WORK_PAUSED_TAG, true);
            }
            setStatus(session.state(), "interaction.work.status.paused");
            return;
        }
        session.state().remove(DISABLED_WORK_PAUSED_TAG);
        if (VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            pauseForRecruitmentCommand(level, villager, session);
            return;
        }

        if (session.role() == HiredVillagerRole.FARMING) {
            VillagerTaskNavigationUtil.enableHiredFarmingWaterTraversal(villager);
        } else {
            VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
        }
        VillagerTaskNavigationUtil.moveInWaterTowardNavigationTarget(level, villager, WORK_AREA_RETURN_WALK_SPEED);
        HiredVillagerFocusService.suppressNonWorkAi(level, villager, session.context());
        if (shouldReturnToWorkArea(session) && returnVillagerToWorkArea(level, villager, session)) {
            return;
        }
        session.worker().maintain(level, villager, session.context());

        int interval = Math.max(10, VillagerRetaliationConfig.HIRED_WORK_TICK_INTERVAL.get());
        if (Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), interval) != 0L) {
            return;
        }

        long nextWorkGameTime = session.state().getLong("NextWorkGameTime");
        if (nextWorkGameTime > level.getGameTime()) {
            setStatus(session.state(), "interaction.work.status.cooldown");
            return;
        }

        WorkResult result = session.worker().tick(level, villager, hirer, session.context());
        setStatus(session.state(), result.status(), result.replacements());
        maybeNotifyStorageFull(level, villager, hirer, session.context(), session.state());
        if (result.awardsSkillGrowth()) {
            HiredWorkSkillGrowthService.onWorkCompleted(level, villager, hirer, session.role(), session.state());
        }
        if (result.completed()) {
            recordCompletedTask(session.state());
            session.state().putLong("NextWorkGameTime", level.getGameTime() + nextTaskCooldownTicks(session.efficiency()));
            HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
            maybeNotify(
                    level,
                    villager,
                    hirer,
                    session.state(),
                    HiredVillagerRoleSettings.workFinalReportMessageKey(session.role()),
                    workReportReplacements(hirer, level, villager, session, snapshot),
                    hiredWorkReportNoticeCooldownTicks());
            if (session.role() == HiredVillagerRole.BUILDER
                    && HiredVillagerContractService.isOneOffBuilderJob(level, villager)
                    && !BuilderTaskState.hasTask(session.state())) {
                HiredVillagerContractService.finishOneOffBuilderJob(level, villager, result.status());
            }
        }
    }

    private static boolean shouldSkipHiredWorkTick(ServerLevel level, Villager villager) {
        return villager.isBaby()
                || !villager.isAlive()
                || villager.isTrading()
                || VillagerConversationService.isConversing(villager)
                || villager.getTarget() != null
                || villager.getLastHurtByMob() != null
                || !HiredVillagerContractService.hasContract(villager)
                || !HiredVillagerContractService.isHired(level, villager);
    }

    private static void pauseForVanillaRest(ServerLevel level, Villager villager, HiredWorkSession session) {
        VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
        if (!session.state().getBoolean(VANILLA_REST_PAUSED_TAG)) {
            if (session.worker() != null) {
                session.worker().pause(level, villager, session.context());
            } else {
                HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            }
            if (!villager.getNavigation().isDone() && !isNavigatingToHome(level, villager)) {
                villager.getNavigation().stop();
            }
            session.state().putBoolean(VANILLA_REST_PAUSED_TAG, true);
        }
        setStatus(session.state(), villager.isSleeping() ? SLEEPING_STATUS : TIRED_STATUS);
    }

    private static boolean isNavigatingToHome(ServerLevel level, Villager villager) {
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (navigationTarget == null) {
            return false;
        }
        return villager.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(home -> home.dimension() == level.dimension())
                .map(GlobalPos::pos)
                .map(homePos -> homePos.distSqr(navigationTarget) <= 4.0D)
                .orElse(false);
    }

    private static void clearVanillaRestPause(CompoundTag state) {
        if (!state.getBoolean(VANILLA_REST_PAUSED_TAG)) {
            return;
        }
        state.remove(VANILLA_REST_PAUSED_TAG);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.IDLE, null);
        String status = state.getString("Status");
        if (TIRED_STATUS.equals(status) || SLEEPING_STATUS.equals(status)) {
            setStatus(state, "interaction.work.status.waiting_tick");
        }
    }

    private static void pauseForRecruitmentCommand(ServerLevel level, Villager villager, HiredWorkSession session) {
        if (!PAUSED_FOR_COMMAND_STATUS.equals(session.state().getString("Status"))) {
            if (session.role() == HiredVillagerRole.BUILDER && BuilderTaskState.hasTask(session.state())) {
                session.context().setProgressTicks(0);
                HiredWorkPlan.clear(session.context());
                HiredWorkerBrain.clearFailure(session.context());
                HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            } else {
                session.worker().stop(level, villager, session.context());
            }
        }
        setStatus(session.state(), PAUSED_FOR_COMMAND_STATUS);
    }

    private static boolean shouldReturnToWorkArea(HiredWorkSession session) {
        if (HiredVillagerRoleSettings.supportsRoutes(session.role()) && session.route().usableForNavigation()) {
            return false;
        }
        return session.role() != HiredVillagerRole.BUILDER || !BuilderTaskState.hasTask(session.state());
    }

    private static boolean returnVillagerToWorkArea(ServerLevel level, Villager villager, HiredWorkSession session) {
        HiredWorkContext context = session.context();
        CompoundTag state = session.state();
        if (!context.hasWorkArea()) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }
        BlockPos excavationEntry = excavationSurfaceEntryTarget(level, session, villager);
        BlockPos excavationCompletionEntry = excavationCompletionEntryTarget(level, session);
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(state, level.getGameTime());
        if (isReturnedToWorkArea(level, villager, session, excavationCompletionEntry, excavationEntry)) {
            if (brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA) {
                VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.IDLE, null);
            }
            clearWorkAreaReturnState(state);
            return false;
        }
        if (brain.taskState().keepsStorageTarget() && brain.storageTargetPos() != null) {
            clearWorkAreaReturnState(state);
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && navigationTarget != null
                && ((excavationEntry != null && navigationTarget.equals(excavationEntry))
                || isInsideReturnedWorkArea(session, navigationTarget)
                || brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA)) {
            if (pathEntersLiquid(level, villager.getNavigation().getPath())
                    || isWetReturnPosition(level, navigationTarget)
                    || excavationEntry != null && navigationTarget.getY() < excavationEntry.getY() - 2) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            } else {
                BlockPos progressTarget = isInsideReturnedWorkArea(session, navigationTarget)
                        ? navigationTarget
                        : context.workCenter();
                if (isWorkAreaReturnNavigationStuck(level, villager, state, progressTarget)) {
                    VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                    state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                    setStatus(state, "interaction.work.status.return_path_lost");
                    return true;
                }
                setWorkAreaReturnWalkTarget(villager, navigationTarget);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, navigationTarget);
                setStatus(state, "interaction.work.status.returning_bounds");
                return true;
            }
        }

        long gameTime = level.getGameTime();
        if (gameTime < state.getLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, null);
            setStatus(state, "interaction.work.status.return_retry_wait");
            return true;
        }

        if (excavationEntry != null) {
            ReturnPath entryPath = findDryReturnPathTo(level, villager, excavationEntry);
            if (entryPath != null && VillagerTaskNavigationUtil.moveToHiredPath(
                    villager,
                    entryPath.path(),
                    excavationEntry,
                    WORK_AREA_RETURN_WALK_SPEED,
                    EXCAVATION_RETURN_CLOSE_ENOUGH)) {
                rememberWorkAreaReturnProgress(level, villager, state, excavationEntry);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, excavationEntry);
                setStatus(state, "interaction.work.status.returning_excavation_ladder");
                return true;
            }
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, excavationEntry, WORK_AREA_RETURN_WALK_SPEED)
                    || VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, excavationEntry, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, excavationEntry);
                setStatus(state, "interaction.work.status.moving_excavation_ladder");
                return true;
            }
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, excavationEntry);
            state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
            setStatus(state, "interaction.work.status.need_excavation_ladder_path");
            return true;
        }

        ReturnPath returnPath = findWorkAreaReturnPath(level, villager, session);
        if (returnPath == null) {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, context.workCenter(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                setStatus(state, "interaction.work.status.returning_by_ladder");
            } else if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, context.workCenter(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                setStatus(state, "interaction.work.status.returning_to_safe_ground");
            } else if (moveTowardWorkAreaIntermediate(level, villager, context, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                setStatus(state, "interaction.work.status.returning_from_far_bounds");
            } else {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                setStatus(state, "interaction.work.status.return_path_missing");
            }
            return true;
        }

        if (shouldPreferLadderReturn(villager, returnPath.target())
                && VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "interaction.work.status.returning_by_ladder");
            return true;
        }
        if (shouldPreferLadderReturn(villager, returnPath.target())
                && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "interaction.work.status.returning_to_safe_ground");
            return true;
        }

        if (VillagerTaskNavigationUtil.moveToHiredPath(
                villager,
                returnPath.path(),
                returnPath.target(),
                WORK_AREA_RETURN_WALK_SPEED,
                WORK_AREA_RETURN_CLOSE_ENOUGH)) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            rememberWorkAreaReturnProgress(level, villager, state, returnPath.target());
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            setStatus(state, "interaction.work.status.returning_bounds");
        } else {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
                setStatus(state, "interaction.work.status.returning_by_ladder");
            } else if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
                setStatus(state, "interaction.work.status.returning_to_safe_ground");
            } else if (moveTowardWorkAreaIntermediate(level, villager, context, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                setStatus(state, "interaction.work.status.returning_from_far_bounds");
            } else {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                setStatus(state, "interaction.work.status.return_could_not_move");
            }
        }
        return true;
    }

    private static boolean shouldPreferLadderReturn(Villager villager, BlockPos target) {
        return target != null && Math.abs(villager.blockPosition().getY() - target.getY()) > 2;
    }

    private static BlockPos excavationSurfaceEntryTarget(ServerLevel level, HiredWorkSession session, Villager villager) {
        if (session.role() != HiredVillagerRole.MINING
                || !HiredMiningMode.fromState(session.state()).usesExcavationShaft()) {
            return null;
        }
        HiredWorkContext context = session.context();
        if (context.isInsideWorkArea(villager.blockPosition())) {
            return null;
        }
        return MiningWorker.excavationReturnTarget(level, villager, context);
    }

    private static BlockPos excavationCompletionEntryTarget(ServerLevel level, HiredWorkSession session) {
        if (session.role() != HiredVillagerRole.MINING
                || !HiredMiningMode.fromState(session.state()).usesExcavationShaft()) {
            return null;
        }
        return MiningWorker.excavationEntryTarget(level, session.context());
    }

    private static BlockPos bestExcavationSurfaceEntryTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        int entryY = context.workMax().getY() + 1;
        List<ReturnIntermediate> candidates = new ArrayList<>();
        addExcavationSurfaceCandidate(level, villager, context, candidates, new BlockPos(
                context.workCenter().getX(),
                entryY,
                context.workCenter().getZ()));

        BlockPos min = context.workMin().offset(-EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS, 1, -EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS);
        BlockPos max = context.workMax().offset(EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS, 1, EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS);
        for (BlockPos raw : BlockPos.betweenClosed(min, max)) {
            BlockPos candidate = raw.immutable();
            if (candidate.getY() != entryY) {
                continue;
            }
            addExcavationSurfaceCandidate(level, villager, context, candidates, candidate);
        }

        candidates.sort(Comparator.comparingDouble(ReturnIntermediate::score));
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (ReturnIntermediate candidate : candidates) {
            if (HiredPathMemory.isApproachRecentlyUnreachable(level, villager, candidate.pos())) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate.pos(), 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                HiredPathMemory.clearUnreachableApproach(villager, candidate.pos());
                double score = candidate.score() + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.pos();
                }
            } else {
                HiredPathMemory.rememberUnreachableApproach(level, villager, candidate.pos());
            }
        }
        return best != null ? best : candidates.isEmpty() ? null : candidates.getFirst().pos();
    }

    private static void addExcavationSurfaceCandidate(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<ReturnIntermediate> candidates,
            BlockPos candidate) {
        if (!isValidWorkAreaReturnTarget(level, candidate)) {
            return;
        }
        candidates.add(new ReturnIntermediate(candidate, excavationSurfaceEntryScore(villager, context, candidate)));
    }

    private static double excavationSurfaceEntryScore(Villager villager, HiredWorkContext context, BlockPos pos) {
        double centerDistance = pos.distSqr(context.workCenter().above());
        double villagerDistance = villager.distanceToSqr(pos.getCenter());
        int outsideX = distanceOutside(pos.getX(), context.workMin().getX(), context.workMax().getX());
        int outsideZ = distanceOutside(pos.getZ(), context.workMin().getZ(), context.workMax().getZ());
        return villagerDistance + centerDistance * 0.5D + (outsideX + outsideZ) * 6.0D;
    }

    private static int distanceOutside(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    private static boolean moveTowardWorkAreaIntermediate(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            double speed) {
        BlockPos target = bestIntermediateReturnTarget(level, villager, context);
        if (target == null) {
            return false;
        }
        if (HiredPathMemory.shouldDelayPathSearch(level, villager)
                || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target)) {
            return false;
        }
        Path path = HiredPathMemory.createPath(level, villager, target, 0);
        if (path == null
                || !path.canReach()
                || pathEntersLiquid(level, path)
                || !VillagerTaskNavigationUtil.moveToHiredPath(
                        villager,
                        path,
                        target,
                        speed,
                        WORK_AREA_RETURN_INTERMEDIATE_CLOSE_ENOUGH)) {
            HiredPathMemory.rememberUnreachableApproach(level, villager, target);
            HiredPathMemory.recordPathSearchFailure(level, villager);
            return false;
        }
        HiredPathMemory.clearUnreachableApproach(villager, target);
        HiredPathMemory.clearPathSearchFailures(villager);
        rememberWorkAreaReturnProgress(level, villager, context.state(), context.workCenter());
        return true;
    }

    private static BlockPos bestIntermediateReturnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos origin = villager.blockPosition();
        double currentCenterDistance = origin.distSqr(context.workCenter());
        List<ReturnIntermediate> candidates = new ArrayList<>();
        for (BlockPos raw : BlockPos.betweenClosed(
                origin.offset(-RETURN_INTERMEDIATE_SEARCH_RADIUS, -RETURN_INTERMEDIATE_VERTICAL_RADIUS, -RETURN_INTERMEDIATE_SEARCH_RADIUS),
                origin.offset(RETURN_INTERMEDIATE_SEARCH_RADIUS, RETURN_INTERMEDIATE_VERTICAL_RADIUS, RETURN_INTERMEDIATE_SEARCH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (candidate.equals(origin) || !isValidWorkAreaReturnTarget(level, candidate)) {
                continue;
            }
            double centerDistance = candidate.distSqr(context.workCenter());
            if (centerDistance >= currentCenterDistance - 1.0D) {
                continue;
            }
            candidates.add(new ReturnIntermediate(candidate, intermediateReturnScore(level, villager, context, candidate)));
        }
        candidates.sort(Comparator.comparingDouble(ReturnIntermediate::score));
        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (ReturnIntermediate candidate : candidates) {
            if (attempts >= HiredPathMemory.adjustedCandidateLimit(level, villager, MAX_RETURN_INTERMEDIATE_PATH_ATTEMPTS)) {
                break;
            }
            if (HiredPathMemory.isApproachRecentlyUnreachable(level, villager, candidate.pos())) {
                continue;
            }
            attempts++;
            Path path = HiredPathMemory.createPath(level, villager, candidate.pos(), 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                HiredPathMemory.clearUnreachableApproach(villager, candidate.pos());
                double score = candidate.score() + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.pos();
                }
            } else {
                HiredPathMemory.rememberUnreachableApproach(level, villager, candidate.pos());
            }
        }
        if (best == null && attempts > 0) {
            HiredPathMemory.recordPathSearchFailure(level, villager);
        } else if (best != null) {
            HiredPathMemory.clearPathSearchFailures(villager);
        }
        return best;
    }

    private static void setWorkAreaReturnWalkTarget(Villager villager, BlockPos target) {
        VillagerTaskNavigationUtil.setHiredWalkTarget(
                villager,
                target,
                WORK_AREA_RETURN_WALK_SPEED,
                WORK_AREA_RETURN_CLOSE_ENOUGH);
    }

    public static boolean hasEffectiveWorkArea(ServerLevel level, Villager villager, HiredWorkSession session) {
        return session != null
                && hasEffectiveWorkArea(level, villager, session.role(), session.context());
    }

    public static boolean hasEffectiveWorkArea(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            HiredWorkContext context) {
        return context != null && (usesRouteAssignment(role, context) || context.hasNavigationTether());
    }

    public static boolean isInsideEffectiveWorkArea(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            HiredWorkContext context,
            BlockPos pos) {
        return context != null
                && (usesRouteAssignment(role, context)
                ? context.isInsideRouteArea(pos)
                : context.isInsideNavigationTether(
                        pos,
                        WORK_AREA_TETHER_HORIZONTAL_PADDING,
                        WORK_AREA_TETHER_VERTICAL_PADDING));
    }

    public static boolean isInsideEffectiveWorkArea(HiredVillagerRole role, HiredWorkContext context, BlockPos pos) {
        return context != null
                && (usesRouteAssignment(role, context)
                ? context.isInsideRouteArea(pos)
                : context.isInsideNavigationTether(
                        pos,
                        WORK_AREA_TETHER_HORIZONTAL_PADDING,
                        WORK_AREA_TETHER_VERTICAL_PADDING));
    }

    public static boolean usesRouteAssignment(HiredVillagerRole role, HiredWorkContext context) {
        return HiredVillagerRoleSettings.supportsRoutes(role)
                && context != null
                && context.hasRoute();
    }

    public static boolean hasClaimedJobSiteInLevel(ServerLevel level, Villager villager) {
        return claimedJobSitePos(level, villager) != null;
    }

    public static BlockPos claimedJobSitePos(ServerLevel level, Villager villager) {
        if (level == null || villager == null) {
            return null;
        }
        return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(jobSite -> jobSite.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .orElse(null);
    }

    private static boolean isReturnedToWorkArea(
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            BlockPos excavationEntry,
            BlockPos excavationSurfaceEntry) {
        HiredWorkContext context = session.context();
        BlockPos pos = villager.blockPosition();
        if (isInsideReturnedWorkArea(session, pos)) {
            return true;
        }
        if (excavationEntry != null) {
            return isAtExcavationEntry(villager, excavationEntry);
        }
        if (excavationSurfaceEntry != null
                && isAtExcavationSurfaceEntry(villager, context, excavationSurfaceEntry)) {
            return true;
        }
        return false;
    }

    private static boolean isInsideReturnedWorkArea(HiredWorkSession session, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        HiredWorkContext context = session.context();
        if (context.isInsideWorkArea(pos)) {
            return true;
        }
        return session.role() == HiredVillagerRole.FARMING
                && (context.isInsideWorkArea(pos.below()) || context.isInsideWorkArea(pos.above()));
    }

    private static boolean isAtExcavationSurfaceEntry(
            Villager villager,
            HiredWorkContext context,
            BlockPos excavationEntry) {
        if (isAtExcavationEntry(villager, excavationEntry)) {
            return true;
        }
        BlockPos pos = villager.blockPosition();
        int horizontalDistance = Math.abs(pos.getX() - excavationEntry.getX())
                + Math.abs(pos.getZ() - excavationEntry.getZ());
        return excavationEntry.getY() == context.workMax().getY() + 1
                && horizontalDistance <= EXCAVATION_RETURN_CLOSE_ENOUGH
                && pos.getY() >= excavationEntry.getY()
                && pos.getY() <= excavationEntry.getY() + 1;
    }

    private static boolean isAtExcavationEntry(Villager villager, BlockPos excavationEntry) {
        BlockPos pos = villager.blockPosition();
        if (pos.distSqr(excavationEntry) <= 1.0D
                && Math.abs(pos.getY() - excavationEntry.getY()) <= 1) {
            return true;
        }
        int horizontalDistance = Math.abs(pos.getX() - excavationEntry.getX())
                + Math.abs(pos.getZ() - excavationEntry.getZ());
        return horizontalDistance <= 1
                && pos.getY() >= excavationEntry.getY()
                && pos.getY() <= excavationEntry.getY() + 2;
    }

    private static boolean isWorkAreaReturnNavigationStuck(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            BlockPos target) {
        double distanceSqr = villager.distanceToSqr(target.getCenter());
        if (distanceSqr <= WORK_AREA_RETURN_CLOSE_ENOUGH * WORK_AREA_RETURN_CLOSE_ENOUGH) {
            rememberWorkAreaReturnProgress(level, villager, state, target);
            return false;
        }
        if (!state.contains(WORK_AREA_RETURN_TARGET_TAG, Tag.TAG_LONG)
                || state.getLong(WORK_AREA_RETURN_TARGET_TAG) != target.asLong()) {
            rememberWorkAreaReturnProgress(level, villager, state, target);
            return false;
        }
        long gameTime = level.getGameTime();
        if (gameTime - state.getLong(WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG) < WORK_AREA_RETURN_STUCK_CHECK_TICKS) {
            return false;
        }
        double previousDistanceSqr = state.getDouble(WORK_AREA_RETURN_DISTANCE_SQR_TAG);
        int stuckChecks = previousDistanceSqr - distanceSqr > WORK_AREA_RETURN_MIN_PROGRESS_SQR
                ? 0
                : state.getInt(WORK_AREA_RETURN_STUCK_CHECKS_TAG) + 1;
        state.putDouble(WORK_AREA_RETURN_DISTANCE_SQR_TAG, distanceSqr);
        state.putLong(WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG, gameTime);
        state.putInt(WORK_AREA_RETURN_STUCK_CHECKS_TAG, stuckChecks);
        return stuckChecks >= WORK_AREA_RETURN_STUCK_LIMIT;
    }

    private static void rememberWorkAreaReturnProgress(
            ServerLevel level,
            Villager villager,
            CompoundTag state,
            BlockPos target) {
        state.putLong(WORK_AREA_RETURN_TARGET_TAG, target.asLong());
        state.putDouble(WORK_AREA_RETURN_DISTANCE_SQR_TAG, villager.distanceToSqr(target.getCenter()));
        state.putLong(WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG, level.getGameTime());
        state.putInt(WORK_AREA_RETURN_STUCK_CHECKS_TAG, 0);
    }

    private static void clearWorkAreaReturnState(CompoundTag state) {
        state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
        state.remove(WORK_AREA_RETURN_TARGET_TAG);
        state.remove(WORK_AREA_RETURN_DISTANCE_SQR_TAG);
        state.remove(WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG);
        state.remove(WORK_AREA_RETURN_STUCK_CHECKS_TAG);
    }

    private static long gameTimeForRetry(ServerLevel level, Villager villager) {
        long minimumRetry = level.getGameTime() + WORK_AREA_RETURN_PATH_RETRY_TICKS;
        long existingCooldown = HiredPathMemory.pathSearchRetryCooldownTicks(level, villager);
        if (existingCooldown > 0L) {
            return Math.max(minimumRetry, level.getGameTime() + existingCooldown);
        }
        return Math.max(minimumRetry, HiredPathMemory.recordPathSearchFailure(level, villager));
    }

    private static ReturnPath findWorkAreaReturnPath(ServerLevel level, Villager villager, HiredWorkSession session) {
        if (HiredPathMemory.shouldDelayPathSearch(level, villager)) {
            return null;
        }
        HiredWorkContext context = session.context();
        BlockPos clamped = new BlockPos(
                Mth.clamp(villager.blockPosition().getX(), context.workMin().getX(), context.workMax().getX()),
                Mth.clamp(villager.blockPosition().getY(), context.workMin().getY(), context.workMax().getY()),
                Mth.clamp(villager.blockPosition().getZ(), context.workMin().getZ(), context.workMax().getZ()));

        List<BlockPos> candidates = new ArrayList<>();
        addWorkAreaReturnCandidate(level, session, candidates, clamped);
        addWorkAreaReturnCandidate(level, session, candidates, context.workCenter());
        for (BlockPos raw : BlockPos.betweenClosed(
                clamped.offset(-3, -2, -3),
                clamped.offset(3, 2, 3))) {
            addWorkAreaReturnCandidate(level, session, candidates, raw.immutable());
        }

        candidates.sort(Comparator.comparingDouble(pos -> returnTargetScore(villager, context, pos)));
        int evaluated = 0;
        ReturnPath best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            if (evaluated >= HiredPathMemory.adjustedCandidateLimit(level, villager, MAX_RETURN_TARGETS_TO_PATHFIND)) {
                break;
            }
            if (!isValidWorkAreaReturnCandidate(level, session, candidate)
                    || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, candidate)) {
                continue;
            }
            evaluated++;
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                HiredPathMemory.clearUnreachableApproach(villager, candidate);
                double score = returnTargetScore(villager, context, candidate)
                        + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = new ReturnPath(candidate, path);
                }
            } else {
                HiredPathMemory.rememberUnreachableApproach(level, villager, candidate);
            }
        }
        if (best == null && evaluated > 0) {
            HiredPathMemory.recordPathSearchFailure(level, villager);
        } else if (best != null) {
            HiredPathMemory.clearPathSearchFailures(villager);
        }
        return best;
    }

    private static ReturnPath findDryReturnPathTo(ServerLevel level, Villager villager, BlockPos target) {
        if (!isValidWorkAreaReturnTarget(level, target)
                || HiredPathMemory.shouldDelayPathSearch(level, villager)
                || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target)) {
            return null;
        }
        Path path = HiredPathMemory.createPath(level, villager, target, 0);
        if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
            HiredPathMemory.clearUnreachableApproach(villager, target);
            HiredPathMemory.clearPathSearchFailures(villager);
            return new ReturnPath(target, path);
        }
        HiredPathMemory.rememberUnreachableApproach(level, villager, target);
        HiredPathMemory.recordPathSearchFailure(level, villager);
        return null;
    }

    private static boolean isValidWorkAreaReturnTarget(ServerLevel level, BlockPos pos) {
        return isValidWorkAreaReturnTarget(level, pos, null);
    }

    private static boolean isValidWorkAreaReturnTarget(ServerLevel level, BlockPos pos, HiredVillagerRole role) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return isReturnPassable(level, pos, feet, role)
                && isReturnPassable(level, pos.above(), head, role)
                && (isReturnFloor(floor, role) || feet.is(Blocks.LADDER));
    }

    private static void addWorkAreaReturnCandidate(
            ServerLevel level,
            HiredWorkSession session,
            List<BlockPos> candidates,
            BlockPos candidate) {
        BlockPos target = workAreaReturnCandidateTarget(level, session, candidate);
        if (target != null && !candidates.contains(target)) {
            candidates.add(target);
        }
    }

    private static BlockPos workAreaReturnCandidateTarget(
            ServerLevel level,
            HiredWorkSession session,
            BlockPos candidate) {
        if (isValidWorkAreaReturnCandidate(level, session, candidate)) {
            return candidate;
        }
        if (session.role() == HiredVillagerRole.FARMING
                && session.context().isInsideWorkArea(candidate)
                && isValidWorkAreaReturnCandidate(level, session, candidate.above())) {
            return candidate.above();
        }
        return null;
    }

    private static boolean isValidWorkAreaReturnCandidate(
            ServerLevel level,
            HiredWorkSession session,
            BlockPos candidate) {
        return isInsideReturnedWorkArea(session, candidate)
                && isValidWorkAreaReturnTarget(level, candidate, session.role());
    }

    private static boolean isReturnPassable(ServerLevel level, BlockPos pos, BlockState state, HiredVillagerRole role) {
        if (state.isAir() || state.is(Blocks.LADDER)) {
            return true;
        }
        return role == HiredVillagerRole.FARMING && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isReturnFloor(BlockState state, HiredVillagerRole role) {
        return state.isSolid() || role == HiredVillagerRole.FARMING && state.is(Blocks.FARMLAND);
    }

    private static boolean pathEntersLiquid(ServerLevel level, Path path) {
        if (path == null) {
            return false;
        }
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (isWetReturnPosition(level, path.getNode(i).asBlockPos())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWetReturnPosition(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && (level.getBlockState(pos).liquid()
                || level.hasChunkAt(pos.above()) && level.getBlockState(pos.above()).liquid());
    }

    private static double returnTargetScore(Villager villager, HiredWorkContext context, BlockPos pos) {
        double villagerDistance = villager.distanceToSqr(pos.getCenter());
        double centerDistance = pos.distSqr(context.workCenter());
        return villagerDistance + centerDistance * 0.25D;
    }

    private static double intermediateReturnScore(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        double centerDistance = pos.distSqr(context.workCenter());
        double stepDistance = villager.distanceToSqr(pos.getCenter());
        int vertical = Math.abs(pos.getY() - villager.blockPosition().getY());
        return centerDistance + stepDistance * 0.15D + vertical * vertical * 4.0D + returnTerrainCost(level, pos);
    }

    private static double returnTerrainCost(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return 256.0D;
        }
        double cost = 0.0D;
        if (level.getBlockState(pos).liquid()) {
            cost += 24.0D;
        }
        if (level.getBlockState(pos.above()).liquid()) {
            cost += 16.0D;
        }
        if (level.getBlockState(pos.below()).liquid()) {
            cost += 8.0D;
        }
        return cost;
    }

    private record ReturnPath(BlockPos target, Path path) {
    }

    private record ReturnIntermediate(BlockPos pos, double score) {
    }

    public static void clearRuntimeState() {
        HiredRoleWorkerRegistry.clearRuntimeState();
    }

    public static void onVillagerLeaveLevel(ServerLevel level, Villager villager) {
        if (!HiredVillagerContractService.isHired(level, villager)) {
            return;
        }

        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        HiredRoleWorkerRegistry.clearRuntimeState(villager);
    }

    public static void sendStatus(ServerPlayer player, ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                HiredVillagerRoleSettings.workReportMessageKey(session.role()),
                workReportReplacements(player, level, villager, session, snapshot)
        );
    }

    public static List<String> debugLines(ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        List<HiredJobInventory.OutputStack> outputs = session.inventory().collectOutputItems();
        int outputStacks = outputs.size();
        int outputItems = outputs.stream().mapToInt(output -> output.stack().getCount()).sum();
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        boolean canDepositNow = AssignedStorageService.canInteractWithAssignedStorage(villager, pos -> session.area().contains(pos));
        HiredPathMemory.PathCreationDebug pathDebug = HiredPathMemory.pathCreationDebug(level, villager);

        List<String> lines = new ArrayList<>();
        lines.add("Hired worker debug: role=" + session.role().serializedName()
                + ", hired=" + HiredVillagerContractService.isHired(level, villager)
                + ", enabled=" + session.state().getBoolean("Enabled")
                + ", efficiency=" + session.efficiency() + "%");
        lines.add("State: task=" + snapshot.taskState().id()
                + ", progress=" + snapshot.progressTicks()
                + ", target=" + HiredWorkerBrain.formatPos(snapshot.targetPos())
                + ", storageTarget=" + HiredWorkerBrain.formatPos(snapshot.storageTargetPos()));
        lines.add("Plan: objective=" + HiredWorkPlan.objectiveType(session.context())
                + ", anchor=" + HiredWorkerBrain.formatPos(HiredWorkPlan.objectiveAnchor(session.context()))
                + ", queuedTargets=" + HiredWorkPlan.size(session.context()));
        if (session.role() == HiredVillagerRole.LOGGING) {
            lines.add(LoggingWorker.debugSummary(session.context()));
        }
        lines.add("Failure: reason=" + valueOrNone(snapshot.failureReason())
                + ", retryCooldown=" + snapshot.retryCooldownTicks()
                + ", lastScan=" + valueOrNone(snapshot.lastTargetScanResult()));
        lines.add("Pathing: createdThisTick=" + pathDebug.currentTickCount()
                + ", lastTick=" + pathDebug.lastTickCount()
                + ", total=" + pathDebug.totalCount()
                + ", cacheHitsThisTick=" + pathDebug.cacheHitsThisTick()
                + ", cacheHitsLastTick=" + pathDebug.cacheHitsLastTick()
                + ", cacheHitTotal=" + pathDebug.cacheHitTotal()
                + ", failureStreak=" + pathDebug.failureStreak()
                + ", pathRetryCooldown=" + pathDebug.retryCooldownTicks()
                + ", cachedApproaches=" + pathDebug.unreachableApproaches());
        lines.add("Work area: " + areaDescription(session.area())
                + ", assigned=" + session.area().explicitlyAssigned()
                + ", usable=" + session.area().usable()
                + ", source=" + session.jobSite().sourceLabel()
                + ", anchor=" + HiredWorkerBrain.formatPos(session.jobSite().anchor())
                + ", radius=" + session.area().horizontalRadius()
                + ", verticalRadius=" + session.area().verticalRadius()
                + ", maxRadius=" + session.maxRadius());
        lines.add("Route: nodes=" + session.route().nodes().size()
                + ", loop=" + session.route().loop()
                + ", supportsRoutes=" + HiredVillagerRoleSettings.supportsRoutes(session.role()));
        lines.add("Inventory/storage: outputStacks=" + outputStacks
                + ", outputItems=" + outputItems
                + ", autoDeposit=" + session.state().getBoolean("AutoDepositOutputs")
                + ", assignedSupplies=" + session.state().getBoolean("UseAssignedStorageForSupplies")
                + ", assignedStorage=" + hasAssignedStorage
                + ", canDepositNow=" + canDepositNow);
        lines.add("Status: " + valueOrNone(snapshot.status()));
        return lines;
    }

    public static void resetForNewContract(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        BrewingWorker.clearOrder(state);
        state.putBoolean("Enabled", true);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        HiredRoute.clear(state);
        HiredRouteNavigator.clearProgress(state);
        resetReportProgress(state);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int radius = HiredVillagerRoleSettings.defaultHorizontalRadius(role, MIN_WORK_RADIUS, maxWorkRadius(level, villager, role));
        int verticalRadius = HiredVillagerRoleSettings.defaultVerticalRadius(role, maxWorkRadius(level, villager, role));
        BlockPos center = villager.blockPosition();
        HiredWorkArea.fromCenter(center, radius, verticalRadius, false).save(state);
        stopWork(level, villager, role, "interaction.work.status.no_work_area");
    }

    public static void resetReportProgress(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        resetReportProgress(state);
    }

    public static void stopWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        stopWork(level, villager, role, status, Map.of());
    }

    public static void stopWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        cancelWork(level, villager, role, status, replacements);
    }

    public static void pauseWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        pauseWork(level, villager, role, status, Map.of());
    }

    public static void pauseWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, WorkLifecycle.PAUSE);
    }

    public static void cancelWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        cancelWork(level, villager, role, status, Map.of());
    }

    public static void cancelWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, WorkLifecycle.CANCEL);
    }

    public static void finishWork(ServerLevel level, Villager villager, HiredVillagerRole role, String status) {
        finishWork(level, villager, role, status, Map.of());
    }

    public static void finishWork(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements) {
        updateWorkLifecycle(level, villager, role, status, replacements, WorkLifecycle.FINISH);
    }

    private static void updateWorkLifecycle(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            String status,
            Map<String, String> replacements,
            WorkLifecycle lifecycle) {
        HiredWorkSession session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            if (lifecycle == WorkLifecycle.PAUSE) {
                session.worker().pause(level, villager, session.context());
            } else {
                session.worker().stop(level, villager, session.context());
            }
        } else {
            session.context().setProgressTicks(0);
        }
        session.state().remove("NextWorkGameTime");
        setStatus(session.state(), status, replacements);
    }

    private enum WorkLifecycle {
        PAUSE,
        CANCEL,
        FINISH
    }

    public static void toggleEnabled(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("Enabled", !state.getBoolean("Enabled"));
        setStatus(state, state.getBoolean("Enabled")
                ? "interaction.work.status.enabled"
                : "interaction.work.status.paused");
        sendStatusNotice(player, villager, state);
    }

    public static void changeRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        int radius = HiredWorkArea.clampRadius(area.horizontalRadius() + delta, MIN_WORK_RADIUS, max);
        HiredWorkArea.fromCenter(area.center(), radius, area.verticalRadius(), true).clampedTo(max).save(state);
        setStatus(state, "interaction.work.status.horizontal_radius", Map.of(
                "radius", Integer.toString(radius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void changeVerticalRadius(ServerPlayer player, ServerLevel level, Villager villager, int delta) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        int verticalRadius = HiredWorkArea.clampRadius(area.verticalRadius() + delta, 1, max);
        HiredWorkArea.fromCenter(area.center(), area.horizontalRadius(), verticalRadius, true).clampedTo(max).save(state);
        setStatus(state, "interaction.work.status.vertical_radius", Map.of(
                "radius", Integer.toString(verticalRadius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void changeBounds(ServerPlayer player, ServerLevel level, Villager villager, Direction direction, int delta) {
        if (direction == null || delta == 0) {
            return;
        }
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
        if (role == HiredVillagerRole.HUNTING) {
            changeCenteredHuntingRadius(player, villager, state, area, max, direction, delta);
            return;
        }
        BlockPos center = area.center();
        BlockPos min = area.min();
        BlockPos maxPos = area.max();
        switch (direction) {
            case NORTH -> min = new BlockPos(min.getX(), min.getY(), Mth.clamp(min.getZ() - delta, center.getZ() - max, center.getZ()));
            case SOUTH -> maxPos = new BlockPos(maxPos.getX(), maxPos.getY(), Mth.clamp(maxPos.getZ() + delta, center.getZ(), center.getZ() + max));
            case WEST -> min = new BlockPos(Mth.clamp(min.getX() - delta, center.getX() - max, center.getX()), min.getY(), min.getZ());
            case EAST -> maxPos = new BlockPos(Mth.clamp(maxPos.getX() + delta, center.getX(), center.getX() + max), maxPos.getY(), maxPos.getZ());
            case DOWN -> min = new BlockPos(min.getX(), Mth.clamp(min.getY() - delta, center.getY() - max, center.getY()), min.getZ());
            case UP -> maxPos = new BlockPos(maxPos.getX(), Mth.clamp(maxPos.getY() + delta, center.getY(), center.getY() + max), maxPos.getZ());
        }
        HiredWorkArea.fromBounds(min, maxPos, true).clampedTo(max).save(state);
        HiredWorkArea updated = workArea(state, villager);
        setStatus(state, "interaction.work.status.bounds", Map.of(
                "dimensions", dimensions(updated),
                "bounds", updated.boundsDescription()));
        sendStatusNotice(player, villager, state);
    }

    private static void changeCenteredHuntingRadius(
            ServerPlayer player,
            Villager villager,
            CompoundTag state,
            HiredWorkArea area,
            int max,
            Direction direction,
            int delta) {
        boolean vertical = direction.getAxis() == Direction.Axis.Y;
        int horizontalRadius = area.horizontalRadius();
        int verticalRadius = area.verticalRadius();
        if (vertical) {
            verticalRadius = HiredWorkArea.clampRadius(verticalRadius + delta, 1, max);
        } else {
            horizontalRadius = HiredWorkArea.clampRadius(horizontalRadius + delta, MIN_WORK_RADIUS, max);
        }
        HiredWorkArea.fromCenter(area.center(), horizontalRadius, verticalRadius, true).clampedTo(max).save(state);
        setStatus(state, vertical
                ? "interaction.work.status.vertical_radius"
                : "interaction.work.status.horizontal_radius", Map.of(
                "radius", Integer.toString(vertical ? verticalRadius : horizontalRadius),
                "max", Integer.toString(max)));
        sendStatusNotice(player, villager, state);
    }

    public static void setWorkCenterHere(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, player.blockPosition(), false);
    }

    public static void resetWorkCenterToVillager(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, villager.blockPosition(), true);
    }

    public static void previewWorkArea(ServerPlayer player, ServerLevel level, Villager villager) {
        if (builderIgnoresWorkArea(player, level, villager, HiredVillagerContractService.activeRole(level, villager))) {
            return;
        }
        HiredWorkArea area = workArea(level, villager);
        if (!area.usable()) {
            VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.status.no_work_area");
            return;
        }
        com.jvn.villagerretaliation.item.HiredStorageClipboardItem.sendWorkAreaOutline(player, level, villager);
    }

    public static void toggleAssignedSupplies(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("UseAssignedStorageForSupplies", !state.getBoolean("UseAssignedStorageForSupplies"));
        setStatus(state, state.getBoolean("UseAssignedStorageForSupplies")
                ? "interaction.work.status.assigned_supplies_enabled"
                : "interaction.work.status.assigned_supplies_disabled");
        sendStatusNotice(player, villager, state);
    }

    public static void toggleAutoDeposit(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("AutoDepositOutputs", !state.getBoolean("AutoDepositOutputs"));
        setStatus(state, state.getBoolean("AutoDepositOutputs")
                ? "interaction.work.status.auto_deposit_enabled"
                : "interaction.work.status.auto_deposit_disabled");
        sendStatusNotice(player, villager, state);
    }

    public static void configureRole(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        switch (role) {
            case COMBAT -> {
                HiredCombatMode current = HiredCombatMode.fromState(state);
                HiredCombatMode next = current.next();
                state.putString(HiredCombatMode.STATE_TAG, next.serializedName());
                setStatus(state, "interaction.work.status.combat_orders", Map.of("mode", next.label()));
            }
            case MINING -> {
                HiredMiningMode current = HiredMiningMode.fromState(state);
                HiredMiningMode next = current.next();
                state.putString(HiredMiningMode.STATE_TAG, next.serializedName());
                HiredWorkSession session = HiredWorkSession.active(level, villager);
                MiningWorker.resetForModeChange(level, villager, session.context(), next);
                setStatus(state, "interaction.work.status.mining_orders", Map.of("mode", next.label()));
            }
            case HUNTING -> {
                HiredHuntingTargets.initializeDefaults(state);
                HiredWorkSession session = HiredWorkSession.active(level, villager);
                HiredWorkPlan.clear(session.context());
                session.context().setProgressTicks(0);
                setStatus(state, "interaction.work.status.hunting_orders", Map.of("mode", HiredHuntingTargets.selectionLabel(state)));
            }
            case LOGGING -> {
                setStatus(state, "interaction.work.status.logging_filter", Map.of("filter", HiredLoggingFilters.selectionLabel(state)));
            }
            case FARMING -> setStatus(state, "interaction.work.status.farming_fields", Map.of(
                    "till_soil", HiredFarmingOptions.tillSoil(state) ? "enabled" : "disabled"));
            case BREWING -> setStatus(
                    state,
                    BrewingWorker.orderSummaryKey(level, state),
                    BrewingWorker.orderSummaryReplacements(level, VillagerLocale.locale(player), state));
            case BUILDER -> {
                if (BuilderTaskState.hasTask(state)) {
                    setStatus(state, "interaction.work.builder.status_summary", BuilderTaskState.replacements(state));
                } else {
                    setStatus(state, "interaction.work.builder.choose_structure");
                }
            }
            case ANIMAL_HANDLING -> setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
            case NITWIT -> setStatus(state, "interaction.work.status.nitwit_focus");
            default -> setStatus(state, "interaction.work.status.no_extra_setup", Map.of("role", role.label()));
        }
        sendStatusNotice(player, villager, state);
    }

    public static void toggleLoggingFilter(ServerPlayer player, ServerLevel level, Villager villager, String filterId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.LOGGING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.LOGGING.label()));
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredLoggingFilters.toggleFilter(state, filterId);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.logging_filter", Map.of("filter", HiredLoggingFilters.selectionLabel(state)));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleLoggingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.LOGGING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.LOGGING.label()));
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredLoggingOptions.ToggleResult result = HiredLoggingOptions.toggle(state, optionId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.logging_option", Map.of(
                "option", HiredLoggingOptions.label(result.optionId()),
                "state", result.enabled() ? "enabled" : "disabled"));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleHorizontalMiningFloorPatching(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        boolean enabled = MiningHorizontalOptions.togglePatchFloor(state);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        MiningWorker.resetForOptionChange(level, villager, session.context(), HiredMiningMode.fromState(state));
        setStatus(state, "interaction.work.status.mining_orders", Map.of(
                "mode", "Horizontal floor patching " + (enabled ? "enabled" : "disabled")));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleFarmingOption(ServerPlayer player, ServerLevel level, Villager villager, String optionId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.FARMING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.FARMING.label()));
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredFarmingOptions.ToggleResult result = HiredFarmingOptions.toggle(state, optionId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.farming_option", Map.of(
                "option", HiredFarmingOptions.label(result.optionId()),
                "state", result.enabled() ? "enabled" : "disabled"));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleHuntingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.HUNTING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.HUNTING.label()));
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredHuntingTargets.ToggleResult result = HiredHuntingTargets.toggle(state, targetId);
        if (result.invalid()) {
            return;
        }
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.hunting_target", Map.of(
                "target", HiredHuntingTargets.label(result.optionId()),
                "state", result.enabled() ? "enabled" : "disabled",
                "mode", HiredHuntingTargets.selectionLabel(state)));
        sendStatusNotice(player, villager, state);
    }

    public static void toggleAnimalBreedingTarget(ServerPlayer player, ServerLevel level, Villager villager, String targetId) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.ANIMAL_HANDLING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.ANIMAL_HANDLING.label()));
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredAnimalBreedingTargets.toggleTarget(state, targetId);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
        sendStatusNotice(player, villager, state);
    }

    public static void setAnimalCullCap(ServerPlayer player, ServerLevel level, Villager villager, int cap) {
        if (!canManageWork(level, villager, player)) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.manage.requires_hirer");
            return;
        }
        if (HiredVillagerContractService.activeRole(level, villager) != HiredVillagerRole.ANIMAL_HANDLING) {
            com.jvn.villagerretaliation.interaction.VillagerInteractionService.sendVillagerNotice(
                    player,
                    villager,
                    "interaction.work.configure.requires_role",
                    Map.of("role", HiredVillagerRole.ANIMAL_HANDLING.label()));
            return;
        }
        if (cap != HiredAnimalCullSettings.DISABLED_CAP && !HiredAnimalCullSettings.isValidCap(cap)) {
            return;
        }

        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredAnimalCullSettings.setCap(state, cap);
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkPlan.clear(session.context());
        session.context().setProgressTicks(0);
        setStatus(state, "interaction.work.status.animal_handling_config", animalHandlingReplacements(state));
        sendStatusNotice(player, villager, state);
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player);
    }

    private static Map<String, String> animalHandlingReplacements(CompoundTag state) {
        return Map.of(
                "target", HiredAnimalBreedingTargets.selectionLabel(state),
                "cap", HiredAnimalCullSettings.selectionLabel(state));
    }

    public static void initializeWorkArea(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        workAreaWithinMax(state, villager, maxWorkRadius(level, villager, role));
    }

    public static HiredWorkArea workArea(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        return jobSite(level, villager, role, state, maxWorkRadius(level, villager, role)).workArea();
    }

    public static HiredJobSite jobSite(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        return jobSite(level, villager, role, state, maxWorkRadius(level, villager, role));
    }

    public static HiredRoute route(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        return HiredRoute.fromState(state);
    }

    public static boolean setRoute(ServerPlayer player, ServerLevel level, Villager villager, HiredRoute route) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (!HiredVillagerRoleSettings.supportsRoutes(role)) {
            player.displayClientMessage(Component.literal(role.label() + " jobs do not use routes."), true);
            return false;
        }
        HiredRoute safeRoute = route == null ? HiredRoute.empty() : route.validatedChain();
        if (safeRoute.isEmpty()) {
            player.displayClientMessage(Component.literal("Build a route first."), true);
            return false;
        }
        safeRoute.save(state);
        HiredRouteNavigator.clearProgress(state);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, safeRoute.first());
        setStatus(state, "interaction.work.status.waiting_tick");
        return true;
    }

    public static boolean clearRoute(ServerPlayer player, ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        if (HiredRoute.fromState(state).isEmpty()) {
            player.displayClientMessage(Component.literal("No route is assigned."), true);
            return false;
        }
        HiredRoute.clear(state);
        HiredRouteNavigator.clearProgress(state);
        HiredWorkerBrain.setState(state, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        return true;
    }

    static HiredJobSite jobSite(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            int maxRadius) {
        HiredWorkArea storedArea = workAreaWithinMax(state, villager, maxRadius);
        if (role == HiredVillagerRole.BUILDER) {
            return HiredJobSite.fromWorkArea(storedArea.asUsable(false));
        }
        BlockPos claimedJobSite = role == HiredVillagerRole.FARMING ? claimedJobSitePos(level, villager) : null;
        if (storedArea.usable()) {
            return claimedJobSite == null
                    ? HiredJobSite.fromWorkArea(storedArea)
                    : HiredJobSite.withAnchor(
                            storedArea,
                            claimedJobSite,
                            HiredJobSite.AnchorSource.VANILLA_JOB_SITE,
                            FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS,
                            FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS);
        }
        if (claimedJobSite != null) {
            return HiredJobSite.fromAnchor(
                    claimedJobSite,
                    FARMING_JOB_SITE_TETHER_HORIZONTAL_RADIUS,
                    FARMING_JOB_SITE_TETHER_VERTICAL_RADIUS,
                    HiredJobSite.AnchorSource.VANILLA_JOB_SITE);
        }
        return HiredJobSite.fromWorkArea(storedArea);
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return false;
        }
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea requested = HiredWorkArea.fromBounds(first, second, true);
        if (role == HiredVillagerRole.HUNTING) {
            HiredWorkArea current = workAreaWithinMax(state, villager, maxRadius);
            int horizontalRadius = current.explicitlyAssigned()
                    ? current.horizontalRadius()
                    : HiredVillagerRoleSettings.defaultHorizontalRadius(role, MIN_WORK_RADIUS, maxRadius);
            int verticalRadius = current.explicitlyAssigned()
                    ? current.verticalRadius()
                    : HiredVillagerRoleSettings.defaultVerticalRadius(role, maxRadius);
            HiredWorkArea.fromCenter(requested.center(), horizontalRadius, verticalRadius, true)
                    .clampedTo(maxRadius)
                    .save(state);
            setStatus(state, "interaction.work.status.center_set_here", Map.of("range", workArea(state, villager).rangeDescription()));
            sendStatusNotice(player, villager, state);
            return true;
        }
        HiredWorkArea area = requested.clampedTo(maxRadius);
        boolean capped = requested.horizontalRadius() > area.horizontalRadius()
                || requested.verticalRadius() > area.verticalRadius();
        area.save(state);
        if (role == HiredVillagerRole.MINING) {
            HiredWorkSession session = HiredWorkSession.active(level, villager);
            MiningWorker.resetForWorkAreaChange(
                    level,
                    villager,
                    session.context(),
                    HiredMiningMode.fromState(state));
        }
        setStatus(state, capped
                ? "interaction.work.status.custom_box_capped"
                : "interaction.work.status.custom_box", Map.of("dimensions", dimensions(area)));
        sendStatusNotice(player, villager, state);
        return true;
    }

    private static void assignCenteredWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos center, boolean villagerCenter) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        if (builderIgnoresWorkArea(player, level, villager, role)) {
            return;
        }
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea current = workAreaWithinMax(state, villager, maxRadius);
        int horizontalRadius = current.explicitlyAssigned()
                ? current.horizontalRadius()
                : HiredVillagerRoleSettings.defaultHorizontalRadius(role, MIN_WORK_RADIUS, maxRadius);
        int verticalRadius = current.explicitlyAssigned()
                ? current.verticalRadius()
                : HiredVillagerRoleSettings.defaultVerticalRadius(role, maxRadius);
        HiredWorkArea.fromCenter(center, horizontalRadius, verticalRadius, true).clampedTo(maxRadius).save(state);
        if (role == HiredVillagerRole.MINING) {
            HiredWorkSession session = HiredWorkSession.active(level, villager);
            MiningWorker.resetForWorkAreaChange(
                    level,
                    villager,
                    session.context(),
                    HiredMiningMode.fromState(state));
        }
        setStatus(state, villagerCenter
                ? "interaction.work.status.center_reset_to_villager"
                : "interaction.work.status.center_set_here", Map.of("range", workArea(state, villager).rangeDescription()));
        sendStatusNotice(player, villager, state);
    }

    private static boolean builderIgnoresWorkArea(ServerPlayer player, ServerLevel level, Villager villager, HiredVillagerRole role) {
        if (role != HiredVillagerRole.BUILDER) {
            return false;
        }
        VillagerInteractionService.sendVillagerNotice(player, villager, "interaction.work.builder.no_job_site");
        return true;
    }

    static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    static void initializeDefaults(CompoundTag state, Villager villager) {
        if (state.getInt(DEFAULTS_VERSION_TAG) >= DEFAULTS_VERSION) {
            return;
        }
        boolean hadStoredArea = state.contains(HiredWorkArea.WORK_CENTER_POS_TAG, Tag.TAG_LONG)
                && state.contains(HiredWorkArea.WORK_MIN_POS_TAG, Tag.TAG_LONG)
                && state.contains(HiredWorkArea.WORK_MAX_POS_TAG, Tag.TAG_LONG);
        if (!state.contains("Enabled", Tag.TAG_BYTE)) {
            state.putBoolean("Enabled", true);
        }
        if (!state.contains(HiredWorkArea.RADIUS_TAG, Tag.TAG_INT)) {
            state.putInt(HiredWorkArea.RADIUS_TAG, Mth.clamp(
                    VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(),
                    MIN_WORK_RADIUS,
                    baseWorkRadiusCap()));
        }
        if (!state.contains(HiredWorkArea.WORK_AREA_ASSIGNED_TAG, Tag.TAG_BYTE)) {
            state.putBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG, false);
        }
        if (!state.contains("UseAssignedStorageForSupplies", Tag.TAG_BYTE)) {
            state.putBoolean("UseAssignedStorageForSupplies", true);
        }
        if (!state.contains("AutoDepositOutputs", Tag.TAG_BYTE)) {
            state.putBoolean("AutoDepositOutputs", true);
        }
        if (!state.contains("LoggingFilter", Tag.TAG_STRING)) {
            state.putString("LoggingFilter", "any");
        }
        HiredLoggingOptions.initializeDefaults(state);
        HiredFarmingOptions.initializeDefaults(state);
        if (!state.contains("NavigationTargetType", Tag.TAG_STRING)) {
            state.putString("NavigationTargetType", "interesting");
        }
        if (!state.contains(HiredCombatMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredCombatMode.STATE_TAG, HiredCombatMode.GUARD.serializedName());
        }
        if (!state.contains(HiredHuntingMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredHuntingMode.STATE_TAG, HiredHuntingMode.fromState(state).serializedName());
        }
        HiredHuntingTargets.initializeDefaults(state);
        MiningHorizontalOptions.initializeDefaults(state);
        if (!state.contains("Status", Tag.TAG_STRING)) {
            setStatus(state, "interaction.work.status.waiting_tick");
        }
        HiredWorkerBrain.initialize(state);
        if (!hadStoredArea) {
            int radius = Mth.clamp(state.getInt(HiredWorkArea.RADIUS_TAG), MIN_WORK_RADIUS, baseWorkRadiusCap());
            HiredWorkArea.fromCenter(villager.blockPosition(), radius, Math.min(radius, 8), state.getBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG)).save(state);
        }
        state.putInt(DEFAULTS_VERSION_TAG, DEFAULTS_VERSION);
    }

    private static BlockPos workCenter(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_CENTER_POS_TAG, Tag.TAG_LONG)) {
            state.putLong(HiredWorkArea.WORK_CENTER_POS_TAG, villager.blockPosition().asLong());
        }
        return BlockPos.of(state.getLong(HiredWorkArea.WORK_CENTER_POS_TAG));
    }

    private static HiredWorkArea workArea(CompoundTag state, Villager villager) {
        if (!state.contains(HiredWorkArea.WORK_MIN_POS_TAG, Tag.TAG_LONG) || !state.contains(HiredWorkArea.WORK_MAX_POS_TAG, Tag.TAG_LONG)) {
            initializeDefaults(state, villager);
        }
        return HiredWorkArea.fromState(state, workCenter(state, villager));
    }

    static HiredWorkArea workAreaWithinMax(CompoundTag state, Villager villager, int maxRadius) {
        int safeMaxRadius = Math.max(MIN_WORK_RADIUS, maxRadius);
        HiredWorkArea area = workArea(state, villager);
        HiredWorkArea clamped = area.clampedTo(safeMaxRadius);
        if (!clamped.min().equals(area.min()) || !clamped.max().equals(area.max()) || state.getInt(HiredWorkArea.RADIUS_TAG) > safeMaxRadius) {
            clamped.save(state);
            return clamped;
        }
        state.putInt(HiredWorkArea.RADIUS_TAG, Mth.clamp(area.horizontalRadius(), MIN_WORK_RADIUS, safeMaxRadius));
        return area;
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role) {
        return maxWorkRadius(level, villager, role, HiredVillagerRoles.roleScore(level, villager, role));
    }

    static int maxWorkRadius(ServerLevel level, Villager villager, HiredVillagerRole role, int roleScore) {
        if (role == HiredVillagerRole.HUNTING) {
            return HUNTING_WORK_RADIUS;
        }
        int base = baseWorkRadiusCap();
        int max = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get(), base, MAX_SKILLED_WORK_RADIUS);
        int score = Mth.clamp(roleScore, 0, 100);
        double progress = Math.max(0.0D, Math.min(1.0D, (score - SKILL_RADIUS_BASELINE) / 50.0D));
        return Mth.clamp(base + (int) Math.round((max - base) * progress), MIN_WORK_RADIUS, max);
    }

    private static int baseWorkRadiusCap() {
        return Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(), MIN_WORK_RADIUS, MAX_SKILLED_WORK_RADIUS);
    }

    private static String dimensions(HiredWorkArea area) {
        return (area.max().getX() - area.min().getX() + 1)
                + "x" + (area.max().getY() - area.min().getY() + 1)
                + "x" + (area.max().getZ() - area.min().getZ() + 1);
    }

    private static String areaDescription(HiredWorkArea area) {
        return area.usable() ? area.boundsDescription() : "No work area assigned";
    }

    private static void setStatus(CompoundTag state, String status) {
        String safeStatus = status == null ? "" : status;
        boolean statusMatches = state.contains("Status", Tag.TAG_STRING)
                && safeStatus.equals(state.getString("Status"));
        boolean hasReplacements = state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND);
        if (statusMatches && !hasReplacements) {
            return;
        }
        if (!statusMatches) {
            state.putString("Status", safeStatus);
        }
        if (hasReplacements) {
            state.remove(STATUS_REPLACEMENTS_TAG);
        }
    }

    private static void setStatus(CompoundTag state, String status, Map<String, String> replacements) {
        String safeStatus = status == null ? "" : status;
        boolean statusMatches = state.contains("Status", Tag.TAG_STRING)
                && safeStatus.equals(state.getString("Status"));
        if (replacements == null || replacements.isEmpty()) {
            if (statusMatches && !state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
                return;
            }
            if (!statusMatches) {
                state.putString("Status", safeStatus);
            }
            if (state.contains(STATUS_REPLACEMENTS_TAG)) {
                state.remove(STATUS_REPLACEMENTS_TAG);
            }
            return;
        }
        if (statusMatches && replacementsMatch(state, replacements)) {
            return;
        }
        if (!statusMatches) {
            state.putString("Status", safeStatus);
        }
        CompoundTag replacementTag = new CompoundTag();
        replacements.forEach((key, value) -> replacementTag.putString(key, value == null ? "" : value));
        state.put(STATUS_REPLACEMENTS_TAG, replacementTag);
    }

    private static boolean replacementsMatch(CompoundTag state, Map<String, String> replacements) {
        if (!state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag replacementTag = state.getCompound(STATUS_REPLACEMENTS_TAG);
        if (replacementTag.getAllKeys().size() != replacements.size()) {
            return false;
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String safeValue = entry.getValue() == null ? "" : entry.getValue();
            if (!replacementTag.contains(entry.getKey(), Tag.TAG_STRING)
                    || !safeValue.equals(replacementTag.getString(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> statusReplacements(CompoundTag state) {
        if (!state.contains(STATUS_REPLACEMENTS_TAG, Tag.TAG_COMPOUND)) {
            return Map.of();
        }
        CompoundTag replacementTag = state.getCompound(STATUS_REPLACEMENTS_TAG);
        Map<String, String> replacements = new java.util.LinkedHashMap<>();
        for (String key : replacementTag.getAllKeys()) {
            replacements.put(key, replacementTag.getString(key));
        }
        return replacements;
    }

    private static void recordCompletedTask(CompoundTag state) {
        state.putInt(COMPLETED_TASKS_TAG, Math.max(0, state.getInt(COMPLETED_TASKS_TAG)) + 1);
    }

    private static void resetReportProgress(CompoundTag state) {
        state.remove(COMPLETED_TASKS_TAG);
    }

    private static int completedTasks(CompoundTag state) {
        return Math.max(0, state.getInt(COMPLETED_TASKS_TAG));
    }

    private static String completedTasksPhrase(int count) {
        return count == 1 ? "1 completed task" : count + " completed tasks";
    }

    private static String outputSummary(HiredWorkSession session) {
        List<HiredJobInventory.OutputStack> outputs = session.inventory().collectOutputItems();
        int outputItems = outputs.stream().mapToInt(output -> output.stack().getCount()).sum();
        if (outputItems <= 0) {
            return "no output waiting";
        }
        return outputItems == 1 ? "1 output item waiting" : outputItems + " output items waiting";
    }

    private static Map<String, String> workReportReplacements(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            HiredWorkerBrain.Snapshot snapshot) {
        String targetDescription = describeCurrentTarget(player, level, villager, snapshot);
        int completedTasks = completedTasks(session.state());
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("activity", describeWorkActivity(player, level, villager, session, snapshot, targetDescription));
        replacements.put("status_detail", describeStatusDetail(player, level, villager, session, targetDescription));
        replacements.put("work_area", describeWorkArea(player, level, villager, session.area()));
        replacements.put("efficiency", Integer.toString(session.efficiency()));
        replacements.put("target", targetDescription);
        replacements.put("role", session.role().label());
        replacements.put("completed_tasks", Integer.toString(completedTasks));
        replacements.put("completed_tasks_phrase", completedTasksPhrase(completedTasks));
        replacements.put("completed_tasks_plural", completedTasks == 1 ? "" : "s");
        replacements.put("progress_summary", "So far I have finished " + completedTasksPhrase(completedTasks) + ".");
        replacements.put("output_summary", outputSummary(session));
        return replacements;
    }

    private static void sendStatusNotice(ServerPlayer player, Villager villager, CompoundTag state) {
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"), statusReplacements(state));
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String describeWorkActivity(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            HiredWorkerBrain.Snapshot snapshot,
            String targetDescription) {
        if (!session.state().getBoolean("Enabled")) {
            return resolveWorkMessage(player, level, villager, "interaction.work.activity.paused", Map.of());
        }
        String key = switch (snapshot.taskState()) {
            case IDLE, AWAITING_INSTRUCTION -> "interaction.work.activity.waiting";
            case SELECTING_TARGET -> "interaction.work.activity.selecting_target";
            case MOVING_TO_TARGET -> "interaction.work.activity.moving_to_target";
            case VALIDATING_TARGET -> "interaction.work.activity.validating_target";
            case WORKING -> "interaction.work.activity.working";
            case COLLECTING_OUTPUT -> "interaction.work.activity.collecting_output";
            case FINDING_CHAIN_TARGET -> "interaction.work.activity.finding_chain_target";
            case MOVING_TO_STORAGE -> "interaction.work.activity.moving_to_storage";
            case RETURNING_TO_WORK_AREA -> "interaction.work.activity.returning_to_work_area";
            case DEPOSITING -> "interaction.work.activity.depositing";
            case WAITING_FOR_MATERIALS -> "interaction.work.activity.waiting_for_materials";
            case PAUSED_STORAGE_FULL -> "interaction.work.activity.paused_storage_full";
            case NO_WORK_AREA -> "interaction.work.activity.no_work_area";
            case PAUSED_FULL_INVENTORY -> "interaction.work.activity.paused_full_inventory";
            case PAUSED_NO_STORAGE -> "interaction.work.activity.paused_no_storage";
            case FAILED_COOLDOWN -> "interaction.work.activity.failed_cooldown";
            case PAUSED_MISSING_TOOL -> "interaction.work.activity.paused_missing_tool";
        };
        return resolveWorkMessage(player, level, villager, key, Map.of("target", targetDescription));
    }

    private static String describeStatusDetail(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            String targetDescription) {
        String status = session.state().getString("Status");
        if (status == null || status.isBlank()) {
            return resolveWorkMessage(player, level, villager, "interaction.work.status.none", Map.of());
        }
        Map<String, String> replacements = new java.util.LinkedHashMap<>(statusReplacements(session.state()));
        replacements.put("efficiency", Integer.toString(session.efficiency()));
        replacements.put("work_area", describeWorkArea(player, level, villager, session.area()));
        replacements.put("target", targetDescription);
        replacements.put("role", session.role().label());
        return resolveWorkMessage(player, level, villager, status, replacements);
    }

    private static String describeWorkArea(ServerPlayer player, ServerLevel level, Villager villager, HiredWorkArea area) {
        return area.usable()
                ? area.boundsDescription()
                : resolveWorkMessage(player, level, villager, "interaction.work.area.none", Map.of());
    }

    private static String describeCurrentTarget(ServerPlayer player, ServerLevel level, Villager villager, HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = currentTarget(snapshot);
        return target == null
                ? resolveWorkMessage(player, level, villager, "interaction.work.target.none", Map.of())
                : HiredWorkerBrain.formatPos(target);
    }

    private static String describeCurrentTarget(HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = currentTarget(snapshot);
        return target == null ? "none" : HiredWorkerBrain.formatPos(target);
    }

    private static BlockPos currentTarget(HiredWorkerBrain.Snapshot snapshot) {
        BlockPos target = snapshot.taskState().keepsStorageTarget() ? snapshot.storageTargetPos() : snapshot.targetPos();
        if (target == null && snapshot.storageTargetPos() != null) {
            target = snapshot.storageTargetPos();
        }
        if (target == null && snapshot.targetPos() != null) {
            target = snapshot.targetPos();
        }
        return target;
    }

    private static String resolveWorkMessage(
            ServerPlayer player,
            ServerLevel level,
            Villager villager,
            String key,
            Map<String, String> replacements) {
        DialogueContext context = VillagerInteractionService.createDialogueContext(level, player, villager);
        return VillagerDialogueResources.message(context, key, replacements).orElse(key);
    }

    static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        return efficiencyPercent(level, villager, role, state, inventory, HiredVillagerRoles.roleScore(level, villager, role));
    }

    static int efficiencyPercent(
            ServerLevel level,
            Villager villager,
            HiredVillagerRole role,
            CompoundTag state,
            HiredJobInventory inventory,
            int roleScore) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int efficiency = VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get();
        efficiency += (roleScore - 50) / 2;
        if (HiredVillagerRoles.isSkillUnlocked(villager, role, roleScore)) {
            efficiency += 10;
        } else if (HiredVillagerRoles.isProfessionPreferred(villager, role)) {
            efficiency += 3;
        }
        VillagerMoodState mood = VillagerMoodService.mood(level, villager);
        efficiency += switch (mood.primaryMood()) {
            case CONTENT, GRATEFUL, PROUD, HOPEFUL -> 8;
            case ANGRY, AFRAID, STRESSED, GRIEVING -> -15;
            case SUSPICIOUS, LONELY -> -8;
            default -> 0;
        };
        ItemStack tool = inventory.getItem(HiredJobInventory.MAINHAND_SLOT);
        if (!tool.isEmpty()) {
            efficiency += toolTierBonus(tool);
        }
        if ((role == HiredVillagerRole.MINING || role == HiredVillagerRole.LOGGING || role == HiredVillagerRole.FARMING || role == HiredVillagerRole.FISHING) && tool.isEmpty()) {
            efficiency -= 20;
        }
        return Mth.clamp(efficiency, min, max);
    }

    private static int toolTierBonus(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem tieredItem) {
            String name = tieredItem.getTier().toString().toLowerCase(Locale.ROOT);
            if (name.contains("netherite")) {
                return 28;
            }
            if (name.contains("diamond")) {
                return 20;
            }
            if (name.contains("iron")) {
                return 12;
            }
            if (name.contains("stone")) {
                return 5;
            }
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.contains("netherite")) {
            return 28;
        }
        if (path.contains("diamond")) {
            return 20;
        }
        if (path.contains("iron")) {
            return 12;
        }
        if (path.contains("stone")) {
            return 5;
        }
        return 0;
    }

    private static int nextTaskCooldownTicks(int efficiency) {
        return Mth.clamp(Math.round(80.0F * 100.0F / Math.max(25.0F, efficiency)), 10, 200);
    }

    private static boolean maybeNotify(ServerLevel level, Villager villager, ServerPlayer hirer, CompoundTag state, String message, long cooldownTicks) {
        return maybeNotify(level, villager, hirer, state, message, Map.of(), cooldownTicks);
    }

    private static boolean maybeNotify(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            CompoundTag state,
            String message,
            Map<String, String> replacements,
            long cooldownTicks) {
        long now = level.getGameTime();
        if (!canHearHiredWorkNotice(level, villager, hirer)) {
            return false;
        }
        if (state.contains("LastNoticeTick", Tag.TAG_LONG) && now - state.getLong("LastNoticeTick") < cooldownTicks) {
            return false;
        }
        state.putLong("LastNoticeTick", now);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, message, replacements, HIRED_WORK_NOTICE_RADIUS);
        return true;
    }

    private static boolean canHearHiredWorkNotice(ServerLevel level, Villager villager, ServerPlayer hirer) {
        return hirer.serverLevel() == level && hirer.distanceToSqr(villager) <= HIRED_WORK_NOTICE_RADIUS_SQR;
    }

    private static void maybeNotifyStorageFull(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkContext context,
            CompoundTag state) {
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(state, level.getGameTime());
        if (brain.taskState() != HiredWorkerTaskState.PAUSED_STORAGE_FULL) {
            if (!context.hasOutputToDeposit()) {
                state.remove(STORAGE_FULL_NOTICE_SHOWN_TAG);
                state.remove(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG);
            }
            return;
        }
        if (state.getBoolean(HiredWorkContext.OUTPUT_DEPOSITED_THIS_STORAGE_TRIP_TAG)) {
            return;
        }
        if (state.getBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG)) {
            return;
        }
        if (maybeNotify(level, villager, hirer, state, STORAGE_FULL_NOTICE, hiredWorkNoticeCooldownTicks())) {
            state.putBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG, true);
        }
    }

    private static long hiredWorkNoticeCooldownTicks() {
        int seconds = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_NOTICE_COOLDOWN_SECONDS.get(), 0, 600);
        return 20L * seconds;
    }

    private static long hiredWorkReportNoticeCooldownTicks() {
        int configuredSeconds = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_NOTICE_COOLDOWN_SECONDS.get(), 0, 600);
        int seconds = configuredSeconds <= 0 ? 0 : Math.max(MIN_ROUTINE_REPORT_COOLDOWN_SECONDS, configuredSeconds);
        return 20L * seconds;
    }
}
