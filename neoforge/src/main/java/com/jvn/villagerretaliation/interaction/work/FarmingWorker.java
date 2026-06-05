package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class FarmingWorker extends AbstractBlockWorker {
    private static final String NEXT_CROP_SCAN_GAME_TIME_TAG = "NextFarmingCropScanGameTime";
    private static final String CROP_SCAN_CURSOR_TAG = "FarmingCropScanCursor";
    private static final int MAX_CROP_SCAN_POSITIONS_PER_WORK_TICK = 1536;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;
    private static final int MAX_PLANNED_CROP_TARGETS = 24;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.FARMING;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        expireWorkPathMemory(level);

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = findMatureCrop(level, villager, context);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (isCropScanInProgress(context)) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("I am looking over the fields for crops ready to harvest.");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("There are no ripe crops nearby, so I am heading to storage for now.");
            }
            if (roamInsideWorkArea(level, villager, context, 0.35D)) {
                return WorkResult.progressed("I found nothing ripe yet, so I am walking the fields.");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("There are no ripe crops within reach just now.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        if (!(targetState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(targetState)) {
            HiredWorkPlan.removeTarget(context, target.blockPos());
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
            return WorkResult.idle("That crop is no longer ready for the harvest I had in mind.");
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canWorkFromCurrentPosition(level, villager, context, target)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            if (!moveToTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("That crop is blocked off, so I am looking for another I can reach.");
                }
                return WorkResult.progressed("That crop is awkward from here, so I am changing my position.");
            }
            return WorkResult.progressed("I am moving toward the ripe crop now.");
        }
        clearWorkPathFailure(villager, target.blockPos());
        holdMiningPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        int needed = Math.max(1, 5 - Math.max(0, context.efficiency() - 75) / 30);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("I am harvesting the crop now.");
        }

        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        ItemStack tool = context.inventory().findTool(stack -> true);
        if (!storeDrops(level, context, villager, target, tool)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED && storeDrops(level, context, villager, target, tool)) {
                replant(level, villager, target.blockPos(), crop, context);
                clearActiveBreakingTarget(level, context, villager);
                return WorkResult.completed("I harvested the ripe crop and set the field right again.");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("My hands are full from the harvest, so I am taking it to storage first.");
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("I cannot carry more harvest, and there is nowhere to put it.");
        }
        replant(level, villager, target.blockPos(), crop, context);
        HiredWorkPlan.removeTarget(context, target.blockPos());
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.completed("I harvested the ripe crop and set the field right again.");
    }

    private HiredPathTarget findMatureCrop(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active != null
                && context.isInsideWorkArea(active.blockPos())
                && context.isLoaded(level, active.blockPos())
                && isMatureCrop(level, active.blockPos())) {
            HiredWorkerBrain.setLastTargetScanResult(context, "active_crop_target");
            return active;
        }
        HiredPathTarget planned = plannedTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && isMatureCrop(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos),
                MAX_PLANNED_CROP_TARGETS);
        if (planned != null) {
            HiredWorkerBrain.setLastTargetScanResult(context, "planned_crop_target");
            return planned;
        }
        if (level.getGameTime() < context.state().getLong(NEXT_CROP_SCAN_GAME_TIME_TAG)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "crop_scan_cooldown");
            return null;
        }

        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                CROP_SCAN_CURSOR_TAG,
                MAX_CROP_SCAN_POSITIONS_PER_WORK_TICK,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && isMatureCrop(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos));
        HiredPathTarget target = rebuildCropObjective(level, villager, context, scan.candidates());
        if (target == null) {
            if (scan.completedFullPass()) {
                context.state().putLong(NEXT_CROP_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
                HiredWorkerBrain.setLastTargetScanResult(context, "crop_scan_full_no_reachable_targets");
            } else {
                HiredWorkerBrain.setLastTargetScanResult(context, "crop_scan_partial_" + scan.visitedPositions());
            }
        } else {
            HiredWorkAreaScan.clearCursor(context, CROP_SCAN_CURSOR_TAG);
            context.state().remove(NEXT_CROP_SCAN_GAME_TIME_TAG);
            HiredWorkerBrain.setLastTargetScanResult(context, "crop_target_found");
        }
        return target;
    }

    private static boolean isCropScanInProgress(HiredWorkContext context) {
        return HiredWorkAreaScan.isInProgress(context, CROP_SCAN_CURSOR_TAG);
    }

    private static boolean isMatureCrop(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    private HiredPathTarget rebuildCropObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> row = bestCropRow(level, villager, candidates);
        if (row.size() >= 2) {
            HiredWorkPlan.replaceWithObjective(context, "row", row.getFirst(), row, MAX_PLANNED_CROP_TARGETS);
            HiredPathTarget target = plannedTarget(
                    level,
                    villager,
                    context,
                    pos -> context.isInsideWorkArea(pos)
                            && context.isLoaded(level, pos)
                            && isMatureCrop(level, pos)
                            && !isTemporarilyAvoidedTarget(level, villager, pos),
                    MAX_PLANNED_CROP_TARGETS);
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(villager.blockPosition(), candidates, MAX_PLANNED_CROP_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() >= 2 ? "patch" : "single_crop",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_CROP_TARGETS);
        return plannedTarget(
                level,
                villager,
                context,
                pos -> context.isInsideWorkArea(pos)
                        && context.isLoaded(level, pos)
                        && isMatureCrop(level, pos)
                        && !isTemporarilyAvoidedTarget(level, villager, pos),
                MAX_PLANNED_CROP_TARGETS);
    }

    private static List<BlockPos> bestCropRow(ServerLevel level, Villager villager, List<BlockPos> candidates) {
        Set<Long> candidateSet = new HashSet<>();
        for (BlockPos candidate : candidates) {
            candidateSet.add(candidate.asLong());
        }

        List<BlockPos> best = List.of();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BlockPos seed : candidates) {
            BlockState seedState = level.getBlockState(seed);
            if (!(seedState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(seedState)) {
                continue;
            }
            List<BlockPos> alongX = contiguousCropLine(level, seed, candidateSet, true);
            List<BlockPos> alongZ = contiguousCropLine(level, seed, candidateSet, false);
            List<BlockPos> row = alongX.size() >= alongZ.size() ? alongX : alongZ;
            double score = row.size() * 1000.0D - villager.distanceToSqr(seed.getCenter());
            if (row.size() >= 2 && score > bestScore) {
                best = row;
                bestScore = score;
            }
        }
        return best;
    }

    private static List<BlockPos> contiguousCropLine(
            ServerLevel level,
            BlockPos seed,
            Set<Long> candidateSet,
            boolean alongX) {
        BlockState seedState = level.getBlockState(seed);
        if (!(seedState.getBlock() instanceof CropBlock seedCrop)) {
            return List.of();
        }

        int fixedY = seed.getY();
        int fixedAxis = alongX ? seed.getZ() : seed.getX();
        List<BlockPos> negatives = new ArrayList<>();
        List<BlockPos> positives = new ArrayList<>();

        for (int step = 1; step <= MAX_PLANNED_CROP_TARGETS; step++) {
            BlockPos candidate = alongX
                    ? seed.offset(-step, 0, 0)
                    : seed.offset(0, 0, -step);
            if (!candidateSet.contains(candidate.asLong()) || !matchesCrop(level, candidate, seedCrop, fixedY, fixedAxis, alongX)) {
                break;
            }
            negatives.addFirst(candidate.immutable());
        }
        for (int step = 1; step <= MAX_PLANNED_CROP_TARGETS; step++) {
            BlockPos candidate = alongX
                    ? seed.offset(step, 0, 0)
                    : seed.offset(0, 0, step);
            if (!candidateSet.contains(candidate.asLong()) || !matchesCrop(level, candidate, seedCrop, fixedY, fixedAxis, alongX)) {
                break;
            }
            positives.add(candidate.immutable());
        }

        List<BlockPos> row = new ArrayList<>(negatives.size() + positives.size() + 1);
        row.addAll(negatives);
        row.add(seed.immutable());
        row.addAll(positives);
        return row;
    }

    private static boolean matchesCrop(
            ServerLevel level,
            BlockPos candidate,
            CropBlock seedCrop,
            int fixedY,
            int fixedAxis,
            boolean alongX) {
        if (candidate.getY() != fixedY) {
            return false;
        }
        if (alongX ? candidate.getZ() != fixedAxis : candidate.getX() != fixedAxis) {
            return false;
        }
        BlockState state = level.getBlockState(candidate);
        return state.getBlock() == seedCrop && seedCrop.isMaxAge(state);
    }

    private static void replant(ServerLevel level, Villager villager, BlockPos pos, CropBlock crop, HiredWorkContext context) {
        if (!"harvest_replant".equals(context.state().getString("CropMode"))) {
            return;
        }
        ItemStack seed = seedForCrop(crop);
        if (seed.isEmpty()) {
            return;
        }
        int consumed = context.consumeSupply(villager, stack -> stack.is(seed.getItem()), 1);
        if (consumed > 0) {
            level.setBlock(pos, crop.defaultBlockState(), 3);
        }
    }

    private static ItemStack seedForCrop(CropBlock crop) {
        if (crop == net.minecraft.world.level.block.Blocks.WHEAT) {
            return new ItemStack(Items.WHEAT_SEEDS);
        }
        if (crop == net.minecraft.world.level.block.Blocks.CARROTS) {
            return new ItemStack(Items.CARROT);
        }
        if (crop == net.minecraft.world.level.block.Blocks.POTATOES) {
            return new ItemStack(Items.POTATO);
        }
        if (crop == net.minecraft.world.level.block.Blocks.BEETROOTS) {
            return new ItemStack(Items.BEETROOT_SEEDS);
        }
        return ItemStack.EMPTY;
    }
}
