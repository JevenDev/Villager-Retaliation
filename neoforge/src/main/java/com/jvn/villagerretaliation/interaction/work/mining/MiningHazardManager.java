package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Plans and performs excavation hazard remediation.
 *
 * <p>A plan is persisted in the work-session state so a server restart cannot make a
 * miner forget which cells are being drained. Small, bounded fluid pockets are filled
 * completely and their temporary fill is then excavated normally. Large or externally
 * fed pockets are sealed at the excavation face and the seal is deliberately retained.</p>
 */
final class MiningHazardManager {
    private static final String PLAN_KIND_TAG = "MiningHazardPlanKind";
    private static final String PLAN_POSITIONS_TAG = "MiningHazardPlanPositions";
    private static final String PLAN_INDEX_TAG = "MiningHazardPlanIndex";
    private static final String PLAN_PERMANENT_TAG = "MiningHazardPlanPermanent";
    private static final String PERMANENT_BARRIERS_TAG = "MiningPermanentHazardBarriers";
    private static final int MAX_WATER_DRAIN_CELLS = 96;
    private static final int MAX_LAVA_DRAIN_CELLS = 32;
    private static final int MAX_SEAL_FACE_CELLS = 8;
    private static final int MAX_FILL_BLOCKS_PER_STORAGE_TRIP = 32;
    private static final int HAZARD_SCAN_MARGIN = 1;
    private static final double HAZARD_PLACEMENT_REACH = HiredMoveToBlockFaceJob.MAX_REACH + 1.5D;
    private static final double PLACEMENT_REACH_SQR = HAZARD_PLACEMENT_REACH * HAZARD_PLACEMENT_REACH;
    private static final double MOVEMENT_SPEED = 0.55D;

    private MiningHazardManager() {
    }

    static void reset(HiredWorkContext context, boolean clearPermanentBarriers) {
        clearPlan(context);
        if (clearPermanentBarriers) {
            context.state().remove(PERMANENT_BARRIERS_TAG);
        }
    }

    static WorkResult tick(ServerLevel level, Villager villager, HiredWorkContext context) {
        prunePermanentBarriers(level, context);
        HazardPlan plan = loadPlan(context);
        if (plan == null) {
            plan = findPlan(level, villager, context);
            if (plan == null) {
                return null;
            }
            savePlan(context, plan);
        }
        return executePlan(level, villager, context, plan);
    }

    static boolean isProtectedBarrier(HiredWorkContext context, BlockPos pos) {
        if (context == null || pos == null) {
            return false;
        }
        long packed = pos.asLong();
        for (long planned : context.state().getLongArray(PLAN_POSITIONS_TAG)) {
            if (planned == packed) {
                return true;
            }
        }
        for (long permanent : context.state().getLongArray(PERMANENT_BARRIERS_TAG)) {
            if (permanent == packed) {
                return true;
            }
        }
        return false;
    }

    static int permanentBarrierCount(HiredWorkContext context) {
        return context == null ? 0 : context.state().getLongArray(PERMANENT_BARRIERS_TAG).length;
    }

    static boolean isPermanentBarrier(HiredWorkContext context, BlockPos pos) {
        if (context == null || pos == null) {
            return false;
        }
        long packed = pos.asLong();
        for (long permanent : context.state().getLongArray(PERMANENT_BARRIERS_TAG)) {
            if (permanent == packed) {
                return true;
            }
        }
        return false;
    }

    private static HazardPlan findPlan(ServerLevel level, Villager villager, HiredWorkContext context) {
        Integer layerY = MiningBlockRules.currentExcavationLayer(level, context);
        if (layerY == null) {
            return null;
        }

        List<HazardSeed> seeds = new ArrayList<>();
        for (int x = context.workMin().getX(); x <= context.workMax().getX(); x++) {
            for (int z = context.workMin().getZ(); z <= context.workMax().getZ(); z++) {
                BlockPos target = new BlockPos(x, layerY, z);
                if (isProtectedBarrier(context, target)
                        || !MiningBlockRules.isMineableExcavationBlock(level, target)) {
                    continue;
                }
                collectFluidSeeds(level, target, seeds);
                if (seeds.stream().noneMatch(seed -> seed.target().equals(target))) {
                    BlockPos landing = target.below();
                    if (MiningSafety.needsFallGuard(level, landing)
                            && canReplaceWithFill(level, landing)) {
                        seeds.add(new HazardSeed(target, landing, HazardKind.FALL));
                    }
                }
            }
        }
        if (seeds.isEmpty()) {
            return null;
        }
        seeds.sort(Comparator
                .comparingInt((HazardSeed seed) -> seed.kind().priority())
                .thenComparingDouble(seed -> villager.distanceToSqr(seed.hazardPos().getCenter()))
                .thenComparingLong(seed -> seed.hazardPos().asLong()));

        HazardSeed first = seeds.getFirst();
        if (first.kind() == HazardKind.FALL) {
            boolean permanent = first.hazardPos().getY() < context.workMin().getY();
            return new HazardPlan(first.kind(), List.of(first.hazardPos()), 0, permanent);
        }

        ConnectedFluid connected = collectConnectedFluid(level, context, first.hazardPos(), first.kind());
        int drainLimit = first.kind() == HazardKind.WATER ? MAX_WATER_DRAIN_CELLS : MAX_LAVA_DRAIN_CELLS;
        if (connected.bounded() && !connected.positions().isEmpty() && connected.positions().size() <= drainLimit) {
            List<BlockPos> positions = new ArrayList<>(connected.positions());
            positions.sort(fluidPlacementOrder(level, villager));
            return new HazardPlan(first.kind(), positions, 0, false);
        }

        LinkedHashSet<BlockPos> sealFaces = new LinkedHashSet<>();
        for (HazardSeed seed : seeds) {
            if (seed.kind() == first.kind()
                    && canReplaceWithFill(level, seed.hazardPos())
                    && sealFaces.size() < MAX_SEAL_FACE_CELLS) {
                sealFaces.add(seed.hazardPos().immutable());
            }
        }
        if (sealFaces.isEmpty()) {
            return new HazardPlan(HazardKind.BLOCKED_TARGET, List.of(first.target()), 0, true);
        }
        return new HazardPlan(first.kind(), new ArrayList<>(sealFaces), 0, true);
    }

    private static void collectFluidSeeds(ServerLevel level, BlockPos target, List<HazardSeed> seeds) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = target.relative(direction);
            if (!level.hasChunkAt(neighbor)) {
                continue;
            }
            HazardKind kind = HazardKind.fromFluid(level.getFluidState(neighbor));
            if (kind != null) {
                seeds.add(new HazardSeed(target.immutable(), neighbor.immutable(), kind));
            }
        }
    }

    private static ConnectedFluid collectConnectedFluid(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos origin,
            HazardKind kind) {
        int hardLimit = (kind == HazardKind.WATER ? MAX_WATER_DRAIN_CELLS : MAX_LAVA_DRAIN_CELLS) + 1;
        BlockPos min = context.workMin().offset(-HAZARD_SCAN_MARGIN, -HAZARD_SCAN_MARGIN, -HAZARD_SCAN_MARGIN);
        BlockPos max = context.workMax().offset(HAZARD_SCAN_MARGIN, HAZARD_SCAN_MARGIN + 1, HAZARD_SCAN_MARGIN);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<Long> visited = new LinkedHashSet<>();
        List<BlockPos> positions = new ArrayList<>();
        boolean bounded = true;
        queue.add(origin.immutable());

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            if (!visited.add(current.asLong())) {
                continue;
            }
            if (!within(current, min, max)) {
                bounded = false;
                continue;
            }
            if (!level.hasChunkAt(current)) {
                bounded = false;
                continue;
            }
            if (!kind.matches(level.getFluidState(current))) {
                continue;
            }
            if (!canReplaceWithFill(level, current)) {
                bounded = false;
                continue;
            }
            positions.add(current.immutable());
            if (positions.size() >= hardLimit) {
                bounded = false;
                break;
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (visited.contains(next.asLong())) {
                    continue;
                }
                if (!level.hasChunkAt(next)) {
                    bounded = false;
                    continue;
                }
                if (!kind.matches(level.getFluidState(next))) {
                    continue;
                }
                if (!within(next, min, max)) {
                    bounded = false;
                    continue;
                }
                queue.addLast(next.immutable());
            }
        }
        return new ConnectedFluid(positions, bounded);
    }

    private static Comparator<BlockPos> fluidPlacementOrder(ServerLevel level, Villager villager) {
        return Comparator
                .comparingInt((BlockPos pos) -> level.getFluidState(pos).isSource() ? 0 : 1)
                .thenComparingDouble(pos -> villager.distanceToSqr(pos.getCenter()))
                .thenComparingInt(BlockPos::getY)
                .thenComparingLong(BlockPos::asLong);
    }

    private static WorkResult executePlan(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HazardPlan plan) {
        if (plan.kind() == HazardKind.BLOCKED_TARGET) {
            addPermanentBarriers(context, plan.positions());
            clearPlan(context);
            MiningWorkerState.clearExcavationLayerCache(context);
            MiningWorkerState.set(context, MiningWorkerState.Phase.REMEDIATE_HAZARD);
            HiredWorkerBrain.setLastTargetScanResult(context, "hazard_target_sealed");
            return WorkResult.progressed("interaction.work.mining.hazard.sealed_uncontainable");
        }
        if (plan.index() >= plan.positions().size()) {
            return finishPlan(context, plan);
        }

        BlockPos target = plan.positions().get(plan.index());
        if (!context.isLoaded(level, target)) {
            HiredWorkerBrain.setFailure(context, "hazard_chunk_unloaded", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target);
            return WorkResult.idle("interaction.work.mining.hazard.unreachable");
        }
        if (!needsPlacement(level, target)) {
            advancePlan(context, plan);
            return WorkResult.progressed(plan.kind().checkingStatus());
        }

        Predicate<ItemStack> fillPredicate = stack -> isSafeFillStack(level, target, stack);
        if (!hasFillBlock(context, fillPredicate)) {
            WorkResult gather = gatherFillBlocks(level, villager, context, plan, target, fillPredicate);
            if (gather != null) {
                return gather;
            }
        }
        if (!hasFillBlock(context, fillPredicate)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
            HiredWorkerBrain.setFailure(context, "missing_hazard_fill_blocks", 0L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, target);
            return WorkResult.idle("interaction.work.mining.hazard.missing_fill_blocks");
        }

        PlacementMovement movement = moveToPlacement(level, villager, context, target);
        if (movement == PlacementMovement.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target);
            return WorkResult.progressed(plan.kind().movingStatus());
        }
        if (movement == PlacementMovement.FAILED) {
            HiredWorkerBrain.setFailure(context, "hazard_placement_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target);
            return WorkResult.idle("interaction.work.mining.hazard.unreachable");
        }

        ItemStack consumed = consumeFillBlock(context, fillPredicate);
        if (!(consumed.getItem() instanceof BlockItem blockItem)) {
            return WorkResult.idle("interaction.work.mining.hazard.missing_fill_blocks");
        }
        BlockState fillState = blockItem.getBlock().defaultBlockState();
        if (!isSafeFillState(level, target, fillState) || !canReplaceWithFill(level, target)) {
            return WorkResult.idle("interaction.work.mining.hazard.unreachable");
        }

        villager.getNavigation().stop();
        villager.getLookControl().setLookAt(
                target.getX() + 0.5D,
                target.getY() + 0.5D,
                target.getZ() + 0.5D,
                60.0F,
                60.0F);
        if (!villager.swinging) {
            villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
        level.setBlock(target, fillState, Block.UPDATE_ALL);
        HiredPathMemory.onBlockChanged(level, target);
        MiningWorkerState.clearExcavationLayerCache(context);
        MiningWorkerState.set(context, MiningWorkerState.Phase.REMEDIATE_HAZARD);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.WORKING, target);
        advancePlan(context, plan);
        return WorkResult.progressed(plan.kind().placedStatus(plan.permanent()));
    }

    private static WorkResult finishPlan(HiredWorkContext context, HazardPlan plan) {
        if (plan.permanent()) {
            addPermanentBarriers(context, plan.positions());
        }
        clearPlan(context);
        MiningWorkerState.clearExcavationLayerCache(context);
        HiredWorkerBrain.clearFailure(context);
        HiredWorkerBrain.setLastTargetScanResult(
                context,
                plan.permanent() ? "hazard_sealed" : "hazard_drained");
        return WorkResult.progressed(plan.kind().completedStatus(plan.permanent()));
    }

    private static WorkResult gatherFillBlocks(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HazardPlan plan,
            BlockPos target,
            Predicate<ItemStack> predicate) {
        if (!context.useAssignedStorageForSupplies()) {
            return null;
        }
        if (!context.inventory().canStoreSuppliesAfterDepositingOutputs(List.of(new ItemStack(Items.COBBLESTONE)))) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_SUPPLIES);
            HiredWorkerBrain.setFailure(context, "hazard_fill_inventory_full", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, target);
            return WorkResult.idle("interaction.work.mining.hazard.inventory_full");
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, predicate);
        if (storage == null) {
            return null;
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                MOVEMENT_SPEED);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_HAZARD_BLOCKS);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.mining.hazard.gathering_fill_blocks");
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "hazard_fill_storage_unreachable", level.getGameTime() + 100L);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE, storage);
            return WorkResult.idle("interaction.work.mining.hazard.storage_unreachable");
        }

        int remaining = Math.max(1, plan.positions().size() - plan.index());
        int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storage,
                predicate,
                Math.min(MAX_FILL_BLOCKS_PER_STORAGE_TRIP, remaining),
                context.inventory()::insertSupplyFromStorage);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        if (moved > 0) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.GATHER_HAZARD_BLOCKS);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
            return WorkResult.progressed("interaction.work.mining.hazard.gathered_fill_blocks");
        }
        return null;
    }

    private static PlacementMovement moveToPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        if (canPlaceFrom(level, villager, villager.blockPosition(), target)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredPathMemory.clearNavigationProgress(villager);
            return PlacementMovement.READY;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && navigationTarget != null) {
            if (canPlaceFrom(level, villager, navigationTarget, target)) {
                if (HiredPathMemory.isNavigationBlocked(
                        level,
                        villager,
                        navigationTarget,
                        villager.distanceToSqr(navigationTarget.getCenter()))) {
                    VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                    HiredPathMemory.clearNavigationProgress(villager);
                } else {
                    return PlacementMovement.MOVING;
                }
            }
        }

        List<BlockPos> approaches = new ArrayList<>();
        for (BlockPos raw : BlockPos.betweenClosed(target.offset(-2, -1, -2), target.offset(2, 2, 2))) {
            BlockPos approach = raw.immutable();
            if (isPlacementApproachInScope(context, approach)
                    && canPlaceFrom(level, villager, approach, target)) {
                approaches.add(approach);
            }
        }
        approaches.sort(Comparator
                .comparingDouble((BlockPos pos) -> villager.distanceToSqr(pos.getCenter()))
                .thenComparingDouble(pos -> pos.distSqr(target)));
        Predicate<BlockPos> safeRoute = pos -> pos.equals(villager.blockPosition())
                || level.getBlockState(pos).is(Blocks.LADDER)
                || MiningSafety.isSafePathPosition(level, pos);
        for (BlockPos approach : approaches) {
            Path path = HiredPathMemory.createPath(level, villager, approach, 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, safeRoute)) {
                continue;
            }
            if (VillagerTaskNavigationUtil.moveToHiredPath(villager, path, approach, MOVEMENT_SPEED, 0)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        approach,
                        villager.distanceToSqr(approach.getCenter()));
                return PlacementMovement.MOVING;
            }
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return PlacementMovement.FAILED;
    }

    private static boolean canPlaceFrom(ServerLevel level, Villager villager, BlockPos stance, BlockPos target) {
        if (stance == null || !contextFreePlacementStance(level, stance)) {
            return false;
        }
        Vec3 eye = Vec3.atBottomCenterOf(stance).add(0.0D, villager.getEyeHeight(), 0.0D);
        return eye.distanceToSqr(target.getCenter()) <= PLACEMENT_REACH_SQR;
    }

    private static boolean contextFreePlacementStance(ServerLevel level, BlockPos stance) {
        if (!level.hasChunkAt(stance) || !level.hasChunkAt(stance.above()) || !level.hasChunkAt(stance.below())) {
            return false;
        }
        BlockState feet = level.getBlockState(stance);
        BlockState head = level.getBlockState(stance.above());
        BlockState floor = level.getBlockState(stance.below());
        return feet.getFluidState().isEmpty()
                && head.getFluidState().isEmpty()
                && floor.getFluidState().isEmpty()
                && (feet.isAir() || feet.is(Blocks.LADDER) || feet.getCollisionShape(level, stance).isEmpty())
                && (head.isAir() || head.is(Blocks.LADDER) || head.getCollisionShape(level, stance.above()).isEmpty())
                && (floor.isSolid() || feet.is(Blocks.LADDER));
    }

    private static boolean isPlacementApproachInScope(HiredWorkContext context, BlockPos pos) {
        if (context.isInsideWorkArea(pos)) {
            return true;
        }
        return pos.getY() == context.workMax().getY() + 1
                && pos.getX() >= context.workMin().getX() - 1
                && pos.getX() <= context.workMax().getX() + 1
                && pos.getZ() >= context.workMin().getZ() - 1
                && pos.getZ() <= context.workMax().getZ() + 1;
    }

    private static boolean hasFillBlock(HiredWorkContext context, Predicate<ItemStack> predicate) {
        return context.inventory().hasOutput(predicate)
                || !context.inventory().findSupply(predicate).isEmpty();
    }

    private static ItemStack consumeFillBlock(HiredWorkContext context, Predicate<ItemStack> predicate) {
        ItemStack output = context.inventory().consumeOutput(predicate, 1);
        if (!output.isEmpty()) {
            return output;
        }
        ItemStack supply = context.inventory().findSupply(predicate);
        if (supply.isEmpty() || context.inventory().consumeSupply(predicate, 1) <= 0) {
            return ItemStack.EMPTY;
        }
        return supply.copyWithCount(1);
    }

    private static boolean isSafeFillStack(ServerLevel level, BlockPos pos, ItemStack stack) {
        return stack != null
                && stack.getItem() instanceof BlockItem blockItem
                && !(blockItem.getBlock() instanceof FallingBlock)
                && isSafeFillState(level, pos, blockItem.getBlock().defaultBlockState());
    }

    private static boolean isSafeFillState(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()
                || state.liquid()
                || state.hasBlockEntity()
                || state.getDestroySpeed(level, pos) < 0.0F
                || HiredOreBlockTracker.isTrackedOre(state)
                || !state.canSurvive(level, pos)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!state.isFaceSturdy(level, pos, direction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canReplaceWithFill(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.hasBlockEntity()
                && (state.isAir()
                || state.is(Blocks.WATER)
                || state.is(Blocks.LAVA)
                || state.liquid());
    }

    private static boolean needsPlacement(ServerLevel level, BlockPos pos) {
        return canReplaceWithFill(level, pos);
    }

    private static boolean within(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static void advancePlan(HiredWorkContext context, HazardPlan plan) {
        context.state().putInt(PLAN_INDEX_TAG, plan.index() + 1);
    }

    private static HazardPlan loadPlan(HiredWorkContext context) {
        HazardKind kind = HazardKind.byId(context.state().getString(PLAN_KIND_TAG));
        long[] packedPositions = context.state().getLongArray(PLAN_POSITIONS_TAG);
        if (kind == null || packedPositions.length == 0 || packedPositions.length > MAX_WATER_DRAIN_CELLS + 1) {
            clearPlan(context);
            return null;
        }
        List<BlockPos> positions = new ArrayList<>(packedPositions.length);
        for (long packed : packedPositions) {
            BlockPos pos = BlockPos.of(packed);
            if (!isWithinHazardScope(context, pos)) {
                clearPlan(context);
                return null;
            }
            positions.add(pos);
        }
        int index = Math.clamp(context.state().getInt(PLAN_INDEX_TAG), 0, positions.size());
        return new HazardPlan(kind, positions, index, context.state().getBoolean(PLAN_PERMANENT_TAG));
    }

    private static void savePlan(HiredWorkContext context, HazardPlan plan) {
        context.state().putString(PLAN_KIND_TAG, plan.kind().id());
        context.state().putLongArray(PLAN_POSITIONS_TAG, plan.positions().stream().mapToLong(BlockPos::asLong).toArray());
        context.state().putInt(PLAN_INDEX_TAG, plan.index());
        context.state().putBoolean(PLAN_PERMANENT_TAG, plan.permanent());
    }

    private static void clearPlan(HiredWorkContext context) {
        context.state().remove(PLAN_KIND_TAG);
        context.state().remove(PLAN_POSITIONS_TAG);
        context.state().remove(PLAN_INDEX_TAG);
        context.state().remove(PLAN_PERMANENT_TAG);
    }

    private static void addPermanentBarriers(HiredWorkContext context, List<BlockPos> positions) {
        LinkedHashSet<Long> barriers = new LinkedHashSet<>();
        for (long packed : context.state().getLongArray(PERMANENT_BARRIERS_TAG)) {
            barriers.add(packed);
        }
        for (BlockPos pos : positions) {
            barriers.add(pos.asLong());
        }
        context.state().putLongArray(PERMANENT_BARRIERS_TAG, barriers.stream().mapToLong(Long::longValue).toArray());
    }

    private static void prunePermanentBarriers(ServerLevel level, HiredWorkContext context) {
        long[] packed = context.state().getLongArray(PERMANENT_BARRIERS_TAG);
        if (packed.length == 0) {
            return;
        }
        List<Long> retained = new ArrayList<>(packed.length);
        for (long value : packed) {
            BlockPos pos = BlockPos.of(value);
            if (isWithinHazardScope(context, pos)
                    && context.isLoaded(level, pos)
                    && !level.getBlockState(pos).isAir()
                    && level.getFluidState(pos).isEmpty()) {
                retained.add(value);
            }
        }
        if (retained.size() != packed.length) {
            context.state().putLongArray(PERMANENT_BARRIERS_TAG, retained.stream().mapToLong(Long::longValue).toArray());
            MiningWorkerState.clearExcavationLayerCache(context);
        }
    }

    private static boolean isWithinHazardScope(HiredWorkContext context, BlockPos pos) {
        return context != null
                && context.hasWorkArea()
                && pos.getX() >= context.workMin().getX() - HAZARD_SCAN_MARGIN
                && pos.getX() <= context.workMax().getX() + HAZARD_SCAN_MARGIN
                && pos.getY() >= context.workMin().getY() - HAZARD_SCAN_MARGIN
                && pos.getY() <= context.workMax().getY() + HAZARD_SCAN_MARGIN + 1
                && pos.getZ() >= context.workMin().getZ() - HAZARD_SCAN_MARGIN
                && pos.getZ() <= context.workMax().getZ() + HAZARD_SCAN_MARGIN;
    }

    private record HazardSeed(BlockPos target, BlockPos hazardPos, HazardKind kind) {
    }

    private record ConnectedFluid(List<BlockPos> positions, boolean bounded) {
    }

    private record HazardPlan(HazardKind kind, List<BlockPos> positions, int index, boolean permanent) {
    }

    private enum PlacementMovement {
        READY,
        MOVING,
        FAILED
    }

    private enum HazardKind {
        LAVA("lava", 0),
        FALL("fall", 1),
        WATER("water", 2),
        BLOCKED_TARGET("blocked_target", 3);

        private final String id;
        private final int priority;

        HazardKind(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }

        String id() {
            return this.id;
        }

        int priority() {
            return this.priority;
        }

        boolean matches(FluidState state) {
            return switch (this) {
                case WATER -> state.is(FluidTags.WATER);
                case LAVA -> state.is(FluidTags.LAVA);
                default -> false;
            };
        }

        String movingStatus() {
            return "interaction.work.mining.hazard.moving_to_" + this.id;
        }

        String checkingStatus() {
            return "interaction.work.mining.hazard.checking_" + this.id;
        }

        String placedStatus(boolean permanent) {
            if (this == FALL) {
                return "interaction.work.mining.hazard.placed_fall_guard";
            }
            return "interaction.work.mining.hazard." + (permanent ? "plugged_" : "filling_") + this.id;
        }

        String completedStatus(boolean permanent) {
            if (this == FALL) {
                return "interaction.work.mining.hazard.completed_fall_guard";
            }
            return "interaction.work.mining.hazard." + (permanent ? "sealed_" : "drained_") + this.id;
        }

        static HazardKind fromFluid(FluidState state) {
            if (state.is(FluidTags.LAVA)) {
                return LAVA;
            }
            if (state.is(FluidTags.WATER)) {
                return WATER;
            }
            return null;
        }

        static HazardKind byId(String id) {
            for (HazardKind kind : values()) {
                if (kind.id.equals(id)) {
                    return kind;
                }
            }
            return null;
        }
    }
}
