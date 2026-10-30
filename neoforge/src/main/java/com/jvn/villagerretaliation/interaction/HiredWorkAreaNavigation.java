package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

final class HiredWorkAreaNavigation {
    private static final String NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG = "NextWorkAreaReturnPathGameTime";
    private static final String WORK_AREA_RETURN_TARGET_TAG = "WorkAreaReturnTarget";
    private static final String WORK_AREA_RETURN_DISTANCE_SQR_TAG = "WorkAreaReturnDistanceSqr";
    private static final String WORK_AREA_RETURN_LAST_CHECK_GAME_TIME_TAG = "WorkAreaReturnLastCheckGameTime";
    private static final String WORK_AREA_RETURN_STUCK_CHECKS_TAG = "WorkAreaReturnStuckChecks";
    private static final int WORK_AREA_RETURN_PATH_RETRY_TICKS = 20;
    private static final int MAX_RETURN_TARGETS_TO_PATHFIND = 32;
    private static final int RETURN_INTERMEDIATE_SEARCH_RADIUS = 10;
    private static final int RETURN_INTERMEDIATE_VERTICAL_RADIUS = 3;
    private static final int MAX_RETURN_INTERMEDIATE_PATH_ATTEMPTS = 24;
    static final float WORK_AREA_RETURN_WALK_SPEED = 0.5F;
    private static final int WORK_AREA_RETURN_CLOSE_ENOUGH = 0;
    private static final int WORK_AREA_RETURN_INTERMEDIATE_CLOSE_ENOUGH = 2;
    private static final int EXCAVATION_RETURN_CLOSE_ENOUGH = 2;
    private static final int WORK_AREA_RETURN_STUCK_CHECK_TICKS = 20;
    private static final int WORK_AREA_RETURN_STUCK_LIMIT = 3;
    private static final double WORK_AREA_RETURN_MIN_PROGRESS_SQR = 0.20D;
    private static final int WORK_AREA_TETHER_HORIZONTAL_PADDING = 2;
    private static final int WORK_AREA_TETHER_VERTICAL_PADDING = 2;

    private HiredWorkAreaNavigation() {
    }

    static boolean shouldReturnToWorkArea(HiredWorkSession session) {
        if (HiredVillagerRoleSettings.supportsRoutes(session.role()) && session.route().usableForNavigation()) {
            return false;
        }
        return session.role() != HiredVillagerRole.BUILDER || !BuilderTaskState.hasTask(session.state());
    }

    static boolean returnVillagerToWorkArea(
            ServerLevel level,
            Villager villager,
            HiredWorkSession session,
            boolean allowPathCreation) {
        HiredWorkContext context = session.context();
        CompoundTag state = session.state();
        if (!context.hasWorkArea()) {
            state.remove(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG);
            MiningWorker.invalidateExcavationReturnTarget(villager);
            return false;
        }
        if (HiredTemporaryWorkService.active(state, com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask.Kind.PREPARE_FARM)
                && com.jvn.villagerretaliation.settlement.SettlementFarmWater.collecting(level, villager,
                        state.getCompound(com.jvn.villagerretaliation.settlement.SettlementFarmWork.TAG))) {
            clearWorkAreaReturnState(state);
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
            MiningWorker.invalidateExcavationReturnTarget(villager);
            return false;
        }
        if (brain.taskState().keepsStorageTarget() && brain.storageTargetPos() != null) {
            clearWorkAreaReturnState(state);
            MiningWorker.invalidateExcavationReturnTarget(villager);
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
                    MiningWorker.invalidateExcavationReturnTarget(villager);
                    state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                    HiredWorkStateStore.setStatus(state, "interaction.work.status.return_path_lost");
                    return true;
                }
                setWorkAreaReturnWalkTarget(villager, navigationTarget);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, navigationTarget);
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_bounds");
                return true;
            }
        }

        if (!allowPathCreation) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, null);
            HiredWorkStateStore.setStatus(state, "interaction.work.status.waiting_tick");
            return true;
        }

        long gameTime = level.getGameTime();
        if (gameTime < state.getLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, null);
            HiredWorkStateStore.setStatus(state, "interaction.work.status.return_retry_wait");
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
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_excavation_ladder");
                return true;
            }
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, excavationEntry, WORK_AREA_RETURN_WALK_SPEED)
                    || VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, excavationEntry, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, excavationEntry);
                HiredWorkStateStore.setStatus(state, "interaction.work.status.moving_excavation_ladder");
                return true;
            }
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, excavationEntry);
            MiningWorker.invalidateExcavationReturnTarget(villager);
            state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
            HiredWorkStateStore.setStatus(state, "interaction.work.status.need_excavation_ladder_path");
            return true;
        }

        ReturnPath returnPath = findWorkAreaReturnPath(level, villager, session);
        if (returnPath == null) {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, context.workCenter(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_by_ladder");
            } else if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, context.workCenter(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_to_safe_ground");
            } else if (moveTowardWorkAreaIntermediate(level, villager, context, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_from_far_bounds");
            } else {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                HiredWorkStateStore.setStatus(state, "interaction.work.status.return_path_missing");
            }
            return true;
        }

        if (shouldPreferLadderReturn(villager, returnPath.target())
                && VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_by_ladder");
            return true;
        }
        if (shouldPreferLadderReturn(villager, returnPath.target())
                && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
            HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_to_safe_ground");
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
            HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_bounds");
        } else {
            if (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_by_ladder");
            } else if (VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, returnPath.target(), WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnPath.target());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_to_safe_ground");
            } else if (moveTowardWorkAreaIntermediate(level, villager, context, WORK_AREA_RETURN_WALK_SPEED)) {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
                HiredWorkStateStore.setStatus(state, "interaction.work.status.returning_from_far_bounds");
            } else {
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
                state.putLong(NEXT_WORK_AREA_RETURN_PATH_GAME_TIME_TAG, gameTimeForRetry(level, villager));
                HiredWorkStateStore.setStatus(state, "interaction.work.status.return_could_not_move");
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

}
