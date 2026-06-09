package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
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

    static WorkResult maintain(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            AbstractBlockWorker worker) {
        int supportFloorY = supportFloorY(level, context);
        SupportPlacement placement = nextSupportPlacement(level, villager, context, supportFloorY);
        if (placement == null) {
            return null;
        }
        HiredPathTarget target = supportPlacementTarget(level, villager, context, placement);
        if (target == null) {
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
        if (hasAvailableSupportSupply(level, villager, context, SupportType.LADDER)
                || hasCompleteLadderToLayer(level, context, currentLayerY)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "missing_ladders", 0L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
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
        boolean needsLadders = context.inventory().findSupply(SupportType.LADDER::matchesSupply).isEmpty()
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
            HiredWorkerBrain.clearStorageTarget(context);
            HiredStorageNavigationGoal.clearStorageNavigationState(context);
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
        HiredWorkerBrain.clearStorageTarget(context);
        HiredStorageNavigationGoal.clearStorageNavigationState(context);
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
        return Math.abs(verticalDelta) > 2;
    }

    private static int supportFloorY(ServerLevel level, HiredWorkContext context) {
        int openY = deepestOpenSupportY(level, context);
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY != null) {
            openY = Math.max(openY, currentLayerY);
        }
        return Math.clamp(openY, context.workMin().getY(), context.workMax().getY());
    }

    private static int deepestOpenSupportY(ServerLevel level, HiredWorkContext context) {
        int lowestOpenY = context.workMax().getY();
        LadderShaft stored = storedLadderShaft(context);
        Iterable<LadderShaft> candidates = stored == null ? ladderShaftCandidates(context) : List.of(stored);
        for (LadderShaft candidate : candidates) {
            for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
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

    private static SupportPlacement nextSupportPlacement(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        SupportPlacement ladder = nextLadderPlacement(level, context, lowestOpenY);
        if (ladder != null) {
            return ladder;
        }
        return nextTorchPlacement(level, context, lowestOpenY);
    }

    private static SupportPlacement nextLadderPlacement(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        LadderShaft shaft = ladderShaft(level, context, lowestOpenY);
        if (shaft == null) {
            return null;
        }
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            BlockPos pos = new BlockPos(shaft.x(), y, shaft.z());
            BlockState current = level.getBlockState(pos);
            if (current.is(Blocks.LADDER)) {
                continue;
            }
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, shaft.facing());
            if (hasSupportSupply(context, SupportType.LADDER)
                    && (canPlaceSupportBlock(level, pos, ladder) || canPrepareSupportBacking(level, context, pos, ladder))) {
                return new SupportPlacement(pos, ladder, SupportType.LADDER);
            }
        }
        return null;
    }

    private static LadderShaft ladderShaft(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        LadderShaft stored = storedLadderShaft(context);
        if (stored != null) {
            return stored;
        }
        for (LadderShaft candidate : ladderShaftCandidates(context)) {
            for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
                BlockPos pos = new BlockPos(candidate.x(), y, candidate.z());
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, candidate.facing());
                if (level.getBlockState(pos).is(Blocks.LADDER)
                        || canPlaceSupportBlock(level, pos, ladder)
                        || canPrepareSupportBacking(level, context, pos, ladder)) {
                    storeLadderShaft(context, candidate);
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
        int minX = context.workMin().getX();
        int maxX = context.workMax().getX();
        int minZ = context.workMin().getZ();
        int maxZ = context.workMax().getZ();
        List<LadderShaft> candidates = new ArrayList<>();
        candidates.add(new LadderShaft(minX, minZ, Direction.SOUTH));
        candidates.add(new LadderShaft(minX, minZ, Direction.EAST));
        candidates.add(new LadderShaft(maxX, minZ, Direction.SOUTH));
        candidates.add(new LadderShaft(maxX, minZ, Direction.WEST));
        candidates.add(new LadderShaft(maxX, maxZ, Direction.NORTH));
        candidates.add(new LadderShaft(maxX, maxZ, Direction.WEST));
        candidates.add(new LadderShaft(minX, maxZ, Direction.NORTH));
        candidates.add(new LadderShaft(minX, maxZ, Direction.EAST));
        return candidates;
    }

    private static SupportPlacement nextTorchPlacement(ServerLevel level, HiredWorkContext context, int lowestOpenY) {
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            if (!isTorchLayer(context, y)) {
                continue;
            }
            for (TorchPlacement placement : torchPlacements(context, y)) {
                if (isLadderShaft(context, placement.pos())) {
                    continue;
                }
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, placement.facing());
                if (hasSupportSupply(context, SupportType.TORCH)
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
            return false;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null && path.canReach() && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            villager.getNavigation().moveTo(path, speed);
            return false;
        }
        VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed);
        return false;
    }

    private static boolean hasSupportSupply(HiredWorkContext context, SupportType type) {
        return !context.inventory().findSupply(type::matchesSupply).isEmpty()
                || context.useAssignedStorageForSupplies();
    }

    private static boolean hasAvailableSupportSupply(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            SupportType type) {
        if (!context.inventory().findSupply(type::matchesSupply).isEmpty()) {
            return true;
        }
        return context.useAssignedStorageForSupplies()
                && AssignedStorageService.countItems(villager, type::matchesSupply) > 0;
    }

    private static boolean hasCompleteLadderToLayer(ServerLevel level, HiredWorkContext context, int layerY) {
        LadderShaft shaft = storedLadderShaft(context);
        Iterable<LadderShaft> candidates = shaft == null ? ladderShaftCandidates(context) : List.of(shaft);
        for (LadderShaft candidate : candidates) {
            boolean complete = true;
            for (int y = context.workMax().getY(); y >= layerY; y--) {
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
        for (int y = context.workMax().getY(); y >= context.workMin().getY(); y--) {
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
        double bestScore = Double.MAX_VALUE;
        for (BlockPos rawCandidate : BlockPos.betweenClosed(
                placement.pos().offset(-1, -1, -1),
                placement.pos().offset(1, 1, 1))) {
            BlockPos approach = rawCandidate.immutable();
            if (!context.isInsideWorkArea(approach)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, approach)
                    || approach.getCenter().distanceToSqr(hitPos) > HiredMoveToBlockFaceJob.MAX_REACH_SQR) {
                continue;
            }
            Path path = villager.getNavigation().createPath(approach, 0);
            if (path == null || !path.canReach() || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
                continue;
            }
            double score = villager.distanceToSqr(approach.getCenter())
                    + Math.abs(approach.getY() - villager.blockPosition().getY()) * 3.0D
                    + HiredMoveToBlockFaceJob.terrainCost(level, approach);
            if (score < bestScore) {
                bestScore = score;
                bestApproach = approach;
            }
        }
        return bestApproach == null ? null : new HiredPathTarget(placement.pos(), bestApproach, hitPos);
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
        if (context.consumeSupply(villager, placement.type()::matchesSupply, 1) <= 0) {
            return false;
        }
        level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL);
        return true;
    }

    private static boolean isTorchLayer(HiredWorkContext context, int y) {
        return Math.floorMod(context.workMax().getY() - y, TORCH_LAYER_INTERVAL) == 0;
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
