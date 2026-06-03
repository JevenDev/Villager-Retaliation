package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.List;
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
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;

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
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("No mature crops found in radius. Walking to assigned storage.");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("No mature crops found in radius.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        if (!(targetState.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(targetState)) {
            clearActiveBreakingTarget(level, context, villager);
            HiredWorkerBrain.setFailure(context, "target_changed", level.getGameTime() + 40L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
            return WorkResult.idle("Crop target changed.");
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
                    return WorkResult.idle("Crop blocked. Looking for another reachable harvest.");
                }
                return WorkResult.progressed("Crop blocked. Repositioning for a reachable harvest.");
            }
            return WorkResult.progressed("Moving to mature crop.");
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
            return WorkResult.progressed("Harvesting crop: " + progress + "/" + needed + ".");
        }

        context.setProgressTicks(0);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        ItemStack tool = context.inventory().findTool(stack -> true);
        if (!storeDrops(level, context, villager, target, tool)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.45D);
            if (depositResult == DepositResult.DEPOSITED && storeDrops(level, context, villager, target, tool)) {
                replant(level, villager, target.blockPos(), crop, context);
                clearActiveBreakingTarget(level, context, villager);
                return WorkResult.completed("Harvested mature crop.");
            }
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("Output full. Walking to assigned storage before harvesting more.");
            }
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("Paused: output storage is full.");
        }
        replant(level, villager, target.blockPos(), crop, context);
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.completed("Harvested mature crop.");
    }

    private HiredPathTarget findMatureCrop(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        if (active != null
                && context.isInsideWorkArea(active.blockPos())
                && context.isLoaded(level, active.blockPos())
                && isMatureCrop(level, active.blockPos())) {
            return active;
        }
        if (level.getGameTime() < context.state().getLong(NEXT_CROP_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos rawPos : context.workAreaPositions()) {
            BlockPos pos = rawPos.immutable();
            if (context.isInsideWorkArea(pos)
                    && context.isLoaded(level, pos)
                    && isMatureCrop(level, pos)
                    && !isTemporarilyAvoidedTarget(level, villager, pos)) {
                candidates.add(pos);
            }
        }
        HiredPathTarget target = chooseReachableTarget(level, villager, context, candidates);
        if (target == null) {
            context.state().putLong(NEXT_CROP_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        } else {
            context.state().remove(NEXT_CROP_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private static boolean isMatureCrop(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
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
