package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class MiningWorker extends AbstractBlockWorker {
    private static final String MINING_STATE_TAG = "MiningState";
    private static final String LAST_MINED_BLOCK_POS_TAG = "LastMinedBlockPos";
    private static final String MINING_ANCHOR_POS_TAG = "MiningAnchorPos";
    private static final String MINING_ANCHOR_EXPIRES_GAME_TIME_TAG = "MiningAnchorExpiresGameTime";
    private static final String NEXT_FULL_SCAN_GAME_TIME_TAG = "NextMiningFullScanGameTime";
    private static final int MINING_POCKET_RADIUS = 6;
    private static final long MINING_ANCHOR_TICKS = 20L * 90L;
    private static final int NO_TARGET_SCAN_COOLDOWN_TICKS = 100;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.MINING;
    }

    public static void clearRuntimeState() {
        clearSharedRuntimeState();
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        expireWorkPathMemory(level);

        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = resolveTarget(level, villager, context);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            setMiningState(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                    ? MiningState.DEPOSIT_OUTPUT
                    : MiningState.WAITING_NO_TARGETS);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("No exposed ores in the current pocket. Walking to assigned storage.");
            }
            if (roamInsideWorkArea(level, villager, context, 0.4D)) {
                return WorkResult.progressed("No exposed ores nearby. Roaming the assigned area.");
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            ensureNoTargetScanCooldown(level, context);
            return WorkResult.idle(depositResult == DepositResult.DEPOSITED
                    ? "Depositing mined output. No exposed ore nearby."
                    : "No exposed ores in radius. Waiting for new mining instructions.");
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack pickaxe = context.inventory().equipBestTool(
                stack -> stack.is(ItemTags.PICKAXES) && stack.isCorrectToolForDrops(targetState),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (pickaxe.isEmpty()) {
            clearActiveBreakingTarget(level, context, villager);
            setMiningState(context, MiningState.BLOCKED_MISSING_TOOL);
            HiredWorkerBrain.setFailure(context, "missing_pickaxe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("Paused: mining needs a pickaxe that can harvest the target ore.");
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canStartMining(level, villager, context, target)) {
            context.setProgressTicks(0);
            setMiningState(context, MiningState.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean closeEnough = isWithinMiningDistance(villager, target.blockPos());
            boolean hasLineOfSight = hasLineOfSightToTarget(level, villager, target);
            if (!moveToTarget(level, villager, context, target, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle("Mining target blocked. Looking for another exposed ore.");
                }
                return WorkResult.progressed("Mining target blocked. Repositioning for a reachable ore face.");
            }
            return WorkResult.progressed(closeEnough && !hasLineOfSight
                    ? "No direct line of sight to mining target. Repositioning."
                    : "Moving to mining target.");
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdMiningPosition(villager, target);
        setMiningState(context, MiningState.MINE_TARGET);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        int needed = adjustedBreakProgressGoal(level, target.blockPos(), pickaxe, context.efficiency());
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed("Mining in progress: " + progress + "/" + needed + ".");
        }

        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                pickaxe);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        if (!context.canStoreOutputs(drops)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.MOVING) {
                setMiningState(context, MiningState.DEPOSIT_OUTPUT);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("Output full. Walking to assigned storage before mining more.");
            }
        }
        if (!context.canStoreOutputs(drops)) {
            context.setProgressTicks(0);
            setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("Paused: mining output is full.");
        }

        context.setProgressTicks(0);
        if (!storeDrops(level, context, villager, target, pickaxe)) {
            setMiningState(context, MiningState.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("Paused: mining output is full.");
        }
        HiredOreBlockTracker.onBlockBroken(level, target.blockPos());

        rememberLastMined(context, target.blockPos());
        rememberMiningAnchor(level, context, target.blockPos());
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.FINDING_CHAIN_TARGET, target.blockPos());
        HiredPathTarget nextTarget = findAdjacentMineable(level, villager, context, target.blockPos());
        if (nextTarget == null) {
            nextTarget = findMineableInCurrentPocket(level, villager, context);
        }
        if (nextTarget != null) {
            rememberMiningAnchor(level, context, nextTarget.blockPos());
            prepareBreakingTarget(level, context, villager, nextTarget);
            setMiningState(context, MiningState.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, nextTarget.blockPos());
            return WorkResult.progressed("Mined block and collected output. Continuing to the next exposed ore.");
        }

        clearMiningAnchor(context);
        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        setMiningState(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                ? MiningState.DEPOSIT_OUTPUT
                : MiningState.WAITING_NO_TARGETS);
        if (depositResult == DepositResult.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("Mined block and collected output. Walking to assigned storage.");
        }
        if (roamInsideWorkArea(level, villager, context, 0.4D)) {
            return WorkResult.progressed("Mined block and collected output. Roaming the assigned area.");
        }
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        ensureNoTargetScanCooldown(level, context);
        return WorkResult.progressed(depositResult == DepositResult.DEPOSITED
                ? "Mined block, collected output, and deposited mined output."
                : "Mined block and collected output. No exposed ores remain nearby.");
    }

    private HiredPathTarget resolveTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        BlockPos anchor = miningAnchor(level, context);
        if (active != null && isValidMiningTarget(level, villager, context, active.blockPos(), anchor)) {
            rememberMiningAnchor(level, context, active.blockPos());
            return active;
        }
        if (storedWorkTarget(context.state()) != null) {
            clearActiveBreakingTarget(level, context, villager);
        }

        BlockPos lastMined = lastMinedBlock(context);
        if (lastMined != null) {
            HiredPathTarget adjacent = findAdjacentMineable(level, villager, context, lastMined);
            if (adjacent != null) {
                rememberMiningAnchor(level, context, adjacent.blockPos());
                setMiningState(context, MiningState.FIND_TARGET);
                return adjacent;
            }
        }

        HiredPathTarget pocketTarget = findMineableInCurrentPocket(level, villager, context);
        if (pocketTarget != null) {
            rememberMiningAnchor(level, context, pocketTarget.blockPos());
            setMiningState(context, MiningState.FIND_TARGET);
            return pocketTarget;
        }
        clearMiningAnchor(context);

        if (level.getGameTime() < context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        setMiningState(context, MiningState.FIND_TARGET);
        HiredPathTarget target = findNearestMineableInRadius(level, villager, context, false);
        if (target != null) {
            rememberMiningAnchor(level, context, target.blockPos());
        }
        return target;
    }

    private HiredPathTarget findAdjacentMineable(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos origin) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos anchor = miningAnchor(level, context);
        for (Direction direction : Direction.values()) {
            BlockPos pos = origin.relative(direction).immutable();
            if (isValidMiningTarget(level, villager, context, pos, anchor)) {
                candidates.add(pos);
            }
        }
        return chooseReachableTarget(level, villager, context, candidates);
    }

    private HiredPathTarget findMineableInCurrentPocket(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos anchor = miningAnchor(level, context);
        if (anchor == null) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(level, anchor, pocketRadius(context), context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, anchor)) {
                candidates.add(pos);
            }
        }
        return chooseReachableTarget(level, villager, context, candidates);
    }

    private HiredPathTarget findNearestMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            boolean ignoreScanCooldown) {
        if (!ignoreScanCooldown && level.getGameTime() < context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        HiredPathTarget target = chooseReachableTarget(level, villager, context, candidates);
        if (target == null) {
            context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        } else {
            context.state().remove(NEXT_FULL_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private boolean isValidMiningTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isValidMiningTarget(level, villager, context, pos, miningAnchor(level, context));
    }

    private boolean isValidMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor) {
        return isInsideWorkArea(context, pos)
                && isInsideMiningPocket(context, pos, anchor)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && isMineableOre(level, pos);
    }

    private static boolean isInsideWorkArea(HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos);
    }

    private static boolean isInsideMiningPocket(HiredWorkContext context, BlockPos pos, BlockPos anchor) {
        if (anchor == null) {
            return true;
        }
        int radius = pocketRadius(context);
        return anchor.distSqr(pos) <= radius * radius;
    }

    private static boolean isOre(BlockState state) {
        return HiredOreBlockTracker.isTrackedOre(state);
    }

    private static boolean isMineableOre(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && !state.hasBlockEntity()
                && isOre(state)
                && isExposed(level, pos);
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(direction))) {
                continue;
            }
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || neighbor.liquid()) {
                return true;
            }
        }
        return false;
    }

    private boolean canStartMining(ServerLevel level, Villager villager, HiredWorkContext context, HiredPathTarget target) {
        return canWorkFromCurrentPosition(level, villager, context, target)
                && isWithinMiningDistance(villager, target.blockPos())
                && hasLineOfSightToTarget(level, villager, target)
                && canMineFromCurrentPosition(level, villager, target);
    }

    private static boolean isWithinMiningDistance(Villager villager, BlockPos pos) {
        return villager.position().distanceToSqr(Vec3.atCenterOf(pos)) <= 9.0D;
    }

    private boolean hasLineOfSightToTarget(ServerLevel level, Villager villager, HiredPathTarget target) {
        return hasLineOfSightToBlock(level, villager, villager.getEyePosition(), target.blockPos(), target.hitPos());
    }

    private int adjustedBreakProgressGoal(ServerLevel level, BlockPos pos, ItemStack tool, int efficiency) {
        int base = breakProgressGoal(level, pos, tool);
        float multiplier = 100.0F / Math.max(25.0F, efficiency);
        return Math.clamp(Math.round(base * multiplier), 1, 60);
    }

    private static void ensureNoTargetScanCooldown(ServerLevel level, HiredWorkContext context) {
        if (context.state().getLong(NEXT_FULL_SCAN_GAME_TIME_TAG) <= level.getGameTime()) {
            context.state().putLong(NEXT_FULL_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_TARGET_SCAN_COOLDOWN_TICKS);
        }
    }

    private static void setMiningState(HiredWorkContext context, MiningState state) {
        context.state().putString(MINING_STATE_TAG, state.id);
    }

    private static void rememberLastMined(HiredWorkContext context, BlockPos pos) {
        context.state().putLong(LAST_MINED_BLOCK_POS_TAG, pos.asLong());
    }

    private static BlockPos lastMinedBlock(HiredWorkContext context) {
        return context.state().contains(LAST_MINED_BLOCK_POS_TAG)
                ? BlockPos.of(context.state().getLong(LAST_MINED_BLOCK_POS_TAG))
                : null;
    }

    private static void rememberMiningAnchor(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        context.state().putLong(MINING_ANCHOR_POS_TAG, pos.asLong());
        context.state().putLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG, level.getGameTime() + MINING_ANCHOR_TICKS);
    }

    private static BlockPos miningAnchor(ServerLevel level, HiredWorkContext context) {
        if (!context.state().contains(MINING_ANCHOR_POS_TAG)) {
            return null;
        }
        if (context.state().getLong(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG) <= level.getGameTime()) {
            clearMiningAnchor(context);
            return null;
        }
        return BlockPos.of(context.state().getLong(MINING_ANCHOR_POS_TAG));
    }

    private static void clearMiningAnchor(HiredWorkContext context) {
        context.state().remove(MINING_ANCHOR_POS_TAG);
        context.state().remove(MINING_ANCHOR_EXPIRES_GAME_TIME_TAG);
    }

    private static int pocketRadius(HiredWorkContext context) {
        return Math.min(Math.max(1, context.radius()), MINING_POCKET_RADIUS);
    }

    private enum MiningState {
        IDLE("idle"),
        FIND_TARGET("find_target"),
        PATH_TO_TARGET("path_to_target"),
        MINE_TARGET("mine_target"),
        DEPOSIT_OUTPUT("deposit_output"),
        WAITING_NO_TARGETS("waiting_no_targets"),
        BLOCKED_OUTPUT_FULL("blocked_output_full"),
        BLOCKED_MISSING_TOOL("blocked_missing_tool");

        private final String id;

        MiningState(String id) {
            this.id = id;
        }
    }
}
