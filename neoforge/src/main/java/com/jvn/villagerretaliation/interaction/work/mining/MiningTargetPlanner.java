package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredTargetSearch;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Selects mining objectives and approaches; execution remains in {@link MiningWorker}. */
final class MiningTargetPlanner {
    static final int MAX_TARGETS = 20;
    private static final int MAX_EXCAVATION_SCAN_POSITIONS = 768;
    private static final HiredTargetSearch.Messages EXCAVATION_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_excavation_target",
            "planned_excavation_target",
            "excavation_scan_cooldown",
            "no_targets",
            "excavation_scan_partial_",
            "excavation_targets_found",
            MiningWorkerState.noTargetScanCooldownTicks());

    private final MiningWorker worker;

    MiningTargetPlanner(MiningWorker worker) {
        this.worker = worker;
    }

    HiredPathTarget resolve(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode) {
        return mode.excavatesArea()
                ? resolveExcavation(level, villager, context)
                : resolveOre(level, villager, context);
    }

    HiredPathTarget findAdjacent(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos origin,
            HiredMiningMode mode) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz).immutable();
                    if (isValidTarget(level, villager, context, pos, anchor, mode)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        return mode.excavatesArea()
                ? chooseExcavationTarget(level, villager, context, candidates)
                : chooseReachableOreTarget(level, villager, context, candidates);
    }

    HiredPathTarget findNearestExcavation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                pos -> isValidExcavationTarget(level, villager, context, pos));
        if (!scan.candidates().isEmpty()) {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_targets_found");
            return rebuildExcavationObjective(level, villager, context, scan.candidates());
        }
        if (!scan.completedFullPass()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_scan_in_progress");
            return null;
        }
        if (HiredMiningMode.fromState(context.state()).excavatesHorizontally()
                && !MiningHorizontalStairPlan.cleanup(context)
                && MiningHorizontalStairPlan.hasRemainingSupport(level, context)) {
            MiningHorizontalStairPlan.beginCleanup(context);
            context.state().remove(MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG);
            HiredWorkPlan.clear(context);
            HiredWorkerBrain.setLastTargetScanResult(context, "horizontal_stair_cleanup");
            return null;
        }
        context.state().putLong(
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        return null;
    }

    boolean isValidExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos) {
        HiredMiningMode mode = HiredMiningMode.fromState(context.state());
        boolean horizontal = mode.excavatesHorizontally();
        boolean neededShaftTarget = !horizontal && MiningExcavationSupport.isNeededLadderShaftTarget(level, context, pos);
        return context.isInsideWorkArea(pos)
                && (!horizontal || !MiningHorizontalStairPlan.isReservedSupport(context, villager, pos))
                && !this.worker.isTemporarilyAvoidedTargetForPlanner(level, villager, pos)
                && (neededShaftTarget || MiningBlockRules.isMineableExcavationBlock(level, context, pos))
                && (horizontal || MiningBlockRules.isCurrentExcavationLayer(level, context, pos))
                && (horizontal || MiningExcavationSupport.canMineCurrentLayerTarget(level, context, pos))
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, pos);
    }

    private HiredPathTarget resolveOre(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = this.worker.activeWorkTargetForPlanner(level, context, villager);
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (active != null
                && isValidOreTarget(level, villager, context, active.blockPos(), anchor)
                && MiningWorker.isSafeMiningWorkTarget(level, villager, active)) {
            MiningWorkerState.rememberMiningAnchor(level, context, active.blockPos());
            return active;
        }
        if (this.worker.storedWorkTargetForPlanner(context) != null) {
            this.worker.clearActiveTargetForPlanner(level, context, villager);
        }

        HiredPathTarget planned = plannedOreTarget(
                level,
                villager,
                context,
                pos -> isValidOreTarget(
                        level,
                        villager,
                        context,
                        pos,
                        MiningWorkerState.miningAnchor(level, context)));
        if (planned != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, planned.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return planned;
        }

        BlockPos lastMined = MiningWorkerState.lastMinedBlock(context);
        if (lastMined != null) {
            HiredPathTarget adjacent = findAdjacent(
                    level,
                    villager,
                    context,
                    lastMined,
                    HiredMiningMode.EXPOSED_ORES);
            if (adjacent != null) {
                MiningWorkerState.rememberMiningAnchor(level, context, adjacent.blockPos());
                MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                return adjacent;
            }
        }

        HiredPathTarget pocketTarget = findMineableInCurrentPocket(level, villager, context);
        if (pocketTarget != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, pocketTarget.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return pocketTarget;
        }
        MiningWorkerState.clearMiningAnchor(context);

        HiredPathTarget recentlyExposed = findRecentlyExposedMineableInRadius(level, villager, context);
        if (recentlyExposed != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, recentlyExposed.blockPos());
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return recentlyExposed;
        }
        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
        HiredPathTarget target = findNearestMineableInRadius(level, villager, context);
        if (target != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, target.blockPos());
        }
        return target;
    }

    private HiredPathTarget resolveExcavation(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        HiredPathTarget immediate = immediateExcavationWorkTarget(level, villager, context);
        if (immediate != null) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return immediate;
        }
        HiredPathTarget shaftExtension = ladderShaftExtensionTarget(level, villager, context);
        if (shaftExtension != null) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            HiredWorkPlan.replaceWithObjective(
                    context,
                    "ladder_shaft",
                    shaftExtension.blockPos(),
                    List.of(shaftExtension.blockPos()),
                    MAX_TARGETS);
            return shaftExtension;
        }
        return HiredTargetSearch.find(
                level,
                context,
                () -> {
                    HiredPathTarget active = activeExcavationWorkTarget(level, context, villager);
                    if (active == null && this.worker.storedWorkTargetForPlanner(context) != null) {
                        this.worker.clearActiveTargetForPlanner(level, context, villager);
                    }
                    return active;
                },
                target -> isValidExcavationTarget(level, villager, context, target.blockPos()),
                filter -> {
                    HiredPathTarget planned = plannedExcavationTarget(level, villager, context, filter);
                    if (planned != null) {
                        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    }
                    return planned;
                },
                pos -> isValidExcavationTarget(level, villager, context, pos),
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                candidates -> {
                    MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    return rebuildExcavationObjective(level, villager, context, candidates);
                },
                EXCAVATION_SEARCH_MESSAGES);
    }

    private HiredPathTarget ladderShaftExtensionTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        BlockPos target = MiningExcavationSupport.nextNeededLadderShaftTarget(level, context);
        if (target == null || !context.isLoaded(level, target)) {
            return null;
        }
        List<BlockPos> approaches = new ArrayList<>();
        approaches.add(target.above());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            approaches.add(target.relative(direction));
            approaches.add(target.relative(direction).above());
        }
        approaches.sort(Comparator.comparingDouble(pos -> villager.distanceToSqr(pos.getCenter())));
        for (BlockPos approach : approaches) {
            if (!this.worker.isValidExcavationWorkStance(level, context, approach)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)) {
                continue;
            }
            Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(
                    level,
                    villager,
                    Vec3.atBottomCenterOf(approach).add(0.0D, villager.getEyeHeight(), 0.0D),
                    target);
            HiredPathTarget pathTarget = new HiredPathTarget(
                    target.immutable(),
                    approach.immutable(),
                    hit == null ? target.getCenter() : hit);
            if (approach.equals(villager.blockPosition())
                    && !this.worker.canStartMining(level, villager, context, pathTarget, HiredMiningMode.EXCAVATE_AREA)) {
                continue;
            }
            return pathTarget;
        }
        return null;
    }

    private HiredPathTarget rebuildExcavationObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> ordered = MiningExcavationPlan.lineOrder(villager, context, candidates, MAX_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "excavation" : "excavation_block",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_TARGETS);
        return plannedExcavationTarget(
                level,
                villager,
                context,
                pos -> isValidExcavationTarget(level, villager, context, pos));
    }

    private HiredPathTarget activeExcavationWorkTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager) {
        HiredPathTarget target = this.worker.storedWorkTargetForPlanner(context);
        if (target == null || HiredPathMemory.isAvoided(level, villager, target.blockPos())) {
            return null;
        }
        boolean ladderRecovery = MiningExcavationSupport.shouldUseLadderFallback(context, villager, target);
        if (!context.isInsideWorkArea(target.blockPos())
                || !isValidExcavationTarget(level, villager, context, target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !this.worker.isValidExcavationWorkStance(level, context, target.approachPos())
                || MiningWorker.isUnsafeExcavationUnderfoot(
                        level,
                        context,
                        target.blockPos(),
                        target.approachPos())) {
            return null;
        }
        if (!ladderRecovery
                && (!MiningWorker.isUsableExcavationApproachForCurrentLayer(
                        level,
                        context,
                        villager.blockPosition(),
                        target.approachPos())
                || !this.worker.isValidExcavationApproach(level, context, villager.blockPosition()))) {
            return null;
        }
        if (this.worker.canMineFromCurrentPositionForPlanner(level, villager, target)) {
            return target;
        }
        if (!HiredMoveToBlockFaceJob.isValidApproachPosition(level, target.approachPos())) {
            return null;
        }
        Vec3 approachEye = new Vec3(
                target.approachPos().getX() + 0.5D,
                target.approachPos().getY() + villager.getEyeHeight(),
                target.approachPos().getZ() + 0.5D);
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(
                level,
                villager,
                approachEye,
                target.blockPos(),
                target.hitPos()) ? target : null;
    }

    private HiredPathTarget plannedExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator) {
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, MAX_TARGETS);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestExcavationWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget plannedOreTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Predicate<BlockPos> validator) {
        Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, MAX_TARGETS);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestOreWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget bestOreWorkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        return context.isInsideWorkArea(target)
                ? chooseReachableOreTarget(level, villager, context, List.of(target))
                : null;
    }

    private HiredPathTarget chooseReachableOreTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_TARGETS,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                context::isInsideWorkArea,
                ignored -> false,
                null,
                (target, approach) -> !MiningWorker.isUnsafeUnderfootMiningTarget(level, target, approach))
                .search()
                .target();
    }

    private HiredPathTarget bestExcavationWorkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        return context.isInsideWorkArea(target)
                ? chooseExcavationTarget(level, villager, context, List.of(target))
                : null;
    }

    private HiredPathTarget chooseExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Iterable<BlockPos> targets) {
        BlockPos pathOrigin = villager.blockPosition().immutable();
        boolean horizontal = HiredMiningMode.fromState(context.state()).excavatesHorizontally();
        Predicate<BlockPos> routeFilter = pos -> this.worker.isValidExcavationApproach(level, context, pos);
        Predicate<BlockPos> approachFilter = pos -> routeFilter.test(pos)
                && this.worker.isValidExcavationWorkStance(level, context, pos)
                && MiningWorker.isUsableExcavationApproachForCurrentLayer(level, context, pathOrigin, pos)
                && !level.getBlockState(pos).is(Blocks.LADDER);
        Predicate<BlockPos> pathFilter = pos -> routeFilter.test(pos) || pos.equals(pathOrigin);
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                targets,
                MAX_TARGETS,
                context::isInsideWorkArea,
                approachFilter,
                pathFilter,
                ignored -> false,
                (target, approach) -> canUseExcavationLadderApproach(
                        level,
                        villager,
                        context,
                        target,
                        approach),
                (target, approach) -> !MiningWorker.isUnsafeExcavationUnderfoot(
                        level,
                        context,
                        target,
                        approach),
                horizontal ? MiningWorker.HORIZONTAL_EXCAVATION_REACH : HiredMoveToBlockFaceJob.MAX_REACH)
                .search()
                .target();
    }

    private boolean canUseExcavationLadderApproach(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            BlockPos approach) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY >= context.workMax().getY()) {
            return false;
        }
        if (!context.isInsideWorkArea(target)
                || !context.isInsideWorkArea(approach)
                || !this.worker.isValidExcavationWorkStance(level, context, approach)
                || !MiningExcavationSupport.hasCompleteLadderRouteToLayer(level, context, currentLayerY)
                || MiningExcavationSupport.entryTarget(level, context) == null) {
            return false;
        }
        return MiningExcavationSupport.shouldUseLadderFallback(
                context,
                villager,
                new HiredPathTarget(target.immutable(), approach.immutable(), target.getCenter()));
    }

    private HiredPathTarget immediateExcavationWorkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        BlockPos current = villager.blockPosition().immutable();
        if (!this.worker.isValidExcavationWorkStance(level, context, current)) {
            return null;
        }
        HiredPathTarget stored = this.worker.storedWorkTargetForPlanner(context);
        if (stored != null) {
            HiredPathTarget recovered = immediateExcavationWorkTarget(
                    level,
                    villager,
                    context,
                    stored.blockPos(),
                    current);
            if (recovered != null) {
                return recovered;
            }
        }
        for (BlockPos rawPos : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
            HiredPathTarget recovered = immediateExcavationWorkTarget(
                    level,
                    villager,
                    context,
                    rawPos,
                    current);
            if (recovered != null) {
                return recovered;
            }
        }
        return null;
    }

    private HiredPathTarget immediateExcavationWorkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos targetPos,
            BlockPos approachPos) {
        BlockPos target = targetPos.immutable();
        if (!isReachableCurrentExcavationTarget(level, context, target)
                || MiningWorker.isUnsafeExcavationUnderfoot(level, context, target, approachPos)
                || approachPos.distSqr(target) > 4) {
            return null;
        }
        Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(level, villager, villager.getEyePosition(), target);
        if (hit == null) {
            return null;
        }
        HiredPathTarget pathTarget = new HiredPathTarget(target, approachPos, hit);
        if (!this.worker.canStartMining(
                level,
                villager,
                context,
                pathTarget,
                HiredMiningMode.EXCAVATE_AREA)) {
            return null;
        }
        HiredPathMemory.clearAvoided(villager, target);
        HiredWorkPlan.prioritize(context, target, MAX_TARGETS);
        return pathTarget;
    }

    private boolean isReachableCurrentExcavationTarget(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos pos) {
        boolean horizontal = HiredMiningMode.fromState(context.state()).excavatesHorizontally();
        boolean neededShaftTarget = !horizontal && MiningExcavationSupport.isNeededLadderShaftTarget(level, context, pos);
        return context.isInsideWorkArea(pos)
                && (neededShaftTarget || MiningBlockRules.isMineableExcavationBlock(level, context, pos))
                && (horizontal || MiningBlockRules.isCurrentExcavationLayer(level, context, pos))
                && (horizontal || MiningExcavationSupport.canMineCurrentLayerTarget(level, context, pos))
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, pos);
    }

    private HiredPathTarget findMineableInCurrentPocket(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (anchor == null) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(
                level,
                anchor,
                MiningWorkerState.pocketRadius(context),
                context.verticalRadius())) {
            if (isValidOreTarget(level, villager, context, pos, anchor)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, anchor);
    }

    private HiredPathTarget findRecentlyExposedMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.recentlyExposedOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidOreTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, center);
    }

    private HiredPathTarget findNearestMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidOreTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        HiredPathTarget target = rebuildVeinObjective(level, villager, context, candidates, center);
        if (target == null) {
            context.state().putLong(
                    MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                    level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        } else {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private HiredPathTarget rebuildVeinObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            BlockPos origin) {
        BlockPos routeOrigin = origin == null ? villager.blockPosition() : origin;
        List<BlockPos> vein = MiningVeinPlan.best(level, routeOrigin, candidates, MAX_TARGETS);
        if (!vein.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    vein.size() > 1 ? "vein" : "ore",
                    vein.getFirst(),
                    vein,
                    MAX_TARGETS);
            HiredPathTarget target = plannedOreTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidOreTarget(
                            level,
                            villager,
                            context,
                            pos,
                            MiningWorkerState.miningAnchor(level, context)));
            if (target != null) {
                return target;
            }
        }
        List<BlockPos> ordered = HiredWorkPlan.routeOrder(routeOrigin, candidates, MAX_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "ore_route" : "single_ore",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_TARGETS);
        return plannedOreTarget(
                level,
                villager,
                context,
                pos -> isValidOreTarget(
                        level,
                        villager,
                        context,
                        pos,
                        MiningWorkerState.miningAnchor(level, context)));
    }

    private boolean isValidTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor,
            HiredMiningMode mode) {
        return mode.excavatesArea()
                ? isValidExcavationTarget(level, villager, context, pos)
                : isValidOreTarget(level, villager, context, pos, anchor);
    }

    private boolean isValidOreTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor) {
        return context.isInsideWorkArea(pos)
                && isInsideMiningPocket(context, pos, anchor)
                && !this.worker.isTemporarilyAvoidedTargetForPlanner(level, villager, pos)
                && MiningBlockRules.isMineableOre(level, pos);
    }

    private static boolean isInsideMiningPocket(HiredWorkContext context, BlockPos pos, BlockPos anchor) {
        if (anchor == null) {
            return true;
        }
        int radius = MiningWorkerState.pocketRadius(context);
        return anchor.distSqr(pos) <= radius * radius;
    }
}
