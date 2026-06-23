package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.villager.VillagerContainerClimbGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class HiredMoveToBlockFaceJob extends HiredPathJob {
    public static final double MAX_REACH = 3.0D;
    public static final double MAX_REACH_SQR = MAX_REACH * MAX_REACH;
    private static final double BODY_REACH_BUFFER = 0.75D;
    private static final double BODY_REACH_SQR = (MAX_REACH + BODY_REACH_BUFFER) * (MAX_REACH + BODY_REACH_BUFFER);
    private static final int FACE_APPROACH_RADIUS = 1;
    private static final int MAX_APPROACHES_TO_PATHFIND = 32;
    private static final int MAX_REACHABLE_APPROACHES_TO_COMPARE = 5;
    private static final int MAX_TRANSPARENT_LOS_STEPS = 48;
    private static final double TRANSPARENT_LOS_STEP = 0.25D;
    private static final double FACE_INSET = 0.01D;
    private final Iterable<BlockPos> candidatePositions;
    private final Predicate<BlockPos> targetFilter;
    private final Predicate<BlockPos> approachFilter;
    private final Predicate<BlockPos> pathFilter;
    private final Predicate<BlockState> sightTransparent;
    private final BiPredicate<BlockPos, BlockPos> alternateApproachReachable;
    private final BiPredicate<BlockPos, BlockPos> targetApproachFilter;

    public HiredMoveToBlockFaceJob(ServerLevel level, Villager villager, Iterable<BlockPos> candidatePositions, int maxCandidates) {
        this(level, villager, candidatePositions, maxCandidates, ignored -> true);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> positionFilter) {
        this(level, villager, candidatePositions, maxCandidates, positionFilter, positionFilter, ignored -> false);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> positionFilter,
            Predicate<BlockState> sightTransparent) {
        this(level, villager, candidatePositions, maxCandidates, positionFilter, positionFilter, sightTransparent);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> targetFilter,
            Predicate<BlockPos> approachFilter,
            Predicate<BlockState> sightTransparent) {
        this(level, villager, candidatePositions, maxCandidates, targetFilter, approachFilter, approachFilter, sightTransparent);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> targetFilter,
            Predicate<BlockPos> approachFilter,
            Predicate<BlockPos> pathFilter,
            Predicate<BlockState> sightTransparent) {
        this(level, villager, candidatePositions, maxCandidates, targetFilter, approachFilter, pathFilter, sightTransparent, null);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> targetFilter,
            Predicate<BlockPos> approachFilter,
            Predicate<BlockPos> pathFilter,
            Predicate<BlockState> sightTransparent,
            BiPredicate<BlockPos, BlockPos> alternateApproachReachable) {
        this(level, villager, candidatePositions, maxCandidates, targetFilter, approachFilter, pathFilter, sightTransparent, alternateApproachReachable, null);
    }

    public HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> targetFilter,
            Predicate<BlockPos> approachFilter,
            Predicate<BlockPos> pathFilter,
            Predicate<BlockState> sightTransparent,
            BiPredicate<BlockPos, BlockPos> alternateApproachReachable,
            BiPredicate<BlockPos, BlockPos> targetApproachFilter) {
        super(level, villager, maxCandidates);
        this.candidatePositions = candidatePositions;
        this.targetFilter = targetFilter == null ? ignored -> true : targetFilter;
        this.approachFilter = approachFilter == null ? ignored -> true : approachFilter;
        this.pathFilter = pathFilter == null ? this.approachFilter : pathFilter;
        this.sightTransparent = sightTransparent == null ? ignored -> false : sightTransparent;
        this.alternateApproachReachable = alternateApproachReachable == null ? (target, approach) -> false : alternateApproachReachable;
        this.targetApproachFilter = targetApproachFilter == null ? (target, approach) -> true : targetApproachFilter;
    }

    @Override
    protected void collectCandidates(CandidateSink sink) {
        for (BlockPos candidate : this.candidatePositions) {
            if (!HiredPathMemory.isAvoided(this.level, this.villager, candidate)
                    && !HiredPathMemory.isReservedByOther(this.level, this.villager, candidate)) {
                sink.add(candidate);
            }
        }
    }

    @Override
    protected HiredPathResult evaluate(BlockPos target) {
        if (!this.targetFilter.test(target) || !isLoaded(this.level, target)) {
            return HiredPathResult.blocked();
        }
        HiredPathTarget current = targetFromCurrentPosition(target);
        if (current != null) {
            return new HiredPathResult(current, null, true, 0.0D);
        }
        BlockPos currentPos = this.villager.blockPosition().immutable();

        List<ApproachCandidate> approaches = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos exposedNeighbor = target.relative(direction);
            if (!isLoaded(this.level, exposedNeighbor)) {
                continue;
            }
            BlockState exposedState = this.level.getBlockState(exposedNeighbor);
            if (!isPassableForApproach(this.level, exposedNeighbor, exposedState)
                    && !this.sightTransparent.test(exposedState)) {
                continue;
            }
            Vec3 hit = hitPosition(target, direction);
            for (BlockPos rawCandidate : BlockPos.betweenClosed(
                    exposedNeighbor.offset(-FACE_APPROACH_RADIUS, -1, -FACE_APPROACH_RADIUS),
                    exposedNeighbor.offset(FACE_APPROACH_RADIUS, 1, FACE_APPROACH_RADIUS))) {
                BlockPos approach = rawCandidate.immutable();
                if (approach.equals(currentPos)
                        || !this.approachFilter.test(approach)
                        || !this.targetApproachFilter.test(target, approach)
                        || !isAdjacentApproach(approach, target)
                        || !isValidApproachPosition(this.level, approach)) {
                    continue;
                }
                Vec3 eye = new Vec3(
                        approach.getX() + 0.5D,
                        approach.getY() + this.villager.getEyeHeight(),
                        approach.getZ() + 0.5D);
                if (!hasLineOfSightToBlock(this.level, this.villager, eye, target, hit, this.sightTransparent)
                        || eye.distanceToSqr(hit) > MAX_REACH_SQR
                        || approach.getCenter().distanceToSqr(hit) > BODY_REACH_SQR) {
                    continue;
                }
                approaches.add(new ApproachCandidate(approach, hit, approachScore(approach, hit, target)));
            }
        }
        approaches.sort(Comparator.comparingDouble(ApproachCandidate::score));

        int evaluated = 0;
        int reachableApproaches = 0;
        HiredPathResult bestResult = null;
        for (ApproachCandidate approach : approaches) {
            if (evaluated >= MAX_APPROACHES_TO_PATHFIND) {
                break;
            }
            evaluated++;
            Path path = this.villager.getNavigation().createPath(approach.pos(), 0);
            if (path != null && path.canReach() && pathStaysInsideFilter(this.level, path, this.pathFilter)) {
                double score = approach.score() + pathTraversalCost(this.level, path);
                HiredPathResult result = new HiredPathResult(
                        new HiredPathTarget(target.immutable(), approach.pos(), approach.hitPos()),
                        path,
                        true,
                        score);
                if (bestResult == null || result.score() < bestResult.score()) {
                    bestResult = result;
                }
                reachableApproaches++;
                if (reachableApproaches >= MAX_REACHABLE_APPROACHES_TO_COMPARE) {
                    break;
                }
            } else if (this.alternateApproachReachable.test(target, approach.pos())) {
                double score = approach.score() + this.villager.distanceToSqr(approach.pos().getCenter());
                HiredPathResult result = new HiredPathResult(
                        new HiredPathTarget(target.immutable(), approach.pos(), approach.hitPos()),
                        null,
                        true,
                        score);
                if (bestResult == null || result.score() < bestResult.score()) {
                    bestResult = result;
                }
                reachableApproaches++;
                if (reachableApproaches >= MAX_REACHABLE_APPROACHES_TO_COMPARE) {
                    break;
                }
            }
        }
        return bestResult != null ? bestResult : HiredPathResult.blocked();
    }

    private HiredPathTarget targetFromCurrentPosition(BlockPos target) {
        BlockPos currentPos = this.villager.blockPosition().immutable();
        if (!isLoaded(this.level, target)
                || !this.approachFilter.test(currentPos)
                || !this.targetApproachFilter.test(target, currentPos)
                || !isAdjacentApproach(currentPos, target)) {
            return null;
        }
        Vec3 hit = visibleHitPosition(this.level, this.villager, this.villager.getEyePosition(), target, this.sightTransparent);
        if (hit == null) {
            return null;
        }
        HiredPathTarget pathTarget = new HiredPathTarget(target.immutable(), currentPos, hit);
        return canReachFromCurrentPosition(this.level, this.villager, pathTarget, this.sightTransparent) ? pathTarget : null;
    }

    private double approachScore(BlockPos approach, Vec3 hitPos, BlockPos target) {
        double distance = this.villager.distanceToSqr(approach.getCenter());
        int vertical = Math.abs(approach.getY() - this.villager.blockPosition().getY());
        double reachSlack = hitPos.distanceToSqr(approach.getCenter());
        double targetVertical = Math.abs(target.getY() - approach.getY());
        return distance
                + vertical * vertical * 3.0D
                + targetVertical * 2.0D
                + reachSlack * 0.25D
                + terrainCost(this.level, approach)
                + HiredPathMemory.recentCost(this.villager, target);
    }

    public static boolean canReachFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        return canReachFromCurrentPosition(level, villager, target, ignored -> false);
    }

    public static boolean canReachFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredPathTarget target,
            Predicate<BlockState> sightTransparent) {
        Vec3 currentHit = visibleHitPosition(level, villager, villager.getEyePosition(), target.blockPos(), sightTransparent);
        if (currentHit != null) {
            return villager.getEyePosition().distanceToSqr(currentHit) <= MAX_REACH_SQR
                    && villager.position().distanceToSqr(currentHit) <= BODY_REACH_SQR;
        }
        return isLoaded(level, target.blockPos())
                && isLoaded(level, target.approachPos())
                && isCloseEnough(villager, target)
                && hasLineOfSightToBlock(level, villager, villager.getEyePosition(), target.blockPos(), target.hitPos(), sightTransparent);
    }

    public static boolean isCloseEnough(Villager villager, HiredPathTarget target) {
        return villager.getEyePosition().distanceToSqr(target.hitPos()) <= MAX_REACH_SQR
                && villager.position().distanceToSqr(target.hitPos()) <= BODY_REACH_SQR;
    }

    public static boolean pathStaysInsideFilter(Path path, Predicate<BlockPos> positionFilter) {
        if (path == null) {
            return false;
        }
        Predicate<BlockPos> filter = positionFilter == null ? ignored -> true : positionFilter;
        for (int i = 0; i < path.getNodeCount(); i++) {
            if (!filter.test(path.getNode(i).asBlockPos())) {
                return false;
            }
        }
        return true;
    }

    public static boolean pathStaysInsideFilter(ServerLevel level, Path path, Predicate<BlockPos> positionFilter) {
        if (path == null) {
            return false;
        }
        Predicate<BlockPos> filter = positionFilter == null ? ignored -> true : positionFilter;
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            if (!filter.test(pos) || VillagerContainerClimbGuard.isForbiddenStandingFloor(level, pos.below())) {
                return false;
            }
        }
        return true;
    }

    public static double pathTraversalCost(ServerLevel level, Path path) {
        if (path == null) {
            return Double.POSITIVE_INFINITY;
        }
        double cost = path.getNodeCount() * 1.5D;
        int previousY = Integer.MIN_VALUE;
        for (int i = 0; i < path.getNodeCount(); i++) {
            BlockPos pos = path.getNode(i).asBlockPos();
            cost += terrainCost(level, pos) * 0.35D;
            if (previousY != Integer.MIN_VALUE) {
                int verticalStep = Math.abs(pos.getY() - previousY);
                if (verticalStep > 1) {
                    cost += verticalStep * verticalStep * 8.0D;
                }
            }
            previousY = pos.getY();
        }
        return cost;
    }

    public static Vec3 visibleHitPosition(ServerLevel level, Villager villager, Vec3 start, BlockPos target) {
        return visibleHitPosition(level, villager, start, target, ignored -> false);
    }

    public static Vec3 visibleHitPosition(
            ServerLevel level,
            Villager villager,
            Vec3 start,
            BlockPos target,
            Predicate<BlockState> sightTransparent) {
        if (!isLoaded(level, target)) {
            return null;
        }
        Predicate<BlockState> transparent = sightTransparent == null ? ignored -> false : sightTransparent;
        Vec3 bestHit = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!isLoaded(level, neighbor)) {
                continue;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (!isPassableForApproach(level, neighbor, neighborState)
                    && !transparent.test(neighborState)) {
                continue;
            }
            Vec3 hit = hitPosition(level, target, direction);
            if (!hasLineOfSightToBlock(level, villager, start, target, hit, transparent)) {
                continue;
            }
            double distance = start.distanceToSqr(hit);
            if (distance < bestDistance) {
                bestHit = hit;
                bestDistance = distance;
            }
        }
        return bestHit;
    }

    public static boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        return hasLineOfSightToBlock(level, villager, start, target, hitPos, ignored -> false);
    }

    public static boolean hasLineOfSightToBlock(
            ServerLevel level,
            Villager villager,
            Vec3 start,
            BlockPos target,
            Vec3 hitPos,
            Predicate<BlockState> sightTransparent) {
        if (!isLoaded(level, target)) {
            return false;
        }
        Predicate<BlockState> transparent = sightTransparent == null ? ignored -> false : sightTransparent;
        ClipContext.Block blockMode = level.getBlockState(target)
                .getCollisionShape(level, target, CollisionContext.empty())
                .isEmpty()
                ? ClipContext.Block.OUTLINE
                : ClipContext.Block.COLLIDER;
        Vec3 currentStart = start;
        Vec3 ray = hitPos.subtract(start);
        Vec3 step = ray.lengthSqr() <= 0.000001D ? Vec3.ZERO : ray.normalize().scale(TRANSPARENT_LOS_STEP);
        for (int i = 0; i < MAX_TRANSPARENT_LOS_STEPS; i++) {
            BlockHitResult hit = level.clip(new ClipContext(
                    currentStart,
                    hitPos,
                    blockMode,
                    ClipContext.Fluid.NONE,
                    villager));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return false;
            }
            if (hit.getBlockPos().equals(target)) {
                return true;
            }
            if (!isLoaded(level, hit.getBlockPos()) || !transparent.test(level.getBlockState(hit.getBlockPos()))) {
                return false;
            }
            currentStart = hit.getLocation().add(step);
            if (currentStart.distanceToSqr(hitPos) <= 0.0001D) {
                return false;
            }
        }
        return false;
    }

    public static boolean isValidApproachPosition(ServerLevel level, BlockPos pos) {
        if (!isLoaded(level, pos) || !isLoaded(level, pos.above()) || !isLoaded(level, pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        return isPassableForApproach(level, pos, feet)
                && isPassableForApproach(level, pos.above(), head)
                && floor.isSolid()
                && !VillagerContainerClimbGuard.isForbiddenStandingFloor(level, pos.below());
    }

    private static boolean isAdjacentApproach(BlockPos approach, BlockPos target) {
        return approach.distSqr(target) <= 4;
    }

    private static boolean isPassableForApproach(CollisionGetter level, BlockPos pos, BlockState state) {
        if (state.getFluidState().is(FluidTags.LAVA) || state.getBlock() instanceof BaseFireBlock) {
            return false;
        }
        return state.isAir()
                || state.liquid()
                || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    public static double terrainCost(ServerLevel level, BlockPos pos) {
        if (!isLoaded(level, pos) || !isLoaded(level, pos.above()) || !isLoaded(level, pos.below())) {
            return 256.0D;
        }
        double cost = 0.0D;
        cost += blockRiskCost(level.getBlockState(pos)) * 3.0D;
        cost += blockRiskCost(level.getBlockState(pos.above())) * 2.0D;
        cost += blockRiskCost(level.getBlockState(pos.below()));
        return cost;
    }

    private static double blockRiskCost(BlockState state) {
        if (state.isAir()) {
            return 0.0D;
        }
        double cost = state.liquid() ? 24.0D : 0.0D;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("fire")
                || path.contains("lava")
                || path.contains("magma")
                || path.contains("cactus")
                || path.contains("sweet_berry_bush")
                || path.contains("powder_snow")) {
            cost += 120.0D;
        }
        return cost;
    }

    private Vec3 hitPosition(BlockPos target, Direction direction) {
        return this.level.getBlockState(target)
                .getCollisionShape(this.level, target, CollisionContext.empty())
                .isEmpty()
                ? target.getCenter()
                : faceHitPosition(target, direction);
    }

    private static Vec3 hitPosition(ServerLevel level, BlockPos target, Direction direction) {
        return level.getBlockState(target)
                .getCollisionShape(level, target, CollisionContext.empty())
                .isEmpty()
                ? target.getCenter()
                : faceHitPosition(target, direction);
    }

    private static Vec3 faceHitPosition(BlockPos target, Direction direction) {
        return new Vec3(
                target.getX() + 0.5D + direction.getStepX() * (0.5D - FACE_INSET),
                target.getY() + 0.5D + direction.getStepY() * (0.5D - FACE_INSET),
                target.getZ() + 0.5D + direction.getStepZ() * (0.5D - FACE_INSET));
    }

    private static boolean isLoaded(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos);
    }

    private record ApproachCandidate(BlockPos pos, Vec3 hitPos, double score) {
    }
}
