package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

/** Owns the persisted excavation shaft and all geometry derived from it. */
final class MiningExcavationShaft {
    private static final String X_TAG = "ExcavationLadderX";
    private static final String Z_TAG = "ExcavationLadderZ";
    private static final String FACING_TAG = "ExcavationLadderFacing";
    private static final int SURFACE_ENTRY_SEARCH_RADIUS = 2;
    private static final long SURFACE_ENTRY_CACHE_TICKS = 20L * 10L;
    private static final Map<Villager, CachedSurfaceEntry> SURFACE_ENTRY_TARGETS = new IdentityHashMap<>();

    private MiningExcavationShaft() {
    }

    static BlockPos entryTarget(ServerLevel level, HiredWorkContext context) {
        if (level == null || context == null || !context.hasWorkArea()) {
            return null;
        }
        Shaft stored = stored(context);
        BlockPos entry = stored == null ? null : highestExistingLadder(level, context, stored);
        if (entry != null) {
            return entry;
        }
        for (Shaft candidate : candidates(context)) {
            entry = highestExistingLadder(level, context, candidate);
            if (entry != null) {
                store(context, candidate);
                return entry;
            }
        }
        return null;
    }

    static BlockPos returnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos entry = entryTarget(level, context);
        if (entry != null) {
            if (isAtEntry(villager, entry) || isAtSurfaceEntry(villager, entry)) {
                invalidateSurfaceEntryTarget(villager);
                return entry;
            }
            BlockPos surfaceEntry = bestSurfaceEntryTarget(level, villager, context, entry);
            return surfaceEntry == null ? entry : surfaceEntry;
        }
        BlockPos surface = bestSurfaceEntryTarget(level, villager, context, null);
        if (surface != null) {
            return surface;
        }
        BlockPos fallback = new BlockPos(
                context.workCenter().getX(),
                context.workMax().getY() + 1,
                context.workCenter().getZ());
        return level.hasChunkAt(fallback) ? fallback : context.workCenter();
    }

    static void invalidateSurfaceEntryTarget(Villager villager) {
        if (villager != null) {
            SURFACE_ENTRY_TARGETS.remove(villager);
        }
    }

    static void clearRuntimeState() {
        SURFACE_ENTRY_TARGETS.clear();
    }

    static BlockPos currentLayerDescentTarget(ServerLevel level, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY >= context.workMax().getY()) {
            return null;
        }
        Shaft shaft = stored(context);
        if (shaft == null) {
            entryTarget(level, context);
            shaft = stored(context);
        }
        int targetY = Math.min(context.workMax().getY(), currentLayerY + 1);
        if (shaft == null || !hasCompleteRouteToLayer(level, context, targetY)) {
            return null;
        }
        for (int y = targetY; y >= currentLayerY; y--) {
            BlockPos target = shaft.at(y);
            if (level.hasChunkAt(target) && level.getBlockState(target).is(Blocks.LADDER)) {
                return target.immutable();
            }
        }
        return null;
    }

    static boolean shouldUseFallback(HiredWorkContext context, Villager villager, HiredPathTarget target) {
        if (target == null) {
            return false;
        }
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            return true;
        }
        int verticalDelta = target.approachPos().getY() - villager.blockPosition().getY();
        if (villager.blockPosition().getY() >= context.workMax().getY() - 1 && verticalDelta >= 0) {
            return false;
        }
        return Math.abs(verticalDelta) > 1;
    }

    static boolean hasCompleteRouteToLayer(ServerLevel level, HiredWorkContext context, int layerY) {
        Shaft shaft = stored(context);
        Iterable<Shaft> possibleShafts = shaft == null ? candidates(context) : List.of(shaft);
        for (Shaft candidate : possibleShafts) {
            int topY = topY(level, context, candidate);
            if (layerY >= topY) {
                store(context, candidate);
                return true;
            }
            boolean complete = true;
            for (int y = topY; y >= layerY; y--) {
                BlockPos pos = candidate.at(y);
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.LADDER)) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                store(context, candidate);
                return true;
            }
        }
        return false;
    }

    static boolean canMineCurrentLayerTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadder(level, context, currentLayerY)
                || hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return true;
        }
        Shaft shaft = select(level, context, currentLayerY);
        if (shaft == null) {
            return false;
        }
        if (shaft.contains(target)) {
            return true;
        }
        BlockPos shaftPos = shaft.at(currentLayerY);
        if (currentLayerY <= context.workMin().getY()) {
            return false;
        }
        if ((MiningBlockRules.isMineableExcavationBlock(level, context, shaftPos)
                || isMineableShaftBlock(level, shaftPos))
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, shaftPos)) {
            return false;
        }
        return horizontalDistance(target, shaftPos) == 1;
    }

    static boolean isNeededTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadder(level, context, currentLayerY)
                || hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return false;
        }
        Shaft shaft = select(level, context, currentLayerY);
        return shaft != null
                && shaft.contains(target)
                && isMineableShaftBlock(level, target)
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, target);
    }

    static BlockPos nextNeededTarget(ServerLevel level, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || !requiresLadder(level, context, currentLayerY)
                || hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return null;
        }
        Shaft shaft = select(level, context, currentLayerY);
        if (shaft == null) {
            return null;
        }
        BlockPos target = shaft.at(currentLayerY);
        return isNeededTarget(level, context, target) ? target : null;
    }

    static boolean needsOutputReserve(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadder(level, context, currentLayerY)
                || hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return false;
        }
        Shaft shaft = select(level, context, currentLayerY);
        return shaft != null && !shaft.contains(target);
    }

    static int supportFloorY(ServerLevel level, HiredWorkContext context, int currentLayerY) {
        int openY = Math.max(deepestOpenY(level, context), currentLayerY);
        return Math.clamp(openY, context.workMin().getY(), context.workMax().getY());
    }

    static Shaft select(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        return select(level, null, context, lowestOpenY, false);
    }

    static Shaft select(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        return select(level, villager, context, lowestOpenY, true);
    }

    static Shaft stored(HiredWorkContext context) {
        boolean hasSelection = context.state().contains(X_TAG)
                || context.state().contains(Z_TAG)
                || context.state().contains(FACING_TAG);
        if (!hasSelection) {
            return null;
        }
        if (!context.state().contains(X_TAG, Tag.TAG_INT)
                || !context.state().contains(Z_TAG, Tag.TAG_INT)
                || !context.state().contains(FACING_TAG, Tag.TAG_STRING)) {
            clear(context);
            return null;
        }
        int x = context.state().getInt(X_TAG);
        int z = context.state().getInt(Z_TAG);
        Direction facing = Direction.byName(context.state().getString(FACING_TAG));
        if (facing == null || facing.getAxis().isVertical()) {
            clear(context);
            return null;
        }
        Shaft shaft = new Shaft(x, z, facing);
        if (!context.isInsideWorkArea(shaft.at(context.workMax().getY())) || !isCandidate(context, shaft)) {
            clear(context);
            return null;
        }
        return shaft;
    }

    static void clear(HiredWorkContext context) {
        context.state().remove(X_TAG);
        context.state().remove(Z_TAG);
        context.state().remove(FACING_TAG);
    }

    static int topY(ServerLevel level, HiredWorkContext context) {
        Shaft stored = stored(context);
        if (stored != null) {
            return topY(level, context, stored);
        }
        int topY = context.workMax().getY();
        boolean foundSurface = false;
        for (Shaft candidate : candidates(context)) {
            int candidateTopY = topY(level, context, candidate);
            topY = Math.min(topY, candidateTopY);
            foundSurface = foundSurface || candidateTopY < context.workMax().getY();
        }
        return foundSurface ? topY : context.workMax().getY();
    }

    static int topY(ServerLevel level, HiredWorkContext context, Shaft shaft) {
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
            if (hasDismount(level, context, shaft, y)) {
                return y;
            }
        }
        return context.workMax().getY();
    }

    static boolean requiresLadder(ServerLevel level, HiredWorkContext context, int layerY) {
        return layerY < topY(level, context);
    }

    static boolean isShaftColumn(HiredWorkContext context, BlockPos pos) {
        Shaft shaft = stored(context);
        return shaft != null && shaft.contains(pos);
    }

    static List<Shaft> candidates(HiredWorkContext context) {
        LinkedHashSet<Shaft> candidates = new LinkedHashSet<>(cornerCandidates(context));
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        int centerX = context.workCenter().getX();
        int centerZ = context.workCenter().getZ();
        addCandidate(candidates, minX, centerZ, Direction.EAST);
        addCandidate(candidates, maxX, centerZ, Direction.WEST);
        addCandidate(candidates, centerX, minZ, Direction.SOUTH);
        addCandidate(candidates, centerX, maxZ, Direction.NORTH);
        return new ArrayList<>(candidates);
    }

    private static Shaft select(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
        boolean storeSelected) {
        Shaft stored = stored(context);
        if (stored != null && hasViableRoute(level, context, stored, lowestOpenY, true)) {
            return stored;
        }
        if (stored != null) {
            clear(context);
        }
        for (Shaft candidate : candidatesByTop(level, villager, context)) {
            if (!hasViableRoute(level, context, candidate, lowestOpenY, false)) {
                continue;
            }
            if (storeSelected) {
                store(context, candidate);
            }
            return candidate;
        }
        return null;
    }

    private static boolean hasViableRoute(
            ServerLevel level,
            HiredWorkContext context,
            Shaft shaft,
            int lowestOpenY,
            boolean preserveOnUnloadedChunk) {
        int routeFloorY = Math.clamp(lowestOpenY, context.workMin().getY(), context.workMax().getY());
        int topY = topY(level, context, shaft);
        for (int y = topY; y >= routeFloorY; y--) {
            BlockPos pos = shaft.at(y);
            if (!level.hasChunkAt(pos)) {
                if (preserveOnUnloadedChunk) {
                    continue;
                }
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LADDER)
                    || MiningSupportManager.canEventuallyPlace(level, context, pos, shaft.ladderState())) {
                continue;
            }
            if (y == routeFloorY
                    && isMineableShaftBlock(level, pos)
                    && !MiningBlockRules.hasAdjacentExcavationFluid(level, pos)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static int deepestOpenY(ServerLevel level, HiredWorkContext context) {
        int lowestOpenY = context.workMax().getY();
        Shaft stored = stored(context);
        Iterable<Shaft> possibleShafts = stored == null ? candidates(context) : List.of(stored);
        for (Shaft candidate : possibleShafts) {
            int topY = topY(level, context, candidate);
            for (int y = topY; y >= context.workMin().getY(); y--) {
                BlockPos pos = candidate.at(y);
                if (!level.hasChunkAt(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.LADDER)) {
                    lowestOpenY = Math.min(lowestOpenY, y);
                }
            }
        }
        return lowestOpenY;
    }

    private static boolean isAtEntry(Villager villager, BlockPos entry) {
        return villager.blockPosition().distSqr(entry) <= 1.0D
                && Math.abs(villager.blockPosition().getY() - entry.getY()) <= 1;
    }

    private static boolean isAtSurfaceEntry(Villager villager, BlockPos entry) {
        BlockPos pos = villager.blockPosition();
        int horizontalDistance = Math.abs(pos.getX() - entry.getX()) + Math.abs(pos.getZ() - entry.getZ());
        return horizontalDistance <= 1 && pos.getY() >= entry.getY() && pos.getY() <= entry.getY() + 2;
    }

    private static BlockPos bestSurfaceEntryTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos ladderEntry) {
        CachedSurfaceEntry cached = SURFACE_ENTRY_TARGETS.get(villager);
        if (cached != null
                && cached.matches(level, context, ladderEntry)
                && isValidSurfaceEntry(level, cached.target())
                && (level.getGameTime() < cached.expiresGameTime()
                || !villager.getNavigation().isDone()
                && cached.target().equals(villager.getNavigation().getTargetPos()))) {
            return cached.target();
        }
        if (cached != null) {
            SURFACE_ENTRY_TARGETS.remove(villager);
        }

        int entryY = context.workMax().getY() + 1;
        List<SurfaceEntry> surfaceEntries = new ArrayList<>();
        addSurfaceEntry(level, villager, context, surfaceEntries, ladderEntry, new BlockPos(
                context.workCenter().getX(),
                entryY,
                context.workCenter().getZ()));
        BlockPos min = context.workMin().offset(-SURFACE_ENTRY_SEARCH_RADIUS, 1, -SURFACE_ENTRY_SEARCH_RADIUS);
        BlockPos max = context.workMax().offset(SURFACE_ENTRY_SEARCH_RADIUS, 1, SURFACE_ENTRY_SEARCH_RADIUS);
        for (BlockPos raw : BlockPos.betweenClosed(min, max)) {
            BlockPos candidate = raw.immutable();
            if (candidate.getY() == entryY) {
                addSurfaceEntry(level, villager, context, surfaceEntries, ladderEntry, candidate);
            }
        }
        surfaceEntries.sort(Comparator.comparingDouble(SurfaceEntry::score));
        BlockPos fallback = null;
        for (SurfaceEntry candidate : surfaceEntries) {
            if (fallback == null) {
                fallback = candidate.pos();
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate.pos(), 0);
            if (path != null && path.canReach()) {
                return rememberSurfaceEntryTarget(level, villager, context, ladderEntry, candidate.pos());
            }
        }
        return rememberSurfaceEntryTarget(level, villager, context, ladderEntry, fallback);
    }

    private static BlockPos rememberSurfaceEntryTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos ladderEntry,
            BlockPos target) {
        if (target != null) {
            SURFACE_ENTRY_TARGETS.put(villager, new CachedSurfaceEntry(
                    level.dimension(), context.workMin(), context.workMax(), ladderEntry, target,
                    level.getGameTime() + SURFACE_ENTRY_CACHE_TICKS));
        }
        return target;
    }

    private static void addSurfaceEntry(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<SurfaceEntry> candidates,
            BlockPos ladderEntry,
            BlockPos candidate) {
        if (!isValidSurfaceEntry(level, candidate)
                || (ladderEntry != null && candidate.distSqr(ladderEntry.above()) > 4.0D)) {
            return;
        }
        double centerDistance = candidate.distSqr(context.workCenter().above());
        double villagerDistance = villager.distanceToSqr(candidate.getCenter());
        double ladderDistance = ladderEntry == null ? 0.0D : candidate.distSqr(ladderEntry.above()) * 0.75D;
        int outsideX = distanceOutside(candidate.getX(), context.workMin().getX(), context.workMax().getX());
        int outsideZ = distanceOutside(candidate.getZ(), context.workMin().getZ(), context.workMax().getZ());
        candidates.add(new SurfaceEntry(
                candidate,
                villagerDistance + centerDistance * 0.5D + ladderDistance + (outsideX + outsideZ) * 6.0D));
    }

    private static boolean hasDismount(ServerLevel level, HiredWorkContext context, Shaft shaft, int y) {
        BlockPos ladder = shaft.at(y);
        if (!level.hasChunkAt(ladder)) {
            return false;
        }
        BlockState current = level.getBlockState(ladder);
        if (!current.is(Blocks.LADDER)
                && !MiningSupportManager.canPlace(level, ladder, shaft.ladderState())
                && !MiningSupportManager.canPrepareBacking(level, context, ladder, shaft.ladderState())) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos dismount = ladder.relative(direction);
            if (context.isInsideWorkArea(dismount)
                    && HiredMoveToBlockFaceJob.isValidApproachPosition(level, dismount)) {
                return true;
            }
            BlockPos surfaceDismount = dismount.above();
            if (isAdjacentSurfaceExit(context, surfaceDismount)
                    && isValidSurfaceEntry(level, surfaceDismount)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSurfaceExit(ServerLevel level, HiredWorkContext context, Shaft shaft) {
        return surfaceExitScore(level, context, shaft) < Double.MAX_VALUE;
    }

    private static double surfaceExitScore(ServerLevel level, HiredWorkContext context, Shaft shaft) {
        double bestScore = Double.MAX_VALUE;
        BlockPos ladderTop = shaft.at(context.workMax().getY());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos surfaceDismount = ladderTop.relative(direction).above();
            if (isAdjacentSurfaceExit(context, surfaceDismount)
                    && isValidSurfaceEntry(level, surfaceDismount)) {
                bestScore = Math.min(bestScore, surfaceDismount.distSqr(context.workCenter().above()));
            }
        }
        return bestScore;
    }

    private static double storageExitScore(
            ServerLevel level,
            HiredWorkContext context,
            Shaft shaft,
            BlockPos storage) {
        if (storage == null) {
            return 0.0D;
        }
        double bestScore = Double.MAX_VALUE;
        BlockPos ladderTop = shaft.at(context.workMax().getY());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos surfaceDismount = ladderTop.relative(direction).above();
            if (isAdjacentSurfaceExit(context, surfaceDismount)
                    && isValidSurfaceEntry(level, surfaceDismount)) {
                bestScore = Math.min(bestScore, surfaceDismount.distSqr(storage));
            }
        }
        return bestScore;
    }

    private static boolean isValidSurfaceEntry(ServerLevel level, BlockPos pos) {
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

    private static boolean isReturnPassable(BlockState state) {
        return state.isAir() || state.is(Blocks.LADDER);
    }

    private static boolean isAdjacentSurfaceExit(HiredWorkContext context, BlockPos pos) {
        return pos.getY() == context.workMax().getY() + 1
                && !context.isInsideWorkArea(pos)
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean isCandidate(HiredWorkContext context, Shaft shaft) {
        return candidates(context).contains(shaft);
    }

    private static void store(HiredWorkContext context, Shaft shaft) {
        context.state().putInt(X_TAG, shaft.x());
        context.state().putInt(Z_TAG, shaft.z());
        context.state().putString(FACING_TAG, shaft.facing().getName());
    }

    private static List<Shaft> cornerCandidates(HiredWorkContext context) {
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        LinkedHashSet<Shaft> candidates = new LinkedHashSet<>();
        addCandidate(candidates, minX, minZ, Direction.SOUTH);
        addCandidate(candidates, minX, minZ, Direction.EAST);
        addCandidate(candidates, maxX, minZ, Direction.SOUTH);
        addCandidate(candidates, maxX, minZ, Direction.WEST);
        addCandidate(candidates, maxX, maxZ, Direction.NORTH);
        addCandidate(candidates, maxX, maxZ, Direction.WEST);
        addCandidate(candidates, minX, maxZ, Direction.NORTH);
        addCandidate(candidates, minX, maxZ, Direction.EAST);
        return new ArrayList<>(candidates);
    }

    private static List<Shaft> candidatesByTop(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        BlockPos storage = villager == null ? null : context.nearestDepositStorage(level, villager);
        LinkedHashSet<Shaft> ordered = new LinkedHashSet<>();
        if (storage != null) {
            ordered.addAll(storageFacingCandidates(context, storage));
        }
        ordered.addAll(storage == null ? cornerCandidates(context) : candidates(context));
        List<Shaft> candidates = new ArrayList<>(ordered);
        candidates.sort(Comparator
                .comparingInt((Shaft shaft) -> hasSurfaceExit(level, context, shaft) ? 0 : 1)
                .thenComparingDouble(shaft -> storageExitScore(level, context, shaft, storage))
                .thenComparingInt(shaft -> topY(level, context, shaft))
                .thenComparingDouble(shaft -> surfaceExitScore(level, context, shaft)));
        return candidates;
    }

    private static List<Shaft> storageFacingCandidates(HiredWorkContext context, BlockPos storage) {
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        int storageX = Math.clamp(storage.getX(), minX, maxX);
        int storageZ = Math.clamp(storage.getZ(), minZ, maxZ);
        LinkedHashSet<Shaft> candidates = new LinkedHashSet<>();
        if (storage.getX() >= maxX) {
            addCandidate(candidates, maxX, storageZ, Direction.WEST);
        }
        if (storage.getX() <= minX) {
            addCandidate(candidates, minX, storageZ, Direction.EAST);
        }
        if (storage.getZ() >= maxZ) {
            addCandidate(candidates, storageX, maxZ, Direction.NORTH);
        }
        if (storage.getZ() <= minZ) {
            addCandidate(candidates, storageX, minZ, Direction.SOUTH);
        }
        addCandidate(candidates, maxX, storageZ, Direction.WEST);
        addCandidate(candidates, minX, storageZ, Direction.EAST);
        addCandidate(candidates, storageX, maxZ, Direction.NORTH);
        addCandidate(candidates, storageX, minZ, Direction.SOUTH);
        return new ArrayList<>(candidates);
    }

    private static void addCandidate(LinkedHashSet<Shaft> candidates, int x, int z, Direction facing) {
        candidates.add(new Shaft(x, z, facing));
    }

    private static BlockPos highestExistingLadder(ServerLevel level, HiredWorkContext context, Shaft shaft) {
        int topY = topY(level, context, shaft);
        for (int y = topY; y >= context.workMin().getY(); y--) {
            BlockPos pos = shaft.at(y);
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static boolean isMineableShaftBlock(ServerLevel level, BlockPos pos) {
        if (!MiningBlockRules.isBuilderClearableObstruction(level, pos, level.getBlockState(pos))) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
                continue;
            }
            BlockState neighborState = level.getBlockState(neighbor);
            if (neighborState.isAir() || neighborState.liquid() || neighborState.is(Blocks.LADDER)) {
                return true;
            }
        }
        return false;
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
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

    record Shaft(int x, int z, Direction facing) {
        BlockPos at(int y) {
            return new BlockPos(this.x, y, this.z);
        }

        boolean contains(BlockPos pos) {
            return pos.getX() == this.x && pos.getZ() == this.z;
        }

        BlockState ladderState() {
            return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, this.facing);
        }
    }

    private record SurfaceEntry(BlockPos pos, double score) {
    }

    private record CachedSurfaceEntry(
            ResourceKey<Level> dimension,
            BlockPos workMin,
            BlockPos workMax,
            BlockPos ladderEntry,
            BlockPos target,
            long expiresGameTime) {
        private boolean matches(ServerLevel level, HiredWorkContext context, BlockPos currentLadderEntry) {
            return dimension.equals(level.dimension())
                    && workMin.equals(context.workMin())
                    && workMax.equals(context.workMax())
                    && java.util.Objects.equals(ladderEntry, currentLadderEntry);
        }
    }
}
