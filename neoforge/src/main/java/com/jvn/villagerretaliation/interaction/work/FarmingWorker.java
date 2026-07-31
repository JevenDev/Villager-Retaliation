package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredJobSite;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.Tags;

public final class FarmingWorker extends AbstractBlockWorker {
    private static final String NEXT_FIELD_SCAN_GAME_TIME_TAG = "NextFarmingFieldScanGameTime";
    private static final String FIELD_HARVEST_SCAN_CURSOR_TAG = "FarmingFieldHarvestScanCursor";
    private static final String FIELD_PLANT_SCAN_CURSOR_TAG = "FarmingFieldPlantScanCursor";
    private static final String FIELD_TILL_SCAN_CURSOR_TAG = "FarmingFieldTillScanCursor";
    private static final String FIELD_GROWING_SCAN_CURSOR_TAG = "FarmingFieldGrowingScanCursor";
    private static final String FIELD_GROWING_SCAN_COMPLETE_TAG = "FarmingFieldGrowingScanComplete";
    private static final String FIELD_GROWING_CROP_PRESENT_TAG = "FarmingFieldGrowingCropPresent";
    private static final int MAX_FIELD_SCAN_POSITIONS_PER_WORK_TICK = 768;
    private static final int NO_FIELD_TARGET_SCAN_COOLDOWN_TICKS = 40;
    private static final int JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS = 10;
    private static final int JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS = 4;
    private static final double FIELD_GUIDE_WALK_SPEED = 0.5D;
    private static final int FIELD_GUIDE_CLOSE_ENOUGH = 1;
    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.FARMING;
    }

    @Override
    public void maintain(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (canUseVanillaFarmerBrain(level, villager)) {
            activateFarmerWorkBrain(villager);
            HiredWorkerBrain.Snapshot worker = HiredWorkerBrain.snapshot(context.state(), level.getGameTime());
            if (worker.targetPos() != null && isUsableVanillaFieldTarget(level, villager, context, worker.targetPos())) {
                seedSecondaryJobSite(level, villager, worker.targetPos());
            }
        }
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        HiredFarmingInventoryBridge.sweepPersonalFarmItemsToJobInventory(villager, context);

        if (villager.getVillagerData().getProfession() != VillagerProfession.FARMER) {
            HiredWorkerBrain.setFailure(context, "farmer_profession_required", 0L);
            HiredWorkerBrain.setLastTargetScanResult(context, "farmer_profession_required");
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.farming.needs_farmer_profession");
        }
        if (!context.hasWorkArea() && !HiredVillagerWorkService.hasClaimedJobSiteInLevel(level, villager)) {
            HiredWorkerBrain.setFailure(context, "farmer_job_site_required", 0L);
            HiredWorkerBrain.setLastTargetScanResult(context, "farmer_job_site_required");
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.farming.needs_farmer_job_site");
        }

        WorkResult hoeResult = ensureFarmingHoe(level, villager, context);
        if (hoeResult != null) {
            return hoeResult;
        }

        HiredWorkPlan.clear(context);
        clearActiveBreakingTarget(level, context, villager);
        HiredWorkerBrain.clearFailure(context);
        activateFarmerWorkBrain(villager);
        return guideFieldWork(level, villager, context);
    }

    private WorkResult guideFieldWork(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos jobSite = HiredVillagerWorkService.claimedJobSitePos(level, villager);
        BlockPos currentFieldTarget = currentFieldWalkTarget(level, villager, context, jobSite);
        if (currentFieldTarget != null) {
            return guideFieldTarget(level, villager, context, currentFieldTarget, "vanilla_field_target");
        }

        ItemStack hoe = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        BlockPos currentTillTarget = currentTillWalkTarget(level, villager, context, hoe);
        if (currentTillTarget != null) {
            return guideSoilTillTarget(level, villager, context, currentTillTarget);
        }

        if (fieldScanOnCooldown(level, context)) {
            GrowingCropPresence growing = growingCropPresence(level, context, jobSite);
            HiredWorkerBrain.setLastTargetScanResult(
                    context,
                    growing == GrowingCropPresence.PRESENT
                            ? "field_scan_cooldown_waiting_for_crops"
                            : growing == GrowingCropPresence.SCANNING
                            ? "field_scan_cooldown_scanning_growth"
                            : "field_scan_cooldown_no_targets");
            clearSecondaryJobSite(villager);
            setTaskState(context, HiredWorkerTaskState.IDLE);
            return WorkResult.idle("interaction.work.farming.waiting_for_growth");
        }

        FieldSearchResult harvestSearch = findNextFieldTarget(level, villager, context, jobSite, true);
        if (harvestSearch.scanInProgress()) {
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.progressed("interaction.work.farming.searching_scan");
        }
        if (harvestSearch.target() != null) {
            return guideFieldTarget(level, villager, context, harvestSearch.target(), "field_harvest_target_found");
        }

        if (HiredFarmingOptions.tillSoil(context.state()) && context.hasWorkArea()) {
            FieldSearchResult tillSearch = findNextTillTarget(level, villager, context, hoe);
            if (tillSearch.scanInProgress()) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.farming.searching_scan");
            }
            if (tillSearch.target() != null) {
                return guideSoilTillTarget(level, villager, context, tillSearch.target());
            }
        }

        if (HiredFarmingInventoryBridge.hasJobPlantingItem(villager, context)) {
            FieldSearchResult plantSearch = findNextFieldTarget(level, villager, context, jobSite, false);
            if (plantSearch.scanInProgress()) {
                setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
                return WorkResult.progressed("interaction.work.farming.searching_scan");
            }
            if (plantSearch.target() != null) {
                return guideFieldTarget(level, villager, context, plantSearch.target(), "field_plant_target_found");
            }
        }

        finishFullFieldScanWithNoTargets(level, context, jobSite);
        DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
        if (depositResult == DepositResult.MOVING) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.farming.no_target_depositing");
        }
        if (depositResult == DepositResult.STORAGE_FULL) {
            return WorkResult.idle(storageFullStatus(context));
        }
        clearSecondaryJobSite(villager);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.idle("interaction.work.farming.waiting_for_growth");
    }

    private WorkResult guideFieldTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target,
            String scanResult) {
        seedSecondaryJobSite(level, villager, target);
        HiredWorkerBrain.setLastTargetScanResult(context, scanResult);
        if (isInVanillaFieldWorkRange(villager, target)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            if (isHarvestableVanillaFieldTarget(level, target)) {
                return harvestCrop(level, villager, context, target);
            }
            if (isHarvestableBlockOutputTarget(level, target)) {
                return harvestBlockOutput(level, villager, context, target);
            }
            if (isPlantableVanillaFieldTarget(level, target)) {
                return plantCrop(level, villager, context, target);
            }
            HiredWorkerBrain.setLastTargetScanResult(context, "field_target_changed");
            setTaskState(context, HiredWorkerTaskState.IDLE, target);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        return moveToFieldTarget(
                level,
                villager,
                context,
                target,
                target,
                "interaction.work.farming.moving_to_crop",
                "interaction.work.farming.crop_blocked");
    }

    private WorkResult harvestCrop(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos cropTarget) {
        ItemStack hoe = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        BlockState state = level.getBlockState(cropTarget);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "harvest_target_changed");
            setTaskState(context, HiredWorkerTaskState.IDLE, cropTarget);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        List<ItemStack> drops = Block.getDrops(state, level, cropTarget, level.getBlockEntity(cropTarget), villager, hoe);
        if (!context.inventory().canStorePlainOutputs(drops)) {
            OutputFullHandling outputFull = handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    FIELD_GUIDE_WALK_SPEED,
                    cropTarget,
                    "interaction.work.farming.output_full_depositing",
                    "interaction.work.farming.output_full_blocked");
            if (outputFull.handled()) {
                return outputFull.result();
            }
            if (!context.inventory().canStorePlainOutputs(drops)) {
                return WorkResult.idle("interaction.work.farming.output_full_blocked");
            }
        }

        faceBlock(villager, cropTarget);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, hoe, villager, villager, EquipmentSlot.MAINHAND, cropTarget.getCenter(), state, ignored -> {
        });
        level.destroyBlock(cropTarget, false, villager);
        HiredPathMemory.onBlockChanged(level, cropTarget);
        HiredPathMemory.rememberRecent(level, cropTarget);

        if (!HiredFarmingInventoryBridge.storeFarmDrops(villager, context.inventory(), drops)) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, cropTarget);
            return WorkResult.idle("interaction.work.farming.output_full_blocked");
        }

        ItemStack plantingItem = HiredFarmingInventoryBridge.plantingItem(villager, context);
        boolean replanted = HiredFarmingInventoryBridge.plantFromJobInventory(level, villager, context, cropTarget);
        if (replanted) {
            swingWorkItem(level, villager, plantingItem);
        }
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.IDLE, cropTarget);
        return WorkResult.progressedWithPractice(
                replanted
                        ? "interaction.work.farming.completed_crop"
                        : "interaction.work.farming.completed_output",
                HiredWorkPractice.farming(replanted ? "harvest_replant" : "harvest"));
    }

    private WorkResult harvestBlockOutput(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos target) {
        ItemStack hoe = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        BlockState state = level.getBlockState(target);
        if (!isHarvestableBlockOutputTarget(level, target)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "harvest_target_changed");
            setTaskState(context, HiredWorkerTaskState.IDLE, target);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), villager, hoe);
        if (!context.inventory().canStorePlainOutputs(drops)) {
            OutputFullHandling outputFull = handleOutputFullInventory(
                    level,
                    context,
                    villager,
                    FIELD_GUIDE_WALK_SPEED,
                    target,
                    "interaction.work.farming.output_full_depositing",
                    "interaction.work.farming.output_full_blocked");
            if (outputFull.handled()) {
                return outputFull.result();
            }
            if (!context.inventory().canStorePlainOutputs(drops)) {
                return WorkResult.idle("interaction.work.farming.output_full_blocked");
            }
        }

        faceBlock(villager, target);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(
                level,
                hoe,
                villager,
                villager,
                EquipmentSlot.MAINHAND,
                target.getCenter(),
                state,
                ignored -> {
                });
        level.destroyBlock(target, false, villager);
        HiredPathMemory.onBlockChanged(level, target);
        HiredPathMemory.rememberRecent(level, target);
        if (!HiredFarmingInventoryBridge.storeFarmDrops(villager, context.inventory(), drops)) {
            HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, target);
            return WorkResult.idle("interaction.work.farming.output_full_blocked");
        }
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.IDLE, target);
        return WorkResult.progressedWithPractice(
                "interaction.work.farming.completed_output", HiredWorkPractice.farming("harvest"));
    }

    private WorkResult plantCrop(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos cropTarget) {
        if (!isPlantableVanillaFieldTarget(level, cropTarget)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "plant_target_changed");
            setTaskState(context, HiredWorkerTaskState.IDLE, cropTarget);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }
        ItemStack plantingItem = HiredFarmingInventoryBridge.plantingItem(villager, context);
        if (!HiredFarmingInventoryBridge.plantFromJobInventory(level, villager, context, cropTarget)) {
            HiredWorkerBrain.setFailure(context, "missing_planting_item", 0L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, cropTarget);
            return WorkResult.idle("interaction.work.farming.missing_planting_item");
        }

        faceBlock(villager, cropTarget);
        swingWorkItem(level, villager, plantingItem);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.IDLE, cropTarget);
        return WorkResult.progressedWithPractice(
                "interaction.work.farming.tending_fields", HiredWorkPractice.farming("plant"));
    }

    private WorkResult guideSoilTillTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos soilTarget) {
        HiredWorkerBrain.setLastTargetScanResult(context, "till_target_found");
        BlockPos standTarget = soilTarget.above();
        if (!isInVanillaFieldWorkRange(villager, standTarget)) {
            clearSecondaryJobSite(villager);
            return moveToFieldTarget(
                    level,
                    villager,
                    context,
                    standTarget,
                    soilTarget,
                    "interaction.work.farming.moving_to_soil",
                    "interaction.work.farming.crop_blocked");
        }
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        return tillSoil(level, villager, context, soilTarget);
    }

    private WorkResult moveToFieldTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos navigationTarget,
            BlockPos workTarget,
            String movingStatus,
            String blockedStatus) {
        if (navigationTarget == null || workTarget == null) {
            setTaskState(context, HiredWorkerTaskState.IDLE);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        BlockPos currentNavigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && navigationTarget.equals(currentNavigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    navigationTarget,
                    villager.distanceToSqr(navigationTarget.getCenter()))) {
                VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredPathMemory.clearNavigationProgress(villager);
                HiredPathMemory.rememberUnreachableApproach(level, villager, navigationTarget);
            } else {
                HiredWorkerBrain.clearFailure(context);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, workTarget);
                return WorkResult.progressed(movingStatus);
            }
        }

        if (HiredPathMemory.shouldDelayPathSearch(level, villager)
                || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, navigationTarget)) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            setTaskState(context, HiredWorkerTaskState.IDLE, workTarget);
            return WorkResult.progressed(blockedStatus);
        }

        Path path = HiredPathMemory.createPath(level, villager, navigationTarget, FIELD_GUIDE_CLOSE_ENOUGH);
        if (pathMakesProgressTowardField(villager, path, navigationTarget)) {
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(
                    villager,
                    path,
                    navigationTarget,
                    FIELD_GUIDE_WALK_SPEED,
                    FIELD_GUIDE_CLOSE_ENOUGH);
            if (moved) {
                HiredPathMemory.clearFailure(villager, workTarget);
                HiredPathMemory.clearUnreachableApproach(villager, navigationTarget);
                HiredPathMemory.clearPathSearchFailures(villager);
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        navigationTarget,
                        villager.distanceToSqr(navigationTarget.getCenter()));
                HiredWorkerBrain.clearFailure(context);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, workTarget);
                return WorkResult.progressed(movingStatus);
            }
        }
        if (path == null) {
            VillagerTaskNavigationUtil.setHiredWalkTarget(
                    villager,
                    navigationTarget,
                    FIELD_GUIDE_WALK_SPEED,
                    FIELD_GUIDE_CLOSE_ENOUGH);
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, workTarget);
            return WorkResult.progressed(movingStatus);
        }

        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredPathMemory.clearNavigationProgress(villager);
        HiredPathMemory.rememberUnreachableApproach(level, villager, navigationTarget);
        HiredPathMemory.recordFailure(level, villager, workTarget);
        HiredWorkerBrain.setLastTargetScanResult(context, "field_target_unreachable");
        setTaskState(context, HiredWorkerTaskState.IDLE, workTarget);
        return WorkResult.progressed(blockedStatus);
    }

    private static boolean pathMakesProgressTowardField(Villager villager, Path path, BlockPos target) {
        if (path == null || path.getEndNode() == null) {
            return false;
        }
        BlockPos end = path.getEndNode().asBlockPos();
        boolean endsInWorkRange = Math.abs(end.getX() - target.getX()) <= FIELD_GUIDE_CLOSE_ENOUGH
                && Math.abs(end.getY() - target.getY()) <= FIELD_GUIDE_CLOSE_ENOUGH
                && Math.abs(end.getZ() - target.getZ()) <= FIELD_GUIDE_CLOSE_ENOUGH;
        return path.canReach()
                || endsInWorkRange
                || end.distSqr(target) < villager.blockPosition().distSqr(target);
    }

    private WorkResult tillSoil(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos soilTarget) {
        ItemStack hoe = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
        if (hoe.isEmpty() || !FarmerHoeRequirement.isHoe(hoe)) {
            HiredWorkerBrain.setFailure(context, "missing_hoe", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
            return WorkResult.idle("interaction.work.farming.missing_hoe");
        }

        BlockState tilledState = tillModifiedState(level, soilTarget, hoe);
        if (tilledState == null || !isTillableSoilTarget(level, context, soilTarget, hoe)) {
            HiredWorkerBrain.setLastTargetScanResult(context, "till_target_changed");
            setTaskState(context, HiredWorkerTaskState.IDLE);
            return WorkResult.idle("interaction.work.farming.target_changed");
        }

        faceBlock(villager, soilTarget);
        swingWorkTool(villager);
        level.playSound(null, soilTarget, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.setBlock(soilTarget, tilledState, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, soilTarget, GameEvent.Context.of(villager, tilledState));
        HiredPathMemory.onBlockChanged(level, soilTarget);
        damageTool(context, villager, hoe);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.IDLE, soilTarget);
        return WorkResult.progressedWithPractice(
                "interaction.work.farming.tilled_soil", HiredWorkPractice.farming("till"));
    }

    private WorkResult ensureFarmingHoe(ServerLevel level, Villager villager, HiredWorkContext context) {
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                FarmerHoeRequirement::isHoe,
                FarmerHoeRequirement::hoeScore,
                0.45D);
        return switch (toolResult.status()) {
            case READY -> null;
            case COLLECTED -> {
                setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, returnTarget(level, villager, context));
                yield WorkResult.progressed("interaction.work.farming.collected_hoe");
            }
            case MOVING -> WorkResult.progressed("interaction.work.farming.collecting_hoe");
            case MISSING -> {
                HiredWorkerBrain.setFailure(context, "missing_hoe", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL);
                yield WorkResult.idle("interaction.work.farming.missing_hoe");
            }
            case UNREACHABLE -> {
                HiredWorkerBrain.setFailure(context, "farming_hoe_storage_unreachable", level.getGameTime() + 20L * 30L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                yield WorkResult.idle("interaction.work.farming.hoe_unreachable");
            }
            case INVENTORY_FULL -> {
                HiredWorkerBrain.setFailure(context, "farming_hoe_inventory_full", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY);
                yield WorkResult.idle("interaction.work.farming.hoe_inventory_full");
            }
        };
    }

    private static void activateFarmerWorkBrain(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(Activity.WORK)) {
            brain.setActiveActivityIfPossible(Activity.WORK);
        }
    }

    private static boolean canUseVanillaFarmerBrain(ServerLevel level, Villager villager) {
        return villager.getVillagerData().getProfession() == VillagerProfession.FARMER
                && HiredVillagerWorkService.hasClaimedJobSiteInLevel(level, villager)
                && FarmerHoeRequirement.hasHoe(villager);
    }

    private FieldSearchResult findNextFieldTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos jobSite,
            boolean harvestOnly) {
        if (context.hasWorkArea()) {
            String cursorTag = harvestOnly ? FIELD_HARVEST_SCAN_CURSOR_TAG : FIELD_PLANT_SCAN_CURSOR_TAG;
            HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                    context,
                    cursorTag,
                    MAX_FIELD_SCAN_POSITIONS_PER_WORK_TICK,
                    pos -> context.isLoaded(level, pos)
                            && fieldTargetForSearchPos(level, context, pos, harvestOnly) != null);
            BlockPos target = bestFieldTarget(level, villager, context, scan.candidates(), harvestOnly);
            if (target != null) {
                clearFieldScanState(context);
                context.state().remove(NEXT_FIELD_SCAN_GAME_TIME_TAG);
                return FieldSearchResult.target(target);
            }
            if (!scan.completedFullPass()) {
                String phase = harvestOnly ? "harvest" : "plant";
                HiredWorkerBrain.setLastTargetScanResult(context, "field_" + phase + "_scan_partial_" + scan.visitedPositions());
                return FieldSearchResult.inProgress();
            }

            return FieldSearchResult.empty();
        }

        BlockPos target = bestFieldTarget(villager, fieldTargetsAroundJobSite(level, jobSite, harvestOnly));
        return target == null ? FieldSearchResult.empty() : FieldSearchResult.target(target);
    }

    private FieldSearchResult findNextTillTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemStack hoe) {
        if (!context.hasWorkArea()) {
            return FieldSearchResult.empty();
        }

        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                FIELD_TILL_SCAN_CURSOR_TAG,
                MAX_FIELD_SCAN_POSITIONS_PER_WORK_TICK,
                pos -> tillTargetForSearchPos(level, context, pos, hoe) != null);
        BlockPos target = bestTillTarget(level, villager, context, scan.candidates(), hoe);
        if (target != null) {
            clearFieldScanState(context);
            context.state().remove(NEXT_FIELD_SCAN_GAME_TIME_TAG);
            return FieldSearchResult.target(target);
        }
        if (!scan.completedFullPass()) {
            HiredWorkerBrain.setLastTargetScanResult(context, "field_till_scan_partial_" + scan.visitedPositions());
            return FieldSearchResult.inProgress();
        }

        return FieldSearchResult.empty();
    }

    private static List<BlockPos> fieldTargetsAroundJobSite(ServerLevel level, BlockPos jobSite, boolean harvestOnly) {
        List<BlockPos> targets = new ArrayList<>();
        if (jobSite == null) {
            return targets;
        }
        BlockPos min = jobSite.offset(
                -JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS,
                -JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS,
                -JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS);
        BlockPos max = jobSite.offset(
                JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS,
                JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS,
                JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS);
        for (BlockPos raw : BlockPos.betweenClosed(min, max)) {
            BlockPos candidate = raw.immutable();
            int dx = candidate.getX() - jobSite.getX();
            int dz = candidate.getZ() - jobSite.getZ();
            if (dx * dx + dz * dz > JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS * JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS) {
                continue;
            }
            boolean matches = harvestOnly
                    ? isHarvestableFieldTarget(level, candidate)
                    : isPlantableVanillaFieldTarget(level, candidate);
            if (matches) {
                targets.add(candidate);
            }
        }
        return targets;
    }

    private static BlockPos bestFieldTarget(Villager villager, List<BlockPos> candidates) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            double score = villager.distanceToSqr(candidate.getCenter());
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private static BlockPos bestFieldTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            boolean harvestOnly) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            BlockPos target = fieldTargetForSearchPos(level, context, candidate, harvestOnly);
            if (target == null) {
                continue;
            }
            if (HiredPathMemory.isAvoided(level, villager, target)
                    || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target)) {
                continue;
            }
            double score = villager.distanceToSqr(target.getCenter());
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private static BlockPos bestTillTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            List<BlockPos> candidates,
            ItemStack hoe) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos candidate : candidates) {
            BlockPos target = tillTargetForSearchPos(level, context, candidate, hoe);
            if (target == null) {
                continue;
            }
            if (HiredPathMemory.isAvoided(level, villager, target)
                    || HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target.above())) {
                continue;
            }
            double score = villager.distanceToSqr(target.above().getCenter());
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best;
    }

    private static boolean isUsableVanillaFieldTarget(ServerLevel level, Villager villager, HiredWorkContext context, BlockPos pos) {
        return isHarvestableFieldTarget(level, pos)
                || HiredFarmingInventoryBridge.hasJobPlantingItem(villager, context) && isPlantableVanillaFieldTarget(level, pos);
    }

    private static BlockPos fieldTargetForSearchPos(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos raw,
            boolean harvestOnly) {
        if (raw == null) {
            return null;
        }
        if (isFieldTargetForSearch(level, context, raw, harvestOnly)) {
            return raw.immutable();
        }
        BlockPos above = raw.above();
        return isFieldTargetForSearch(level, context, above, harvestOnly) ? above.immutable() : null;
    }

    private static boolean isFieldTargetForSearch(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos target,
            boolean harvestOnly) {
        if (target == null || !context.hasWorkArea() || !context.isLoaded(level, target)) {
            return false;
        }
        if (!isInsideFieldWorkArea(context, target) && !isInsideFieldWorkArea(context, target.below())) {
            return false;
        }
        return harvestOnly
                ? isHarvestableFieldTarget(level, target)
                : isPlantableVanillaFieldTarget(level, target);
    }

    private static boolean isHarvestableFieldTarget(ServerLevel level, BlockPos pos) {
        return isHarvestableVanillaFieldTarget(level, pos) || isHarvestableBlockOutputTarget(level, pos);
    }

    private static boolean isHarvestableVanillaFieldTarget(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return false;
    }

    private static boolean isHarvestableBlockOutputTarget(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock) {
            return false;
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON)
                || state.is(BlockTags.CROPS) && !hasImmatureAgeProperty(state);
    }

    private static boolean hasImmatureAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty ageProperty && "age".equals(property.getName())) {
                int currentAge = state.getValue(ageProperty);
                int maxAge = ageProperty.getPossibleValues().stream()
                        .mapToInt(Integer::intValue)
                        .max()
                        .orElse(currentAge);
                return currentAge < maxAge;
            }
        }
        return false;
    }

    private static boolean isPlantableVanillaFieldTarget(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() && isVillagerFarmland(level.getBlockState(pos.below()));
    }

    private static GrowingCropPresence growingCropPresence(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos jobSite) {
        if (context.state().getBoolean(FIELD_GROWING_SCAN_COMPLETE_TAG)) {
            return context.state().getBoolean(FIELD_GROWING_CROP_PRESENT_TAG)
                    ? GrowingCropPresence.PRESENT
                    : GrowingCropPresence.ABSENT;
        }

        GrowingCropPresence result = context.hasWorkArea()
                ? scanGrowingCropsInWorkArea(level, context)
                : scanGrowingCropsAroundJobSite(level, context, jobSite);
        if (result != GrowingCropPresence.SCANNING) {
            context.state().putBoolean(FIELD_GROWING_SCAN_COMPLETE_TAG, true);
            context.state().putBoolean(FIELD_GROWING_CROP_PRESENT_TAG, result == GrowingCropPresence.PRESENT);
        }
        return result;
    }

    private static GrowingCropPresence scanGrowingCropsInWorkArea(ServerLevel level, HiredWorkContext context) {
        HiredWorkAreaScan.Result scan = HiredWorkAreaScan.collect(
                context,
                FIELD_GROWING_SCAN_CURSOR_TAG,
                MAX_FIELD_SCAN_POSITIONS_PER_WORK_TICK,
                pos -> isGrowingCropForSearchPos(level, context, pos));
        if (!scan.candidates().isEmpty()) {
            HiredWorkAreaScan.clearCursor(context, FIELD_GROWING_SCAN_CURSOR_TAG);
            return GrowingCropPresence.PRESENT;
        }
        return scan.completedFullPass() ? GrowingCropPresence.ABSENT : GrowingCropPresence.SCANNING;
    }

    private static GrowingCropPresence scanGrowingCropsAroundJobSite(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos jobSite) {
        if (jobSite == null) {
            return GrowingCropPresence.ABSENT;
        }
        int horizontalSize = JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS * 2 + 1;
        int verticalSize = JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS * 2 + 1;
        long totalPositions = (long) horizontalSize * horizontalSize * verticalSize;
        long index = context.state().contains(FIELD_GROWING_SCAN_CURSOR_TAG)
                ? Math.floorMod(context.state().getLong(FIELD_GROWING_SCAN_CURSOR_TAG), totalPositions)
                : 0L;
        long visited = 0L;
        while (visited < totalPositions && visited < MAX_FIELD_SCAN_POSITIONS_PER_WORK_TICK) {
            int xOffset = (int) (index % horizontalSize) - JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS;
            long zyIndex = index / horizontalSize;
            int zOffset = (int) (zyIndex % horizontalSize) - JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS;
            int yOffset = (int) (zyIndex / horizontalSize) - JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS;
            BlockPos candidate = jobSite.offset(xOffset, yOffset, zOffset);
            if (xOffset * xOffset + zOffset * zOffset
                    <= JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS * JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS
                    && isGrowingFarmCrop(level, candidate)) {
                HiredWorkAreaScan.clearCursor(context, FIELD_GROWING_SCAN_CURSOR_TAG);
                return GrowingCropPresence.PRESENT;
            }
            index = (index + 1L) % totalPositions;
            visited++;
            if (index == 0L) {
                break;
            }
        }
        if (index == 0L) {
            HiredWorkAreaScan.clearCursor(context, FIELD_GROWING_SCAN_CURSOR_TAG);
            return GrowingCropPresence.ABSENT;
        }
        context.state().putLong(FIELD_GROWING_SCAN_CURSOR_TAG, index);
        return GrowingCropPresence.SCANNING;
    }

    private static boolean isGrowingCropForSearchPos(ServerLevel level, HiredWorkContext context, BlockPos raw) {
        if (raw == null) {
            return false;
        }
        if (isGrowingCropInWorkArea(level, context, raw)) {
            return true;
        }
        return isGrowingCropInWorkArea(level, context, raw.above());
    }

    private static boolean isGrowingCropInWorkArea(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        if (pos == null || !context.isLoaded(level, pos)) {
            return false;
        }
        if (!isInsideFieldWorkArea(context, pos) && !isInsideFieldWorkArea(context, pos.below())) {
            return false;
        }
        return isGrowingFarmCrop(level, pos);
    }

    private static boolean isGrowingFarmCrop(ServerLevel level, BlockPos pos) {
        if (pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop) {
            return !crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof StemBlock || state.getBlock() instanceof AttachedStemBlock) {
            return true;
        }
        return state.is(BlockTags.CROPS) && hasImmatureAgeProperty(state);
    }

    private static BlockPos tillTargetForSearchPos(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos raw,
            ItemStack hoe) {
        if (raw == null) {
            return null;
        }
        if (isTillableSoilTarget(level, context, raw, hoe)) {
            return raw.immutable();
        }
        BlockPos below = raw.below();
        return isTillableSoilTarget(level, context, below, hoe) ? below.immutable() : null;
    }

    private static boolean isTillableSoilTarget(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos soilPos,
            ItemStack hoe) {
        if (soilPos == null || !context.hasWorkArea() || !context.isLoaded(level, soilPos)) {
            return false;
        }
        BlockPos cropPos = soilPos.above();
        if (!context.isLoaded(level, cropPos) || !level.getBlockState(cropPos).isAir()) {
            return false;
        }
        if (!isInsideFieldWorkArea(context, soilPos) && !isInsideFieldWorkArea(context, cropPos)) {
            return false;
        }
        return tillModifiedState(level, soilPos, hoe) != null;
    }

    private static BlockState tillModifiedState(ServerLevel level, BlockPos soilPos, ItemStack hoe) {
        UseOnContext useContext = new UseOnContext(
                level,
                null,
                InteractionHand.MAIN_HAND,
                hoe,
                new BlockHitResult(Vec3.atCenterOf(soilPos), Direction.UP, soilPos, false));
        return level.getBlockState(soilPos).getToolModifiedState(useContext, ItemAbilities.HOE_TILL, false);
    }

    private static boolean isVillagerFarmland(BlockState state) {
        return state.getBlock() instanceof FarmBlock
                || state.getBlock().builtInRegistryHolder().is(Tags.Blocks.VILLAGER_FARMLANDS);
    }

    private static BlockPos currentFieldWalkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos jobSite) {
        return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> walkTarget.getTarget().currentBlockPosition())
                .filter(target -> !target.equals(jobSite))
                .filter(target -> isInsideFieldSearchArea(target, context, jobSite))
                .filter(target -> !HiredPathMemory.isAvoided(level, villager, target))
                .filter(target -> !HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target))
                .filter(target -> isUsableVanillaFieldTarget(level, villager, context, target))
                .orElse(null);
    }

    private static BlockPos currentTillWalkTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            ItemStack hoe) {
        if (!HiredFarmingOptions.tillSoil(context.state()) || !context.hasWorkArea()) {
            return null;
        }
        return villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                .map(walkTarget -> walkTarget.getTarget().currentBlockPosition())
                .map(target -> tillTargetForSearchPos(level, context, target, hoe))
                .filter(target -> target != null)
                .filter(target -> !HiredPathMemory.isAvoided(level, villager, target))
                .filter(target -> !HiredPathMemory.isApproachRecentlyUnreachable(level, villager, target.above()))
                .orElse(null);
    }

    private static boolean fieldScanOnCooldown(ServerLevel level, HiredWorkContext context) {
        return context.hasWorkArea()
                && !HiredWorkAreaScan.isInProgress(context, FIELD_HARVEST_SCAN_CURSOR_TAG)
                && !HiredWorkAreaScan.isInProgress(context, FIELD_PLANT_SCAN_CURSOR_TAG)
                && !HiredWorkAreaScan.isInProgress(context, FIELD_TILL_SCAN_CURSOR_TAG)
                && level.getGameTime() < context.state().getLong(NEXT_FIELD_SCAN_GAME_TIME_TAG);
    }

    private static void finishFullFieldScanWithNoTargets(ServerLevel level, HiredWorkContext context, BlockPos jobSite) {
        if (context.hasWorkArea()) {
            context.state().putLong(NEXT_FIELD_SCAN_GAME_TIME_TAG, level.getGameTime() + NO_FIELD_TARGET_SCAN_COOLDOWN_TICKS);
        }
        if (context.state().getBoolean(FIELD_GROWING_SCAN_COMPLETE_TAG)) {
            context.state().remove(FIELD_GROWING_SCAN_COMPLETE_TAG);
            context.state().remove(FIELD_GROWING_CROP_PRESENT_TAG);
        }
        GrowingCropPresence growing = growingCropPresence(level, context, jobSite);
        HiredWorkerBrain.setLastTargetScanResult(
                context,
                growing == GrowingCropPresence.PRESENT
                        ? "field_scan_full_waiting_for_crops"
                        : growing == GrowingCropPresence.SCANNING
                        ? "field_scan_full_scanning_growth"
                        : "field_scan_full_no_targets");
    }

    private static boolean isInsideFieldWorkArea(HiredWorkContext context, BlockPos pos) {
        if (context == null || pos == null) {
            return false;
        }
        HiredJobSite jobSite = context.jobSite();
        if (jobSite.hasAnchor() && !jobSite.workArea().explicitlyAssigned()) {
            return jobSite.isNearAnchor(pos);
        }
        return context.isInsideWorkArea(pos);
    }

    private static boolean isInsideFieldSearchArea(BlockPos pos, HiredWorkContext context, BlockPos jobSite) {
        if (context.hasWorkArea()) {
            return isInsideFieldWorkArea(context, pos)
                    || isInsideFieldWorkArea(context, pos.below())
                    || isInsideFieldWorkArea(context, pos.above());
        }
        if (jobSite == null) {
            return false;
        }
        int dx = pos.getX() - jobSite.getX();
        int dz = pos.getZ() - jobSite.getZ();
        return dx * dx + dz * dz <= JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS * JOB_SITE_FIELD_SCAN_HORIZONTAL_RADIUS
                && Math.abs(pos.getY() - jobSite.getY()) <= JOB_SITE_FIELD_SCAN_VERTICAL_RADIUS;
    }

    private static boolean isInVanillaFieldWorkRange(Villager villager, BlockPos target) {
        BlockPos pos = villager.blockPosition();
        return Math.abs(pos.getX() - target.getX()) <= 1
                && Math.abs(pos.getY() - target.getY()) <= 1
                && Math.abs(pos.getZ() - target.getZ()) <= 1;
    }

    private static void seedSecondaryJobSite(ServerLevel level, Villager villager, BlockPos fieldTarget) {
        BlockPos farmland = fieldTarget.below();
        Brain<Villager> brain = villager.getBrain();
        GlobalPos secondaryJobSite = GlobalPos.of(level.dimension(), farmland);
        if (brain.getMemory(MemoryModuleType.SECONDARY_JOB_SITE)
                .filter(sites -> sites.size() == 1 && secondaryJobSite.equals(sites.getFirst()))
                .isPresent()) {
            return;
        }
        brain.setMemory(MemoryModuleType.SECONDARY_JOB_SITE, List.of(secondaryJobSite));
    }

    private static void clearSecondaryJobSite(Villager villager) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.SECONDARY_JOB_SITE)) {
            villager.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
        }
    }

    private static void clearFieldScanState(HiredWorkContext context) {
        HiredWorkAreaScan.clearCursor(context, FIELD_HARVEST_SCAN_CURSOR_TAG);
        HiredWorkAreaScan.clearCursor(context, FIELD_PLANT_SCAN_CURSOR_TAG);
        HiredWorkAreaScan.clearCursor(context, FIELD_TILL_SCAN_CURSOR_TAG);
        HiredWorkAreaScan.clearCursor(context, FIELD_GROWING_SCAN_CURSOR_TAG);
        context.state().remove(FIELD_GROWING_SCAN_COMPLETE_TAG);
        context.state().remove(FIELD_GROWING_CROP_PRESENT_TAG);
    }

    private static BlockPos returnTarget(ServerLevel level, Villager villager, HiredWorkContext context) {
        BlockPos jobSite = HiredVillagerWorkService.claimedJobSitePos(level, villager);
        return jobSite != null ? jobSite : context.workCenter();
    }

    private enum GrowingCropPresence {
        PRESENT,
        ABSENT,
        SCANNING
    }

    private record FieldSearchResult(BlockPos target, boolean scanInProgress) {
        static FieldSearchResult target(BlockPos target) {
            return new FieldSearchResult(target, false);
        }

        static FieldSearchResult inProgress() {
            return new FieldSearchResult(null, true);
        }

        static FieldSearchResult empty() {
            return new FieldSearchResult(null, false);
        }
    }
}
