package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.VillagerDialogueResources;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.work.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredRoleWorkerRegistry;
import com.jvn.villagerretaliation.interaction.work.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.MiningWorker;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.mood.VillagerMoodService;
import com.jvn.villagerretaliation.mood.VillagerMoodState;
import com.jvn.villagerretaliation.reputation.VillagerAggressionPolicy;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.skill.HiredWorkSkillGrowthService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
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
    private static final String STORAGE_FULL_NOTICE = "interaction.work.status.storage_full";
    private static final long DAY_TICKS = 24000L;
    private static final int MIN_WORK_RADIUS = 4;
    private static final int SKILL_RADIUS_BASELINE = 50;
    private static final int MAX_SKILLED_WORK_RADIUS = 32;
    private static final int WORK_AREA_RETURN_PATH_RETRY_TICKS = 20;
    private static final int MAX_RETURN_TARGETS_TO_PATHFIND = 32;
    private static final int RETURN_INTERMEDIATE_SEARCH_RADIUS = 10;
    private static final int RETURN_INTERMEDIATE_VERTICAL_RADIUS = 3;
    private static final int MAX_RETURN_INTERMEDIATE_PATH_ATTEMPTS = 24;
    private static final int EXCAVATION_SURFACE_ENTRY_SEARCH_RADIUS = 2;
    private static final float WORK_AREA_RETURN_WALK_SPEED = 0.5F;
    private static final int WORK_AREA_RETURN_CLOSE_ENOUGH = 2;
    private static final int WORK_AREA_RETURN_STUCK_CHECK_TICKS = 20;
    private static final int WORK_AREA_RETURN_STUCK_LIMIT = 3;
    private static final double WORK_AREA_RETURN_MIN_PROGRESS_SQR = 0.20D;

    private HiredVillagerWorkService() {
    }

    public static void onVillagerTickPost(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)
                || HiredVillagerFocusService.shouldSkipHiredFocus(level, villager)) {
            return;
        }
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
            setStatus(waitingState, "interaction.work.status.waiting_for_hirer");
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

        HiredWorkSession session = HiredWorkSession.active(level, villager);
        if (session.worker() == null) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "interaction.work.status.no_routine");
            return;
        }
        if (!session.state().getBoolean("Enabled")) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
            HiredWorkerBrain.setState(session.state(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            setStatus(session.state(), "interaction.work.status.paused");
            return;
        }
        if (VillagerRecruitmentService.isFollowingAnyPlayer(villager)) {
            VillagerTaskNavigationUtil.restoreHiredWaterTraversal(villager);
            pauseForRecruitmentCommand(level, villager, session);
            return;
        }

        VillagerTaskNavigationUtil.enableHiredWaterTraversal(villager);
        VillagerTaskNavigationUtil.moveInWaterTowardNavigationTarget(level, villager, WORK_AREA_RETURN_WALK_SPEED);
        HiredVillagerFocusService.suppressNonWorkAi(level, villager, session.context());
        if (returnVillagerToWorkArea(level, villager, session)) {
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

        handleDailyFood(level, villager, hirer, session);
        WorkResult result = session.worker().tick(level, villager, hirer, session.context());
        setStatus(session.state(), result.status(), result.replacements());
        maybeNotifyStorageFull(level, villager, hirer, session.context(), session.state());
        if (result.awardsSkillGrowth()) {
            HiredWorkSkillGrowthService.onWorkCompleted(level, villager, hirer, session.role(), session.state());
        }
        if (result.completed()) {
            session.state().putLong("NextWorkGameTime", level.getGameTime() + nextTaskCooldownTicks(session.efficiency()));
            maybeNotify(level, villager, hirer, session.state(), result.status(), result.replacements(), 20L * 30L);
        }
    }

    private static void pauseForRecruitmentCommand(ServerLevel level, Villager villager, HiredWorkSession session) {
        session.worker().stop(level, villager, session.context());
        VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
        HiredWorkPlan.clear(session.context());
        HiredWorkerBrain.clearFailure(session.context());
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        setStatus(session.state(), "interaction.work.status.paused_for_command");
    }

    private static boolean returnVillagerToWorkArea(ServerLevel level, Villager villager, HiredWorkSession session) {
        HiredWorkContext context = session.context();
        CompoundTag state = session.state();
        if (!context.hasWorkArea()) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            return false;
        }
        BlockPos excavationEntry = excavationSurfaceEntryTarget(level, session, villager);
        HiredWorkerBrain.Snapshot brain = HiredWorkerBrain.snapshot(state, level.getGameTime());
        if (isReturnedToWorkArea(villager, session.context(), excavationEntry)) {
            if (brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA) {
                VillagerTaskNavigationUtil.stopNavigationAndClearTargets(villager);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.IDLE, null);
            }
            clearWorkAreaReturnState(state);
            return false;
        }
        if ((brain.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || brain.taskState() == HiredWorkerTaskState.DEPOSITING
                || brain.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL)
                && brain.storageTargetPos() != null) {
            clearWorkAreaReturnState(state);
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone()
                && navigationTarget != null
                && ((excavationEntry != null && navigationTarget.equals(excavationEntry))
                || context.isInsideWorkArea(navigationTarget)
                || brain.taskState() == HiredWorkerTaskState.RETURNING_TO_WORK_AREA)) {
            if (pathEntersLiquid(level, villager.getNavigation().getPath())
                    || isWetReturnPosition(level, navigationTarget)
                    || excavationEntry != null && navigationTarget.getY() < excavationEntry.getY() - 2) {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.PATH);
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                BlockPos progressTarget = context.isInsideWorkArea(navigationTarget) ? navigationTarget : context.workCenter();
                if (isWorkAreaReturnNavigationStuck(level, villager, state, progressTarget)) {
                    villager.getNavigation().stop();
                    villager.getBrain().eraseMemory(MemoryModuleType.PATH);
                    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level));
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
            if (entryPath != null && villager.getNavigation().moveTo(entryPath.path(), WORK_AREA_RETURN_WALK_SPEED)) {
                setWorkAreaReturnWalkTarget(villager, excavationEntry);
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
            state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
            setStatus(state, "interaction.work.status.need_excavation_ladder_path");
            return true;
        }

        ReturnPath returnPath = findWorkAreaReturnPath(level, villager, context);
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
                villager.getNavigation().stop();
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
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

        if (villager.getNavigation().moveTo(returnPath.path(), WORK_AREA_RETURN_WALK_SPEED)) {
            setWorkAreaReturnWalkTarget(villager, returnPath.target());
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
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTime + WORK_AREA_RETURN_PATH_RETRY_TICKS);
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
                || !HiredMiningMode.fromState(session.state()).excavatesArea()) {
            return null;
        }
        HiredWorkContext context = session.context();
        if (context.isInsideWorkArea(villager.blockPosition())) {
            return null;
        }
        BlockPos ladderEntry = MiningWorker.excavationEntryTarget(level, context);
        if (ladderEntry != null && !isAtExcavationEntry(villager, ladderEntry)) {
            return ladderEntry;
        }
        return bestExcavationSurfaceEntryTarget(level, villager, context);
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
        for (ReturnIntermediate candidate : candidates) {
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                return candidate.pos();
            }
        }
        return candidates.isEmpty() ? null : candidates.getFirst().pos();
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
        Path path = villager.getNavigation().createPath(target, 0);
        if (path == null || !path.canReach() || pathEntersLiquid(level, path) || !villager.getNavigation().moveTo(path, speed)) {
            return false;
        }
        setWorkAreaReturnWalkTarget(villager, target);
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
        for (ReturnIntermediate candidate : candidates) {
            if (attempts++ >= MAX_RETURN_INTERMEDIATE_PATH_ATTEMPTS) {
                break;
            }
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                return candidate.pos();
            }
        }
        return null;
    }

    private static void setWorkAreaReturnWalkTarget(Villager villager, BlockPos target) {
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), WORK_AREA_RETURN_WALK_SPEED, WORK_AREA_RETURN_CLOSE_ENOUGH));
    }

    private static boolean isReturnedToWorkArea(Villager villager, HiredWorkContext context, BlockPos excavationEntry) {
        if (excavationEntry != null) {
            return isAtExcavationEntry(villager, excavationEntry);
        }
        BlockPos pos = villager.blockPosition();
        return context.isInsideWorkArea(pos);
    }

    private static boolean isAtExcavationEntry(Villager villager, BlockPos excavationEntry) {
        return villager.blockPosition().distSqr(excavationEntry) <= 1.0D
                && Math.abs(villager.blockPosition().getY() - excavationEntry.getY()) <= 1;
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

    private static long gameTimeForRetry(ServerLevel level) {
        return level.getGameTime() + WORK_AREA_RETURN_PATH_RETRY_TICKS;
    }

    private static ReturnPath findWorkAreaReturnPath(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos clamped = new BlockPos(
                Mth.clamp(villager.blockPosition().getX(), context.workMin().getX(), context.workMax().getX()),
                Mth.clamp(villager.blockPosition().getY(), context.workMin().getY(), context.workMax().getY()),
                Mth.clamp(villager.blockPosition().getZ(), context.workMin().getZ(), context.workMax().getZ()));

        List<BlockPos> candidates = new ArrayList<>();
        if (isValidWorkAreaReturnCandidate(level, context, clamped)) {
            candidates.add(clamped);
        }
        if (isValidWorkAreaReturnCandidate(level, context, context.workCenter())) {
            candidates.add(context.workCenter());
        }
        for (BlockPos raw : BlockPos.betweenClosed(
                clamped.offset(-3, -2, -3),
                clamped.offset(3, 2, 3))) {
            BlockPos candidate = raw.immutable();
            if (isValidWorkAreaReturnCandidate(level, context, candidate)) {
                candidates.add(candidate);
            }
        }

        candidates.sort(Comparator.comparingDouble(pos -> returnTargetScore(villager, context, pos)));
        int evaluated = 0;
        for (BlockPos candidate : candidates) {
            if (evaluated >= MAX_RETURN_TARGETS_TO_PATHFIND) {
                break;
            }
            if (!isValidWorkAreaReturnCandidate(level, context, candidate)) {
                continue;
            }
            evaluated++;
            Path path = villager.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach() && !pathEntersLiquid(level, path)) {
                return new ReturnPath(candidate, path);
            }
        }
        return null;
    }

    private static ReturnPath findDryReturnPathTo(ServerLevel level, Villager villager, BlockPos target) {
        if (!isValidWorkAreaReturnTarget(level, target)) {
            return null;
        }
        Path path = villager.getNavigation().createPath(target, 0);
        return path != null && path.canReach() && !pathEntersLiquid(level, path) ? new ReturnPath(target, path) : null;
    }

    private static boolean isValidWorkAreaReturnTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return isReturnPassable(feet)
                && isReturnPassable(head)
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }

    private static boolean isValidWorkAreaReturnCandidate(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos candidate) {
        return context.isInsideWorkArea(candidate) && isValidWorkAreaReturnTarget(level, candidate);
    }

    private static boolean isReturnPassable(BlockState state) {
        return state.isAir() || state.is(Blocks.LADDER);
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
        String targetDescription = describeCurrentTarget(player, level, villager, snapshot);
        VillagerInteractionService.sendVillagerNotice(
                player,
                villager,
                workReportMessageKey(session.role()),
                Map.of(
                        "activity", describeWorkActivity(player, level, villager, session, snapshot, targetDescription),
                        "status_detail", describeStatusDetail(player, level, villager, session, targetDescription),
                        "work_area", describeWorkArea(player, level, villager, session.area()),
                        "efficiency", Integer.toString(session.efficiency()),
                        "target", targetDescription
                )
        );
    }

    public static List<String> debugLines(ServerLevel level, Villager villager) {
        HiredWorkSession session = HiredWorkSession.active(level, villager);
        HiredWorkerBrain.Snapshot snapshot = HiredWorkerBrain.snapshot(session.state(), level.getGameTime());
        int outputStacks = session.inventory().collectOutputItems().size();
        int outputItems = session.inventory().collectOutputItems().stream().mapToInt(output -> output.stack().getCount()).sum();
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        boolean canDepositNow = AssignedStorageService.canInteractWithAssignedStorage(villager, pos -> session.area().contains(pos));

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
        lines.add("Failure: reason=" + valueOrNone(snapshot.failureReason())
                + ", retryCooldown=" + snapshot.retryCooldownTicks()
                + ", lastScan=" + valueOrNone(snapshot.lastTargetScanResult()));
        lines.add("Work area: " + areaDescription(session.area())
                + ", assigned=" + session.area().explicitlyAssigned()
                + ", usable=" + session.area().usable()
                + ", radius=" + session.area().horizontalRadius()
                + ", verticalRadius=" + session.area().verticalRadius()
                + ", maxRadius=" + session.maxRadius());
        lines.add("Inventory/storage: outputStacks=" + outputStacks
                + ", outputItems=" + outputItems
                + ", autoDeposit=" + session.state().getBoolean("AutoDepositOutputs")
                + ", assignedStorage=" + hasAssignedStorage
                + ", canDepositNow=" + canDepositNow);
        lines.add("Status: " + valueOrNone(snapshot.status()));
        return lines;
    }

    public static void resetForNewContract(ServerLevel level, Villager villager) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        state.putBoolean("Enabled", true);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int radius = roleDefaultHorizontalRadius(role, maxWorkRadius(level, villager, role));
        int verticalRadius = roleDefaultVerticalRadius(role, maxWorkRadius(level, villager, role));
        BlockPos center = villager.blockPosition();
        HiredWorkArea.fromCenter(center, radius, verticalRadius, false).save(state);
        stopWork(level, villager, role, "interaction.work.status.no_work_area");
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
        HiredWorkSession session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            session.worker().stop(level, villager, session.context());
        } else {
            session.context().setProgressTicks(0);
        }
        session.state().remove("NextWorkGameTime");
        setStatus(session.state(), status, replacements);
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
        int max = maxWorkRadius(level, villager, role);
        HiredWorkArea area = workAreaWithinMax(state, villager, max);
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

    public static void setWorkCenterHere(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, player.blockPosition(), false);
    }

    public static void resetWorkCenterToVillager(ServerPlayer player, ServerLevel level, Villager villager) {
        assignCenteredWorkArea(player, level, villager, villager.blockPosition(), true);
    }

    public static void previewWorkArea(ServerPlayer player, ServerLevel level, Villager villager) {
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
                HiredWorkPlan.clear(session.context());
                session.context().setProgressTicks(0);
                setStatus(state, "interaction.work.status.mining_orders", Map.of("mode", next.label()));
            }
            case LOGGING -> {
                setStatus(state, "interaction.work.status.logging_filter", Map.of("filter", HiredLoggingFilters.selectionLabel(state)));
            }
            case FARMING -> {
                String current = state.getString("CropMode");
                state.putString("CropMode", "harvest_replant".equals(current) ? "harvest_only" : "harvest_replant");
                setStatus(state, "interaction.work.status.farming_mode", Map.of("mode", state.getString("CropMode")));
            }
            case BREWING -> setStatus(
                    state,
                    BrewingWorker.orderSummaryKey(level, state),
                    BrewingWorker.orderSummaryReplacements(level, state));
            case BUILDER -> {
                if (BuilderTaskState.hasTask(state)) {
                    setStatus(state, "interaction.work.builder.status_summary", BuilderTaskState.replacements(state));
                } else {
                    setStatus(state, "interaction.work.builder.choose_structure");
                }
            }
            case ANIMAL_HANDLING -> setStatus(state, "interaction.work.status.animal_breeding_target", Map.of("target", HiredAnimalBreedingTargets.selectionLabel(state)));
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
        setStatus(state, "interaction.work.status.animal_breeding_target", Map.of("target", HiredAnimalBreedingTargets.selectionLabel(state)));
        sendStatusNotice(player, villager, state);
    }

    public static boolean canManageWork(ServerLevel level, Villager villager, ServerPlayer player) {
        return HiredVillagerContractService.isHiredBy(level, villager, player);
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
        return workAreaWithinMax(state, villager, maxWorkRadius(level, villager, role));
    }

    public static boolean setWorkArea(ServerPlayer player, ServerLevel level, Villager villager, BlockPos first, BlockPos second) {
        CompoundTag state = state(villager);
        initializeDefaults(state, villager);
        HiredVillagerRole role = HiredVillagerContractService.activeRole(level, villager);
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea requested = HiredWorkArea.fromBounds(first, second, true);
        HiredWorkArea area = requested.clampedTo(maxRadius);
        boolean capped = requested.horizontalRadius() > area.horizontalRadius()
                || requested.verticalRadius() > area.verticalRadius();
        area.save(state);
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
        int maxRadius = maxWorkRadius(level, villager, role);
        HiredWorkArea current = workAreaWithinMax(state, villager, maxRadius);
        int horizontalRadius = current.explicitlyAssigned()
                ? current.horizontalRadius()
                : roleDefaultHorizontalRadius(role, maxRadius);
        int verticalRadius = current.explicitlyAssigned()
                ? current.verticalRadius()
                : roleDefaultVerticalRadius(role, maxRadius);
        HiredWorkArea.fromCenter(center, horizontalRadius, verticalRadius, true).clampedTo(maxRadius).save(state);
        setStatus(state, villagerCenter
                ? "interaction.work.status.center_reset_to_villager"
                : "interaction.work.status.center_set_here", Map.of("range", workArea(state, villager).rangeDescription()));
        sendStatusNotice(player, villager, state);
    }

    static CompoundTag state(Villager villager) {
        CompoundTag persistentData = villager.getPersistentData();
        if (!persistentData.contains(TAG, Tag.TAG_COMPOUND)) {
            persistentData.put(TAG, new CompoundTag());
        }
        return persistentData.getCompound(TAG);
    }

    static void initializeDefaults(CompoundTag state, Villager villager) {
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
            state.putBoolean("UseAssignedStorageForSupplies", false);
        }
        if (!state.contains("AutoDepositOutputs", Tag.TAG_BYTE)) {
            state.putBoolean("AutoDepositOutputs", true);
        }
        if (!state.contains("LoggingFilter", Tag.TAG_STRING)) {
            state.putString("LoggingFilter", "any");
        }
        if (!state.contains("CropMode", Tag.TAG_STRING)) {
            state.putString("CropMode", "harvest_replant");
        }
        if (!state.contains("NavigationTargetType", Tag.TAG_STRING)) {
            state.putString("NavigationTargetType", "interesting");
        }
        if (!state.contains(HiredCombatMode.STATE_TAG, Tag.TAG_STRING)) {
            state.putString(HiredCombatMode.STATE_TAG, HiredCombatMode.GUARD.serializedName());
        }
        if (!state.contains("Status", Tag.TAG_STRING)) {
            setStatus(state, "interaction.work.status.waiting_tick");
        }
        HiredWorkerBrain.initialize(state);
        if (!hadStoredArea) {
            int radius = Mth.clamp(state.getInt(HiredWorkArea.RADIUS_TAG), MIN_WORK_RADIUS, baseWorkRadiusCap());
            HiredWorkArea.fromCenter(villager.blockPosition(), radius, Math.min(radius, 8), state.getBoolean(HiredWorkArea.WORK_AREA_ASSIGNED_TAG)).save(state);
        }
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
        int base = baseWorkRadiusCap();
        int max = Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_MAX_RADIUS.get(), base, MAX_SKILLED_WORK_RADIUS);
        int score = Mth.clamp(HiredVillagerRoles.roleScore(level, villager, role), 0, 100);
        double progress = Math.max(0.0D, Math.min(1.0D, (score - SKILL_RADIUS_BASELINE) / 50.0D));
        return Mth.clamp(base + (int) Math.round((max - base) * progress), MIN_WORK_RADIUS, max);
    }

    private static int baseWorkRadiusCap() {
        return Mth.clamp(VillagerRetaliationConfig.HIRED_WORK_DEFAULT_RADIUS.get(), MIN_WORK_RADIUS, MAX_SKILLED_WORK_RADIUS);
    }

    private static int roleDefaultHorizontalRadius(HiredVillagerRole role, int maxRadius) {
        int preferred = switch (role) {
            case MINING -> 24;
            case LOGGING -> 32;
            case FARMING -> 24;
            case FISHING -> 24;
            case BUILDER -> 32;
            default -> 24;
        };
        return HiredWorkArea.clampRadius(preferred, MIN_WORK_RADIUS, maxRadius);
    }

    private static int roleDefaultVerticalRadius(HiredVillagerRole role, int maxRadius) {
        int preferred = switch (role) {
            case LOGGING -> 16;
            case FARMING -> 6;
            case FISHING -> 8;
            case BUILDER -> 12;
            default -> 8;
        };
        return HiredWorkArea.clampRadius(preferred, 1, maxRadius);
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
        state.putString("Status", status == null ? "" : status);
        state.remove(STATUS_REPLACEMENTS_TAG);
    }

    private static void setStatus(CompoundTag state, String status, Map<String, String> replacements) {
        state.putString("Status", status == null ? "" : status);
        if (replacements == null || replacements.isEmpty()) {
            state.remove(STATUS_REPLACEMENTS_TAG);
            return;
        }
        CompoundTag replacementTag = new CompoundTag();
        replacements.forEach((key, value) -> replacementTag.putString(key, value == null ? "" : value));
        state.put(STATUS_REPLACEMENTS_TAG, replacementTag);
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

    private static void sendStatusNotice(ServerPlayer player, Villager villager, CompoundTag state) {
        VillagerInteractionService.sendVillagerNotice(player, villager, state.getString("Status"), statusReplacements(state));
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String workReportMessageKey(HiredVillagerRole role) {
        return switch (role) {
            case COMBAT -> "interaction.work_report.combat";
            case MINING -> "interaction.work_report.mining";
            case LOGGING -> "interaction.work_report.logging";
            case FARMING -> "interaction.work_report.farming";
            case FISHING -> "interaction.work_report.fishing";
            case BREWING -> "interaction.work_report.brewing";
            case BUILDER -> "interaction.work_report.builder";
            case ANIMAL_HANDLING -> "interaction.work_report.animal_handling";
            case NITWIT -> "interaction.work_report.nitwit";
        };
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

    private static void handleDailyFood(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            HiredWorkSession session) {
        long day = level.getDayTime() / DAY_TICKS;
        if (!VillagerRetaliationConfig.HIRED_WORK_FOOD_ENABLED.get()
                || !session.worker().requiresFood()
                || session.state().getLong("LastFoodCheckDay") == day) {
            return;
        }
        session.state().putLong("LastFoodCheckDay", day);
        int needed = Math.max(0, roleFoodCost(session.role()));
        if (needed <= 0 || consumeFood(
                villager,
                session.inventory(),
                needed,
                session.state().getBoolean("UseAssignedStorageForSupplies"),
                session.area()) >= needed) {
            int starvationDays = Math.max(0, session.state().getInt("StarvationDays") - 1);
            session.state().putInt("StarvationDays", starvationDays);
            if (starvationDays == 0) {
                VillagerMoodService.setMood(level, villager, VillagerMood.CONTENT, 14, "hired_work_fed", hirer.getUUID(), hirer.getUUID(), VillagerMoodService.SHORT_DECAY_TICKS);
            }
            return;
        }

        int maxDays = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_MAX_STARVATION_PENALTY_DAYS.get());
        int starvationDays = Mth.clamp(session.state().getInt("StarvationDays") + 1, 0, maxDays);
        session.state().putInt("StarvationDays", starvationDays);
        VillagerMood mood = starvationDays >= Math.max(2, maxDays / 2) ? VillagerMood.ANGRY : VillagerMood.STRESSED;
        VillagerMoodService.setMood(
                level,
                villager,
                mood,
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_MOOD_INTENSITY.get(),
                "hired_work_no_food",
                hirer.getUUID(),
                hirer.getUUID(),
                VillagerMoodService.MEDIUM_DECAY_TICKS);
        VillagerReputationManager.addHiredWorkReputation(
                level,
                villager,
                hirer.getUUID(),
                VillagerRetaliationConfig.HIRED_WORK_NO_FOOD_REPUTATION_PENALTY.get());
        maybeNotify(level, villager, hirer, session.state(), "interaction.work.status.no_food", DAY_TICKS);
    }

    private static int consumeFood(
            Villager villager,
            HiredJobInventory inventory,
            int neededNutrition,
            boolean assignedSupplies,
            HiredWorkArea area) {
        int nutrition = 0;
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (stack.isEmpty() || food == null) {
                continue;
            }
            while (!stack.isEmpty() && nutrition < neededNutrition) {
                nutrition += Math.max(1, food.nutrition());
                inventory.consumeSupply(candidate -> candidate == stack, 1);
            }
            if (nutrition >= neededNutrition) {
                return nutrition;
            }
        }
        if (assignedSupplies && nutrition < neededNutrition) {
            nutrition += AssignedStorageService.consumeItems(
                    villager,
                    stack -> stack.get(DataComponents.FOOD) != null,
                    neededNutrition - nutrition,
                    area::contains);
        }
        return nutrition;
    }

    private static int roleFoodCost(HiredVillagerRole role) {
        int base = Math.max(0, VillagerRetaliationConfig.HIRED_WORK_BASE_FOOD_PER_DAY.get());
        int roleCost = switch (role) {
            case COMBAT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_COMBAT.get();
            case MINING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_MINING.get();
            case LOGGING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_LOGGING.get();
            case FARMING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_FARMING.get();
            case FISHING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_FARMING.get();
            case BREWING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BREWING.get();
            case BUILDER -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_BUILDER.get();
            case ANIMAL_HANDLING -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_ANIMAL_HANDLING.get();
            case NITWIT -> VillagerRetaliationConfig.HIRED_WORK_FOOD_COST_NITWIT.get();
        };
        return Math.max(0, Math.max(base, roleCost));
    }

    static int efficiencyPercent(ServerLevel level, Villager villager, HiredVillagerRole role, CompoundTag state, HiredJobInventory inventory) {
        int min = Math.max(1, VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get());
        int max = Math.max(min, VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        int efficiency = VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get();
        efficiency += (HiredVillagerRoles.roleScore(level, villager, role) - 50) / 2;
        if (HiredVillagerRoles.isSkillUnlocked(level, villager, role)) {
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
        efficiency -= 15 * Math.max(0, state.getInt("StarvationDays"));
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

    private static void maybeNotify(ServerLevel level, Villager villager, ServerPlayer hirer, CompoundTag state, String message, long cooldownTicks) {
        maybeNotify(level, villager, hirer, state, message, Map.of(), cooldownTicks);
    }

    private static void maybeNotify(
            ServerLevel level,
            Villager villager,
            ServerPlayer hirer,
            CompoundTag state,
            String message,
            Map<String, String> replacements,
            long cooldownTicks) {
        long now = level.getGameTime();
        if (now - state.getLong("LastNoticeTick") < cooldownTicks) {
            return;
        }
        state.putLong("LastNoticeTick", now);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, message, replacements);
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
        state.putBoolean(STORAGE_FULL_NOTICE_SHOWN_TAG, true);
        VillagerInteractionService.sendVillagerNotice(hirer, villager, STORAGE_FULL_NOTICE);
    }
}
