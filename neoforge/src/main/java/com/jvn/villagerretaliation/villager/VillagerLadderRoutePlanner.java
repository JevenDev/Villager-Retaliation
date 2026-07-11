package com.jvn.villagerretaliation.villager;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/** Finds a complete, usable ladder route instead of selecting an isolated nearest rung. */
final class VillagerLadderRoutePlanner {
    private static final double DIRECT_ENTRY_HORIZONTAL_SQR = 2.25D;
    private static final double RECOVERY_DIRECT_ENTRY_HORIZONTAL_SQR = 16.0D;

    private VillagerLadderRoutePlanner() {
    }

    static Route findBest(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            int horizontalRadius,
            int verticalRadius) {
        if (level == null || villager == null || target == null) {
            return null;
        }
        BlockPos origin = villager.blockPosition();
        int verticalDelta = target.getY() - origin.getY();
        if (Math.abs(verticalDelta) <= 1) {
            return null;
        }

        Set<ColumnSegment> visited = new HashSet<>();
        Route best = null;
        for (BlockPos raw : BlockPos.betweenClosed(
                origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                origin.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            BlockPos rung = raw.immutable();
            if (!isLoadedLadder(level, rung)) {
                continue;
            }
            BlockPos bottom = bottomOfColumn(level, rung);
            BlockPos top = topOfColumn(level, rung);
            ColumnSegment segment = new ColumnSegment(bottom, top);
            if (!visited.add(segment)) {
                continue;
            }
            Route candidate = evaluate(level, villager, target, bottom, top, verticalDelta > 0);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    static boolean remainsUsable(ServerLevel level, Villager villager, BlockPos target, Route route) {
        if (route == null
                || target == null
                || !isLoadedLadder(level, route.entryRung())
                || !isWalkable(level, route.approach())) {
            return false;
        }
        if (route.directApproach()) {
            double dx = villager.getX() - (route.approach().getX() + 0.5D);
            double dz = villager.getZ() - (route.approach().getZ() + 0.5D);
            if (dx * dx + dz * dz > RECOVERY_DIRECT_ENTRY_HORIZONTAL_SQR
                    || !hasClearDirectApproach(level, villager, route.approach())) {
                return false;
            }
        }
        boolean climbingUp = target.getY() > villager.blockPosition().getY();
        if (climbingUp != route.climbingUp()) {
            return false;
        }
        BlockPos columnEnd = climbingUp
                ? topOfColumn(level, route.entryRung())
                : bottomOfColumn(level, route.entryRung());
        return safeDismount(level, villager, columnEnd, target, climbingUp) != null;
    }

    private static Route evaluate(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            BlockPos bottom,
            BlockPos top,
            boolean climbingUp) {
        BlockPos origin = villager.blockPosition();
        BlockPos columnEnd = climbingUp ? top : bottom;
        if (climbingUp ? columnEnd.getY() <= origin.getY() : columnEnd.getY() >= origin.getY()) {
            return null;
        }
        int currentVerticalMiss = Math.abs(target.getY() - origin.getY());
        int routeVerticalMiss = Math.abs(target.getY() - columnEnd.getY());
        if (routeVerticalMiss >= currentVerticalMiss) {
            return null;
        }
        BlockPos dismount = safeDismount(level, villager, columnEnd, target, climbingUp);
        if (dismount == null) {
            return null;
        }
        Entry entry = bestEntry(level, villager, bottom, top, climbingUp);
        if (entry == null) {
            return null;
        }
        double score = entry.score()
                + routeVerticalMiss * routeVerticalMiss * 4.0D
                + dismount.distSqr(target) * 0.5D;
        return new Route(
                entry.rung(),
                entry.approach(),
                bottom.immutable(),
                top.immutable(),
                dismount.immutable(),
                climbingUp,
                entry.direct(),
                score);
    }

    private static Entry bestEntry(
            ServerLevel level,
            Villager villager,
            BlockPos bottom,
            BlockPos top,
            boolean climbingUp) {
        BlockPos origin = villager.blockPosition();
        Entry best = null;
        for (int y = bottom.getY(); y <= top.getY(); y++) {
            if (climbingUp && y < origin.getY() - 1 || !climbingUp && y > origin.getY() + 1) {
                continue;
            }
            BlockPos rung = new BlockPos(bottom.getX(), y, bottom.getZ());
            if (!isLoadedLadder(level, rung)) {
                continue;
            }
            Entry candidate = bestApproachForRung(level, villager, rung);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        BlockPos entryEnd = climbingUp ? bottom : top;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = entryEnd.relative(direction);
            BlockPos outsideLanding = climbingUp ? side.below() : side.above();
            Entry candidate = reachableEntry(level, villager, entryEnd, outsideLanding);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    private static Entry bestApproachForRung(ServerLevel level, Villager villager, BlockPos rung) {
        Entry best = null;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos approach = rung.relative(direction);
            Entry candidate = reachableEntry(level, villager, rung, approach);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }
        return best;
    }

    private static Entry reachableEntry(
            ServerLevel level,
            Villager villager,
            BlockPos rung,
            BlockPos approach) {
        if (!isWalkable(level, approach)) {
            return null;
        }
        double distance = villager.distanceToSqr(approach.getCenter());
        Path path = villager.getNavigation().createPath(approach, 0);
        boolean usablePath = path != null && path.canReach() && !pathUsesEntryColumn(level, path, rung);
        double dx = villager.getX() - (approach.getX() + 0.5D);
        double dz = villager.getZ() - (approach.getZ() + 0.5D);
        boolean directlyReachable = dx * dx + dz * dz <= DIRECT_ENTRY_HORIZONTAL_SQR
                && hasClearDirectApproach(level, villager, approach);
        if (!usablePath && !directlyReachable) {
            return null;
        }
        double pathCost = usablePath ? path.getNodeCount() * 0.25D : 4.0D;
        double verticalCost = Math.abs(approach.getY() - villager.blockPosition().getY()) * 2.0D;
        return new Entry(
                rung.immutable(),
                approach.immutable(),
                !usablePath,
                distance + verticalCost + pathCost);
    }

    static boolean hasClearDirectApproach(ServerLevel level, Villager villager, BlockPos approach) {
        if (level == null
                || villager == null
                || approach == null
                || !isWalkable(level, approach)
                || approach.getY() != villager.blockPosition().getY()) {
            return false;
        }
        double dx = approach.getX() + 0.5D - villager.getX();
        double dz = approach.getZ() + 0.5D - villager.getZ();
        if (dx * dx + dz * dz > RECOVERY_DIRECT_ENTRY_HORIZONTAL_SQR) {
            return false;
        }
        return level.noCollision(
                villager,
                villager.getBoundingBox()
                        .expandTowards(dx, 0.0D, dz)
                        .deflate(0.05D));
    }

    private static boolean pathUsesEntryColumn(ServerLevel level, Path path, BlockPos rung) {
        for (int index = 0; index < path.getNodeCount(); index++) {
            BlockPos node = path.getNode(index).asBlockPos();
            if (node.getX() == rung.getX()
                    && node.getZ() == rung.getZ()
                    && isLoadedLadder(level, node)) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos safeDismount(
            ServerLevel level,
            Villager villager,
            BlockPos columnEnd,
            BlockPos target,
            boolean climbingUp) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = columnEnd.relative(direction);
            BlockPos[] candidates = climbingUp
                    ? new BlockPos[] { side, side.above() }
                    : new BlockPos[] { side, side.below() };
            for (BlockPos candidate : candidates) {
                if (!isWalkable(level, candidate)) {
                    continue;
                }
                double score = candidate.distSqr(target)
                        + villager.distanceToSqr(candidate.getCenter()) * 0.2D
                        + Math.abs(candidate.getY() - columnEnd.getY()) * 4.0D;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.immutable();
                }
            }
        }
        return best;
    }

    private static boolean isWalkable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.above()) || !level.hasChunkAt(pos.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());
        if (!feet.getFluidState().isEmpty()
                || !head.getFluidState().isEmpty()
                || !floor.getFluidState().isEmpty()
                || isDangerous(feet)
                || isDangerous(head)
                || isDangerous(floor)
                || VillagerContainerClimbGuard.isForbiddenStandingFloor(level, pos.below())) {
            return false;
        }
        return (feet.isAir() || feet.is(Blocks.LADDER) || feet.getCollisionShape(level, pos).isEmpty())
                && (head.isAir() || head.is(Blocks.LADDER) || head.getCollisionShape(level, pos.above()).isEmpty())
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }

    private static boolean isDangerous(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW)
                || state.getFluidState().is(FluidTags.LAVA);
    }

    private static boolean isLoadedLadder(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER);
    }

    private static BlockPos topOfColumn(ServerLevel level, BlockPos rung) {
        BlockPos top = rung;
        while (isLoadedLadder(level, top.above())) {
            top = top.above();
        }
        return top;
    }

    private static BlockPos bottomOfColumn(ServerLevel level, BlockPos rung) {
        BlockPos bottom = rung;
        while (isLoadedLadder(level, bottom.below())) {
            bottom = bottom.below();
        }
        return bottom;
    }

    record Route(
            BlockPos entryRung,
            BlockPos approach,
            BlockPos bottom,
            BlockPos top,
            BlockPos dismount,
            boolean climbingUp,
            boolean directApproach,
            double score) {
        Route withDirectApproach() {
            return new Route(
                    this.entryRung,
                    this.approach,
                    this.bottom,
                    this.top,
                    this.dismount,
                    this.climbingUp,
                    true,
                    this.score);
        }
    }

    private record Entry(BlockPos rung, BlockPos approach, boolean direct, double score) {
    }

    private record ColumnSegment(BlockPos bottom, BlockPos top) {
    }
}
