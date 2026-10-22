package com.jvn.villagerretaliation.villager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Finds a complete, usable ladder route instead of selecting an isolated nearest rung. */
final class VillagerLadderRoutePlanner {
    private static final int MAX_PATH_ATTEMPTS_PER_TICK = 4;
    private static final Map<Villager, Search> SEARCHES = new WeakHashMap<>();
    private static final Map<Villager, PathBudget> PATH_BUDGETS = new WeakHashMap<>();
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
            SEARCHES.remove(villager);
            return null;
        }
        BlockPos origin = villager.blockPosition();
        int verticalDelta = target.getY() - origin.getY();
        if (Math.abs(verticalDelta) <= 1) {
            SEARCHES.remove(villager);
            return null;
        }

        Search search = SEARCHES.get(villager);
        if (search == null || !search.matches(level, villager, target, horizontalRadius, verticalRadius)) {
            search = new Search(level.dimension(), villager.position(), target.immutable(), horizontalRadius, verticalRadius,
                    collectCandidates(level, villager, target, horizontalRadius, verticalRadius));
            SEARCHES.put(villager, search);
        }
        if (search.best != null && (!remainsUsable(level, villager, target, search.best)
                || !VillagerNavigationSafety.isSafeLadderPosition(level, search.best.dismount()))) {
            search.best = null;
        }
        PathBudget budget = PATH_BUDGETS.computeIfAbsent(villager, ignored -> new PathBudget());
        while (!search.candidates.isEmpty()) {
            Candidate candidate = search.candidates.peekFirst();
            if (search.best != null && candidate.minimumScore() >= search.best.score()) {
                break;
            }
            if (!budget.consume(level.getGameTime())) {
                return null;
            }
            search.candidates.removeFirst();
            if (!isLoadedLadder(level, candidate.rung())
                    || !VillagerNavigationSafety.isSafeLadderPosition(level, candidate.dismount())) {
                continue;
            }
            Entry entry = reachableEntry(level, villager, candidate.rung(), candidate.approach());
            if (entry == null) {
                continue;
            }
            double score = entry.score() + candidate.routeCost();
            if (search.best == null || score < search.best.score()) {
                search.best = new Route(entry.rung(), entry.approach(), candidate.bottom(), candidate.top(),
                        candidate.dismount(), candidate.climbingUp(), entry.direct(), score);
            }
        }
        SEARCHES.remove(villager);
        return search.best;
    }

    static boolean isSearching(Villager villager) {
        return SEARCHES.containsKey(villager);
    }

    static void clearRuntimeState() {
        SEARCHES.clear();
        PATH_BUDGETS.clear();
    }

    static void clearRuntimeState(Villager villager) {
        SEARCHES.remove(villager);
        PATH_BUDGETS.remove(villager);
    }

    private static List<Candidate> collectCandidates(
            ServerLevel level, Villager villager, BlockPos target, int horizontalRadius, int verticalRadius) {
        BlockPos origin = villager.blockPosition();
        List<Candidate> candidates = new ArrayList<>();
        for (int x = origin.getX() - horizontalRadius; x <= origin.getX() + horizontalRadius; x++) {
            for (int z = origin.getZ() - horizontalRadius; z <= origin.getZ() + horizontalRadius; z++) {
                for (int y = origin.getY() - verticalRadius; y <= origin.getY() + verticalRadius; y++) {
                    BlockPos rung = new BlockPos(x, y, z);
                    if (!isLoadedLadder(level, rung)) {
                        continue;
                    }
                    BlockPos bottom = bottomOfColumn(level, rung);
                    BlockPos top = topOfColumn(level, rung);
                    collectColumnEntries(level, villager, target, bottom, top, target.getY() > origin.getY(), candidates);
                    y = top.getY();
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::minimumScore));
        return candidates;
    }

    static boolean remainsUsable(ServerLevel level, Villager villager, BlockPos target, Route route) {
        if (route == null
                || target == null
                || !isLoadedLadder(level, route.entryRung())
                || !VillagerNavigationSafety.isSafeLadderPosition(level, route.approach())) {
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

    private static void collectColumnEntries(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            BlockPos bottom,
            BlockPos top,
            boolean climbingUp,
            List<Candidate> candidates) {
        BlockPos origin = villager.blockPosition();
        BlockPos columnEnd = climbingUp ? top : bottom;
        if (climbingUp ? columnEnd.getY() <= origin.getY() : columnEnd.getY() >= origin.getY()) {
            return;
        }
        int currentVerticalMiss = Math.abs(target.getY() - origin.getY());
        int routeVerticalMiss = Math.abs(target.getY() - columnEnd.getY());
        if (routeVerticalMiss >= currentVerticalMiss) {
            return;
        }
        BlockPos dismount = safeDismount(level, villager, columnEnd, target, climbingUp);
        if (dismount == null) {
            return;
        }
        double routeCost = routeVerticalMiss * routeVerticalMiss * 4.0D + dismount.distSqr(target) * 0.5D;
        for (int y = bottom.getY(); y <= top.getY(); y++) {
            if (climbingUp && y < origin.getY() - 1 || !climbingUp && y > origin.getY() + 1) {
                continue;
            }
            BlockPos rung = new BlockPos(bottom.getX(), y, bottom.getZ());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                addCandidate(level, villager, rung, rung.relative(direction), bottom, top, dismount, climbingUp, routeCost, candidates);
            }
        }
        BlockPos entryEnd = climbingUp ? bottom : top;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = entryEnd.relative(direction);
            BlockPos outsideLanding = climbingUp ? side.below() : side.above();
            addCandidate(level, villager, entryEnd, outsideLanding, bottom, top, dismount, climbingUp, routeCost, candidates);
        }
    }

    private static void addCandidate(
            ServerLevel level, Villager villager, BlockPos rung, BlockPos approach,
            BlockPos bottom, BlockPos top, BlockPos dismount, boolean climbingUp,
            double routeCost, List<Candidate> candidates) {
        if (!VillagerNavigationSafety.isSafeLadderPosition(level, approach)) {
            return;
        }
        double minimumScore = villager.distanceToSqr(approach.getCenter())
                + Math.abs(approach.getY() - villager.blockPosition().getY()) * 2.0D + routeCost;
        candidates.add(new Candidate(rung, approach, bottom, top, dismount, climbingUp, routeCost, minimumScore));
    }

    private static Entry reachableEntry(
            ServerLevel level,
            Villager villager,
            BlockPos rung,
            BlockPos approach) {
        if (!VillagerNavigationSafety.isSafeLadderPosition(level, approach)) {
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
                || !VillagerNavigationSafety.isSafeLadderPosition(level, approach)) {
            return false;
        }
        int verticalDelta = approach.getY() - villager.blockPosition().getY();
        if (Math.abs(verticalDelta) > 1) {
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
                        .move(0.0D, verticalDelta, 0.0D)
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
                if (!VillagerNavigationSafety.isSafeLadderPosition(level, candidate)) {
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

    private record Candidate(
            BlockPos rung, BlockPos approach, BlockPos bottom, BlockPos top, BlockPos dismount,
            boolean climbingUp, double routeCost, double minimumScore) {
    }

    private static final class Search {
        private final ResourceKey<Level> dimension;
        private final Vec3 origin;
        private final BlockPos target;
        private final int horizontalRadius;
        private final int verticalRadius;
        private final ArrayDeque<Candidate> candidates;
        private Route best;

        private Search(ResourceKey<Level> dimension, Vec3 origin, BlockPos target,
                int horizontalRadius, int verticalRadius, List<Candidate> candidates) {
            this.dimension = dimension;
            this.origin = origin;
            this.target = target;
            this.horizontalRadius = horizontalRadius;
            this.verticalRadius = verticalRadius;
            this.candidates = new ArrayDeque<>(candidates);
        }

        private boolean matches(ServerLevel level, Villager villager, BlockPos target, int horizontalRadius, int verticalRadius) {
            return dimension.equals(level.dimension()) && this.target.equals(target)
                    && origin.distanceToSqr(villager.position()) < 0.0001D
                    && this.horizontalRadius == horizontalRadius && this.verticalRadius == verticalRadius;
        }
    }

    private static final class PathBudget {
        private long tick = Long.MIN_VALUE;
        private int attempts;

        private boolean consume(long now) {
            if (tick != now) {
                tick = now;
                attempts = 0;
            }
            if (attempts >= MAX_PATH_ATTEMPTS_PER_TICK) {
                return false;
            }
            attempts++;
            return true;
        }
    }
}
