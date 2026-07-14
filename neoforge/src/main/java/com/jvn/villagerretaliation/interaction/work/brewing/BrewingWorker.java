package com.jvn.villagerretaliation.interaction.work.brewing;

import com.jvn.villagerretaliation.interaction.VillagerItemText;
import com.jvn.villagerretaliation.util.VillagerLocale;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.mixin.BrewingStandBlockEntityAccessor;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

public final class BrewingWorker extends AbstractBlockWorker {
    private static final String TARGET_ITEM_TAG = "BrewingTargetItem";
    private static final String TARGET_POTION_TAG = "BrewingTargetPotion";
    private static final String REMAINING_TAG = "BrewingRemaining";
    private static final String CONTINUOUS_TAG = "BrewingContinuous";
    private static final String ORDER_CONTRACT_ID_TAG = "BrewingOrderContractId";
    private static final String CACHED_STAND_POS_TAG = "BrewingCachedStandPos";
    private static final String CACHED_WATER_POS_TAG = "BrewingCachedWaterPos";
    private static final String NEXT_STAND_SCAN_GAME_TIME_TAG = "NextBrewingStandScanGameTime";
    private static final String NEXT_WATER_SCAN_GAME_TIME_TAG = "NextBrewingWaterScanGameTime";
    private static final String MISSING_MATERIALS_TAG = "BrewingMissingMaterials";
    private static final String BLOCKED_REASON_TAG = "BrewingBlockedReason";
    private static final long FACILITY_SCAN_COOLDOWN_TICKS = 100L;
    private static final int FUEL_USES_PER_BLAZE_POWDER = 20;
    private static final int FIRST_BOTTLE_SLOT = 0;
    private static final int BOTTLE_SLOT_COUNT = 3;
    private static final int INGREDIENT_SLOT = 3;
    private static final int FUEL_SLOT = 4;
    private static final int MAX_FACILITY_PATH_ATTEMPTS = 12;
    private static final int BREWING_STAND_APPROACH_SEARCH_RADIUS = 4;
    private static final double BREWING_STAND_BODY_REACH_SQR = 16.0D;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.BREWING;
    }

    public static void setOrder(CompoundTag state, ResourceLocation itemId, ResourceLocation potionId, int amount, boolean continuous) {
        setOrder(state, itemId, potionId, amount, continuous, null);
    }

    public static void setOrder(
            CompoundTag state,
            ResourceLocation itemId,
            ResourceLocation potionId,
            int amount,
            boolean continuous,
            UUID contractId) {
        state.putString(TARGET_ITEM_TAG, itemId.toString());
        state.putString(TARGET_POTION_TAG, potionId.toString());
        state.putInt(REMAINING_TAG, Math.max(0, amount));
        state.putBoolean(CONTINUOUS_TAG, continuous);
        if (contractId == null) {
            state.remove(ORDER_CONTRACT_ID_TAG);
        } else {
            state.putUUID(ORDER_CONTRACT_ID_TAG, contractId);
        }
        state.remove("NextWorkGameTime");
        clearBrewingBlocked(state);
    }

    public static boolean hasOrder(CompoundTag state) {
        return state.contains(TARGET_ITEM_TAG, Tag.TAG_STRING)
                && state.contains(TARGET_POTION_TAG, Tag.TAG_STRING)
                && (state.getBoolean(CONTINUOUS_TAG) || state.getInt(REMAINING_TAG) > 0);
    }

    public static String orderSummary(ServerLevel level, CompoundTag state) {
        return orderSummaryKey(level, state);
    }

    public static String orderSummaryKey(ServerLevel level, CompoundTag state) {
        return targetRoute(level, state).isPresent()
                ? "interaction.work.brewing.order_summary"
                : "interaction.work.brewing.no_order";
    }

    public static Map<String, String> orderSummaryReplacements(ServerLevel level, CompoundTag state) {
        return orderSummaryReplacements(level, VillagerLocale.DEFAULT_LOCALE, state);
    }

    public static Map<String, String> orderSummaryReplacements(ServerLevel level, String locale, CompoundTag state) {
        return targetRoute(level, state)
                .map(route -> Map.of(
                        "amount", state.getBoolean(CONTINUOUS_TAG) ? "continuously" : Integer.toString(state.getInt(REMAINING_TAG)),
                        "item", route.output().getHoverName().getString(),
                        "order", orderDescription(
                                level,
                                locale,
                                route.output(),
                                state.getInt(REMAINING_TAG),
                                state.getBoolean(CONTINUOUS_TAG))))
                .orElse(Map.of());
    }

    public static String orderDescription(ServerLevel level, ItemStack output, int amount, boolean continuous) {
        return orderDescription(level, VillagerLocale.DEFAULT_LOCALE, output, amount, continuous);
    }

    public static String orderDescription(
            ServerLevel level,
            String locale,
            ItemStack output,
            int amount,
            boolean continuous) {
        if (continuous) {
            return VillagerItemText.dialogueName(level.getServer(), locale, output) + " continuously";
        }
        return VillagerItemText.stackName(level.getServer(), locale, output.copyWithCount(Math.max(1, amount)));
    }

    public static String missingMaterials(CompoundTag state) {
        return state.getString(MISSING_MATERIALS_TAG);
    }

    public static String blockedReason(CompoundTag state) {
        return state.getString(BLOCKED_REASON_TAG);
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!context.hasWorkArea()) {
            return waitForWorkAreaAssignment(level, villager, context);
        }
        boolean hasAssignedStorage = AssignedStorageService.hasAssignedStorage(level, villager);
        if (hasAssignedStorage) {
            DepositResult depositResult = depositOutputsOrMoveToStorage(level, context, villager, 0.45D);
            if (depositResult == DepositResult.MOVING) {
                return WorkResult.progressed("interaction.work.brewing.depositing_outputs");
            }
            if (depositResult == DepositResult.STORAGE_FULL) {
                return WorkResult.idle(storageFullStatus(context));
            }
        }
        if (!hasOrder(context.state())) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.brewing.choose_order");
        }

        HiredBrewingRecipeCatalog.BrewingRoute route = targetRoute(level, context.state()).orElse(null);
        if (route == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "unknown_brewing_target", 0L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.brewing.recipe_unavailable");
        }

        BlockPos stand = nearestBrewingStand(level, villager, context);
        if (stand == null) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "no_brewing_stand", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.brewing.no_stand");
        }

        int batchSize = nextBatchSize(context.state());
        if (batchSize <= 0) {
            clearOrder(context.state());
            return WorkResult.completed("interaction.work.brewing.order_complete");
        }
        BlockPos water = nearestWaterSource(level, villager, context, stand);
        boolean waterSource = water != null;

        BrewingStandPlan standPlan = BrewingStandPlan.create(level, stand, route, batchSize);
        if (standPlan.hasWrongBottles()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_bottles");
        }
        if (!standPlan.hasFinishedOutput(route) && standPlan.hasWrongIngredient()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_ingredient");
        }
        if (!standPlan.hasFinishedOutput(route) && standPlan.hasWrongFuel()) {
            context.setProgressTicks(0);
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_fuel");
        }

        MaterialPlan materials = MaterialPlan.create(level, villager, context, route, waterSource, standPlan);
        if (!materials.hasEverything()) {
            WorkResult gatheredPartialMaterials = gatherBrewingMaterials(level, villager, context, materials);
            if (gatheredPartialMaterials != null) {
                context.setProgressTicks(0);
                return gatheredPartialMaterials;
            }
            context.setProgressTicks(0);
            setBrewingBlocked(context, "missing_brewing_materials", materials.missingMaterials());
            HiredWorkerBrain.setFailure(context, materials.missingStatus(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle(materials.missingStatus());
        }
        clearBrewingBlocked(context);
        WorkResult gatheredMaterials = gatherBrewingMaterials(level, villager, context, materials);
        if (gatheredMaterials != null) {
            context.setProgressTicks(0);
            return gatheredMaterials;
        }
        WorkResult preparedInputs = prepareBrewingInputs(level, context, route, standPlan, materials);
        if (preparedInputs != null) {
            context.setProgressTicks(0);
            swingWorkTool(villager);
            return preparedInputs;
        }
        if (materials.shouldFillWaterBottles() && materials.missingCarriedWaterBottles(context) > 0) {
            WorkResult waterResult = moveToWaterSourceAndFill(level, villager, context, water, materials.waterBottleCount());
            if (waterResult != null) {
                context.setProgressTicks(0);
                return waterResult;
            }
        }
        if (materials.missingCarriedWaterBottles(context) > 0 && countJobWaterBottles(context) <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_water_bottles", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.brewing.missing_materials");
        }
        HiredPathTarget target = bestBrewingStandTarget(level, villager, context, stand);
        if (target == null) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, stand);
            if (moveNearBrewingStand(level, villager, context, stand, 0.45D)) {
                return WorkResult.progressed("interaction.work.brewing.moving_to_stand");
            }
            HiredWorkerBrain.setFailure(context, "brewing_stand_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
            return WorkResult.idle("interaction.work.brewing.stand_unreachable");
        }
        if (storedWorkTarget(context.state()) != null) {
            clearActiveBreakingTarget(level, context, villager);
        }
        if (!canUseBrewingStandFromCurrentPosition(level, villager, context, stand)) {
            context.setProgressTicks(0);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, stand);
            if (!moveToBrewingStandTarget(level, villager, context, target, 0.45D)
                    && !moveNearBrewingStand(level, villager, context, stand, 0.45D)) {
                if (recordWorkPathFailure(level, villager, stand)) {
                    HiredWorkerBrain.setFailure(context, "brewing_stand_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, stand);
                    return WorkResult.idle("interaction.work.brewing.stand_blocked");
                }
                return WorkResult.progressed("interaction.work.brewing.repositioning_stand");
            }
            return WorkResult.progressed("interaction.work.brewing.moving_to_stand");
        }
        clearWorkPathFailure(villager, stand);
        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, stand);

        context.setProgressTicks(0);
        return workBrewingStand(level, villager, context, route, standPlan.targetBottleCount(), standPlan.collectLimit(context.state()), stand);
    }

    private boolean moveToBrewingStandTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        if (canUseBrewingStandFromCurrentPosition(level, villager, context, target.blockPos())) {
            holdWorkPosition(villager, target);
            return true;
        }
        if (!context.isInsideWorkArea(villager.blockPosition())) {
            stopWorkNavigation(villager);
            return false;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return false;
            }
            return true;
        }

        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        if (villager.distanceToSqr(target.approachPos().getCenter()) <= 2.25D
                && settleIntoApproach(villager, target, speed)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private static boolean moveNearBrewingStand(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand,
            double speed) {
        Path path = HiredPathMemory.createPath(level, villager, stand, 2);
        return path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, stand, speed, 2);
    }

    private static java.util.Optional<HiredBrewingRecipeCatalog.BrewingRoute> targetRoute(ServerLevel level, CompoundTag state) {
        ResourceLocation itemId = ResourceLocation.tryParse(state.getString(TARGET_ITEM_TAG));
        ResourceLocation potionId = ResourceLocation.tryParse(state.getString(TARGET_POTION_TAG));
        return HiredBrewingRecipeCatalog.find(level, itemId, potionId);
    }

    private static BlockPos nearestBrewingStand(ServerLevel level, Villager villager, HiredWorkContext context) {
        CompoundTag state = context.state();
        BlockPos cached = cachedPos(state, CACHED_STAND_POS_TAG);
        if (isValidBrewingStand(level, context, cached)
                && !HiredPathMemory.isAvoided(level, villager, cached)) {
            return cached;
        }
        state.remove(CACHED_STAND_POS_TAG);
        if (level.getGameTime() < state.getLong(NEXT_STAND_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        List<FacilityCandidate> candidates = new ArrayList<>();
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (isValidBrewingStand(level, context, pos)
                    && !HiredPathMemory.isAvoided(level, villager, pos)) {
                candidates.add(new FacilityCandidate(pos, villager.distanceToSqr(pos.getCenter())));
            }
        }
        candidates.sort(Comparator.comparingDouble(FacilityCandidate::score));
        BlockPos fallback = candidates.isEmpty() ? null : candidates.getFirst().pos();
        int attempts = 0;
        for (FacilityCandidate candidate : candidates) {
            double score = brewingStandPathScore(level, villager, context, candidate.pos());
            if (score >= Double.MAX_VALUE) {
                continue;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate.pos();
            }
            if (++attempts >= MAX_FACILITY_PATH_ATTEMPTS) {
                break;
            }
        }
        if (best == null) {
            if (fallback != null) {
                state.putLong(CACHED_STAND_POS_TAG, fallback.asLong());
                state.remove(NEXT_STAND_SCAN_GAME_TIME_TAG);
                return fallback;
            }
            state.putLong(NEXT_STAND_SCAN_GAME_TIME_TAG, level.getGameTime() + FACILITY_SCAN_COOLDOWN_TICKS);
        } else {
            state.putLong(CACHED_STAND_POS_TAG, best.asLong());
            state.remove(NEXT_STAND_SCAN_GAME_TIME_TAG);
        }
        return best;
    }

    private static double brewingStandPathScore(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand) {
        if (stand == null) {
            return Double.MAX_VALUE;
        }
        HiredPathTarget target = bestBrewingStandTarget(level, villager, context, stand);
        if (target == null) {
            return Double.MAX_VALUE;
        }
        return villager.distanceToSqr(target.approachPos().getCenter())
                + HiredMoveToBlockFaceJob.terrainCost(level, target.approachPos())
                + HiredPathMemory.recentCost(villager, stand);
    }

    private static BlockPos nearestWaterSource(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand) {
        CompoundTag state = context.state();
        BlockPos cached = cachedPos(state, CACHED_WATER_POS_TAG);
        if (isValidWaterSource(level, context, cached)
                && !HiredPathMemory.isAvoided(level, villager, cached)) {
            return cached;
        }
        state.remove(CACHED_WATER_POS_TAG);
        if (level.getGameTime() < state.getLong(NEXT_WATER_SCAN_GAME_TIME_TAG)) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        List<FacilityCandidate> candidates = new ArrayList<>();
        for (BlockPos raw : context.workAreaPositions()) {
            BlockPos pos = raw.immutable();
            if (isValidWaterSource(level, context, pos)
                    && !HiredPathMemory.isAvoided(level, villager, pos)) {
                candidates.add(new FacilityCandidate(
                        pos,
                        stand.distSqr(pos) + villager.distanceToSqr(pos.getCenter()) * 0.25D));
            }
        }
        candidates.sort(Comparator.comparingDouble(FacilityCandidate::score));
        BlockPos fallback = candidates.isEmpty() ? null : candidates.getFirst().pos();
        int attempts = 0;
        for (FacilityCandidate candidate : candidates) {
            double score = waterPathScore(level, villager, context, candidate.pos(), stand);
            if (score >= Double.MAX_VALUE) {
                continue;
            }
            if (score < bestScore) {
                bestScore = score;
                best = candidate.pos();
            }
            if (++attempts >= MAX_FACILITY_PATH_ATTEMPTS) {
                break;
            }
        }
        if (best == null) {
            if (fallback != null) {
                state.putLong(CACHED_WATER_POS_TAG, fallback.asLong());
                state.remove(NEXT_WATER_SCAN_GAME_TIME_TAG);
                return fallback;
            }
            state.putLong(NEXT_WATER_SCAN_GAME_TIME_TAG, level.getGameTime() + FACILITY_SCAN_COOLDOWN_TICKS);
        } else {
            state.putLong(CACHED_WATER_POS_TAG, best.asLong());
            state.remove(NEXT_WATER_SCAN_GAME_TIME_TAG);
        }
        return best;
    }

    private static double waterPathScore(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos water,
            BlockPos stand) {
        HiredPathTarget target = bestWaterTarget(level, villager, context, water);
        if (target == null) {
            return Double.MAX_VALUE;
        }
        double standDistance = stand == null ? 0.0D : stand.distSqr(water) * 0.25D;
        return villager.distanceToSqr(target.approachPos().getCenter())
                + standDistance
                + HiredMoveToBlockFaceJob.terrainCost(level, target.approachPos())
                + HiredPathMemory.recentCost(villager, water);
    }

    private static BlockPos cachedPos(CompoundTag state, String tagName) {
        return state.contains(tagName, Tag.TAG_LONG) ? BlockPos.of(state.getLong(tagName)) : null;
    }

    private static boolean isValidBrewingStand(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        return pos != null
                && context.isInsideWorkArea(pos)
                && context.isLoaded(level, pos)
                && level.getBlockState(pos).is(Blocks.BREWING_STAND);
    }

    private static boolean isValidWaterSource(ServerLevel level, HiredWorkContext context, BlockPos pos) {
        if (pos == null || !context.isInsideWorkArea(pos) || !context.isLoaded(level, pos)) {
            return false;
        }
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource();
    }

    private static int standFuelUses(ServerLevel level, BlockPos stand) {
        if (level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity) {
            return Math.max(0, ((BrewingStandBlockEntityAccessor) blockEntity).villagerretaliation$getFuel());
        }
        return 0;
    }

    private static int nextBatchSize(CompoundTag state) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return 3;
        }
        return Math.min(3, Math.max(0, state.getInt(REMAINING_TAG)));
    }

    private static void decrementOrder(CompoundTag state, int brewed) {
        if (state.getBoolean(CONTINUOUS_TAG)) {
            return;
        }
        int remaining = Math.max(0, state.getInt(REMAINING_TAG) - brewed);
        state.putInt(REMAINING_TAG, remaining);
        if (remaining <= 0) {
            clearOrder(state);
        }
    }

    public static void clearOrder(CompoundTag state) {
        state.remove(TARGET_ITEM_TAG);
        state.remove(TARGET_POTION_TAG);
        state.remove(REMAINING_TAG);
        state.remove(CONTINUOUS_TAG);
        state.remove(ORDER_CONTRACT_ID_TAG);
        clearBrewingBlocked(state);
    }

    private static void setBrewingBlocked(HiredWorkContext context, String reason, String missingMaterials) {
        context.state().putString(BLOCKED_REASON_TAG, reason == null ? "" : reason);
        if (missingMaterials == null || missingMaterials.isBlank()) {
            context.state().remove(MISSING_MATERIALS_TAG);
        } else {
            context.state().putString(MISSING_MATERIALS_TAG, missingMaterials);
        }
    }

    private static void clearBrewingBlocked(HiredWorkContext context) {
        clearBrewingBlocked(context.state());
    }

    private static void clearBrewingBlocked(CompoundTag state) {
        state.remove(BLOCKED_REASON_TAG);
        state.remove(MISSING_MATERIALS_TAG);
    }

    private WorkResult moveToWaterSourceAndFill(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos water,
            int requiredWaterBottles) {
        int missingWaterBottles = Math.max(0, requiredWaterBottles - countJobWaterBottles(context));
        if (missingWaterBottles <= 0) {
            return null;
        }
        HiredPathTarget target = bestWaterTarget(level, villager, context, water);
        if (target == null) {
            HiredWorkerBrain.setFailure(context, "brewing_water_source_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, water);
            return WorkResult.idle("interaction.work.brewing.water_unreachable");
        }
        if (!canUseWaterFromCurrentPosition(villager, water)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, water);
            if (!moveToWaterTarget(level, villager, context, target, 0.45D)) {
                if (recordWorkPathFailure(level, villager, water)) {
                    HiredWorkerBrain.setFailure(context, "brewing_water_source_path_failed", level.getGameTime() + 20L * 30L);
                    setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, water);
                    return WorkResult.idle("interaction.work.brewing.water_blocked");
                }
                return WorkResult.progressed("interaction.work.brewing.repositioning_water");
            }
            return WorkResult.progressed("interaction.work.brewing.moving_to_water");
        }
        clearWorkPathFailure(villager, water);
        faceBlock(villager, water);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
        int filled = fillWaterBottles(context, missingWaterBottles);
        if (filled <= 0) {
            HiredWorkerBrain.setFailure(context, "brewing_water_bottle_space", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, water);
            return WorkResult.idle("interaction.work.brewing.water_bottle_space");
        }
        swingWorkTool(villager);
        level.playSound(null, water, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        stopWorkNavigation(villager);
        HiredWorkerBrain.clearTarget(context);
        return null;
    }

    private static HiredPathTarget bestWaterTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos water) {
        if (canUseWaterFromCurrentPosition(villager, water)
                && context.isInsideWorkArea(villager.blockPosition())) {
            return new HiredPathTarget(water.immutable(), villager.blockPosition().immutable(), Vec3.atCenterOf(water));
        }
        HiredPathTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(water.offset(-1, -1, -1), water.offset(1, 1, 1))) {
            BlockPos candidate = raw.immutable();
            if (!context.isInsideWorkArea(candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || candidate.distSqr(water) > 4) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
                continue;
            }
            double score = villager.distanceToSqr(candidate.getCenter())
                    + HiredMoveToBlockFaceJob.pathTraversalCost(level, path)
                    + HiredMoveToBlockFaceJob.terrainCost(level, candidate)
                    + HiredPathMemory.recentCost(villager, water);
            if (score < bestScore) {
                bestScore = score;
                best = new HiredPathTarget(water.immutable(), candidate, Vec3.atCenterOf(water));
            }
        }
        return best;
    }

    private static HiredPathTarget bestBrewingStandTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand) {
        if (stand == null || !isValidBrewingStand(level, context, stand)) {
            return null;
        }
        Vec3 currentHit = brewingStandHitFrom(level, villager, villager.getEyePosition(), stand);
        if (canUseBrewingStandFromCurrentPosition(level, villager, context, stand, currentHit)) {
            return new HiredPathTarget(stand.immutable(), villager.blockPosition().immutable(), currentHit);
        }

        HiredPathTarget best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos raw : BlockPos.betweenClosed(
                stand.offset(-BREWING_STAND_APPROACH_SEARCH_RADIUS, -2, -BREWING_STAND_APPROACH_SEARCH_RADIUS),
                stand.offset(BREWING_STAND_APPROACH_SEARCH_RADIUS, 2, BREWING_STAND_APPROACH_SEARCH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (!context.isInsideWorkArea(candidate)
                    || candidate.equals(villager.blockPosition())
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)
                    || HiredPathMemory.isAvoided(level, villager, candidate)) {
                continue;
            }
            Vec3 eye = new Vec3(
                    candidate.getX() + 0.5D,
                    candidate.getY() + villager.getEyeHeight(),
                    candidate.getZ() + 0.5D);
            Vec3 hit = brewingStandHitFrom(level, villager, eye, stand);
            if (!canUseBrewingStandFrom(level, candidate, eye, stand, hit)) {
                continue;
            }
            Path path = HiredPathMemory.createPath(level, villager, candidate, 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)) {
                continue;
            }
            double score = villager.distanceToSqr(candidate.getCenter())
                    + HiredMoveToBlockFaceJob.pathTraversalCost(level, path)
                    + HiredMoveToBlockFaceJob.terrainCost(level, candidate)
                    + HiredPathMemory.recentCost(villager, stand);
            if (score < bestScore) {
                bestScore = score;
                best = new HiredPathTarget(stand.immutable(), candidate, hit);
            }
        }
        return best;
    }

    private static boolean canUseBrewingStandFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand) {
        return canUseBrewingStandFromCurrentPosition(
                level,
                villager,
                context,
                stand,
                brewingStandHitFrom(level, villager, villager.getEyePosition(), stand));
    }

    private static boolean canUseBrewingStandFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos stand,
            Vec3 hit) {
        return context.isInsideWorkArea(villager.blockPosition())
                && isValidBrewingStand(level, context, stand)
                && canUseBrewingStandFrom(level, villager.blockPosition(), villager.getEyePosition(), stand, hit);
    }

    private static boolean canUseBrewingStandFrom(
            ServerLevel level,
            BlockPos bodyPos,
            Vec3 eye,
            BlockPos stand,
            Vec3 hit) {
        return hit != null
                && level.hasChunkAt(stand)
                && eye.distanceToSqr(hit) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR
                && bodyPos.getCenter().distanceToSqr(hit) <= BREWING_STAND_BODY_REACH_SQR;
    }

    private static Vec3 brewingStandHitFrom(ServerLevel level, Villager villager, Vec3 eye, BlockPos stand) {
        if (stand == null || !level.hasChunkAt(stand)) {
            return null;
        }
        Vec3 hit = HiredMoveToBlockFaceJob.visibleHitPosition(level, villager, eye, stand);
        if (hit != null) {
            return hit;
        }
        Vec3 center = Vec3.atCenterOf(stand);
        return HiredMoveToBlockFaceJob.hasLineOfSightToBlock(level, villager, eye, stand, center) ? center : null;
    }

    private static boolean canUseWaterFromCurrentPosition(Villager villager, BlockPos water) {
        return villager.getEyePosition().distanceToSqr(water.getCenter()) <= HiredMoveToBlockFaceJob.MAX_REACH_SQR
                && villager.position().distanceToSqr(water.getCenter()) <= 16.0D;
    }

    private boolean moveToWaterTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            double speed) {
        if (!context.isInsideWorkArea(target.blockPos())
                || !context.isInsideWorkArea(target.approachPos())
                || !context.isLoaded(level, target.blockPos())
                || !context.isLoaded(level, target.approachPos())) {
            return false;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return false;
            }
            return true;
        }
        Path path = HiredPathMemory.createPath(level, villager, target.approachPos(), 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, context::isInsideWorkArea)
                && VillagerTaskNavigationUtil.moveToHiredPath(villager, path, target.approachPos(), speed, 0)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return true;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return false;
    }

    private WorkResult prepareBrewingInputs(
            ServerLevel level,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            BrewingStandPlan standPlan,
            MaterialPlan materials) {
        for (int i = standPlan.nextIngredientIndex(); i < route.ingredients().size(); i++) {
            Item ingredient = route.ingredients().get(i);
            if (countJobItem(context, ingredient) > 0) {
                continue;
            }
            if (!HiredSupplyCrafting.craftCarriedSupplyItem(level, context, ingredient)) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_ingredient", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.idle("interaction.work.brewing.missing_materials");
            }
            return WorkResult.progressed("interaction.work.brewing.crafted_ingredients");
        }
        if (standPlan.needsFuelForBrewing(route) && countJobItem(context, Items.BLAZE_POWDER) <= 0) {
            if (!HiredSupplyCrafting.craftCarriedSupplyItem(level, context, Items.BLAZE_POWDER)) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_fuel", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                return WorkResult.idle("interaction.work.brewing.missing_materials");
            }
            return WorkResult.progressed("interaction.work.brewing.crafted_blaze_powder");
        }
        if (materials.shouldFillWaterBottles() && materials.missingCarriedWaterBottles(context) > 0) {
            int neededGlassBottles = materials.missingCarriedWaterBottles(context);
            if (countJobItem(context, Items.GLASS_BOTTLE) < neededGlassBottles) {
                if (!HiredSupplyCrafting.craftCarriedSupplyItem(level, context, Items.GLASS_BOTTLE)) {
                    HiredWorkerBrain.setFailure(context, "missing_brewing_water_bottles", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
                    return WorkResult.idle("interaction.work.brewing.missing_materials");
                }
                return WorkResult.progressed("interaction.work.brewing.crafted_ingredients");
            }
        }
        return null;
    }

    private WorkResult workBrewingStand(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            int batchSize,
            int collectLimit,
            BlockPos stand) {
        if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity)) {
            HiredWorkerBrain.setFailure(context, "no_brewing_stand", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.SELECTING_TARGET);
            return WorkResult.idle("interaction.work.brewing.no_stand");
        }

        int collected = collectFinishedPotions(level, villager, context, route, blockEntity, stand, collectLimit);
        if (collected > 0) {
            boolean completesOrder = completesOrder(context.state(), collected);
            decrementOrder(context.state(), collected);
            level.playSound(null, stand, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 1.0F);
            setTaskState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, stand);
            Map<String, String> replacements = Map.of(
                    "count", Integer.toString(collected),
                    "item", route.output().getHoverName().getString());
            return completesOrder
                    ? WorkResult.completed("interaction.work.brewing.collected_output", replacements)
                    : WorkResult.skilledProgress("interaction.work.brewing.collected_output", replacements);
        }

        int currentStep = currentBrewingStep(level, blockEntity, route);
        if (currentStep < 0) {
            HiredWorkerBrain.setFailure(context, "brewing_stand_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_bottles");
        }
        int loadedBottles = loadedBottleCount(blockEntity);
        if (loadedBottles > batchSize) {
            if (currentStep == 0 && blockEntity.getItem(INGREDIENT_SLOT).isEmpty()) {
                int unloaded = unloadExtraWaterBottlesFromStand(level, context, blockEntity, stand, loadedBottles - batchSize);
                if (unloaded <= 0) {
                    HiredWorkerBrain.setFailure(context, "brewing_water_bottle_space", level.getGameTime() + 100L);
                    setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
                    return WorkResult.idle("interaction.work.brewing.clear_bottle_space");
                }
                swingWorkTool(villager);
                return WorkResult.progressed("interaction.work.brewing.cleared_extra_bottles");
            }
            return WorkResult.progressed("interaction.work.brewing.finishing_larger_batch");
        }
        if (loadedBottles < batchSize) {
            if (loadedBottles > 0 && (currentStep > 0 || !blockEntity.getItem(INGREDIENT_SLOT).isEmpty())) {
                return WorkResult.progressed("interaction.work.brewing.waiting_current_batch");
            }
            int loaded = loadWaterBottlesIntoStand(level, context, blockEntity, stand, batchSize - loadedBottles);
            if (loaded <= 0) {
                HiredWorkerBrain.setFailure(context, "missing_brewing_water_bottles", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
                return WorkResult.idle("interaction.work.brewing.missing_materials");
            }
            swingWorkTool(villager);
            return WorkResult.progressed("interaction.work.brewing.loaded_water_bottles");
        }

        if (currentStep >= route.ingredients().size()) {
            return WorkResult.progressed("interaction.work.brewing.ready_to_collect");
        }

        Item nextIngredient = route.ingredients().get(currentStep);
        ItemStack ingredientSlot = blockEntity.getItem(INGREDIENT_SLOT);
        if (!ingredientSlot.isEmpty()) {
            if (ingredientSlot.is(nextIngredient)) {
                return WorkResult.progressed("interaction.work.brewing.waiting_stand");
            }
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_ingredient");
        }

        WorkResult fuelResult = ensureStandFuel(level, villager, context, blockEntity, stand);
        if (fuelResult != null) {
            return fuelResult;
        }

        int consumed = context.inventory().consumeSupply(stack -> stack.is(nextIngredient), 1);
        if (consumed <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_ingredient", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.missing_materials");
        }
        blockEntity.setItem(INGREDIENT_SLOT, new ItemStack(nextIngredient));
        updateBrewingStand(level, blockEntity, stand);
        swingWorkTool(villager);
        return WorkResult.progressed("interaction.work.brewing.loaded_ingredient");
    }

    private static boolean completesOrder(CompoundTag state, int brewed) {
        return !state.getBoolean(CONTINUOUS_TAG)
                && Math.max(0, state.getInt(REMAINING_TAG) - brewed) <= 0;
    }

    private WorkResult ensureStandFuel(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand) {
        if (standFuelUses(level, stand) > 0) {
            return null;
        }
        ItemStack fuelSlot = blockEntity.getItem(FUEL_SLOT);
        if (!fuelSlot.isEmpty()) {
            if (fuelSlot.is(Items.BLAZE_POWDER)) {
                return null;
            }
            HiredWorkerBrain.setFailure(context, "brewing_stand_wrong_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.wrong_fuel");
        }
        int consumed = context.inventory().consumeSupply(stack -> stack.is(Items.BLAZE_POWDER), 1);
        if (consumed <= 0) {
            HiredWorkerBrain.setFailure(context, "missing_brewing_fuel", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, stand);
            return WorkResult.idle("interaction.work.brewing.missing_materials");
        }
        blockEntity.setItem(FUEL_SLOT, new ItemStack(Items.BLAZE_POWDER));
        updateBrewingStand(level, blockEntity, stand);
        swingWorkTool(villager);
        return WorkResult.progressed("interaction.work.brewing.loaded_blaze_powder");
    }

    private static int loadWaterBottlesIntoStand(
            ServerLevel level,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int count) {
        int loaded = 0;
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT && loaded < count; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) {
                continue;
            }
            if (context.inventory().consumeSupply(HiredBrewingRecipeCatalog::isWaterPotion, 1) <= 0) {
                break;
            }
            blockEntity.setItem(slot, PotionContents.createItemStack(Items.POTION, Potions.WATER));
            loaded++;
        }
        if (loaded > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return loaded;
    }

    private static int unloadExtraWaterBottlesFromStand(
            ServerLevel level,
            HiredWorkContext context,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int count) {
        int unloaded = 0;
        for (int slot = FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT - 1; slot >= FIRST_BOTTLE_SLOT && unloaded < count; slot--) {
            ItemStack stack = blockEntity.getItem(slot);
            if (!HiredBrewingRecipeCatalog.isWaterPotion(stack)) {
                continue;
            }
            ItemStack bottle = stack.copyWithCount(1);
            if (!context.inventory().insertSupply(bottle).isEmpty()) {
                break;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                blockEntity.setItem(slot, ItemStack.EMPTY);
            }
            unloaded++;
        }
        if (unloaded > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return unloaded;
    }

    private int collectFinishedPotions(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            BrewingStandBlockEntity blockEntity,
            BlockPos stand,
            int maxCount) {
        int collected = 0;
        int remaining = Math.max(0, maxCount);
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT && remaining > 0; slot++) {
            ItemStack stack = blockEntity.getItem(slot);
            if (stack.isEmpty() || !samePotionStack(stack, route.output())) {
                continue;
            }
            ItemStack output = stack.copyWithCount(1);
            if (!context.storeOutputAfterDepositIfFull(villager, output).isEmpty()) {
                HiredWorkerBrain.setFailure(context, "brewing_output_full_after_brew", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, stand);
                break;
            }
            stack.shrink(1);
            remaining--;
            if (stack.isEmpty()) {
                blockEntity.setItem(slot, ItemStack.EMPTY);
            }
            collected++;
        }
        if (collected > 0) {
            updateBrewingStand(level, blockEntity, stand);
        }
        return collected;
    }

    private static int currentBrewingStep(
            ServerLevel level,
            BrewingStandBlockEntity blockEntity,
            HiredBrewingRecipeCatalog.BrewingRoute route) {
        int loaded = loadedBottleCount(blockEntity);
        if (loaded <= 0) {
            return 0;
        }
        for (int step = 0; step <= route.ingredients().size(); step++) {
            ItemStack expected = routeStackAt(level, route, step);
            boolean matches = true;
            for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT; slot++) {
                ItemStack stack = blockEntity.getItem(slot);
                if (!stack.isEmpty() && !samePotionStack(stack, expected)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return step;
            }
        }
        return -1;
    }

    private static int loadedBottleCount(BrewingStandBlockEntity blockEntity) {
        int count = 0;
        for (int slot = FIRST_BOTTLE_SLOT; slot < FIRST_BOTTLE_SLOT + BOTTLE_SLOT_COUNT; slot++) {
            if (!blockEntity.getItem(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static ItemStack routeStackAt(
            ServerLevel level,
            HiredBrewingRecipeCatalog.BrewingRoute route,
            int step) {
        ItemStack stack = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        int clampedStep = Math.min(Math.max(0, step), route.ingredients().size());
        for (int i = 0; i < clampedStep; i++) {
            stack = level.potionBrewing().mix(new ItemStack(route.ingredients().get(i)), stack);
        }
        return stack.copyWithCount(1);
    }

    private static boolean samePotionStack(ItemStack first, ItemStack second) {
        return !first.isEmpty()
                && !second.isEmpty()
                && ItemStack.isSameItemSameComponents(first.copyWithCount(1), second.copyWithCount(1));
    }

    private static void updateBrewingStand(ServerLevel level, BrewingStandBlockEntity blockEntity, BlockPos stand) {
        blockEntity.setChanged();
        BlockState state = level.getBlockState(stand);
        level.sendBlockUpdated(stand, state, state, 3);
    }

    private static int fillWaterBottles(HiredWorkContext context, int count) {
        int filled = 0;
        for (int i = 0; i < count; i++) {
            if (countJobItem(context, Items.GLASS_BOTTLE) <= 0) {
                break;
            }
            ItemStack waterBottle = PotionContents.createItemStack(Items.POTION, Potions.WATER);
            if (!HiredSupplyCrafting.canInsertSupply(context, waterBottle)
                    && !HiredSupplyCrafting.willConsumeOnlyCarriedSupplyStack(context, Items.GLASS_BOTTLE)) {
                break;
            }
            if (context.inventory().consumeSupply(stack -> stack.is(Items.GLASS_BOTTLE), 1) <= 0) {
                break;
            }
            ItemStack remainder = context.inventory().insertSupply(waterBottle);
            if (!remainder.isEmpty()) {
                context.inventory().insertSupply(new ItemStack(Items.GLASS_BOTTLE));
                break;
            }
            filled++;
        }
        return filled;
    }

    private WorkResult gatherBrewingMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            MaterialPlan materials) {
        List<StorageNeed> needs = materials.storageNeeds(context);
        if (needs.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return null;
        }
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            setBrewingBlocked(context, "missing_brewing_materials", materials.materialsSummary(context));
            HiredWorkerBrain.setFailure(context, "missing_brewing_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.brewing.missing_materials");
        }
        Predicate<ItemStack> storageNeedFilter = stack -> matchesAnyNeed(needs, stack);
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, storageNeedFilter);
        if (storage == null) {
            setBrewingBlocked(context, "missing_brewing_materials", materials.materialsSummary(context));
            HiredWorkerBrain.setFailure(context, "missing_brewing_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
            return WorkResult.idle("interaction.work.brewing.missing_materials");
        }
        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                0.45D);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return WorkResult.progressed("interaction.work.brewing.collecting_materials");
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            BlockPos failedStorage = storage;
            for (BlockPos alternateStorage : AssignedStorageService.assignedStoragePositionsContaining(
                    level,
                    villager,
                    storageNeedFilter)) {
                if (failedStorage.equals(alternateStorage)) {
                    continue;
                }
                HiredWorkerBrain.setStorageTarget(context, alternateStorage);
                HiredStorageNavigationGoal.Result alternateMoveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                        level,
                        context,
                        villager,
                        alternateStorage,
                        0.45D);
                if (alternateMoveResult == HiredStorageNavigationGoal.Result.MOVING) {
                    HiredWorkerBrain.clearFailure(context);
                    setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                    return WorkResult.progressed("interaction.work.brewing.collecting_materials");
                }
                if (alternateMoveResult == HiredStorageNavigationGoal.Result.ARRIVED) {
                    storage = alternateStorage;
                    HiredWorkerBrain.setStorageTarget(context, storage);
                    moveResult = HiredStorageNavigationGoal.Result.ARRIVED;
                    break;
                }
            }
            if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
                HiredWorkerBrain.setStorageTarget(context, failedStorage);
                setBrewingBlocked(context, "brewing_storage_path_failed", materials.materialsSummary(context));
                HiredWorkerBrain.setFailure(context, "brewing_storage_path_failed", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, failedStorage);
                return WorkResult.idle("interaction.work.brewing.materials_unreachable");
            }
        }
        faceBlock(villager, storage);
        int movedTotal = 0;
        for (StorageNeed need : needs) {
            movedTotal += AssignedStorageService.transferItemsAtAssignedStorage(
                    villager,
                    storage,
                    need.predicate(),
                    need.count(),
                    context.inventory()::insertSupplyFromStorage);
        }
        if (movedTotal <= 0) {
            setBrewingBlocked(context, "brewing_material_inventory_full", materials.materialsSummary(context));
            HiredWorkerBrain.setFailure(context, "brewing_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return WorkResult.idle("interaction.work.brewing.material_inventory_full");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.clearFailure(context);
        clearBrewingBlocked(context);
        stopWorkNavigation(villager);
        setTaskState(context, HiredWorkerTaskState.RETURNING_TO_WORK_AREA, context.workCenter());
        return WorkResult.progressed("interaction.work.brewing.gathered_materials");
    }

    private static boolean matchesAnyNeed(List<StorageNeed> needs, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (StorageNeed need : needs) {
            if (need.predicate().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private record BrewingStandPlan(
            int currentStep,
            int loadedBottleCount,
            int targetBottleCount,
            int waterBottleCount,
            int nextIngredientIndex,
            boolean hasFuelAvailable,
            boolean hasWrongBottles,
            boolean hasWrongIngredient,
            boolean hasWrongFuel) {
        static BrewingStandPlan create(
                ServerLevel level,
                BlockPos stand,
                HiredBrewingRecipeCatalog.BrewingRoute route,
                int requestedBatchSize) {
            if (!(level.getBlockEntity(stand) instanceof BrewingStandBlockEntity blockEntity)) {
                return new BrewingStandPlan(0, 0, Math.max(0, requestedBatchSize), Math.max(0, requestedBatchSize), 0, false, false, false, false);
            }

            int currentStep = currentBrewingStep(level, blockEntity, route);
            int loadedBottles = BrewingWorker.loadedBottleCount(blockEntity);
            boolean hasWrongBottles = currentStep < 0;
            ItemStack ingredientSlot = blockEntity.getItem(INGREDIENT_SLOT);
            boolean currentIngredientLoaded = false;
            boolean hasWrongIngredient = false;
            if (!ingredientSlot.isEmpty()) {
                if (!hasWrongBottles
                        && currentStep < route.ingredients().size()
                        && ingredientSlot.is(route.ingredients().get(currentStep))) {
                    currentIngredientLoaded = true;
                } else {
                    hasWrongIngredient = true;
                }
            }

            ItemStack fuelSlot = blockEntity.getItem(FUEL_SLOT);
            boolean hasWrongFuel = !fuelSlot.isEmpty() && !fuelSlot.is(Items.BLAZE_POWDER);
            boolean hasFuelAvailable = standFuelUses(level, stand) > 0 || fuelSlot.is(Items.BLAZE_POWDER);
            boolean canAddBaseBottles = !hasWrongBottles && currentStep == 0 && !hasWrongIngredient && loadedBottles < BOTTLE_SLOT_COUNT;
            int requested = Math.clamp(requestedBatchSize, 0, BOTTLE_SLOT_COUNT);
            int targetBottleCount = canAddBaseBottles
                    ? requested
                    : (loadedBottles > 0 ? loadedBottles : requested);
            int waterBottleCount = Math.max(0, targetBottleCount - loadedBottles);
            int nextIngredientIndex = hasWrongBottles || hasWrongIngredient
                    ? 0
                    : Math.min(route.ingredients().size(), currentStep + (currentIngredientLoaded ? 1 : 0));
            return new BrewingStandPlan(
                    currentStep,
                    loadedBottles,
                    targetBottleCount,
                    waterBottleCount,
                    nextIngredientIndex,
                    hasFuelAvailable,
                    hasWrongBottles,
                    hasWrongIngredient,
                    hasWrongFuel);
        }

        private boolean needsFuelForBrewing(HiredBrewingRecipeCatalog.BrewingRoute route) {
            return !this.hasFuelAvailable
                    && !this.hasWrongBottles
                    && !this.hasWrongIngredient
                    && this.currentStep < route.ingredients().size()
                    && this.targetBottleCount > 0;
        }

        private boolean hasFinishedOutput(HiredBrewingRecipeCatalog.BrewingRoute route) {
            return !this.hasWrongBottles
                    && this.loadedBottleCount > 0
                    && this.currentStep >= route.ingredients().size();
        }

        private int collectLimit(CompoundTag state) {
            if (state.getBoolean(CONTINUOUS_TAG)) {
                return BOTTLE_SLOT_COUNT;
            }
            return Math.clamp(state.getInt(REMAINING_TAG), 0, BOTTLE_SLOT_COUNT);
        }
    }

    private record MaterialPlan(
            Map<Item, Integer> items,
            int waterBottleCount,
            boolean fillWaterBottles,
            boolean hasEverything,
            String missingStatus,
            String missingMaterials) {
        static MaterialPlan create(
                ServerLevel level,
                Villager villager,
                HiredWorkContext context,
                HiredBrewingRecipeCatalog.BrewingRoute route,
                boolean waterSource,
                BrewingStandPlan standPlan) {
            HiredSupplyCrafting.MaterialPlanner planner = new HiredSupplyCrafting.MaterialPlanner(level, villager, context);
            Map<Item, Integer> items = new LinkedHashMap<>();
            for (int i = standPlan.nextIngredientIndex(); i < route.ingredients().size(); i++) {
                Item ingredient = route.ingredients().get(i);
                if (!planner.plan(ingredient, 1, items)) {
                    return missing(items, standPlan.waterBottleCount(), false, itemLabel(ingredient));
                }
            }
            if (standPlan.needsFuelForBrewing(route) && planFuel(planner, items) <= 0) {
                return missing(items, standPlan.waterBottleCount(), false, itemLabel(Items.BLAZE_POWDER));
            }
            boolean fillWaterBottles = false;
            int carriedWaterBottles = countJobWaterBottles(context);
            if (waterSource && carriedWaterBottles < standPlan.waterBottleCount()) {
                int missingCarriedWaterBottles = standPlan.waterBottleCount() - carriedWaterBottles;
                Map<Item, Integer> withGlassBottles = new LinkedHashMap<>(items);
                if (planner.plan(Items.GLASS_BOTTLE, missingCarriedWaterBottles, withGlassBottles)) {
                    items = withGlassBottles;
                    fillWaterBottles = true;
                } else {
                    int availableWaterBottles = countWaterBottles(villager, context);
                    int missingWaterBottles = standPlan.waterBottleCount() - availableWaterBottles;
                    withGlassBottles = new LinkedHashMap<>(items);
                    if (missingWaterBottles > 0 && planner.plan(Items.GLASS_BOTTLE, missingWaterBottles, withGlassBottles)) {
                        items = withGlassBottles;
                        fillWaterBottles = true;
                    } else if (availableWaterBottles < standPlan.waterBottleCount()) {
                        return missing(items, standPlan.waterBottleCount(), false, itemLabel(Items.GLASS_BOTTLE));
                    }
                }
            } else if (!waterSource && countWaterBottles(villager, context) < standPlan.waterBottleCount()) {
                return missing(items, standPlan.waterBottleCount(), false, waterBottleLabel());
            }
            return new MaterialPlan(items, standPlan.waterBottleCount(), fillWaterBottles, true, "", "");
        }

        private static MaterialPlan missing(
                Map<Item, Integer> items,
                int waterBottleCount,
                boolean fillWaterBottles,
                String missingMaterials) {
            return new MaterialPlan(
                    items,
                    waterBottleCount,
                    fillWaterBottles,
                    false,
                    "interaction.work.brewing.missing_materials",
                    missingMaterials == null ? "" : missingMaterials);
        }

        private static int planFuel(HiredSupplyCrafting.MaterialPlanner planner, Map<Item, Integer> items) {
            if (planner.directAvailable(Items.BLAZE_POWDER, items) > 0) {
                items.merge(Items.BLAZE_POWDER, 1, Integer::sum);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            Map<Item, Integer> withPowder = new LinkedHashMap<>(items);
            if (planner.surplusAvailable(Items.BLAZE_POWDER) > 0 && planner.plan(Items.BLAZE_POWDER, 1, withPowder)) {
                items.clear();
                items.putAll(withPowder);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            withPowder = new LinkedHashMap<>(items);
            if (planner.plan(Items.BLAZE_POWDER, 1, withPowder)) {
                items.clear();
                items.putAll(withPowder);
                return FUEL_USES_PER_BLAZE_POWDER;
            }
            return 0;
        }

        public boolean hasEverything() {
            return this.hasEverything;
        }

        public String missingStatus() {
            return this.missingStatus.isBlank() ? "interaction.work.brewing.missing_materials" : this.missingStatus;
        }

        private List<StorageNeed> storageNeeds(HiredWorkContext context) {
            List<StorageNeed> needs = new ArrayList<>();
            for (Map.Entry<Item, Integer> entry : this.items.entrySet()) {
                int carried = countJobItem(context, entry.getKey());
                int missing = Math.max(0, entry.getValue() - carried);
                if (missing > 0) {
                    Item item = entry.getKey();
                    needs.add(new StorageNeed(stack -> stack.is(item), missing, itemLabel(item)));
                }
            }
            if (this.waterBottleCount > 0) {
                int carried = countJobWaterBottles(context);
                int missing = Math.max(0, this.waterBottleCount - carried);
                if (missing > 0) {
                    if (this.fillWaterBottles) {
                        return needs;
                    }
                    needs.add(new StorageNeed(HiredBrewingRecipeCatalog::isWaterPotion, missing, waterBottleLabel()));
                }
            }
            return needs;
        }

        private String materialsSummary(HiredWorkContext context) {
            if (!this.hasEverything() && !this.missingMaterials().isBlank()) {
                return this.missingMaterials();
            }
            List<StorageNeed> needs = storageNeeds(context);
            if (needs.isEmpty()) {
                return this.missingMaterials();
            }
            List<String> parts = new ArrayList<>();
            for (StorageNeed need : needs) {
                String part = need.count() + " " + need.label();
                parts.add(part);
                if (parts.size() >= 4) {
                    break;
                }
            }
            return String.join(", ", parts);
        }

        private int missingCarriedWaterBottles(HiredWorkContext context) {
            return Math.max(0, this.waterBottleCount - countJobWaterBottles(context));
        }

        private boolean shouldFillWaterBottles() {
            return this.fillWaterBottles;
        }

        private static int countWaterBottles(Villager villager, HiredWorkContext context) {
            return HiredSupplyCrafting.countAvailable(villager, context, HiredBrewingRecipeCatalog::isWaterPotion);
        }

        private static int countJobItem(HiredWorkContext context, Item item) {
            return HiredSupplyCrafting.countCarried(context, item);
        }

        private static int countJobWaterBottles(HiredWorkContext context) {
            return HiredSupplyCrafting.countCarried(context, HiredBrewingRecipeCatalog::isWaterPotion);
        }

        private static String itemLabel(Item item) {
            return new ItemStack(item).getHoverName().getString();
        }

        private static String waterBottleLabel() {
            return PotionContents.createItemStack(Items.POTION, Potions.WATER).getHoverName().getString();
        }
    }

    private record StorageNeed(Predicate<ItemStack> predicate, int count, String label) {
    }

    private record FacilityCandidate(BlockPos pos, double score) {
    }

    private static int countJobItem(HiredWorkContext context, Item item) {
        return MaterialPlan.countJobItem(context, item);
    }

    private static int countJobWaterBottles(HiredWorkContext context) {
        return MaterialPlan.countJobWaterBottles(context);
    }
}
