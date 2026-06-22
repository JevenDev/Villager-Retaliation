package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

final class MiningExcavationSupport {
    private static final String EXCAVATION_LADDER_X_TAG = "ExcavationLadderX";
    private static final String EXCAVATION_LADDER_Z_TAG = "ExcavationLadderZ";
    private static final String EXCAVATION_LADDER_FACING_TAG = "ExcavationLadderFacing";
    private static final int TORCH_LAYER_INTERVAL = 5;
    private static final int SURFACE_ENTRY_SEARCH_RADIUS = 2;

    private MiningExcavationSupport() {
    }

    static BlockPos entryTarget(ServerLevel level, HiredWorkContext context) {
        if (level == null || context == null || !context.hasWorkArea()) {
            return null;
        }
        LadderShaft stored = storedLadderShaft(context);
        BlockPos entry = stored == null ? null : highestExistingLadder(level, context, stored);
        if (entry != null) {
            return entry;
        }
        for (LadderShaft candidate : ladderShaftCandidates(context)) {
            entry = highestExistingLadder(level, context, candidate);
            if (entry != null) {
                storeLadderShaft(context, candidate);
                return entry;
            }
        }
        return null;
    }

    static BlockPos returnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos entry = entryTarget(level, context);
        if (entry != null) {
            if (isAtEntry(villager, entry) || isAtLadderSurfaceEntry(villager, entry)) {
                return entry;
            }
            BlockPos surfaceEntry = bestSurfaceEntryTarget(level, villager, context, entry);
            if (surfaceEntry != null) {
                return surfaceEntry;
            }
            return entry;
        }
        BlockPos surface = bestSurfaceEntryTarget(level, villager, context, null);
        if (surface != null) {
            return surface;
        }
        BlockPos fallback = new BlockPos(context.workCenter().getX(), context.workMax().getY() + 1, context.workCenter().getZ());
        return level.hasChunkAt(fallback) ? fallback : context.workCenter();
    }

    static BlockPos currentLayerDescentTarget(ServerLevel level, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null || currentLayerY >= context.workMax().getY()) {
            return null;
        }
        LadderShaft shaft = storedLadderShaft(context);
        if (shaft == null) {
            entryTarget(level, context);
            shaft = storedLadderShaft(context);
        }
        int targetY = Math.min(context.workMax().getY(), currentLayerY + 1);
        if (shaft == null || !hasCompleteLadderToLayer(level, context, targetY)) {
            return null;
        }

        for (int y = targetY; y >= currentLayerY; y--) {
            BlockPos target = new BlockPos(shaft.x(), y, shaft.z());
            if (level.hasChunkAt(target) && level.getBlockState(target).is(Blocks.LADDER)) {
                return target.immutable();
            }
        }
        return null;
    }

    private static boolean isAtEntry(Villager villager, BlockPos entry) {
        return villager.blockPosition().distSqr(entry) <= 1.0D
                && Math.abs(villager.blockPosition().getY() - entry.getY()) <= 1;
    }

    private static boolean isAtLadderSurfaceEntry(Villager villager, BlockPos entry) {
        BlockPos pos = villager.blockPosition();
        int horizontalDistance = Math.abs(pos.getX() - entry.getX()) + Math.abs(pos.getZ() - entry.getZ());
        return horizontalDistance <= 1 && pos.getY() >= entry.getY() && pos.getY() <= entry.getY() + 2;
    }

    private static BlockPos bestSurfaceEntryTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos ladderEntry) {
        int entryY = context.workMax().getY() + 1;
        List<SurfaceEntryCandidate> candidates = new ArrayList<>();
        addSurfaceEntryCandidate(level, villager, context, candidates, ladderEntry, new BlockPos(
                context.workCenter().getX(),
                entryY,
                context.workCenter().getZ()));
        BlockPos min = context.workMin().offset(-SURFACE_ENTRY_SEARCH_RADIUS, 1, -SURFACE_ENTRY_SEARCH_RADIUS);
        BlockPos max = context.workMax().offset(SURFACE_ENTRY_SEARCH_RADIUS, 1, SURFACE_ENTRY_SEARCH_RADIUS);
        for (BlockPos raw : BlockPos.betweenClosed(min, max)) {
            BlockPos candidate = raw.immutable();
            if (candidate.getY() == entryY) {
                addSurfaceEntryCandidate(level, villager, context, candidates, ladderEntry, candidate);
            }
        }
        candidates.sort(Comparator.comparingDouble(SurfaceEntryCandidate::score));

        BlockPos fallback = null;
        double fallbackScore = Double.MAX_VALUE;
        for (SurfaceEntryCandidate candidate : candidates) {
            if (candidate.score() < fallbackScore) {
                fallback = candidate.pos();
                fallbackScore = candidate.score();
            }
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach()) {
                return candidate.pos();
            }
        }
        return fallback;
    }

    private static void addSurfaceEntryCandidate(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<SurfaceEntryCandidate> candidates,
            BlockPos ladderEntry,
            BlockPos candidate) {
        if (!isValidSurfaceEntryTarget(level, candidate)) {
            return;
        }
        if (ladderEntry != null && candidate.distSqr(ladderEntry.above()) > 4.0D) {
            return;
        }
        double centerDistance = candidate.distSqr(context.workCenter().above());
        double villagerDistance = villager.distanceToSqr(candidate.getCenter());
        double ladderDistance = ladderEntry == null ? 0.0D : candidate.distSqr(ladderEntry.above()) * 0.75D;
        int outsideX = distanceOutside(candidate.getX(), context.workMin().getX(), context.workMax().getX());
        int outsideZ = distanceOutside(candidate.getZ(), context.workMin().getZ(), context.workMax().getZ());
        candidates.add(new SurfaceEntryCandidate(
                candidate,
                villagerDistance + centerDistance * 0.5D + ladderDistance + (outsideX + outsideZ) * 6.0D));
    }

    private static boolean isValidSurfaceEntryTarget(ServerLevel level, BlockPos pos) {
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

    private static int distanceOutside(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    static WorkResult maintain(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractBlockWorker worker) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        int supportFloorY = supportFloorY(level, context, currentLayerY);
        SupportPlacement placement = nextSupportPlacement(level, villager, context, supportFloorY, currentLayerY);
        if (placement == null) {
            return null;
        }
        HiredPathTarget target = supportPlacementTarget(level, villager, context, placement);
        if (target == null) {
            if (placement.type() == SupportType.LADDER) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
                HiredWorkerBrain.setFailure(context, "mining_support_unreachable", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, placement.pos());
                return WorkResult.idle("interaction.work.mining.support.unreachable");
            }
            return null;
        }
        if (!moveToSupportTarget(level, villager, context, target, worker, 0.55D)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, placement.pos());
            return WorkResult.progressed("interaction.work.mining.support.moving");
        }
        if (!placeSupportPlacement(level, villager, context, placement)) {
            return null;
        }
        return WorkResult.progressed(placement.type().placedStatusKey());
    }

    static WorkResult requireLadder(ServerLevel level, Villager villager, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        if (!requiresLadderToContinue(level, context, currentLayerY)
                || hasInventorySupportSupply(context, SupportType.LADDER)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "missing_ladders", 0L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.mining.support.missing_ladders");
    }

    static WorkResult gatherSupplies(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!context.useAssignedStorageForSupplies()) {
            return null;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        boolean needsLadders = requiresLadderToContinue(level, context, currentLayerY)
                && context.inventory().findSupply(SupportType.LADDER::matchesSupply).isEmpty()
                && !hasCompleteLadderToLayer(level, context, currentLayerY)
                && AssignedStorageService.countItems(villager, SupportType.LADDER::matchesSupply) > 0;
        boolean wantsTorches = context.inventory().findSupply(SupportType.TORCH::matchesSupply).isEmpty()
                && AssignedStorageService.countItems(villager, SupportType.TORCH::matchesSupply) > 0;
        if (!needsLadders && !wantsTorches) {
            return null;
        }

        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(
                level,
                villager,
                needsLadders ? SupportType.LADDER::matchesSupply : SupportType.TORCH::matchesSupply);
        if (storage == null) {
            return null;
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.55D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_SUPPLIES);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.mining.support.gathering_supplies");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            if (needsLadders) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
                HiredWorkerBrain.setFailure(context, "mining_support_storage_path_failed", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, storage);
                return WorkResult.idle("interaction.work.mining.support.missing_ladders");
            }
            return null;
        }

        int moved = 0;
        if (needsLadders) {
            moved += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    SupportType.LADDER::matchesSupply,
                    64,
                    context.inventory()::insertSupply);
        }
        if (wantsTorches) {
            moved += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    SupportType.TORCH::matchesSupply,
                    16,
                    context.inventory()::insertSupply);
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved > 0) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_SUPPLIES);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.mining.support.gathered_supplies");
        }
        return null;
    }

    static boolean shouldUseLadderFallback(HiredWorkContext context, Villager villager, HiredPathTarget target) {
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

    static boolean hasCompleteLadderRouteToLayer(ServerLevel level, HiredWorkContext context, int layerY) {
        return hasCompleteLadderToLayer(level, context, layerY);
    }

    static boolean canMineCurrentLayerTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadderToContinue(level, context, currentLayerY)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return true;
        }
        LadderShaft shaft = ladderShaft(level, context, currentLayerY);
        if (shaft == null) {
            return false;
        }
        if (isLadderShaftColumn(target, shaft)) {
            return true;
        }
        BlockPos shaftPos = new BlockPos(shaft.x(), currentLayerY, shaft.z());
        if (currentLayerY <= context.workMin().getY()) {
            return false;
        }
        if ((MiningBlockRules.isMineableExcavationBlock(level, shaftPos)
                || isMineableLadderShaftBlock(level, shaftPos))
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, shaftPos)) {
            return false;
        }
        return target.getY() == currentLayerY && horizontalDistance(target, shaftPos) == 1;
    }

    static boolean isNeededLadderShaftTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadderToContinue(level, context, currentLayerY)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return false;
        }
        LadderShaft shaft = ladderShaft(level, context, currentLayerY);
        return shaft != null
                && isLadderShaftColumn(target, shaft)
                && isMineableLadderShaftBlock(level, target)
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, target);
    }

    static BlockPos nextNeededLadderShaftTarget(ServerLevel level, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || !requiresLadderToContinue(level, context, currentLayerY)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return null;
        }
        LadderShaft shaft = ladderShaft(level, context, currentLayerY);
        if (shaft == null) {
            return null;
        }
        BlockPos target = new BlockPos(shaft.x(), currentLayerY, shaft.z());
        return isNeededLadderShaftTarget(level, context, target) ? target : null;
    }

    static boolean needsLadderRouteOutputReserve(ServerLevel level, HiredWorkContext context, BlockPos target) {
        if (target == null) {
            return false;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null
                || target.getY() != currentLayerY
                || !requiresLadderToContinue(level, context, currentLayerY)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return false;
        }
        LadderShaft shaft = ladderShaft(level, context, currentLayerY);
        return shaft != null && !isLadderShaftColumn(target, shaft);
    }

    private static boolean isLadderShaftColumn(BlockPos pos, LadderShaft shaft) {
        return pos.getX() == shaft.x() && pos.getZ() == shaft.z();
    }

    private static int horizontalDistance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX()) + Math.abs(first.getZ() - second.getZ());
    }

    private static boolean isMineableLadderShaftBlock(ServerLevel level, BlockPos pos) {
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

    private static int supportFloorY(ServerLevel level, HiredWorkContext context, int currentLayerY) {
        int openY = deepestOpenSupportY(level, context);
        openY = Math.max(openY, currentLayerY);
        return Math.clamp(openY, context.workMin().getY(), context.workMax().getY());
    }

    private static int deepestOpenSupportY(ServerLevel level, HiredWorkContext context) {
        int lowestOpenY = context.workMax().getY();
        LadderShaft stored = storedLadderShaft(context);
        Iterable<LadderShaft> candidates = stored == null ? ladderShaftCandidates(context) : List.of(stored);
        for (LadderShaft candidate : candidates) {
            int topY = ladderTopY(level, context, candidate);
            for (int y = topY; y >= context.workMin().getY(); y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
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

    private static SupportPlacement nextSupportPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
            int currentLayerY) {
        SupportPlacement ladder = nextLadderPlacement(level, villager, context, lowestOpenY, currentLayerY);
        if (ladder != null) {
            return ladder;
        }
        return nextTorchPlacement(level, villager, context, lowestOpenY);
    }

    private static SupportPlacement nextLadderPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
            int currentLayerY) {
        if (!requiresLadderToContinue(level, context, currentLayerY)) {
            return null;
        }
        LadderShaft shaft = ladderShaft(level, villager, context, lowestOpenY);
        if (shaft == null) {
            return null;
        }
        int topY = ladderTopY(level, context, shaft);
        for (int y = topY; y >= lowestOpenY; y--) {
            BlockPos pos = new BlockPos(shaft.x(), y, shaft.z());
            BlockState current = level.getBlockState(pos);
            if (current.is(Blocks.LADDER)) {
                continue;
            }
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, shaft.facing());
            if (hasInventorySupportSupply(context, SupportType.LADDER)
                    && (canPlaceSupportBlock(level, pos, ladder) || canPrepareSupportBacking(level, context, pos, ladder))) {
                return new SupportPlacement(pos, ladder, SupportType.LADDER);
            }
        }
        return null;
    }

    private static LadderShaft ladderShaft(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        return ladderShaft(level, null, context, lowestOpenY, false);
    }

    private static LadderShaft ladderShaft(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        return ladderShaft(level, villager, context, lowestOpenY, true);
    }

    private static LadderShaft ladderShaft(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
            boolean storeSelectedShaft) {
        LadderShaft stored = storedLadderShaft(context);
        if (stored != null) {
            return stored;
        }
        for (LadderShaft candidate : ladderShaftCandidatesByTop(level, villager, context)) {
            int topY = ladderTopY(level, context, candidate);
            for (int y = topY; y >= lowestOpenY; y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, candidate.facing());
                if (level.getBlockState(pos).is(Blocks.LADDER)
                        || canPlaceSupportBlock(level, pos, ladder)
                        || canPrepareSupportBacking(level, context, pos, ladder)) {
                    if (storeSelectedShaft) {
                        storeLadderShaft(context, candidate);
                    }
                    return candidate;
                }
            }
        }
        return null;
    }

    private static LadderShaft storedLadderShaft(HiredWorkContext context) {
        if (!context.state().contains(EXCAVATION_LADDER_X_TAG)
                || !context.state().contains(EXCAVATION_LADDER_Z_TAG)
                || !context.state().contains(EXCAVATION_LADDER_FACING_TAG)) {
            return null;
        }
        int x = context.state().getInt(EXCAVATION_LADDER_X_TAG);
        int z = context.state().getInt(EXCAVATION_LADDER_Z_TAG);
        Direction facing = Direction.byName(context.state().getString(EXCAVATION_LADDER_FACING_TAG));
        if (facing == null || facing.getAxis().isVertical()) {
            return null;
        }
        BlockPos pos = new BlockPos(x, context.workMax().getY(), z);
        LadderShaft shaft = new LadderShaft(x, z, facing);
        return context.isInsideWorkArea(pos) && isLadderShaftCandidate(context, shaft) ? shaft : null;
    }

    private static int ladderTopY(ServerLevel level, HiredWorkContext context) {
        LadderShaft stored = storedLadderShaft(context);
        if (stored != null) {
            return ladderTopY(level, context, stored);
        }
        int topY = context.workMax().getY();
        boolean foundSurface = false;
        for (LadderShaft candidate : ladderShaftCandidates(context)) {
            int candidateTopY = ladderTopY(level, context, candidate);
            if (candidateTopY < topY) {
                topY = candidateTopY;
            }
            foundSurface = foundSurface || candidateTopY < context.workMax().getY();
        }
        return foundSurface ? topY : context.workMax().getY();
    }

    private static int ladderTopY(ServerLevel level, HiredWorkContext context, LadderShaft shaft) {
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
            if (hasSurfaceDismount(level, context, shaft, y)) {
                return y;
            }
        }
        return context.workMax().getY();
    }

    private static boolean hasSurfaceDismount(ServerLevel level, HiredWorkContext context, LadderShaft shaft, int y) {
        BlockPos ladder = new BlockPos(shaft.x(), y, shaft.z());
        if (!level.hasChunkAt(ladder)) {
            return false;
        }
        BlockState ladderState = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, shaft.facing());
        BlockState current = level.getBlockState(ladder);
        if (!current.is(Blocks.LADDER)
                && !canPlaceSupportBlock(level, ladder, ladderState)
                && !canPrepareSupportBacking(level, context, ladder, ladderState)) {
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
                    && isValidSurfaceEntryTarget(level, surfaceDismount)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSurfaceExitDismount(ServerLevel level, HiredWorkContext context, LadderShaft shaft) {
        BlockPos ladderTop = new BlockPos(shaft.x(), context.workMax().getY(), shaft.z());
        return surfaceExitScore(level, context, ladderTop) < Double.MAX_VALUE;
    }

    private static double surfaceExitScore(ServerLevel level, HiredWorkContext context, LadderShaft shaft) {
        BlockPos ladderTop = new BlockPos(shaft.x(), context.workMax().getY(), shaft.z());
        return surfaceExitScore(level, context, ladderTop);
    }

    private static double surfaceExitScore(ServerLevel level, HiredWorkContext context, BlockPos ladderTop) {
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos surfaceDismount = ladderTop.relative(direction).above();
            if (!isAdjacentSurfaceExit(context, surfaceDismount)
                    || !isValidSurfaceEntryTarget(level, surfaceDismount)) {
                continue;
            }
            bestScore = Math.min(bestScore, surfaceDismount.distSqr(context.workCenter().above()));
        }
        return bestScore;
    }

    private static boolean isAdjacentSurfaceExit(HiredWorkContext context, BlockPos pos) {
        return pos.getY() == context.workMax().getY() + 1
                && !context.isInsideWorkArea(pos)
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean isLadderShaftCandidate(HiredWorkContext context, LadderShaft shaft) {
        for (LadderShaft candidate : ladderShaftCandidates(context)) {
            if (candidate.equals(shaft)) {
                return true;
            }
        }
        return false;
    }

    private static void storeLadderShaft(HiredWorkContext context, LadderShaft shaft) {
        context.state().putInt(EXCAVATION_LADDER_X_TAG, shaft.x());
        context.state().putInt(EXCAVATION_LADDER_Z_TAG, shaft.z());
        context.state().putString(EXCAVATION_LADDER_FACING_TAG, shaft.facing().getName());
    }

    private static List<LadderShaft> ladderShaftCandidates(HiredWorkContext context) {
        LinkedHashSet<LadderShaft> candidates = new LinkedHashSet<>(cornerLadderShaftCandidates(context));
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        int centerX = context.workCenter().getX();
        int centerZ = context.workCenter().getZ();
        addLadderShaftCandidate(candidates, minX, centerZ, Direction.EAST);
        addLadderShaftCandidate(candidates, maxX, centerZ, Direction.WEST);
        addLadderShaftCandidate(candidates, centerX, minZ, Direction.SOUTH);
        addLadderShaftCandidate(candidates, centerX, maxZ, Direction.NORTH);
        return new ArrayList<>(candidates);
    }

    private static List<LadderShaft> cornerLadderShaftCandidates(HiredWorkContext context) {
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        LinkedHashSet<LadderShaft> candidates = new LinkedHashSet<>();
        addLadderShaftCandidate(candidates, minX, minZ, Direction.SOUTH);
        addLadderShaftCandidate(candidates, minX, minZ, Direction.EAST);
        addLadderShaftCandidate(candidates, maxX, minZ, Direction.SOUTH);
        addLadderShaftCandidate(candidates, maxX, minZ, Direction.WEST);
        addLadderShaftCandidate(candidates, maxX, maxZ, Direction.NORTH);
        addLadderShaftCandidate(candidates, maxX, maxZ, Direction.WEST);
        addLadderShaftCandidate(candidates, minX, maxZ, Direction.NORTH);
        addLadderShaftCandidate(candidates, minX, maxZ, Direction.EAST);
        return new ArrayList<>(candidates);
    }

    private static List<LadderShaft> ladderShaftCandidatesByTop(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        BlockPos storage = villager == null ? null : context.nearestDepositStorage(level, villager);
        LinkedHashSet<LadderShaft> ordered = new LinkedHashSet<>();
        if (storage != null) {
            ordered.addAll(storageFacingLadderShaftCandidates(context, storage));
        }
        ordered.addAll(storage == null ? cornerLadderShaftCandidates(context) : ladderShaftCandidates(context));
        List<LadderShaft> candidates = new ArrayList<>(ordered);
        candidates.sort(Comparator
                .comparingInt((LadderShaft shaft) -> hasSurfaceExitDismount(level, context, shaft) ? 0 : 1)
                .thenComparingDouble(shaft -> storageSurfaceExitScore(level, context, shaft, storage))
                .thenComparingInt(shaft -> ladderTopY(level, context, shaft))
                .thenComparingDouble(shaft -> surfaceExitScore(level, context, shaft)));
        return candidates;
    }

    private static List<LadderShaft> storageFacingLadderShaftCandidates(HiredWorkContext context, BlockPos storage) {
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        int storageX = Math.clamp(storage.getX(), minX, maxX);
        int storageZ = Math.clamp(storage.getZ(), minZ, maxZ);
        LinkedHashSet<LadderShaft> candidates = new LinkedHashSet<>();

        if (storage.getX() >= maxX) {
            addLadderShaftCandidate(candidates, maxX, storageZ, Direction.WEST);
        }
        if (storage.getX() <= minX) {
            addLadderShaftCandidate(candidates, minX, storageZ, Direction.EAST);
        }
        if (storage.getZ() >= maxZ) {
            addLadderShaftCandidate(candidates, storageX, maxZ, Direction.NORTH);
        }
        if (storage.getZ() <= minZ) {
            addLadderShaftCandidate(candidates, storageX, minZ, Direction.SOUTH);
        }
        addLadderShaftCandidate(candidates, maxX, storageZ, Direction.WEST);
        addLadderShaftCandidate(candidates, minX, storageZ, Direction.EAST);
        addLadderShaftCandidate(candidates, storageX, maxZ, Direction.NORTH);
        addLadderShaftCandidate(candidates, storageX, minZ, Direction.SOUTH);
        return new ArrayList<>(candidates);
    }

    private static void addLadderShaftCandidate(
            LinkedHashSet<LadderShaft> candidates,
            int x,
            int z,
            Direction facing) {
        candidates.add(new LadderShaft(x, z, facing));
    }

    private static double storageSurfaceExitScore(
            ServerLevel level,
            HiredWorkContext context,
            LadderShaft shaft,
            BlockPos storage) {
        if (storage == null) {
            return 0.0D;
        }
        BlockPos ladderTop = new BlockPos(shaft.x(), context.workMax().getY(), shaft.z());
        double bestScore = Double.MAX_VALUE;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos surfaceDismount = ladderTop.relative(direction).above();
            if (!isAdjacentSurfaceExit(context, surfaceDismount)
                    || !isValidSurfaceEntryTarget(level, surfaceDismount)) {
                continue;
            }
            bestScore = Math.min(bestScore, surfaceDismount.distSqr(storage));
        }
        return bestScore;
    }

    private static SupportPlacement nextTorchPlacement(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            if (!isTorchLayer(context, y)) {
                continue;
            }
            for (TorchPlacement placement : torchPlacements(context, y)) {
                if (isLadderShaft(context, placement.pos())) {
                    continue;
                }
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, placement.facing());
                if (hasInventorySupportSupply(context, SupportType.TORCH)
                        && (canPlaceSupportBlock(level, placement.pos(), torch)
                        || canPrepareSupportBacking(level, context, placement.pos(), torch))) {
                    return new SupportPlacement(placement.pos(), torch, SupportType.TORCH);
                }
            }
        }
        return null;
    }

    private static boolean moveToSupportTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            AbstractBlockWorker worker,
            double speed) {
        if (canReachSupportPlacement(level, villager, target.blockPos())) {
            worker.holdWorkPosition(villager, target);
            return true;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
                return moveToSupportByLadder(level, villager, context, target, speed);
            }
            return false;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            if (VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        target.approachPos(),
                        villager.distanceToSqr(target.approachPos().getCenter()));
            }
            return false;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        moveToSupportByLadder(level, villager, context, target, speed);
        return false;
    }

    private static boolean moveToSupportByLadder(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!shouldUseLadderFallback(context, villager, target)) {
            return false;
        }
        boolean moved = VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target.approachPos(), speed)
                || VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed);
        if (moved) {
            boolean progressing = HiredPathMemory.observeNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            if (!progressing) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
            }
            return progressing;
        }
        return false;
    }

    private static boolean hasInventorySupportSupply(HiredWorkContext context, SupportType type) {
        return !context.inventory().findSupply(type::matchesSupply).isEmpty();
    }

    private static boolean requiresLadderToContinue(ServerLevel level, HiredWorkContext context, int layerY) {
        return layerY < ladderTopY(level, context);
    }

    private static boolean hasCompleteLadderToLayer(ServerLevel level, HiredWorkContext context, int layerY) {
        LadderShaft shaft = storedLadderShaft(context);
        Iterable<LadderShaft> candidates = shaft == null ? ladderShaftCandidates(context) : List.of(shaft);
        for (LadderShaft candidate : candidates) {
            int topY = ladderTopY(level, context, candidate);
            if (layerY >= topY) {
                storeLadderShaft(context, candidate);
                return true;
            }
            boolean complete = true;
            for (int y = topY; y >= layerY; y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
                if (!level.hasChunkAt(pos) || !level.getBlockState(pos).is(Blocks.LADDER)) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                storeLadderShaft(context, candidate);
                return true;
            }
        }
        return false;
    }

    private static BlockPos highestExistingLadder(ServerLevel level, HiredWorkContext context, LadderShaft shaft) {
        if (shaft == null) {
            return null;
        }
        int topY = ladderTopY(level, context, shaft);
        for (int y = topY; y >= context.workMin().getY(); y--) {
            BlockPos pos = new BlockPos(shaft.x(), y, shaft.z());
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.LADDER)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static HiredPathTarget supportPlacementTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            SupportPlacement placement) {
        Vec3 hitPos = placement.pos().getCenter();
        if (canReachSupportPlacement(level, villager, placement.pos())) {
            return new HiredPathTarget(placement.pos(), villager.blockPosition().immutable(), hitPos);
        }

        BlockPos bestApproach = null;
        BlockPos bestFallbackApproach = null;
        double bestScore = Double.MAX_VALUE;
        double bestFallbackScore = Double.MAX_VALUE;
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                placement.pos().offset(-1, -1, -1),
                placement.pos().offset(1, 1, 1))) {
            BlockPos approach = rawCandidate.immutable();
            if (!context.isInsideWorkArea(approach)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)
                    || approach.getCenter().distanceToSqr(hitPos) > HiredMoveToBlockFaceJob.MAX_REACH_SQR) {
                continue;
            }
            double score = villager.distanceToSqr(approach.getCenter())
                    + Math.abs(approach.getY() - villager.blockPosition().getY()) * 3.0D
                    + HiredMoveToBlockFaceJob.terrainCost(level, approach);
            HiredPathTarget fallbackTarget = new HiredPathTarget(placement.pos(), approach, hitPos);
            if (MiningExcavationSupport.entryTarget(level, context) != null
                    && shouldUseLadderFallback(context, villager, fallbackTarget)
                    && score < bestFallbackScore) {
                bestFallbackScore = score;
                bestFallbackApproach = approach;
            }
            Path path = villager.getNavigation().createPath(approach, 0);
            if (path == null || !path.canReach() || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
                continue;
            }
            if (score < bestScore) {
                bestScore = score;
                bestApproach = approach;
            }
        }
        BlockPos approach = bestApproach == null ? bestFallbackApproach : bestApproach;
        return approach == null ? null : new HiredPathTarget(placement.pos(), approach, hitPos);
    }

    private static boolean canReachSupportPlacement(ServerLevel level, Villager villager, BlockPos pos) {
        return level.hasChunkAt(pos)
                && villager.getEyePosition().distanceToSqr(pos.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR;
    }

    private static boolean placeSupportPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            SupportPlacement placement) {
        if (!prepareSupportBlockPlacement(level, context, placement.pos(), placement.state())) {
            return false;
        }
        if (context.inventory().consumeSupply(placement.type()::matchesSupply, 1) <= 0) {
            return false;
        }
        level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL);
        MiningWorkerState.clearExcavationLayerCache(context);
        return true;
    }

    private static boolean isTorchLayer(HiredWorkContext context, int y) {
        return y < context.workMax().getY()
                && Math.floorMod(context.workMax().getY() - y, TORCH_LAYER_INTERVAL) == 0;
    }

    private static List<TorchPlacement> torchPlacements(HiredWorkContext context, int y) {
        int centerX = Math.floorDiv(context.workMin().getX() + context.workMax().getX(), 2);
        int centerZ = Math.floorDiv(context.workMin().getZ() + context.workMax().getZ(), 2);
        LinkedHashSet<TorchPlacement> placements = new LinkedHashSet<>();
        placements.add(new TorchPlacement(new BlockPos(centerX, y, context.workMin().getZ()), Direction.SOUTH));
        placements.add(new TorchPlacement(new BlockPos(context.workMax().getX(), y, centerZ), Direction.WEST));
        placements.add(new TorchPlacement(new BlockPos(centerX, y, context.workMax().getZ()), Direction.NORTH));
        placements.add(new TorchPlacement(new BlockPos(context.workMin().getX(), y, centerZ), Direction.EAST));
        return new ArrayList<>(placements);
    }

    private static boolean canPlaceSupportBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return level.hasChunkAt(pos)
                && level.getBlockState(pos).isAir()
                && state.canSurvive(level, pos);
    }

    private static boolean prepareSupportBlockPlacement(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        if (canPlaceSupportBlock(level, pos, state)) {
            return true;
        }
        if (!canPrepareSupportBacking(level, context, pos, state)) {
            return false;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        if (backingPos == null || supportFace == null) {
            return false;
        }
        ItemStack backingStack = context.inventory().consumeOutput(
                stack -> canUseOutputAsSupportBacking(level, backingPos, supportFace, stack),
                1);
        if (!(backingStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        level.setBlock(backingPos, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
        MiningWorkerState.clearExcavationLayerCache(context);
        return canPlaceSupportBlock(level, pos, state);
    }

    private static boolean canPrepareSupportBacking(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        return backingPos != null
                && supportFace != null
                && isAdjacentOutsideWorkArea(context, backingPos)
                && level.hasChunkAt(backingPos)
                && level.getBlockState(backingPos).isAir()
                && context.inventory().hasOutput(stack -> canUseOutputAsSupportBacking(level, backingPos, supportFace, stack));
    }

    private static boolean canUseOutputAsSupportBacking(ServerLevel level, BlockPos pos, Direction supportFace, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || blockItem.getBlock() instanceof FallingBlock) {
            return false;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return !state.isAir()
                && !state.liquid()
                && !state.hasBlockEntity()
                && !HiredOreBlockTracker.isTrackedOre(state)
                && state.canSurvive(level, pos)
                && state.isFaceSturdy(level, pos, supportFace);
    }

    private static BlockPos supportBackingPos(BlockPos pos, BlockState state) {
        Direction face = supportFace(state);
        return face == null ? null : pos.relative(face.getOpposite());
    }

    private static Direction supportFace(BlockState state) {
        return state.hasProperty(LadderBlock.FACING) ? state.getValue(LadderBlock.FACING) : null;
    }

    private static boolean isAdjacentOutsideWorkArea(HiredWorkContext context, BlockPos pos) {
        return !context.isInsideWorkArea(pos)
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getY() >= context.workMin().getY()
                && pos.getY() <= context.workMax().getY()
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean isLadderShaft(HiredWorkContext context, BlockPos pos) {
        LadderShaft shaft = storedLadderShaft(context);
        return shaft != null && shaft.x() == pos.getX() && shaft.z() == pos.getZ();
    }

    private record LadderShaft(int x, int z, Direction facing) {
    }

    private record TorchPlacement(BlockPos pos, Direction facing) {
    }

    private record SupportPlacement(BlockPos pos, BlockState state, SupportType type) {
    }

    private record SurfaceEntryCandidate(BlockPos pos, double score) {
    }

    private enum SupportType {
        LADDER {
            @Override
            boolean matchesSupply(ItemStack stack) {
                return stack.is(Items.LADDER);
            }

            @Override
            String placedStatusKey() {
                return "interaction.work.mining.support.placed_ladder";
            }
        },
        TORCH {
            @Override
            boolean matchesSupply(ItemStack stack) {
                return stack.is(Items.TORCH);
            }

            @Override
            String placedStatusKey() {
                return "interaction.work.mining.support.placed_torch";
            }
        };

        abstract boolean matchesSupply(ItemStack stack);

        abstract String placedStatusKey();
    }
}
