package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class MiningWorker extends AbstractBlockWorker {
    private static final int MAX_PLANNED_MINING_TARGETS = 20;
    private static final int MAX_EXCAVATION_SCAN_POSITIONS = 768;
    private static final HiredTargetSearch.Messages EXCAVATION_SEARCH_MESSAGES = new HiredTargetSearch.Messages(
            "active_excavation_target",
            "planned_excavation_target",
            "excavation_scan_cooldown",
            "no_targets",
            "excavation_scan_partial_",
            "excavation_targets_found",
            MiningWorkerState.noTargetScanCooldownTicks());

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.MINING;
    }

    public static BlockPos excavationEntryTarget(ServerLevel level, HiredWorkContext context) {
        return MiningExcavationSupport.entryTarget(level, context);
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }

        HiredMiningMode mode = HiredMiningMode.fromState(context.state());
        WorkResult fullInventoryResult = depositFullInventoryBeforeMining(level, villager, context);
        if (fullInventoryResult != null) {
            return fullInventoryResult;
        }
        if (mode.excavatesArea()) {
            WorkResult supplyResult = MiningExcavationSupport.gatherSupplies(level, villager, context);
            if (supplyResult != null) {
                return supplyResult;
            }
            WorkResult ladderRequirement = MiningExcavationSupport.requireLadder(level, villager, context);
            if (ladderRequirement != null) {
                return ladderRequirement;
            }
            WorkResult supportResult = MiningExcavationSupport.maintain(level, villager, context, this);
            if (supportResult != null) {
                return supportResult;
            }
        }
        setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
        HiredPathTarget target = resolveTarget(level, villager, context, mode);
        if (target == null) {
            clearActiveBreakingTarget(level, context, villager);
            if (MiningWorkerState.isExcavationScanInProgress(context, mode)) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.mining.scan_excavation");
            }
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
            MiningWorkerState.set(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                    ? MiningWorkerState.Phase.DEPOSIT_OUTPUT
                    : MiningWorkerState.Phase.WAITING_NO_TARGETS);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed(miningStatusKey(mode, "no_target_deposit"));
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
            if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.completed(excavationResultStatusKey(depositResult, "completed_excavation"));
            }
            if (roamInsideWorkArea(level, villager, context, 0.4D)) {
                return WorkResult.progressed(miningStatusKey(mode, "searching"));
            }
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            MiningWorkerState.ensureNoTargetScanCooldown(level, context);
            return WorkResult.idle(depositResult == DepositResult.DEPOSITED
                    ? miningStatusKey(mode, "deposited_waiting")
                    : miningStatusKey(mode, "no_targets"));
        }

        BlockState targetState = level.getBlockState(target.blockPos());
        ItemStack tool = context.inventory().equipBestTool(
                stack -> MiningBlockRules.isUsableMiningTool(mode, stack, targetState),
                stack -> effectiveDestroySpeed(stack, targetState));
        if (tool.isEmpty()) {
            HiredWorkPlan.clear(context);
            clearActiveBreakingTarget(level, context, villager);
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_MISSING_TOOL);
            HiredWorkerBrain.setFailure(context, "missing_pickaxe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle(miningStatusKey(mode, "missing_tool"));
        }
        WorkResult outputCapacityResult = depositBeforeMiningIfTargetDropsDoNotFit(level, villager, context, target, targetState, tool);
        if (outputCapacityResult != null) {
            return outputCapacityResult;
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canStartMining(level, villager, context, target, mode)) {
            context.setProgressTicks(0);
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, target.blockPos());
            boolean closeEnough = isCloseEnough(villager, target);
            boolean hasLineOfSight = hasLineOfSightToTarget(level, villager, target);
            if (!moveToMiningTarget(level, villager, context, target, mode, 0.55D)) {
                if (recordWorkPathFailure(level, villager, target.blockPos())) {
                    clearActiveBreakingTarget(level, context, villager);
                    HiredWorkerBrain.setFailure(context, "target_unreachable", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, target.blockPos());
                    return WorkResult.idle(miningStatusKey(mode, "blocked_target"));
                }
                return WorkResult.progressed(miningStatusKey(mode, "repositioning"));
            }
            return WorkResult.progressed(closeEnough && !hasLineOfSight
                    ? miningStatusKey(mode, "blocked_swing")
                    : miningStatusKey(mode, "moving_to_target"));
        }

        clearWorkPathFailure(villager, target.blockPos());
        holdWorkPosition(villager, target);
        MiningWorkerState.set(context, MiningWorkerState.Phase.MINE_TARGET);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, target.blockPos());

        int needed = actualBreakProgressGoal(level, target.blockPos(), tool);
        int progress = context.progressTicks() + MiningWorkerState.elapsedBreakProgressTicks(level, context);
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, target.blockPos(), progress, needed);
            return WorkResult.progressed(miningStatusKey(mode, "working_target"));
        }

        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                tool);
        setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, target.blockPos());
        if (!context.canStoreOutputs(drops)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
            if (depositResult == DepositResult.MOVING) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.mining.output_full_depositing");
            }
            if (depositResult == DepositResult.DEPOSITED && !context.canStoreOutputs(drops)) {
                MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
                return WorkResult.progressed("interaction.work.mining.deposited_checking_packs");
            }
        }
        if (!context.canStoreOutputs(drops)) {
            context.setProgressTicks(0);
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.mining.output_full_blocked");
        }

        context.setProgressTicks(0);
        if (!storeMinedDrops(level, context, villager, target, tool, mode)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
            return WorkResult.idle("interaction.work.mining.output_full_blocked");
        }
        HiredOreBlockTracker.onBlockBroken(level, target.blockPos());
        HiredWorkPlan.removeTarget(context, target.blockPos());

        MiningWorkerState.rememberLastMined(context, target.blockPos());
        if (!mode.excavatesArea()) {
            MiningWorkerState.rememberMiningAnchor(level, context, target.blockPos());
        }
        clearActiveBreakingTarget(level, context, villager);
        setTaskState(context, HiredWorkerTaskState.FINDING_CHAIN_TARGET, target.blockPos());
        HiredPathTarget nextTarget;
        if (mode.excavatesArea()) {
            nextTarget = plannedExcavationTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidExcavationTarget(level, villager, context, pos),
                    MAX_PLANNED_MINING_TARGETS);
            if (nextTarget == null) {
                nextTarget = findNearestExcavationTarget(level, villager, context);
            }
        } else {
            nextTarget = findAdjacentMineable(level, villager, context, target.blockPos(), mode);
            if (nextTarget == null) {
                nextTarget = findMineableInCurrentPocket(level, villager, context);
            }
        }
        if (nextTarget != null) {
            if (!mode.excavatesArea()) {
                MiningWorkerState.rememberMiningAnchor(level, context, nextTarget.blockPos());
                HiredWorkPlan.prioritize(context, nextTarget.blockPos(), MAX_PLANNED_MINING_TARGETS);
            }
            prepareBreakingTarget(level, context, villager, nextTarget);
            MiningWorkerState.set(context, MiningWorkerState.Phase.PATH_TO_TARGET);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, nextTarget.blockPos());
            return WorkResult.skilledProgress(miningStatusKey(mode, "next_target"));
        }

        MiningWorkerState.clearMiningAnchor(context);
        if (MiningWorkerState.isExcavationScanInProgress(context, mode)) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress("interaction.work.mining.post_clear_scan");
        }
        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.55D);
        MiningWorkerState.set(context, depositResult == DepositResult.DEPOSITED || depositResult == DepositResult.MOVING
                ? MiningWorkerState.Phase.DEPOSIT_OUTPUT
                : MiningWorkerState.Phase.WAITING_NO_TARGETS);
        if (depositResult == DepositResult.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.skilledProgress("interaction.work.mining.post_clear_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            return WorkResult.idle(storageFullStatus(context));
        }
        if (depositResult != DepositResult.UNAVAILABLE && isExcavationComplete(level, context, mode)) {
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            MiningWorkerState.ensureNoTargetScanCooldown(level, context);
            return WorkResult.completed(excavationResultStatusKey(depositResult, "completed_excavation"));
        }
        if (roamInsideWorkArea(level, villager, context, 0.4D)) {
            return WorkResult.skilledProgress(miningStatusKey(mode, "completed_searching"));
        }
        if (mode.excavatesArea()) {
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.skilledProgress(excavationResultStatusKey(depositResult, "continuing_excavation"));
        }
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
        MiningWorkerState.ensureNoTargetScanCooldown(level, context);
        return WorkResult.completed(depositResult == DepositResult.DEPOSITED
                ? miningStatusKey(mode, "completed_deposited")
                : miningStatusKey(mode, "completed_no_next"));
    }

    private HiredPathTarget resolveTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredMiningMode mode) {
        if (mode.excavatesArea()) {
            return resolveExcavationTarget(level, villager, context);
        }
        return resolveOreTarget(level, villager, context);
    }

    private WorkResult depositFullInventoryBeforeMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
        boolean storageTrip = worker.storageTargetPos() != null
                && (worker.taskState() == HiredWorkerTaskState.MOVING_TO_STORAGE
                || worker.taskState() == HiredWorkerTaskState.DEPOSITING
                || worker.taskState() == HiredWorkerTaskState.PAUSED_STORAGE_FULL);
        if (!storageTrip && (context.hasOutputSpace() || !context.hasOutputToDeposit())) {
            return null;
        }
        return depositBeforeMining(level, villager, context);
    }

    private WorkResult depositBeforeMiningIfTargetDropsDoNotFit(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            BlockState targetState,
            ItemStack tool) {
        List<ItemStack> drops = Block.getDrops(
                targetState,
                level,
                target.blockPos(),
                level.getBlockEntity(target.blockPos()),
                villager,
                tool);
        if (context.canStoreOutputs(drops)) {
            return null;
        }
        return depositBeforeMining(level, villager, context);
    }

    private WorkResult depositBeforeMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        clearActiveBreakingTarget(level, context, villager);
        DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, 0.55D);
        if (depositResult == DepositResult.DEPOSITED) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
            return WorkResult.progressed("interaction.work.mining.deposited_checking_packs");
        }
        if (depositResult == DepositResult.MOVING) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.DEPOSIT_OUTPUT);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.mining.output_full_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
            return WorkResult.idle(storageFullStatus(context));
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.BLOCKED_OUTPUT_FULL);
        HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
        return WorkResult.idle("interaction.work.mining.output_full_blocked");
    }

    private HiredPathTarget resolveOreTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredPathTarget active = activeWorkTarget(level, context, villager);
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (active != null && isValidMiningTarget(level, villager, context, active.blockPos(), anchor)) {
            MiningWorkerState.rememberMiningAnchor(level, context, active.blockPos());
            return active;
        }
        if (storedWorkTarget(context.state()) != null) {
            clearActiveBreakingTarget(level, context, villager);
        }

        HiredPathTarget planned = plannedTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
        if (planned != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, planned.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return planned;
        }

        BlockPos lastMined = MiningWorkerState.lastMinedBlock(context);
        if (lastMined != null) {
            HiredPathTarget adjacent = findAdjacentMineable(level, villager, context, lastMined, HiredMiningMode.EXPOSED_ORES);
            if (adjacent != null) {
                MiningWorkerState.rememberMiningAnchor(level, context, adjacent.blockPos());
                MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                return adjacent;
            }
        }

        HiredPathTarget pocketTarget = findMineableInCurrentPocket(level, villager, context);
        if (pocketTarget != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, pocketTarget.blockPos());
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return pocketTarget;
        }
        MiningWorkerState.clearMiningAnchor(context);

        HiredPathTarget recentlyExposedTarget = findRecentlyExposedMineableInRadius(level, villager, context);
        if (recentlyExposedTarget != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, recentlyExposedTarget.blockPos());
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
            return recentlyExposedTarget;
        }

        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
            return null;
        }
        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
        HiredPathTarget target = findNearestMineableInRadius(level, villager, context);
        if (target != null) {
            MiningWorkerState.rememberMiningAnchor(level, context, target.blockPos());
        }
        return target;
    }

    private HiredPathTarget resolveExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        return HiredTargetSearch.find(
                level,
                context,
                () -> {
                    HiredPathTarget active = activeExcavationWorkTarget(level, context, villager);
                    if (active == null && storedWorkTarget(context.state()) != null) {
                        clearActiveBreakingTarget(level, context, villager);
                    }
                    return active;
                },
                target -> isValidExcavationTarget(level, villager, context, target.blockPos()),
                filter -> {
                    HiredPathTarget planned = plannedExcavationTarget(
                            level,
                            villager,
                            context,
                            filter,
                            MAX_PLANNED_MINING_TARGETS);
                    if (planned != null) {
                        MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    }
                    return planned;
                },
                pos -> isValidExcavationTarget(level, villager, context, pos),
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                candidates -> {
                    MiningWorkerState.set(context, MiningWorkerState.Phase.FIND_TARGET);
                    return rebuildExcavationObjective(level, villager, context, candidates);
                },
                EXCAVATION_SEARCH_MESSAGES);
    }

    private HiredPathTarget findAdjacentMineable(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos origin,
            HiredMiningMode mode) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz).immutable();
                    if (isValidMiningTarget(level, villager, context, pos, anchor, mode)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        return mode.excavatesArea()
                ? chooseReachableTarget(level, villager, candidates)
                : chooseReachableTarget(level, villager, context, candidates);
    }

    private HiredPathTarget findNearestExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG,
                MAX_EXCAVATION_SCAN_POSITIONS,
                pos -> isValidExcavationTarget(level, villager, context, pos));
        if (!scan.candidates().isEmpty()) {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_targets_found");
            return rebuildExcavationObjective(level, villager, context, scan.candidates());
        }
        if (!scan.completedFullPass()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "excavation_scan_in_progress");
            return null;
        }
        context.state().putLong(
                MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        HiredWorkerBrain.setLastTargetScanResult(context, "no_targets");
        return null;
    }

    private HiredPathTarget rebuildExcavationObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates) {
        List<BlockPos> ordered = MiningExcavationPlan.lineOrder(villager, context, candidates, MAX_PLANNED_MINING_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "excavation" : "excavation_block",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_MINING_TARGETS);
        return plannedExcavationTarget(
                level,
                villager,
                context,
                pos -> isValidExcavationTarget(level, villager, context, pos),
                MAX_PLANNED_MINING_TARGETS);
    }

    private HiredPathTarget activeExcavationWorkTarget(ServerLevel level, HiredWorkContext context, Villager villager) {
        HiredPathTarget target = storedWorkTarget(context.state());
        if (target == null || HiredPathMemory.isAvoided(level, villager, target.blockPos())) {
            return null;
        }
        if (!context.isInsideWorkArea(target.blockPos())) {
            return null;
        }
        if (!context.isLoaded(level, target.blockPos()) || !context.isLoaded(level, target.approachPos())) {
            return null;
        }
        if (canMineFromCurrentPosition(level, villager, target)) {
            return target;
        }
        if (!HiredMoveToBlockFaceJob.isValidApproachPosition(level, target.approachPos())) {
            return null;
        }
        Vec3 approachEye = new Vec3(
                target.approachPos().getX() + 0.5D,
                target.approachPos().getY() + villager.getEyeHeight(),
                target.approachPos().getZ() + 0.5D);
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, approachEye, target.blockPos(), target.hitPos())
                ? target
                : null;
    }

    private HiredPathTarget plannedExcavationTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            java.util.function.Predicate<BlockPos> validator,
            int maxPlanTargets) {
        java.util.function.Predicate<BlockPos> safeValidator = validator == null ? ignored -> true : validator;
        HiredWorkPlan.retainMatching(context, safeValidator, maxPlanTargets);
        for (BlockPos planned : HiredWorkPlan.targets(context)) {
            HiredPathTarget target = bestExcavationWorkTarget(level, villager, context, planned);
            if (target != null && safeValidator.test(target.blockPos())) {
                return target;
            }
        }
        HiredWorkPlan.clear(context);
        return null;
    }

    private HiredPathTarget bestExcavationWorkTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos target) {
        if (!context.isInsideWorkArea(target)) {
            return null;
        }
        return chooseReachableTarget(level, villager, List.of(target));
    }

    private HiredPathTarget findMineableInCurrentPocket(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos anchor = MiningWorkerState.miningAnchor(level, context);
        if (anchor == null) {
            return null;
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : HiredOreBlockTracker.nearbyOreBlocks(level, anchor, MiningWorkerState.pocketRadius(context), context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, anchor)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, anchor);
    }

    private HiredPathTarget findRecentlyExposedMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos center = context.workCenter();
        for (BlockPos pos : HiredOreBlockTracker.recentlyExposedOreBlocks(
                level,
                center,
                context.horizontalSearchRadius(),
                context.verticalRadius())) {
            if (isValidMiningTarget(level, villager, context, pos, null)) {
                candidates.add(pos);
            }
        }
        return rebuildVeinObjective(level, villager, context, candidates, center);
    }

    private HiredPathTarget findNearestMineableInRadius(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)) {
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
        HiredPathTarget target = rebuildVeinObjective(level, villager, context, candidates, center);
        if (target == null) {
            context.state().putLong(
                    MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG,
                    level.getGameTime() + MiningWorkerState.noTargetScanCooldownTicks());
        } else {
            context.state().remove(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG);
        }
        return target;
    }

    private HiredPathTarget rebuildVeinObjective(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            BlockPos origin) {
        List<BlockPos> vein = MiningVeinPlan.best(
                level,
                origin == null ? villager.blockPosition() : origin,
                candidates,
                MAX_PLANNED_MINING_TARGETS);
        if (!vein.isEmpty()) {
            HiredWorkPlan.replaceWithObjective(
                    context,
                    vein.size() > 1 ? "vein" : "ore",
                    vein.getFirst(),
                    vein,
                    MAX_PLANNED_MINING_TARGETS);
            HiredPathTarget target = plannedTarget(
                    level,
                    villager,
                    context,
                    pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                    MAX_PLANNED_MINING_TARGETS);
            if (target != null) {
                return target;
            }
        }

        List<BlockPos> ordered = HiredWorkPlan.routeOrder(
                origin == null ? villager.blockPosition() : origin,
                candidates,
                MAX_PLANNED_MINING_TARGETS);
        HiredWorkPlan.replaceWithObjective(
                context,
                ordered.size() > 1 ? "ore_route" : "single_ore",
                ordered.isEmpty() ? null : ordered.getFirst(),
                ordered,
                MAX_PLANNED_MINING_TARGETS);
        return plannedTarget(
                level,
                villager,
                context,
                pos -> isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context)),
                MAX_PLANNED_MINING_TARGETS);
    }

    private boolean isValidMiningTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isValidMiningTarget(level, villager, context, pos, MiningWorkerState.miningAnchor(level, context));
    }

    private boolean isValidMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor,
            HiredMiningMode mode) {
        return mode.excavatesArea()
                ? isValidExcavationTarget(level, villager, context, pos)
                : isValidMiningTarget(level, villager, context, pos, anchor);
    }

    private boolean isValidMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos pos,
            BlockPos anchor) {
        return context.isInsideWorkArea(pos)
                && isInsideMiningPocket(context, pos, anchor)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && MiningBlockRules.isMineableOre(level, pos);
    }

    private boolean isValidExcavationTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return context.isInsideWorkArea(pos)
                && !isTemporarilyAvoidedTarget(level, villager, pos)
                && MiningBlockRules.isMineableExcavationBlock(level, pos)
                && MiningBlockRules.isCurrentExcavationLayer(level, context, pos)
                && !MiningBlockRules.hasAdjacentExcavationFluid(level, context, pos);
    }

    private static boolean isInsideMiningPocket(HiredWorkContext context, BlockPos pos, BlockPos anchor) {
        if (anchor == null) {
            return true;
        }
        int radius = MiningWorkerState.pocketRadius(context);
        return anchor.distSqr(pos) <= radius * radius;
    }


    private boolean canStartMining(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredMiningMode mode) {
        if (mode.excavatesArea()) {
            return context.isInsideWorkArea(target.blockPos())
                    && context.isLoaded(level, target.blockPos())
                    && context.isLoaded(level, target.approachPos())
                    && hasLineOfSightToTarget(level, villager, target)
                    && canMineFromCurrentPosition(level, villager, target);
        }
        return canWorkFromCurrentPosition(level, villager, context, target)
                && hasLineOfSightToTarget(level, villager, target)
                && canMineFromCurrentPosition(level, villager, target);
    }

    private boolean moveToMiningTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredMiningMode mode,
            double speed) {
        if (!mode.excavatesArea()) {
            return moveToTarget(level, villager, context, target, speed);
        }
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canStartMining(level, villager, context, target, mode)) {
            holdWorkPosition(villager, target);
            return true;
        }
        if (shouldEscapeUpBeforeMining(villager, target)
                && MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                villager.getNavigation().stop();
                HiredPathMemory.clearNavigationProgress(villager);
                if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                        && VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target.approachPos(), speed)) {
                    HiredPathMemory.rememberNavigationProgress(
                            level,
                            villager,
                            target.approachPos(),
                            villager.distanceToSqr(target.approachPos().getCenter()));
                    return true;
                }
                if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                        && VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)) {
                    HiredPathMemory.rememberNavigationProgress(
                            level,
                            villager,
                            target.approachPos(),
                            villager.distanceToSqr(target.approachPos().getCenter()));
                    return true;
                }
                return false;
            }
            return true;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null && path.canReach() && villager.getNavigation().moveTo(path, speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        if (MiningExcavationSupport.shouldUseLadderFallback(context, villager, target)
                && (VillagerTaskNavigationUtil.moveTowardNearbyLadderThenClimb(level, villager, target.approachPos(), speed)
                || VillagerTaskNavigationUtil.moveTowardHighestSafePositionInLoadedChunk(level, villager, target.approachPos(), speed)
                || VillagerTaskNavigationUtil.moveOnLadderToward(level, villager, target.approachPos(), speed))) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        return false;
    }

    private static boolean shouldEscapeUpBeforeMining(Villager villager, HiredPathTarget target) {
        return target != null && target.approachPos().getY() - villager.blockPosition().getY() > 2;
    }

    private boolean storeMinedDrops(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            HiredPathTarget target,
            ItemStack tool,
            HiredMiningMode mode) {
        if (!mode.excavatesArea()) {
            return storeDrops(level, context, villager, target, tool);
        }
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())
                || !canStartMining(level, villager, context, target, mode)) {
            return false;
        }
        BlockState state = level.getBlockState(target.blockPos());
        List<ItemStack> drops = Block.getDrops(state, level, target.blockPos(), level.getBlockEntity(target.blockPos()), villager, tool);
        if (!context.canStoreOutputs(drops)) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                return false;
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, target.hitPos(), state, ignored -> {
        });
        level.destroyBlock(target.blockPos(), false, villager);
        level.destroyBlockProgress(villager.getId(), target.blockPos(), -1);
        damageTool(context, villager, tool);
        HiredPathMemory.rememberRecent(level, target.blockPos());
        return true;
    }

    private boolean hasLineOfSightToTarget(ServerLevel level, Villager villager, HiredPathTarget target) {
        return hasLineOfSightToBlock(level, villager, villager.getEyePosition(), target.blockPos(), target.hitPos());
    }

    private int actualBreakProgressGoal(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {
            return 1;
        }
        float speed = Math.max(0.001F, effectiveDestroySpeed(tool, state));
        int divisor = tool.isCorrectToolForDrops(state) ? 30 : 100;
        return Math.max(1, (int) Math.ceil(hardness * divisor / speed));
    }

    private static boolean isExcavationComplete(ServerLevel level, HiredWorkContext context, HiredMiningMode mode) {
        return mode.excavatesArea()
                && !HiredWorkAreaScan.isInProgress(context, MiningWorkerState.EXCAVATION_SCAN_CURSOR_TAG)
                && level.getGameTime() < context.state().getLong(MiningWorkerState.NEXT_FULL_SCAN_GAME_TIME_TAG)
                && MiningBlockRules.currentExcavationLayer(level, context) == null;
    }

    private static String miningStatusKey(HiredMiningMode mode, String status) {
        return "interaction.work.mining." + status + "." + (mode.excavatesArea() ? "excavate_area" : "exposed_ores");
    }

    private static String excavationResultStatusKey(DepositResult depositResult, String status) {
        return "interaction.work.mining." + status + "."
                + (depositResult == DepositResult.DEPOSITED ? "deposited" : "default");
    }

}
