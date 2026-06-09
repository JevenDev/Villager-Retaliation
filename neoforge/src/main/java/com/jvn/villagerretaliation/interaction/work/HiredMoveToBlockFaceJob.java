package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.villager.VillagerContainerClimbGuard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

final class HiredMoveToBlockFaceJob extends HiredPathJob {
    static final double MAX_REACH = 3.0D;
    static final double MAX_REACH_SQR = MAX_REACH * MAX_REACH;
    private static final double BODY_REACH_BUFFER = 0.75D;
    private static final double BODY_REACH_SQR = (MAX_REACH + BODY_REACH_BUFFER) * (MAX_REACH + BODY_REACH_BUFFER);
    private static final int FACE_APPROACH_RADIUS = 1;
    private static final int MAX_APPROACHES_TO_PATHFIND = 32;
    private static final int MAX_REACHABLE_APPROACHES_TO_COMPARE = 5;
    private static final double FACE_INSET = 0.01D;
    private final Iterable<BlockPos> candidatePositions;
    private final Predicate<BlockPos> approachFilter;

    HiredMoveToBlockFaceJob(ServerLevel level, Villager villager, Iterable<BlockPos> candidatePositions, int maxCandidates) {
        this(level, villager, candidatePositions, maxCandidates, ignored -> true);
    }

    HiredMoveToBlockFaceJob(
            ServerLevel level,
            Villager villager,
            Iterable<BlockPos> candidatePositions,
            int maxCandidates,
            Predicate<BlockPos> approachFilter) {
        super(level, villager, maxCandidates);
        this.candidatePositions = candidatePositions;
        this.approachFilter = approachFilter == null ? ignored -> true : approachFilter;
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
        if (!this.approachFilter.test(target) || !isLoaded(this.level, target)) {
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
            if (!isPassableForApproach(this.level, exposedNeighbor, exposedState)) {
                continue;
            }
            Vec3 hit = faceHitPosition(target, direction);
            for (BlockPos rawCandidate : BlockPos.betweenClosed(
                    exposedNeighbor.offset(-FACE_APPROACH_RADIUS, -1, -FACE_APPROACH_RADIUS),
                    exposedNeighbor.offset(FACE_APPROACH_RADIUS, 1, FACE_APPROACH_RADIUS))) {
                BlockPos approach = rawCandidate.immutable();
                if (approach.equals(currentPos)
                        || !this.approachFilter.test(approach)
                        || !isValidApproachPosition(this.level, approach)) {
                    continue;
                }
                Vec3 eye = new Vec3(
                        approach.getX() + 0.5D,
                        approach.getY() + this.villager.getEyeHeight(),
                        approach.getZ() + 0.5D);
                if (!hasLineOfSightToBlock(this.level, this.villager, eye, target, hit)
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
            if (path != null && path.canReach() && pathStaysInsideFilter(this.level, path, this.approachFilter)) {
                double score = approach.score() + path.getNodeCount() * 1.5D;
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
            }
        }
        return bestResult != null ? bestResult : HiredPathResult.blocked();
    }

    private HiredPathTarget targetFromCurrentPosition(BlockPos target) {
        BlockPos currentPos = this.villager.blockPosition().immutable();
        if (!isLoaded(this.level, target) || !this.approachFilter.test(currentPos)) {
            return null;
        }
        Vec3 hit = visibleHitPosition(this.level, this.villager, this.villager.getEyePosition(), target);
        if (hit == null) {
            return null;
        }
        HiredPathTarget pathTarget = new HiredPathTarget(target.immutable(), currentPos, hit);
        return canReachFromCurrentPosition(this.level, this.villager, pathTarget) ? pathTarget : null;
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

    static boolean canReachFromCurrentPosition(ServerLevel level, Villager villager, HiredPathTarget target) {
        Vec3 currentHit = visibleHitPosition(level, villager, villager.getEyePosition(), target.blockPos());
        if (currentHit != null) {
            return villager.getEyePosition().distanceToSqr(currentHit) <= MAX_REACH_SQR
                    && villager.position().distanceToSqr(currentHit) <= BODY_REACH_SQR;
        }
        return isLoaded(level, target.blockPos())
                && isLoaded(level, target.approachPos())
                && isCloseEnough(villager, target)
                && hasLineOfSightToBlock(level, villager, villager.getEyePosition(), target.blockPos(), target.hitPos());
    }

    static boolean isCloseEnough(Villager villager, HiredPathTarget target) {
        return villager.getEyePosition().distanceToSqr(target.hitPos()) <= MAX_REACH_SQR
                && villager.position().distanceToSqr(target.hitPos()) <= BODY_REACH_SQR;
    }

    static boolean pathStaysInsideFilter(Path path, Predicate<BlockPos> positionFilter) {
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

    static boolean pathStaysInsideFilter(ServerLevel level, Path path, Predicate<BlockPos> positionFilter) {
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

    static Vec3 visibleHitPosition(ServerLevel level, Villager villager, Vec3 start, BlockPos target) {
        if (!isLoaded(level, target)) {
            return null;
        }
        Vec3 bestHit = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!isLoaded(level, neighbor)) {
                continue;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (!isPassableForApproach(level, neighbor, neighborState)) {
                continue;
            }
            Vec3 hit = faceHitPosition(target, direction);
            if (!hasLineOfSightToBlock(level, villager, start, target, hit)) {
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

    static boolean hasLineOfSightToBlock(ServerLevel level, Villager villager, Vec3 start, BlockPos target, Vec3 hitPos) {
        if (!isLoaded(level, target)) {
            return false;
        }
        ClipContext.Block blockMode = level.getBlockState(target)
                .getCollisionShape(level, target, CollisionContext.empty())
                .isEmpty()
                ? ClipContext.Block.OUTLINE
                : ClipContext.Block.COLLIDER;
        BlockHitResult hit = level.clip(new ClipContext(
                start,
                hitPos,
                blockMode,
                ClipContext.Fluid.NONE,
                villager));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    static boolean isValidApproachPosition(ServerLevel level, BlockPos pos) {
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

    private static boolean isPassableForApproach(CollisionGetter level, BlockPos pos, BlockState state) {
        return state.isAir()
                || state.liquid()
                || state.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty();
    }

    static double terrainCost(ServerLevel level, BlockPos pos) {
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
