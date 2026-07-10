package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
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

public final class MiningExcavationSupport {
    private static final int TORCH_LAYER_INTERVAL = 5;

    private MiningExcavationSupport() {
    }

    public static BlockPos entryTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.entryTarget(level, context);
    }

    public static BlockPos returnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        return MiningExcavationShaft.returnTarget(level, villager, context);
    }

    public static BlockPos currentLayerDescentTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.currentLayerDescentTarget(level, context);
    }

    public static WorkResult maintain(
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

    public static WorkResult requireLadder(ServerLevel level, Villager villager, HiredWorkContext context) {
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        if (!MiningExcavationShaft.requiresLadder(level, context, currentLayerY)
                || hasInventorySupportSupply(context, SupportType.LADDER)
                || MiningExcavationShaft.hasCompleteRouteToLayer(level, context, currentLayerY)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "missing_ladders", 0L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.mining.support.missing_ladders");
    }

    public static WorkResult gatherSupplies(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!context.useAssignedStorageForSupplies()) {
            return null;
        }
        Integer currentLayerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (currentLayerY == null) {
            return null;
        }
        boolean needsLadders = MiningExcavationShaft.requiresLadder(level, context, currentLayerY)
                && context.inventory().findSupply(SupportType.LADDER::matchesSupply).isEmpty()
                && !MiningExcavationShaft.hasCompleteRouteToLayer(level, context, currentLayerY)
                && AssignedStorageService.countItems(villager, SupportType.LADDER::matchesSupply) > 0;
        if (needsLadders && !canAcceptSupportSupply(context, SupportType.LADDER)) {
            return blockOnFullSupportInventory(level, context);
        }
        int supportFloorY = supportFloorY(level, context, currentLayerY);
        SupportBackingNeed backingNeed = pendingSupportBackingNeed(level, villager, context, supportFloorY, currentLayerY);
        boolean needsBacking = !needsLadders
                && backingNeed != null
                && !hasInventorySupportBacking(level, context, backingNeed)
                && hasStoredSupportBacking(level, villager, backingNeed);
        if (needsBacking && !context.hasOutputSpace()) {
            return blockOnFullSupportInventory(level, context);
        }
        boolean wantsTorches = context.inventory().findSupply(SupportType.TORCH::matchesSupply).isEmpty()
                && canAcceptSupportSupply(context, SupportType.TORCH)
                && hasPendingTorchSupportPlacement(level, villager, context, supportFloorY)
                && AssignedStorageService.countItems(villager, SupportType.TORCH::matchesSupply) > 0;
        if (!needsLadders && !needsBacking && !wantsTorches) {
            return null;
        }

        BlockPos storage = needsBacking
                ? AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                level,
                villager,
                stack -> canUseOutputAsSupportBacking(level, backingNeed.backingPos(), backingNeed.supportFace(), stack))
                : AssignedStorageService.nearestAssignedStoragePosContaining(
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
                    context.inventory()::insertSupplyFromStorage);
        } else if (needsBacking) {
            moved += AssignedStorageService.transferItemsAtAssignedNonPaymentStorage(
                    villager,
                    storage,
                    stack -> canUseOutputAsSupportBacking(level, backingNeed.backingPos(), backingNeed.supportFace(), stack),
                    1,
                    context.inventory()::insertOutput);
        }
        if (!needsBacking && wantsTorches) {
            moved += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    SupportType.TORCH::matchesSupply,
                    16,
                    context.inventory()::insertSupplyFromStorage);
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved > 0) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_SUPPLIES);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.mining.support.gathered_supplies");
        }
        if (needsLadders) {
            return blockOnFullSupportInventory(level, context);
        }
        return null;
    }

    public static boolean shouldUseLadderFallback(HiredWorkContext context, Villager villager, HiredPathTarget target) {
        return MiningExcavationShaft.shouldUseFallback(context, villager, target);
    }

    public static boolean hasCompleteLadderRouteToLayer(ServerLevel level, HiredWorkContext context, int layerY) {
        return MiningExcavationShaft.hasCompleteRouteToLayer(level, context, layerY);
    }

    public static boolean canMineCurrentLayerTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        return MiningExcavationShaft.canMineCurrentLayerTarget(level, context, target);
    }

    public static boolean isNeededLadderShaftTarget(ServerLevel level, HiredWorkContext context, BlockPos target) {
        return MiningExcavationShaft.isNeededTarget(level, context, target);
    }

    public static BlockPos nextNeededLadderShaftTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationShaft.nextNeededTarget(level, context);
    }

    public static boolean needsLadderRouteOutputReserve(ServerLevel level, HiredWorkContext context, BlockPos target) {
        return MiningExcavationShaft.needsOutputReserve(level, context, target);
    }

    private static int supportFloorY(ServerLevel level, HiredWorkContext context, int currentLayerY) {
        return MiningExcavationShaft.supportFloorY(level, context, currentLayerY);
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
        if (!MiningExcavationShaft.requiresLadder(level, context, currentLayerY)) {
            return null;
        }
        MiningExcavationShaft.Shaft shaft = MiningExcavationShaft.select(level, villager, context, lowestOpenY);
        if (shaft == null) {
            return null;
        }
        int topY = MiningExcavationShaft.topY(level, context, shaft);
        for (int y = topY; y >= lowestOpenY; y--) {
            BlockPos pos = shaft.at(y);
            BlockState current = level.getBlockState(pos);
            if (current.is(Blocks.LADDER)) {
                continue;
            }
            BlockState ladder = shaft.ladderState();
            if (hasInventorySupportSupply(context, SupportType.LADDER)
                    && (canPlaceSupportBlock(level, pos, ladder)
                    || canPrepareSupportBacking(level, context, pos, ladder)
                    || canRequestSupportBacking(level, villager, context, pos, ladder))) {
                return new SupportPlacement(pos, ladder, SupportType.LADDER);
            }
        }
        return null;
    }

    private static boolean hasPendingTorchSupportPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY) {
        return nextTorchPlacement(level, villager, context, lowestOpenY, false) != null;
    }

    private static SupportPlacement nextTorchPlacement(ServerLevel level, Villager villager, HiredWorkContext context, int lowestOpenY) {
        return nextTorchPlacement(level, villager, context, lowestOpenY, true);
    }

    private static SupportPlacement nextTorchPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
            boolean requireInventorySupply) {
        for (int y = context.workMax().getY(); y >= lowestOpenY; y--) {
            if (!isTorchLayer(context, y)) {
                continue;
            }
            for (TorchPlacement placement : torchPlacements(context, y)) {
                if (MiningExcavationShaft.isShaftColumn(context, placement.pos())) {
                    continue;
                }
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, placement.facing());
                if ((!requireInventorySupply || hasInventorySupportSupply(context, SupportType.TORCH))
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
        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
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

    private static boolean canAcceptSupportSupply(HiredWorkContext context, SupportType type) {
        return context.inventory().canStoreSuppliesAfterDepositingOutputs(List.of(type.probeStack()));
    }

    private static WorkResult blockOnFullSupportInventory(ServerLevel level, HiredWorkContext context) {
        HiredStorageNavigationGoal.clearStorageTarget(context);
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
        HiredWorkerBrain.setFailure(context, "support_inventory_full", level.getGameTime() + 100L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
        return WorkResult.idle("interaction.work.mining.support.inventory_full");
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
            Path path = HiredPathMemory.createPath(level, villager, approach, 0);
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
        HiredPathMemory.onBlockChanged(level, placement.pos());
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

    static boolean canPlaceSupportBlock(ServerLevel level, BlockPos pos, BlockState state) {
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
        HiredPathMemory.onBlockChanged(level, backingPos);
        MiningWorkerState.clearExcavationLayerCache(context);
        return canPlaceSupportBlock(level, pos, state);
    }

    static boolean canPrepareSupportBacking(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        SupportBackingNeed need = supportBackingNeed(level, context, pos, state);
        return need != null && hasInventorySupportBacking(level, context, need);
    }

    private static boolean canRequestSupportBacking(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockState state) {
        SupportBackingNeed need = supportBackingNeed(level, context, pos, state);
        return need != null
                && (hasInventorySupportBacking(level, context, need)
                || hasStoredSupportBacking(level, villager, need));
    }

    private static SupportBackingNeed pendingSupportBackingNeed(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int supportFloorY,
            int currentLayerY) {
        SupportPlacement placement = nextSupportPlacement(level, villager, context, supportFloorY, currentLayerY);
        if (placement == null || canPlaceSupportBlock(level, placement.pos(), placement.state())) {
            return null;
        }
        return supportBackingNeed(level, context, placement.pos(), placement.state());
    }

    private static SupportBackingNeed supportBackingNeed(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos pos,
            BlockState state) {
        if (!level.hasChunkAt(pos) || !level.getBlockState(pos).isAir()) {
            return null;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        if (backingPos == null
                || supportFace == null
                || !isAdjacentOutsideWorkArea(context, backingPos)
                || !level.hasChunkAt(backingPos)
                || !level.getBlockState(backingPos).isAir()) {
            return null;
        }
        return new SupportBackingNeed(backingPos, supportFace);
    }

    private static boolean hasInventorySupportBacking(ServerLevel level, HiredWorkContext context, SupportBackingNeed need) {
        return context.inventory().hasOutput(stack -> canUseOutputAsSupportBacking(level, need.backingPos(), need.supportFace(), stack));
    }

    private static boolean hasStoredSupportBacking(ServerLevel level, Villager villager, SupportBackingNeed need) {
        return villager != null
                && AssignedStorageService.countItemsInNonPaymentStorage(
                villager,
                stack -> canUseOutputAsSupportBacking(level, need.backingPos(), need.supportFace(), stack)) > 0;
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

    private record TorchPlacement(BlockPos pos, Direction facing) {
    }

    private record SupportPlacement(BlockPos pos, BlockState state, SupportType type) {
    }

    private record SupportBackingNeed(BlockPos backingPos, Direction supportFace) {
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

            @Override
            ItemStack probeStack() {
                return new ItemStack(Items.LADDER);
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

            @Override
            ItemStack probeStack() {
                return new ItemStack(Items.TORCH);
            }
        };

        abstract boolean matchesSupply(ItemStack stack);

        abstract String placedStatusKey();

        abstract ItemStack probeStack();
    }
}
