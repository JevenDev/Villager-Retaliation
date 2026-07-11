package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
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

/** Plans, reaches, and places physical excavation infrastructure. */
final class MiningSupportManager {
    private static final int TORCH_LAYER_INTERVAL = 5;

    private MiningSupportManager() {
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
        Placement placement = nextPlacement(level, villager, context, supportFloorY, currentLayerY);
        if (placement == null) {
            return null;
        }
        HiredPathTarget target = placementTarget(level, villager, context, placement);
        if (target == null) {
            if (placement.type() == SupportType.LADDER) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
                HiredWorkerBrain.setFailure(context, "mining_support_unreachable", level.getGameTime() + 100L);
                HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, placement.pos());
                return WorkResult.idle("interaction.work.mining.support.unreachable");
            }
            return null;
        }
        SupportMovement movement = moveToTarget(level, villager, context, target, worker, 0.55D);
        if (movement == SupportMovement.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, placement.pos());
            return WorkResult.progressed("interaction.work.mining.support.moving");
        }
        if (movement == SupportMovement.FAILED) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            HiredWorkerBrain.setFailure(context, "mining_support_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, placement.pos());
            return WorkResult.idle("interaction.work.mining.support.unreachable");
        }
        if (!place(level, context, placement)) {
            return null;
        }
        return WorkResult.progressed(placement.type().placedStatusKey());
    }

    static int supportFloorY(ServerLevel level, HiredWorkContext context, int currentLayerY) {
        return MiningExcavationShaft.supportFloorY(level, context, currentLayerY);
    }

    static boolean hasPendingTorchPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY) {
        return nextTorchPlacement(level, villager, context, lowestOpenY, false) != null;
    }

    static BackingNeed pendingBackingNeed(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int supportFloorY,
            int currentLayerY) {
        Placement placement = nextPlacement(level, villager, context, supportFloorY, currentLayerY);
        if (placement == null || canPlace(level, placement.pos(), placement.state())) {
            return null;
        }
        return backingNeed(level, context, placement.pos(), placement.state());
    }

    static boolean hasInventorySupply(HiredWorkContext context, SupportType type) {
        return !context.inventory().findSupply(type::matchesSupply).isEmpty();
    }

    static boolean canAcceptSupply(HiredWorkContext context, SupportType type) {
        return context.inventory().canStoreSuppliesAfterDepositingOutputs(List.of(type.probeStack()));
    }

    static boolean hasInventoryBacking(ServerLevel level, HiredWorkContext context, BackingNeed need) {
        return context.inventory().hasOutput(
                stack -> canUseBacking(level, need.backingPos(), need.supportFace(), stack));
    }

    static boolean hasStoredBacking(ServerLevel level, Villager villager, BackingNeed need) {
        return villager != null
                && AssignedStorageService.countItemsInNonPaymentStorage(
                villager,
                stack -> canUseBacking(level, need.backingPos(), need.supportFace(), stack)) > 0;
    }

    static boolean canUseBacking(ServerLevel level, BlockPos pos, Direction supportFace, ItemStack stack) {
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

    static boolean canPlace(ServerLevel level, BlockPos pos, BlockState state) {
        return level.hasChunkAt(pos)
                && level.getBlockState(pos).isAir()
                && state.canSurvive(level, pos);
    }

    static boolean canPrepareBacking(ServerLevel level, HiredWorkContext context, BlockPos pos, BlockState state) {
        BackingNeed need = backingNeed(level, context, pos, state);
        return need != null && hasInventoryBacking(level, context, need);
    }

    private static Placement nextPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            int lowestOpenY,
            int currentLayerY) {
        Placement ladder = nextLadderPlacement(level, villager, context, lowestOpenY, currentLayerY);
        return ladder == null ? nextTorchPlacement(level, villager, context, lowestOpenY, true) : ladder;
    }

    private static Placement nextLadderPlacement(
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
            if (level.getBlockState(pos).is(Blocks.LADDER)) {
                continue;
            }
            BlockState ladder = shaft.ladderState();
            if (hasInventorySupply(context, SupportType.LADDER)
                    && (canPlace(level, pos, ladder)
                    || canPrepareBacking(level, context, pos, ladder)
                    || canRequestBacking(level, villager, context, pos, ladder))) {
                return new Placement(pos, ladder, SupportType.LADDER);
            }
        }
        return null;
    }

    private static Placement nextTorchPlacement(
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
                BlockState torch = Blocks.WALL_TORCH.defaultBlockState()
                        .setValue(WallTorchBlock.FACING, placement.facing());
                if ((!requireInventorySupply || hasInventorySupply(context, SupportType.TORCH))
                        && (canPlace(level, placement.pos(), torch)
                        || canPrepareBacking(level, context, placement.pos(), torch))) {
                    return new Placement(placement.pos(), torch, SupportType.TORCH);
                }
            }
        }
        return null;
    }

    private static HiredPathTarget placementTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            Placement placement) {
        Vec3 hitPos = placement.pos().getCenter();
        if (canReach(level, villager, placement.pos())) {
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
            if (MiningExcavationShaft.entryTarget(level, context) != null
                    && MiningExcavationShaft.shouldUseFallback(context, villager, fallbackTarget)
                    && score < bestFallbackScore) {
                bestFallbackScore = score;
                bestFallbackApproach = approach;
            }
            Path path = HiredPathMemory.createPath(level, villager, approach, 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
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

    private static SupportMovement moveToTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            AbstractBlockWorker worker,
            double speed) {
        if (canReach(level, villager, target.blockPos())) {
            worker.holdWorkPosition(villager, target);
            return SupportMovement.READY;
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
                return moveByLadder(level, villager, context, target, speed)
                        ? SupportMovement.MOVING
                        : SupportMovement.FAILED;
            }
            return SupportMovement.MOVING;
        }
        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
            if (VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        target.approachPos(),
                        villager.distanceToSqr(target.approachPos().getCenter()));
                return SupportMovement.MOVING;
            }
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return moveByLadder(level, villager, context, target, speed)
                ? SupportMovement.MOVING
                : SupportMovement.FAILED;
    }

    private static boolean moveByLadder(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!MiningExcavationShaft.shouldUseFallback(context, villager, target)) {
            return false;
        }
        boolean moved = VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(
                level,
                villager,
                target.approachPos(),
                speed)
                || VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed);
        if (!moved) {
            return false;
        }
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

    private static boolean place(ServerLevel level, HiredWorkContext context, Placement placement) {
        if (!preparePlacement(level, context, placement.pos(), placement.state())) {
            return false;
        }
        ItemStack supply = context.inventory().findSupply(placement.type()::matchesSupply);
        if (supply.isEmpty()) {
            return false;
        }
        ItemStack consumed = supply.copyWithCount(1);
        if (context.inventory().consumeSupply(placement.type()::matchesSupply, 1) <= 0) {
            return false;
        }
        if (!level.setBlock(placement.pos(), placement.state(), Block.UPDATE_ALL)) {
            context.inventory().insertSupply(consumed);
            return false;
        }
        HiredPathMemory.onBlockChanged(level, placement.pos());
        MiningWorkerState.clearExcavationLayerCache(context);
        return true;
    }

    private static boolean preparePlacement(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos pos,
            BlockState state) {
        if (canPlace(level, pos, state)) {
            return true;
        }
        if (!canPrepareBacking(level, context, pos, state)) {
            return false;
        }
        BlockPos backingPos = supportBackingPos(pos, state);
        Direction supportFace = supportFace(state);
        if (backingPos == null || supportFace == null) {
            return false;
        }
        ItemStack backingStack = context.inventory().consumeOutput(
                stack -> canUseBacking(level, backingPos, supportFace, stack),
                1);
        if (!(backingStack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        if (!level.setBlock(backingPos, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL)) {
            context.inventory().insertOutput(backingStack);
            return false;
        }
        HiredPathMemory.onBlockChanged(level, backingPos);
        MiningWorkerState.clearExcavationLayerCache(context);
        return canPlace(level, pos, state);
    }

    private static boolean canRequestBacking(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockState state) {
        BackingNeed need = backingNeed(level, context, pos, state);
        return need != null
                && (hasInventoryBacking(level, context, need)
                || hasStoredBacking(level, villager, need));
    }

    private static BackingNeed backingNeed(
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
        return new BackingNeed(backingPos, supportFace);
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

    private static boolean canReach(ServerLevel level, Villager villager, BlockPos pos) {
        return level.hasChunkAt(pos)
                && villager.getEyePosition().distanceToSqr(pos.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR;
    }

    private static boolean isTorchLayer(HiredWorkContext context, int y) {
        return y < context.workMax().getY()
                && Math.floorMod(context.workMax().getY() - y, TORCH_LAYER_INTERVAL) == 0;
    }

    private enum SupportMovement {
        READY,
        MOVING,
        FAILED
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

    record BackingNeed(BlockPos backingPos, Direction supportFace) {
    }

    enum SupportType {
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

    private record TorchPlacement(BlockPos pos, Direction facing) {
    }

    private record Placement(BlockPos pos, BlockState state, SupportType type) {
    }
}
