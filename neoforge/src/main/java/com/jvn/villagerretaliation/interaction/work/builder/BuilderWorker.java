package com.jvn.villagerretaliation.interaction.work.builder;

import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredStorageNavigationGoal;
import com.jvn.villagerretaliation.interaction.work.HiredPathTarget;
import com.jvn.villagerretaliation.interaction.work.HiredPathResult;
import com.jvn.villagerretaliation.interaction.work.HiredPathMemory;
import com.jvn.villagerretaliation.interaction.work.HiredMoveToBlockFaceJob;
import com.jvn.villagerretaliation.interaction.work.AbstractBlockWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BuilderWorker extends AbstractBlockWorker {
    private static final String BUILD_SITE_NAV_TARGET_TAG = "BuilderSiteNavigationTarget";
    private static final double BUILD_WALK_SPEED = 0.52D;
    private static final int BUILD_WALK_CLOSE_ENOUGH = 1;
    private static final int MAX_BUILD_TARGETS_TO_PATHFIND = 128;
    private static final double BUILD_REACH = 12.0D;
    private static final double BUILD_REACH_SQR = BUILD_REACH * BUILD_REACH;
    private static final double BUILD_SITE_BORDER_SAFE_DISTANCE = 3.0D;
    private static final double BUILD_SITE_BORDER_SAFE_DISTANCE_SQR =
            BUILD_SITE_BORDER_SAFE_DISTANCE * BUILD_SITE_BORDER_SAFE_DISTANCE;
    private static final int BUILD_APPROACH_RADIUS = 12;
    private static final int BUILD_APPROACH_VERTICAL_SEARCH = 4;
    private static final int BUILD_SITE_INTERMEDIATE_SEARCH_RADIUS = 10;
    private static final int BUILD_SITE_INTERMEDIATE_VERTICAL_RADIUS = 3;
    private static final int MAX_BUILD_SITE_INTERMEDIATE_PATH_ATTEMPTS = 24;
    private static final int BUILD_SITE_INTERMEDIATE_CLOSE_ENOUGH = 2;
    private static final int SELF_PLACEMENT_CLEAR_RADIUS = 4;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.BUILDER;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!BuilderTaskState.hasTask(context.state())) {
            HiredWorkerBrain.clearFailure(context);
            HiredStorageNavigationGoal.clearStorageTarget(context);
            clearBuildSiteIntermediateNavigation(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            return WorkResult.idle("interaction.work.builder.choose_structure");
        }

        Optional<BuilderStructureScanner.StructurePlan> plan = currentPlan(level, context.state());
        if (plan.isEmpty()) {
            BuilderTaskState.setBlocked(context.state(), "missing_structure");
            HiredWorkerBrain.setFailure(context, "missing_structure", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN);
            return WorkResult.idle("interaction.work.builder.structure_unavailable", BuilderTaskState.replacements(context.state()));
        }

        BlockPos origin = BuilderTaskState.origin(context.state());
        HiredWorkArea area = null;
        BuilderSitePlanner.SiteResult siteResult = BuilderSitePlanner.validateStartedSite(
                level,
                villager,
                plan.get(),
                origin);
        if (!siteResult.valid()) {
            BuilderTaskState.setBlocked(context.state(), siteResult.statusKey());
            HiredWorkerBrain.setFailure(context, siteResult.statusKey(), level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN);
            return WorkResult.idle(siteResult.statusKey(), siteResult.replacements());
        }

        int index = skipAlreadyPlaced(level, context.state(), plan.get(), origin);
        if (index >= plan.get().blocks().size()) {
            return finishBuild(level, villager, context, plan.get());
        }

        BuilderStructureScanner.BuildBlock block = plan.get().blocks().get(index);
        BlockPos worldPos = plan.get().worldPos(origin, block);
        PlacementGroup placementGroup = placementGroup(plan.get(), origin, block);

        BlockPos buildCenter = origin.offset(plan.get().localCenter());
        WorkResult interiorEvacuationResult = moveOutOfBuildInteriorIfNeeded(
                level,
                villager,
                context,
                area,
                buildCenter,
                plan.get(),
                origin,
                block);
        if (interiorEvacuationResult != null) {
            return interiorEvacuationResult;
        }

        PlacementGroup reachableObstruction = reachablePlacementObstructionGroup(
                level,
                villager,
                plan.get(),
                origin,
                index,
                area,
                buildCenter);
        WorkResult clearingResult = reachableObstruction == null ? null : clearPlacementObstruction(
                level,
                villager,
                context,
                reachableObstruction,
                area,
                buildCenter,
                plan.get(),
                origin);
        if (clearingResult != null) {
            return clearingResult;
        }

        PlacementGroup remainingObstruction = firstRemainingPlacementObstructionGroup(level, plan.get(), origin, index);
        if (remainingObstruction != null) {
            PlacementPart obstruction = firstPlacementObstruction(level, remainingObstruction);
            BlockPos obstructionPos = obstruction == null ? worldPos : obstruction.worldPos();
            BuilderTaskState.setBlocked(context.state(), "path_blocked");
            HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, obstructionPos);
            return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                    "target", HiredWorkerBrain.formatPos(obstructionPos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        WorkResult evacuationResult = moveOutOfSchematicIfNeeded(level, villager, context, area, buildCenter, plan.get(), origin);
        if (evacuationResult != null) {
            return evacuationResult;
        }

        WorkResult materialBatchResult = ensureConstructionMaterialBatch(
                level,
                villager,
                context,
                plan.get(),
                origin,
                index);
        if (materialBatchResult != null) {
            return materialBatchResult;
        }

        if (firstPlacementObstruction(level, placementGroup) != null) {
            clearingResult = clearPlacementObstruction(
                    level,
                    villager,
                    context,
                    placementGroup,
                    area,
                    buildCenter,
                    plan.get(),
                    origin);
            if (clearingResult != null) {
                return clearingResult;
            }
        }

        for (PlacementPart part : placementGroup.parts()) {
            if (!builderIntersectsPlacement(level, villager, part.worldPos(), part.block().state())) {
                continue;
            }
            if (moveAwayFromPlacement(level, villager, area, buildCenter, part.worldPos())) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, worldPos);
                BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
                return WorkResult.progressed("interaction.work.builder.moving_to_site", Map.of(
                        "target", HiredWorkerBrain.formatPos(worldPos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }
            BuilderTaskState.setBlocked(context.state(), "blocked_entity");
            HiredWorkerBrain.setFailure(context, "blocked_entity", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, part.worldPos());
            return WorkResult.idle("interaction.work.builder.blocked_entity", Map.of(
                    "target", HiredWorkerBrain.formatPos(part.worldPos()),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        for (PlacementPart part : placementGroup.parts()) {
            BuilderSitePlanner.PlacementCheck placementCheck = part == placementGroup.materialPart()
                    ? BuilderSitePlanner.canPlaceAt(level, villager, part.worldPos(), part.block().state())
                    : BuilderSitePlanner.canReserveAt(level, villager, part.worldPos(), part.block().state());
            if (!placementCheck.valid()) {
                BuilderTaskState.setBlocked(context.state(), placementCheck.statusKey());
                HiredWorkerBrain.setFailure(context, placementCheck.statusKey(), level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, part.worldPos());
                return WorkResult.idle(placementCheck.statusKey(), Map.of(
                        "target", HiredWorkerBrain.formatPos(part.worldPos()),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }
        }

        WorkResult toolActionResult = ensurePlacementToolAction(level, villager, context, placementGroup);
        if (toolActionResult != null) {
            return toolActionResult;
        }

        if (!canBuildFromCurrentPosition(villager, worldPos, plan.get(), origin)) {
            if (continueBuildSiteIntermediateNavigation(level, context, villager)) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, buildCenter);
                BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
                return WorkResult.idle("interaction.work.builder.moving_to_site", Map.of(
                        "target", HiredWorkerBrain.formatPos(worldPos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }

            HiredPathTarget target = bestBuildTarget(level, villager, worldPos, area, buildCenter, plan.get(), origin);
            if (target == null) {
                if (moveTowardBuildSiteIntermediate(level, context, villager, plan.get(), origin, buildCenter)) {
                    setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, buildCenter);
                    BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
                    return WorkResult.idle("interaction.work.builder.moving_to_site", Map.of(
                            "target", HiredWorkerBrain.formatPos(worldPos),
                            "structure", BuilderTaskState.structureLabel(context.state())));
                }
                if (recordWorkPathFailure(level, villager, worldPos)) {
                    BuilderTaskState.setBlocked(context.state(), "path_blocked");
                }
                HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, worldPos);
                return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                        "target", HiredWorkerBrain.formatPos(worldPos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }

            prepareBreakingTarget(level, context, villager, target);
            MovementResult movementResult = moveToBuildTarget(level, villager, context, target, area, buildCenter, plan.get(), origin);
            if (movementResult == MovementResult.BLOCKED) {
                if (moveTowardBuildSiteIntermediate(level, context, villager, plan.get(), origin, buildCenter)) {
                    setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, buildCenter);
                    BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
                    return WorkResult.idle("interaction.work.builder.moving_to_site", Map.of(
                            "target", HiredWorkerBrain.formatPos(worldPos),
                            "structure", BuilderTaskState.structureLabel(context.state())));
                }
                if (recordWorkPathFailure(level, villager, worldPos)) {
                    BuilderTaskState.setBlocked(context.state(), "path_blocked");
                }
                HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, worldPos);
                return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                        "target", HiredWorkerBrain.formatPos(worldPos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }
            if (movementResult == MovementResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, worldPos);
                BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
                return WorkResult.idle("interaction.work.builder.moving_to_site", Map.of(
                        "target", HiredWorkerBrain.formatPos(worldPos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }
        } else {
            clearBuildSiteIntermediateNavigation(context);
            stopWorkNavigation(villager);
        }

        if (!placeBlock(level, villager, context, placementGroup)) {
            BuilderTaskState.setBlocked(context.state(), "placement_failed");
            HiredWorkerBrain.setFailure(context, "placement_failed", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, worldPos);
            return WorkResult.idle("interaction.work.builder.placement_failed", Map.of(
                    "target", HiredWorkerBrain.formatPos(worldPos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        clearWorkPathFailure(villager, worldPos);
        HiredWorkerBrain.clearFailure(context);
        HiredPathMemory.rememberRecent(level, worldPos);
        BuilderTaskState.setPlacedIndex(context.state(), index + 1);
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.BUILDING);
        setTaskState(context, HiredWorkerTaskState.WORKING, worldPos);
        return WorkResult.progressed("interaction.work.builder.placed_block", BuilderTaskState.replacements(context.state()));
    }

    @Override
    public void pause(ServerLevel level, Villager villager, HiredWorkContext context) {
        clearActiveBreakingTarget(level, context, villager);
        clearBuildSiteIntermediateNavigation(context);
        super.pause(level, villager, context);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        clearActiveBreakingTarget(level, context, villager);
        clearBuildSiteIntermediateNavigation(context);
        BuilderTaskState.clearPendingStructure(context.state());
        BuilderTaskState.clearTask(context.state());
        super.stop(level, villager, context);
    }

    public static int countAvailableMaterial(Villager villager, HiredJobInventory inventory, ItemStack required) {
        if (required.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int count = 0;
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            if (BuilderStructureScanner.sameMaterial(stack, required)) {
                count += stack.getCount();
            }
        }
        count += AssignedStorageService.countItemsInNonPaymentStorage(
                villager,
                stack -> BuilderStructureScanner.sameMaterial(stack, required));
        return count;
    }

    public static MissingMaterials missingMaterials(
            Villager villager,
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan) {
        return missingMaterials(villager, inventory, plan, 0);
    }

    public static MissingMaterials missingMaterials(
            Villager villager,
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan,
            int startIndex) {
        return missingMaterials(villager, inventory, remainingMaterials(plan, startIndex));
    }

    private static MissingMaterials missingMaterials(
            ServerLevel level,
            Villager villager,
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        return missingMaterials(villager, inventory, remainingMaterials(level, plan, origin, startIndex));
    }

    private static MissingMaterials missingMaterials(
            Villager villager,
            HiredJobInventory inventory,
            List<BuilderStructureScanner.MaterialRequirement> materials) {
        List<String> missing = new ArrayList<>();
        for (BuilderStructureScanner.MaterialRequirement material : materials) {
            int available = countAvailableMaterial(villager, inventory, material.item());
            if (available < material.count()) {
                missing.add((material.count() - available) + "x " + material.itemName());
            }
        }
        return new MissingMaterials(missing);
    }

    private static MissingMaterials missingMaterialDeficits(
            List<BuilderStructureScanner.MaterialRequirement> deficits) {
        List<String> missing = new ArrayList<>();
        for (BuilderStructureScanner.MaterialRequirement material : deficits) {
            if (material.count() > 0) {
                missing.add(material.count() + "x " + material.itemName());
            }
        }
        return new MissingMaterials(missing);
    }

    private WorkResult waitForMaterialsAtAssignedStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            MissingMaterials missing,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
        BuilderTaskState.setMissingMaterials(context.state(), missing.summary());
        Map<String, String> replacements = Map.of(
                "materials", missing.summary(),
                "structure", BuilderTaskState.structureLabel(context.state()),
                "storage_radius", Integer.toString(builderMaterialStorageRadius()));
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            stopAfterBuilderStorageAction(villager);
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "missing_builder_materials", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return WorkResult.idle("interaction.work.builder.waiting_materials", replacements);
        }

        Predicate<BlockPos> storageFilter = builderMaterialStorageFilter(plan, origin);
        List<BlockPos> storages = AssignedStorageService.assignedNonPaymentStoragePositions(level, villager, storageFilter);
        if (storages.isEmpty()) {
            stopAfterBuilderStorageAction(villager);
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "missing_builder_materials_storage_too_far", 0L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.builder.waiting_materials", replacements);
        }

        BlockPos failedStorage = storages.getFirst();
        for (BlockPos storage : storages) {
            HiredWorkerBrain.setStorageTarget(context, storage);
            HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                    level,
                    context,
                    villager,
                    storage,
                    BUILD_WALK_SPEED);
            if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
                HiredWorkerBrain.clearFailure(context);
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                clearBuildSiteIntermediateNavigation(context);
                return WorkResult.progressed("interaction.work.builder.moving_to_material_storage", replacements);
            }
            if (moveResult == HiredStorageNavigationGoal.Result.ARRIVED) {
                stopAfterBuilderStorageAction(villager);
                HiredWorkerBrain.clearFailure(context);
                setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
                return WorkResult.idle("interaction.work.builder.waiting_materials_at_storage", replacements);
            }
            failedStorage = storage;
        }

        HiredWorkerBrain.setFailure(context, "missing_builder_materials_storage_unreachable", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, failedStorage);
        HiredWorkerBrain.setStorageTarget(context, failedStorage);
        return WorkResult.idle("interaction.work.builder.waiting_material_storage_unreachable", replacements);
    }

    private WorkResult ensureConstructionMaterialBatch(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        MaterialBatch batch = currentMaterialBatch(level, context, plan, origin, startIndex);
        if (batch.materials().isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            BuilderTaskState.clearMissingMaterials(context.state());
            return null;
        }

        promoteOutputMaterialsToSupply(context.inventory(), batch.materials());
        List<BuilderStructureScanner.MaterialRequirement> missing = carriedMissingMaterials(
                context.inventory(),
                plan,
                origin,
                level,
                startIndex,
                batch.endIndex());
        if (missing.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            BuilderTaskState.clearMissingMaterials(context.state());
            return null;
        }

        return collectMaterialBatch(level, villager, context, plan, origin, startIndex, batch, missing);
    }

    private MaterialBatch currentMaterialBatch(
            ServerLevel level,
            HiredWorkContext context,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        int storedEnd = BuilderTaskState.materialBatchEndIndex(context.state());
        if (storedEnd > startIndex && storedEnd <= plan.blocks().size()) {
            List<BuilderStructureScanner.MaterialRequirement> materials =
                    remainingMaterials(level, plan, origin, startIndex, storedEnd);
            if (context.inventory().canStoreSuppliesAfterDepositingOutputs(
                    missingMaterialStacks(context.inventory(), materials))) {
                return new MaterialBatch(storedEnd, materials);
            }
        }

        MaterialBatch batch = nextMaterialBatch(level, context.inventory(), plan, origin, startIndex);
        BuilderTaskState.setMaterialBatchEndIndex(context.state(), batch.endIndex());
        return batch;
    }

    private MaterialBatch nextMaterialBatch(
            ServerLevel level,
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        int start = Math.clamp(startIndex, 0, plan.blocks().size());
        int end = start;
        List<BuilderStructureScanner.MaterialRequirement> materials = new ArrayList<>();
        for (int i = start; i < plan.blocks().size(); i++) {
            BuilderStructureScanner.BuildBlock block = plan.blocks().get(i);
            if (!blockNeedsMaterial(level, plan, origin, block)) {
                end = i + 1;
                continue;
            }

            List<BuilderStructureScanner.MaterialRequirement> candidate = copyMaterialRequirements(materials);
            addMaterialRequirement(candidate, block.requiredItem(), 1);
            if (!inventory.canStoreSuppliesAfterDepositingOutputs(missingMaterialStacks(inventory, candidate))) {
                if (end == start && materials.isEmpty()) {
                    materials = candidate;
                    end = i + 1;
                }
                break;
            }
            materials = candidate;
            end = i + 1;
        }
        return new MaterialBatch(end, List.copyOf(materials));
    }

    private WorkResult collectMaterialBatch(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex,
            MaterialBatch batch,
            List<BuilderStructureScanner.MaterialRequirement> missing) {
        promoteOutputMaterialDeficitsToSupply(context.inventory(), missing);
        missing = carriedMissingMaterials(
                context.inventory(),
                plan,
                origin,
                level,
                startIndex,
                batch.endIndex());
        if (missing.isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            BuilderTaskState.clearMissingMaterials(context.state());
            return null;
        }

        MissingMaterials missingSummary = missingMaterialDeficits(missing);
        BuilderTaskState.setMissingMaterials(context.state(), missingSummary.summary());
        List<BuilderStructureScanner.MaterialRequirement> storageMissing = missing;
        Predicate<ItemStack> storageMaterialFilter = stack -> matchesAnyMaterial(storageMissing, stack);
        Predicate<BlockPos> storagePositionFilter = builderMaterialStorageFilter(plan, origin);
        BlockPos storage = AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                level,
                villager,
                storageMaterialFilter,
                storagePositionFilter);
        if (storage == null && context.inventory().hasOutput(stack -> matchesAnyMaterial(storageMissing, stack))) {
            WorkResult outputMaterialResult = makeRoomForCarriedOutputMaterials(
                    level,
                    villager,
                    context,
                    null,
                    plan,
                    origin,
                    startIndex,
                    batch,
                    missing);
            if (outputMaterialResult != null) {
                return outputMaterialResult;
            }
            missing = carriedMissingMaterials(
                    context.inventory(),
                    plan,
                    origin,
                    level,
                    startIndex,
                    batch.endIndex());
            if (missing.isEmpty()) {
                HiredStorageNavigationGoal.clearStorageTarget(context);
                HiredWorkerBrain.clearFailure(context);
                BuilderTaskState.clearMissingMaterials(context.state());
                return null;
            }
            missingSummary = missingMaterialDeficits(missing);
            BuilderTaskState.setMissingMaterials(context.state(), missingSummary.summary());
            List<BuilderStructureScanner.MaterialRequirement> updatedStorageMissing = missing;
            storageMaterialFilter = stack -> matchesAnyMaterial(updatedStorageMissing, stack);
            storage = AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                    level,
                    villager,
                    storageMaterialFilter,
                    storagePositionFilter);
        }
        List<BuilderStructureScanner.MaterialRequirement> currentStorageMissing = missing;
        if (storage == null && context.inventory().hasOutput(stack -> matchesAnyMaterial(currentStorageMissing, stack))) {
            return pauseForOutputMaterialSlotFull(level, context, null);
        }
        if (storage == null) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return waitForMaterialsAtAssignedStorage(level, villager, context, missingSummary, plan, origin);
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                BUILD_WALK_SPEED);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            clearBuildSiteIntermediateNavigation(context);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.idle("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            BlockPos failedStorage = storage;
            for (BlockPos alternateStorage : AssignedStorageService.assignedNonPaymentStoragePositionsContaining(
                    level,
                    villager,
                    storageMaterialFilter,
                    pos -> !failedStorage.equals(pos) && storagePositionFilter.test(pos))) {
                HiredWorkerBrain.setStorageTarget(context, alternateStorage);
                HiredStorageNavigationGoal.Result alternateMoveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                        level,
                        context,
                        villager,
                        alternateStorage,
                        BUILD_WALK_SPEED);
                if (alternateMoveResult == HiredStorageNavigationGoal.Result.MOVING) {
                    HiredWorkerBrain.clearFailure(context);
                    setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                    clearBuildSiteIntermediateNavigation(context);
                    BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
                    return WorkResult.idle("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
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
            }
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED && !AssignedStorageService.canInteractWithAssignedStorage(villager, storage)) {
            BuilderTaskState.setBlocked(context.state(), "builder_material_storage_unreachable");
            HiredWorkerBrain.setFailure(context, "builder_material_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            HiredWorkerBrain.setStorageTarget(context, storage);
            return WorkResult.idle("interaction.work.builder.materials_unreachable", materialReplacements(context.state(), missing));
        }

        faceBlock(villager, storage);
        List<BuilderStructureScanner.MaterialRequirement> expectedMissing = copyMaterialRequirements(missing);
        int depositedOutputs = depositOutputItemsAtStorage(
                villager,
                context.inventory(),
                storage,
                stack -> !matchesAnyMaterial(expectedMissing, stack));
        int promotedOutputs = promoteOutputMaterialDeficitsToSupply(context.inventory(), expectedMissing);
        List<BuilderStructureScanner.MaterialRequirement> transferMissing = carriedMissingMaterials(
                context.inventory(),
                plan,
                origin,
                level,
                startIndex,
                batch.endIndex());
        int movedMaterials = transferMissingBatchMaterials(villager, context, storage, transferMissing);
        if (depositedOutputs > 0 || promotedOutputs > 0 || movedMaterials > 0) {
            swingWorkTool(villager);
        }

        List<BuilderStructureScanner.MaterialRequirement> stillMissing = carriedMissingMaterials(
                context.inventory(),
                plan,
                origin,
                level,
                startIndex,
                batch.endIndex());
        boolean changedStorageInventory = depositedOutputs > 0 || promotedOutputs > 0 || movedMaterials > 0;
        if (stillMissing.isEmpty()) {
            if (changedStorageInventory) {
                stopAfterBuilderStorageAction(villager);
            }
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            BuilderTaskState.clearMissingMaterials(context.state());
            if (changedStorageInventory) {
                return WorkResult.progressed("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
            }
            return null;
        }
        BuilderTaskState.setMissingMaterials(
                context.state(),
                missingMaterialDeficits(stillMissing).summary());
        if (changedStorageInventory) {
            stopAfterBuilderStorageAction(villager);
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, storage);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.progressed("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }

        WorkResult outputRoomResult = makeRoomForNonMaterialOutputs(
                level,
                villager,
                context,
                storage,
                stillMissing);
        if (outputRoomResult != null) {
            return outputRoomResult;
        }

        List<BuilderStructureScanner.MaterialRequirement> outputMissing = stillMissing;
        if (context.inventory().hasOutput(stack -> matchesAnyMaterial(outputMissing, stack))) {
            WorkResult outputMaterialResult = makeRoomForCarriedOutputMaterials(
                    level,
                    villager,
                    context,
                    storage,
                    plan,
                    origin,
                    startIndex,
                    batch,
                    stillMissing);
            if (outputMaterialResult != null) {
                return outputMaterialResult;
            }
            stillMissing = carriedMissingMaterials(
                    context.inventory(),
                    plan,
                    origin,
                    level,
                    startIndex,
                    batch.endIndex());
            if (stillMissing.isEmpty()) {
                HiredStorageNavigationGoal.clearStorageTarget(context);
                HiredWorkerBrain.clearFailure(context);
                BuilderTaskState.clearMissingMaterials(context.state());
                return null;
            }
            BuilderTaskState.setMissingMaterials(
                    context.state(),
                    missingMaterialDeficits(stillMissing).summary());
        }

        List<BuilderStructureScanner.MaterialRequirement> remainingStorageMissing = stillMissing;
        if (AssignedStorageService.nearestAssignedNonPaymentStoragePosContaining(
                level,
                villager,
                stack -> matchesAnyMaterial(remainingStorageMissing, stack),
                storagePositionFilter) == null) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return waitForMaterialsAtAssignedStorage(
                    level,
                    villager,
                    context,
                    missingMaterialDeficits(stillMissing),
                    plan,
                    origin);
        }

        HiredWorkerBrain.setFailure(context, "builder_material_inventory_full", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
        HiredWorkerBrain.setStorageTarget(context, storage);
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.builder.material_inventory_full", BuilderTaskState.replacements(context.state()));
    }

    private static Predicate<BlockPos> builderMaterialStorageFilter(
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        if (plan == null || origin == null) {
            return ignored -> false;
        }
        BlockPos min = plan.worldMin(origin);
        BlockPos max = plan.worldMax(origin);
        long radius = builderMaterialStorageRadius();
        long radiusSqr = radius * radius;
        return pos -> isBuilderMaterialStorageNearSite(pos, min, max, radiusSqr);
    }

    private static boolean isBuilderMaterialStorageNearSite(
            BlockPos storage,
            BlockPos min,
            BlockPos max,
            long radiusSqr) {
        if (storage == null) {
            return false;
        }
        long dx = axisDistance(storage.getX(), min.getX(), max.getX());
        long dy = axisDistance(storage.getY(), min.getY(), max.getY());
        long dz = axisDistance(storage.getZ(), min.getZ(), max.getZ());
        return dx * dx + dy * dy + dz * dz <= radiusSqr;
    }

    private static long axisDistance(int value, int min, int max) {
        if (value < min) {
            return min - (long) value;
        }
        if (value > max) {
            return value - (long) max;
        }
        return 0L;
    }

    private static int builderMaterialStorageRadius() {
        return Math.max(1, VillagerRetaliationConfig.HIRED_BUILDER_MATERIAL_STORAGE_RADIUS.get());
    }

    private WorkResult makeRoomForCarriedOutputMaterials(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos preferredStorage,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex,
            MaterialBatch batch,
            List<BuilderStructureScanner.MaterialRequirement> missing) {
        BlockPos storage = preferredStorage == null ? nearestBuilderOutputStorage(level, villager) : preferredStorage;
        if (storage == null) {
            return null;
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                BUILD_WALK_SPEED);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            clearBuildSiteIntermediateNavigation(context);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.idle("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "builder_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            HiredWorkerBrain.setStorageTarget(context, storage);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.builder.material_inventory_full", BuilderTaskState.replacements(context.state()));
        }

        faceBlock(villager, storage);
        List<BuilderStructureScanner.MaterialRequirement> expectedMissing = copyMaterialRequirements(missing);
        int depositedOutputs = depositOutputItemsAtStorage(
                villager,
                context.inventory(),
                storage,
                stack -> !matchesAnyMaterial(expectedMissing, stack));
        int promotedOutputs = promoteOutputMaterialDeficitsToSupply(context.inventory(), expectedMissing);
        if (depositedOutputs > 0 || promotedOutputs > 0) {
            swingWorkTool(villager);
        }

        List<BuilderStructureScanner.MaterialRequirement> stillMissing = carriedMissingMaterials(
                context.inventory(),
                plan,
                origin,
                level,
                startIndex,
                batch.endIndex());
        boolean changedStorageInventory = depositedOutputs > 0 || promotedOutputs > 0;
        if (stillMissing.isEmpty()) {
            if (changedStorageInventory) {
                stopAfterBuilderStorageAction(villager);
            }
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            BuilderTaskState.clearMissingMaterials(context.state());
            if (changedStorageInventory) {
                return WorkResult.progressed("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
            }
            return null;
        }
        BuilderTaskState.setMissingMaterials(
                context.state(),
                missingMaterialDeficits(stillMissing).summary());
        if (changedStorageInventory) {
            stopAfterBuilderStorageAction(villager);
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, storage);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.progressed("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (context.inventory().hasOutput(stack -> matchesAnyMaterial(stillMissing, stack))) {
            return pauseForOutputMaterialSlotFull(level, context, storage);
        }
        return null;
    }

    private WorkResult pauseForOutputMaterialSlotFull(
            ServerLevel level,
            HiredWorkContext context,
            BlockPos storage) {
        HiredWorkerBrain.setFailure(context, "builder_material_output_slot_full", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
        if (storage == null) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
        } else {
            HiredWorkerBrain.setStorageTarget(context, storage);
        }
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.builder.material_inventory_full", BuilderTaskState.replacements(context.state()));
    }

    private WorkResult makeRoomForNonMaterialOutputs(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BlockPos materialStorage,
            List<BuilderStructureScanner.MaterialRequirement> missing) {
        List<BuilderStructureScanner.MaterialRequirement> expectedMissing = copyMaterialRequirements(missing);
        Predicate<ItemStack> nonMaterialOutput = stack -> !matchesAnyMaterial(expectedMissing, stack);
        if (!context.inventory().hasOutput(nonMaterialOutput)) {
            return null;
        }

        BlockPos storage = nearestBuilderOutputStorage(level, villager, materialStorage);
        if (storage == null) {
            return null;
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                BUILD_WALK_SPEED);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            clearBuildSiteIntermediateNavigation(context);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.idle("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "builder_material_output_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            HiredWorkerBrain.setStorageTarget(context, storage);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.builder.material_inventory_full", BuilderTaskState.replacements(context.state()));
        }

        faceBlock(villager, storage);
        int depositedOutputs = depositOutputItemsAtStorage(villager, context.inventory(), storage, nonMaterialOutput);
        if (depositedOutputs <= 0) {
            return null;
        }

        stopAfterBuilderStorageAction(villager);
        swingWorkTool(villager);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, storage);
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
        return WorkResult.progressed("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
    }

    private static BlockPos nearestBuilderOutputStorage(ServerLevel level, Villager villager) {
        return nearestBuilderOutputStorage(level, villager, null);
    }

    private static BlockPos nearestBuilderOutputStorage(ServerLevel level, Villager villager, BlockPos excludedStorage) {
        Predicate<BlockPos> filter = pos -> excludedStorage == null || !excludedStorage.equals(pos);
        BlockPos storage = AssignedStorageService.nearestAssignedOutputStoragePos(level, villager, filter);
        return storage == null ? AssignedStorageService.nearestAssignedStoragePos(level, villager, filter) : storage;
    }

    private WorkResult makeRoomForToolStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context) {
        if (!context.inventory().hasOutputItems()) {
            return null;
        }
        BlockPos storage = nearestBuilderOutputStorage(level, villager);
        if (storage == null) {
            return null;
        }

        HiredWorkerBrain.setStorageTarget(context, storage);
        HiredStorageNavigationGoal.Result moveResult = HiredStorageNavigationGoal.moveToStorageTarget(
                level,
                context,
                villager,
                storage,
                BUILD_WALK_SPEED);
        if (moveResult == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.clearFailure(context);
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            clearBuildSiteIntermediateNavigation(context);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.progressed("interaction.work.builder.output_full_depositing", BuilderTaskState.replacements(context.state()));
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            return null;
        }

        faceBlock(villager, storage);
        int depositedOutputs = depositOutputItemsAtStorage(villager, context.inventory(), storage);
        if (depositedOutputs <= 0) {
            return null;
        }

        stopAfterBuilderStorageAction(villager);
        swingWorkTool(villager);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS, storage);
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
        return WorkResult.progressed("interaction.work.builder.output_full_depositing", BuilderTaskState.replacements(context.state()));
    }

    private int transferMissingBatchMaterials(
            Villager villager,
            HiredWorkContext context,
            BlockPos storage,
            List<BuilderStructureScanner.MaterialRequirement> missing) {
        int moved = 0;
        for (BuilderStructureScanner.MaterialRequirement material : missing) {
            int needed = material.count();
            if (needed <= 0) {
                continue;
            }
            moved += AssignedStorageService.transferItemsAtAssignedNonPaymentStorage(
                    villager,
                    storage,
                    stack -> BuilderStructureScanner.sameMaterial(stack, material.item()),
                    needed,
                    stack -> context.inventory().insertSupplyFromStorage(stack));
        }
        return moved;
    }

    private int depositOutputItemsAtStorage(Villager villager, HiredJobInventory inventory, BlockPos storage) {
        return depositOutputItemsAtStorage(villager, inventory, storage, ignored -> true);
    }

    private int depositOutputItemsAtStorage(
            Villager villager,
            HiredJobInventory inventory,
            BlockPos storage,
            Predicate<ItemStack> outputFilter) {
        int movedStacks = 0;
        int attempts = 0;
        Predicate<ItemStack> safeFilter = outputFilter == null ? ignored -> true : outputFilter;
        while (inventory.hasOutputItems() && attempts++ < HiredJobInventory.SLOT_COUNT) {
            if (!inventory.depositOutputToAssignedStorageAt(storage, safeFilter)) {
                break;
            }
            movedStacks++;
        }
        return movedStacks;
    }

    private static void stopAfterBuilderStorageAction(Villager villager) {
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ(), 0.0D);
        villager.setDeltaMovement(villager.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
    }

    private static int promoteOutputMaterialsToSupply(
            HiredJobInventory inventory,
            List<BuilderStructureScanner.MaterialRequirement> materials) {
        int promoted = 0;
        for (BuilderStructureScanner.MaterialRequirement material : materials) {
            int needed = material.count() - countCarriedMaterial(inventory, material.item());
            if (needed <= 0) {
                continue;
            }
            promoted += inventory.promoteOutputToSupply(
                    stack -> BuilderStructureScanner.sameMaterial(stack, material.item()),
                    needed);
        }
        return promoted;
    }

    private static int promoteOutputMaterialDeficitsToSupply(
            HiredJobInventory inventory,
            List<BuilderStructureScanner.MaterialRequirement> deficits) {
        int promoted = 0;
        for (BuilderStructureScanner.MaterialRequirement material : deficits) {
            if (material.count() <= 0) {
                continue;
            }
            promoted += inventory.promoteOutputToSupply(
                    stack -> BuilderStructureScanner.sameMaterial(stack, material.item()),
                    material.count());
        }
        return promoted;
    }

    private static List<ItemStack> missingMaterialStacks(
            HiredJobInventory inventory,
            List<BuilderStructureScanner.MaterialRequirement> materials) {
        List<ItemStack> stacks = new ArrayList<>(materials.size());
        for (BuilderStructureScanner.MaterialRequirement material : materials) {
            int missing = material.count() - countCarriedMaterial(inventory, material.item());
            if (missing > 0) {
                stacks.add(material.item().copyWithCount(missing));
            }
        }
        return stacks;
    }

    private static List<BuilderStructureScanner.MaterialRequirement> copyMaterialRequirements(
            List<BuilderStructureScanner.MaterialRequirement> materials) {
        List<BuilderStructureScanner.MaterialRequirement> copy = new ArrayList<>(materials.size());
        for (BuilderStructureScanner.MaterialRequirement material : materials) {
            copy.add(new BuilderStructureScanner.MaterialRequirement(material.item().copyWithCount(1), material.count()));
        }
        return copy;
    }

    private static void addMaterialRequirement(
            List<BuilderStructureScanner.MaterialRequirement> materials,
            ItemStack required,
            int count) {
        int existing = indexOfMaterial(materials, required);
        if (existing >= 0) {
            BuilderStructureScanner.MaterialRequirement material = materials.get(existing);
            materials.set(existing, new BuilderStructureScanner.MaterialRequirement(material.item(), material.count() + count));
        } else {
            materials.add(new BuilderStructureScanner.MaterialRequirement(required.copyWithCount(1), count));
        }
    }

    private static Map<String, String> materialReplacements(
            CompoundTag state,
            BuilderStructureScanner.BuildBlock block) {
        if (block == null || !block.requiresMaterial()) {
            return BuilderTaskState.replacements(state);
        }
        return Map.of(
                "materials", "1x " + block.requiredItem().getHoverName().getString(),
                "structure", BuilderTaskState.structureLabel(state),
                "storage_radius", Integer.toString(builderMaterialStorageRadius()));
    }

    private static Map<String, String> materialReplacements(
            CompoundTag state,
            List<BuilderStructureScanner.MaterialRequirement> materials) {
        return Map.of(
                "materials", BuilderStructureScanner.materialSummary(materials, 5),
                "structure", BuilderTaskState.structureLabel(state),
                "storage_radius", Integer.toString(builderMaterialStorageRadius()));
    }

    private static List<BuilderStructureScanner.MaterialRequirement> remainingMaterials(
            BuilderStructureScanner.StructurePlan plan,
            int startIndex) {
        return remainingMaterials(null, plan, null, startIndex, plan.blocks().size());
    }

    private static List<BuilderStructureScanner.MaterialRequirement> remainingMaterials(
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        return remainingMaterials(level, plan, origin, startIndex, plan.blocks().size());
    }

    private static List<BuilderStructureScanner.MaterialRequirement> remainingMaterials(
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex,
            int endIndex) {
        List<BuilderStructureScanner.MaterialRequirement> materials = new ArrayList<>();
        int start = Math.clamp(startIndex, 0, plan.blocks().size());
        int end = Math.clamp(endIndex, start, plan.blocks().size());
        for (int i = start; i < end; i++) {
            BuilderStructureScanner.BuildBlock block = plan.blocks().get(i);
            if (!blockNeedsMaterial(level, plan, origin, block)) {
                continue;
            }
            addMaterialRequirement(materials, block.requiredItem(), 1);
        }
        return materials;
    }

    private static List<BuilderStructureScanner.MaterialRequirement> carriedMissingMaterials(
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            ServerLevel level,
            int startIndex) {
        return carriedMissingMaterials(inventory, plan, origin, level, startIndex, plan.blocks().size());
    }

    private static List<BuilderStructureScanner.MaterialRequirement> carriedMissingMaterials(
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            ServerLevel level,
            int startIndex,
            int endIndex) {
        List<BuilderStructureScanner.MaterialRequirement> missing = new ArrayList<>();
        for (BuilderStructureScanner.MaterialRequirement material : remainingMaterials(level, plan, origin, startIndex, endIndex)) {
            int carried = countCarriedMaterial(inventory, material.item());
            if (carried < material.count()) {
                missing.add(new BuilderStructureScanner.MaterialRequirement(
                        material.item().copyWithCount(1),
                        material.count() - carried));
            }
        }
        return missing;
    }

    private static boolean blockNeedsMaterial(
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BuilderStructureScanner.BuildBlock block) {
        if (!block.requiresMaterial()) {
            return false;
        }
        if (level == null || origin == null) {
            return true;
        }
        BlockState current = level.getBlockState(plan.worldPos(origin, block));
        return !BuilderStructureScanner.sameSchematicState(current, block.state())
                && !BuilderStructureScanner.canTransformExisting(current, block.state());
    }

    private static int countCarriedMaterial(HiredJobInventory inventory, ItemStack required) {
        if (required.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int count = 0;
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            if (BuilderStructureScanner.sameMaterial(stack, required)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean matchesAnyMaterial(
            List<BuilderStructureScanner.MaterialRequirement> materials,
            ItemStack stack) {
        for (BuilderStructureScanner.MaterialRequirement material : materials) {
            if (BuilderStructureScanner.sameMaterial(stack, material.item())) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfMaterial(List<BuilderStructureScanner.MaterialRequirement> materials, ItemStack required) {
        for (int i = 0; i < materials.size(); i++) {
            if (BuilderStructureScanner.sameMaterial(materials.get(i).item(), required)) {
                return i;
            }
        }
        return -1;
    }

    private Optional<BuilderStructureScanner.StructurePlan> currentPlan(ServerLevel level, CompoundTag state) {
        Optional<net.minecraft.resources.ResourceLocation> structureId = BuilderTaskState.structureId(state);
        if (structureId.isEmpty()) {
            return Optional.empty();
        }
        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(level.getServer(), structureId.get());
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        return BuilderStructureScanner.scan(level, entry.get(), BuilderTaskState.rotation(state));
    }

    private int skipAlreadyPlaced(
            ServerLevel level,
            CompoundTag state,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        int index = Math.min(BuilderTaskState.placedIndex(state), plan.blocks().size());
        while (index < plan.blocks().size()) {
            BuilderStructureScanner.BuildBlock block = plan.blocks().get(index);
            BlockState current = level.getBlockState(plan.worldPos(origin, block));
            if (!BuilderStructureScanner.sameSchematicState(current, block.state())) {
                break;
            }
            index++;
        }
        BuilderTaskState.setPlacedIndex(state, index);
        return index;
    }

    private boolean continueBuildSiteIntermediateNavigation(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager) {
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (villager.getNavigation().isDone()
                || navigationTarget == null
                || !isRememberedBuildSiteNavigationTarget(context, navigationTarget)) {
            return false;
        }
        if (HiredPathMemory.isNavigationBlocked(
                level,
                villager,
                navigationTarget,
                villager.distanceToSqr(navigationTarget.getCenter()))) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            clearBuildSiteIntermediateNavigation(context);
            return false;
        }
        return true;
    }

    private boolean moveTowardBuildSiteIntermediate(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BlockPos buildCenter) {
        BlockPos target = bestBuildSiteIntermediateTarget(level, context, villager, plan, origin, buildCenter);
        if (target == null) {
            return false;
        }
        Path path = villager.getNavigation().createPath(target, 0);
        if (path == null
                || !path.canReach()
                || !VillagerTaskNavigationUtil.moveToHiredPath(
                        villager,
                        path,
                        target,
                        BUILD_WALK_SPEED,
                        BUILD_SITE_INTERMEDIATE_CLOSE_ENOUGH)) {
            return false;
        }
        rememberBuildSiteIntermediateTarget(context, target);
        HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
        return true;
    }

    private BlockPos bestBuildSiteIntermediateTarget(
            ServerLevel level,
            HiredWorkContext context,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BlockPos buildCenter) {
        BlockPos villagerPos = villager.blockPosition();
        double currentDistance = villagerPos.distSqr(buildCenter);
        List<BuildSiteIntermediate> candidates = new ArrayList<>();
        for (BlockPos raw : BlockPos.betweenClosed(
                villagerPos.offset(-BUILD_SITE_INTERMEDIATE_SEARCH_RADIUS, -BUILD_SITE_INTERMEDIATE_VERTICAL_RADIUS, -BUILD_SITE_INTERMEDIATE_SEARCH_RADIUS),
                villagerPos.offset(BUILD_SITE_INTERMEDIATE_SEARCH_RADIUS, BUILD_SITE_INTERMEDIATE_VERTICAL_RADIUS, BUILD_SITE_INTERMEDIATE_SEARCH_RADIUS))) {
            BlockPos candidate = raw.immutable();
            if (candidate.equals(villagerPos)
                    || !context.isLoaded(level, candidate)
                    || HiredPathMemory.isAvoided(level, villager, candidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, candidate)) {
                continue;
            }
            double centerDistance = candidate.distSqr(buildCenter);
            if (centerDistance >= currentDistance - 1.0D) {
                continue;
            }
            candidates.add(new BuildSiteIntermediate(
                    candidate,
                    buildSiteIntermediateScore(level, villager, plan, origin, candidate, centerDistance)));
        }
        candidates.sort(Comparator.comparingDouble(BuildSiteIntermediate::score));

        int attempts = 0;
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BuildSiteIntermediate candidate : candidates) {
            if (attempts++ >= MAX_BUILD_SITE_INTERMEDIATE_PATH_ATTEMPTS) {
                break;
            }
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path != null && path.canReach()) {
                double score = candidate.score() + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate.pos();
                }
            }
        }
        return best;
    }

    private double buildSiteIntermediateScore(
            ServerLevel level,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BlockPos candidate,
            double centerDistance) {
        int vertical = Math.abs(candidate.getY() - villager.blockPosition().getY());
        return centerDistance * 0.35D
                + villager.distanceToSqr(candidate.getCenter()) * 0.15D
                + horizontalDistanceToBuildBorderSqr(candidate.getX() + 0.5D, candidate.getZ() + 0.5D, plan, origin) * 0.2D
                + vertical * vertical * 3.0D
                + HiredMoveToBlockFaceJob.terrainCost(level, candidate);
    }

    private static void rememberBuildSiteIntermediateTarget(HiredWorkContext context, BlockPos target) {
        context.state().putLong(BUILD_SITE_NAV_TARGET_TAG, target.asLong());
    }

    private static boolean isRememberedBuildSiteNavigationTarget(HiredWorkContext context, BlockPos target) {
        return target != null
                && context.state().contains(BUILD_SITE_NAV_TARGET_TAG)
                && context.state().getLong(BUILD_SITE_NAV_TARGET_TAG) == target.asLong();
    }

    private static void clearBuildSiteIntermediateNavigation(HiredWorkContext context) {
        context.state().remove(BUILD_SITE_NAV_TARGET_TAG);
    }

    private HiredPathTarget bestBuildTarget(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        Predicate<BlockPos> approachFilter = pos -> movementFilter.test(pos) && isInsideOrNearBuildSite(pos, plan, origin);
        List<BuilderApproachCandidate> candidates = buildApproachCandidates(level, villager, target, buildCenter, approachFilter);
        candidates.sort(Comparator.comparingDouble(BuilderApproachCandidate::score));

        HiredPathTarget bestTarget = null;
        double bestScore = Double.MAX_VALUE;
        int evaluated = 0;
        int reachable = 0;
        for (BuilderApproachCandidate candidate : candidates) {
            if (evaluated >= MAX_BUILD_TARGETS_TO_PATHFIND) {
                break;
            }
            evaluated++;
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, movementFilter)) {
                continue;
            }
            double score = candidate.score() + HiredMoveToBlockFaceJob.pathTraversalCost(level, path);
            if (score < bestScore) {
                bestScore = score;
                bestTarget = new HiredPathTarget(target.immutable(), candidate.pos(), target.getCenter());
            }
            reachable++;
            if (reachable >= 5) {
                break;
            }
        }
        return bestTarget;
    }

    private PlacementGroup reachablePlacementObstructionGroup(
            ServerLevel level,
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex,
            HiredWorkArea area,
            BlockPos buildCenter) {
        List<ReachableObstruction> candidates = new ArrayList<>();
        Set<Long> seenGroups = new HashSet<>();
        int reachable = 0;
        for (int i = Math.max(0, startIndex); i < plan.blocks().size(); i++) {
            PlacementGroup group = placementGroup(plan, origin, plan.blocks().get(i));
            if (!seenGroups.add(group.materialPart().worldPos().asLong())) {
                continue;
            }
            ReachableObstruction reachableObstruction = reachablePlacementObstruction(
                    level,
                    villager,
                    group,
                    area,
                    buildCenter,
                    i - startIndex);
            if (reachableObstruction == null) {
                continue;
            }
            candidates.add(reachableObstruction);
            reachable++;
            if (reachable >= 5) {
                break;
            }
        }
        candidates.sort(Comparator.comparingDouble(ReachableObstruction::score));
        return candidates.isEmpty() ? null : candidates.getFirst().group();
    }

    private ReachableObstruction reachablePlacementObstruction(
            ServerLevel level,
            Villager villager,
            PlacementGroup group,
            HiredWorkArea area,
            BlockPos buildCenter,
            int blockOffset) {
        ReachableObstruction best = null;
        double bestScore = Double.MAX_VALUE;
        for (PlacementPart obstruction : placementObstructions(level, group)) {
            HiredPathResult result = clearingPathResult(level, villager, obstruction.worldPos(), area, buildCenter);
            if (!result.reachesDestination()) {
                continue;
            }
            double score = result.score() + Math.max(0, blockOffset) * 0.05D;
            if (score < bestScore) {
                bestScore = score;
                best = new ReachableObstruction(group, obstruction, score);
            }
        }
        return best;
    }

    private PlacementGroup firstRemainingPlacementObstructionGroup(
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            int startIndex) {
        Set<Long> seenGroups = new HashSet<>();
        for (int i = Math.max(0, startIndex); i < plan.blocks().size(); i++) {
            PlacementGroup group = placementGroup(plan, origin, plan.blocks().get(i));
            if (!seenGroups.add(group.materialPart().worldPos().asLong())) {
                continue;
            }
            if (firstPlacementObstruction(level, group) != null) {
                return group;
            }
        }
        return null;
    }

    private HiredPathResult clearingPathResult(
            ServerLevel level,
            Villager villager,
            BlockPos pos,
            HiredWorkArea area,
            BlockPos buildCenter) {
        return new HiredMoveToBlockFaceJob(
                level,
                villager,
                List.of(pos),
                8,
                approach -> BuilderSitePlanner.movementAllowed(area, buildCenter, approach)).search();
    }

    private List<BuilderApproachCandidate> buildApproachCandidates(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            BlockPos buildCenter,
            Predicate<BlockPos> movementFilter) {
        List<BuilderApproachCandidate> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        BlockPos villagerPos = villager.blockPosition();
        for (int x = target.getX() - BUILD_APPROACH_RADIUS; x <= target.getX() + BUILD_APPROACH_RADIUS; x++) {
            for (int z = target.getZ() - BUILD_APPROACH_RADIUS; z <= target.getZ() + BUILD_APPROACH_RADIUS; z++) {
                if (horizontalDistanceSqr(x + 0.5D, z + 0.5D, target) > BUILD_REACH_SQR) {
                    continue;
                }
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                addBuildApproachCandidate(level, villager, target, movementFilter, candidates, seen, new BlockPos(x, groundY, z));
                addBuildApproachCandidate(level, villager, target, movementFilter, candidates, seen, new BlockPos(x, groundY - 1, z));
                for (int y = villagerPos.getY() - BUILD_APPROACH_VERTICAL_SEARCH; y <= villagerPos.getY() + BUILD_APPROACH_VERTICAL_SEARCH; y++) {
                    addBuildApproachCandidate(level, villager, target, movementFilter, candidates, seen, new BlockPos(x, y, z));
                }
                if (buildCenter != null) {
                    for (int y = buildCenter.getY() - BUILD_APPROACH_VERTICAL_SEARCH; y <= buildCenter.getY() + BUILD_APPROACH_VERTICAL_SEARCH; y++) {
                        addBuildApproachCandidate(level, villager, target, movementFilter, candidates, seen, new BlockPos(x, y, z));
                    }
                }
            }
        }
        return candidates;
    }

    private void addBuildApproachCandidate(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            Predicate<BlockPos> movementFilter,
            List<BuilderApproachCandidate> candidates,
            Set<Long> seen,
            BlockPos rawPos) {
        BlockPos pos = rawPos.immutable();
        if (!seen.add(pos.asLong())
                || HiredPathMemory.isAvoided(level, villager, pos)
                || !movementFilter.test(pos)
                || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, pos)
                || horizontalDistanceSqr(pos, target) > BUILD_REACH_SQR) {
            return;
        }
        candidates.add(new BuilderApproachCandidate(pos, buildApproachScore(level, villager, pos, target)));
    }

    private double buildApproachScore(ServerLevel level, Villager villager, BlockPos approach, BlockPos target) {
        int vertical = Math.abs(approach.getY() - villager.blockPosition().getY());
        return villager.distanceToSqr(approach.getCenter())
                + horizontalDistanceSqr(approach, target) * 0.35D
                + vertical * vertical * 2.0D
                + HiredMoveToBlockFaceJob.terrainCost(level, approach)
                + HiredPathMemory.recentCost(villager, target);
    }

    private MovementResult moveToBuildTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return moveToBuildTarget(level, villager, context, target, area, buildCenter, plan, origin, false);
    }

    private MovementResult moveToBuildTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            boolean requireReach) {
        HiredPathTarget currentTarget = target;
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        Predicate<BlockPos> approachFilter = pos -> movementFilter.test(pos) && isInsideOrNearBuildSite(pos, plan, origin);
        if (!movementFilter.test(currentTarget.blockPos()) || !approachFilter.test(currentTarget.approachPos())) {
            return MovementResult.BLOCKED;
        }

        if (canUseBuildTargetFromCurrentPosition(level, villager, currentTarget, plan, origin, requireReach)) {
            clearBuildSiteIntermediateNavigation(context);
            holdWorkPosition(villager, currentTarget);
            return MovementResult.READY;
        }

        if (villager.distanceToSqr(currentTarget.approachPos().getCenter()) <= 2.25D
                && !canUseBuildTargetFromCurrentPosition(level, villager, currentTarget, plan, origin, requireReach)) {
            HiredPathTarget repickedTarget = bestBuildTarget(level, villager, currentTarget.blockPos(), area, buildCenter, plan, origin);
            if (repickedTarget != null
                    && (!repickedTarget.approachPos().equals(currentTarget.approachPos())
                    || !repickedTarget.hitPos().equals(currentTarget.hitPos()))) {
                prepareBreakingTarget(level, context, villager, repickedTarget);
                currentTarget = repickedTarget;
            } else if (settleIntoApproach(villager, currentTarget, BUILD_WALK_SPEED)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        currentTarget.approachPos(),
                        villager.distanceToSqr(currentTarget.approachPos().getCenter()));
                return MovementResult.MOVING;
            } else {
                stopWorkNavigation(villager);
                return MovementResult.BLOCKED;
            }
        }
        if (canUseBuildTargetFromCurrentPosition(level, villager, currentTarget, plan, origin, requireReach)) {
            clearBuildSiteIntermediateNavigation(context);
            holdWorkPosition(villager, currentTarget);
            return MovementResult.READY;
        }

        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && currentTarget.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    currentTarget.approachPos(),
                    villager.distanceToSqr(currentTarget.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return MovementResult.BLOCKED;
            }
            return MovementResult.MOVING;
        }

        Path path = villager.getNavigation().createPath(currentTarget.approachPos(), 0);
        if (path == null
                || !path.canReach()
                || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, movementFilter)) {
            if (villager.distanceToSqr(currentTarget.approachPos().getCenter()) <= 2.25D
                    && settleIntoApproach(villager, currentTarget, BUILD_WALK_SPEED)) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        currentTarget.approachPos(),
                        villager.distanceToSqr(currentTarget.approachPos().getCenter()));
                return MovementResult.MOVING;
            }
            HiredPathMemory.clearNavigationProgress(villager);
            return MovementResult.BLOCKED;
        }
        boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(
                villager,
                path,
                currentTarget.approachPos(),
                BUILD_WALK_SPEED,
                BUILD_WALK_CLOSE_ENOUGH);
        if (moved) {
            clearBuildSiteIntermediateNavigation(context);
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    currentTarget.approachPos(),
                    villager.distanceToSqr(currentTarget.approachPos().getCenter()));
            return MovementResult.MOVING;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return MovementResult.BLOCKED;
    }

    private WorkResult clearPlacementObstruction(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            PlacementGroup group,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        PlacementPart obstruction = firstPlacementObstruction(level, group);
        if (obstruction == null) {
            return null;
        }

        PlacementPart reachableObstruction = bestReachablePlacementObstruction(level, villager, group, area, buildCenter);
        if (reachableObstruction != null) {
            obstruction = reachableObstruction;
        }
        BlockPos pos = obstruction.worldPos();
        BlockState state = level.getBlockState(pos);
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                stack -> MiningBlockRules.isUsableBuilderClearingTool(stack, state),
                stack -> effectiveDestroySpeed(stack, state),
                0.55D);
        if (toolResult.status() != ToolStorageStatus.READY && toolResult.status() != ToolStorageStatus.COLLECTED) {
            if (toolResult.status() == ToolStorageStatus.MOVING) {
                BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
                return WorkResult.progressed("interaction.work.status.collecting_tool");
            }
            clearActiveBreakingTarget(level, context, villager);
            if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
                BuilderTaskState.setBlocked(context.state(), "tool_storage_unreachable");
                HiredWorkerBrain.setFailure(context, "tool_storage_unreachable", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_storage_unreachable");
            }
            if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
                WorkResult outputDumpResult = makeRoomForToolStorage(level, villager, context);
                if (outputDumpResult != null) {
                    return outputDumpResult;
                }
                BuilderTaskState.setBlocked(context.state(), "tool_inventory_full");
                HiredWorkerBrain.setFailure(context, "tool_inventory_full", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
                return WorkResult.idle("interaction.work.status.tool_inventory_full");
            }
            BuilderTaskState.setBlocked(context.state(), "missing_clear_tool");
            HiredWorkerBrain.setFailure(context, "missing_clear_tool", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL, pos);
            return WorkResult.idle("interaction.work.builder.missing_clear_tool", Map.of(
                    "target", HiredWorkerBrain.formatPos(pos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }
        ItemStack tool = toolResult.tool();
        HiredStorageNavigationGoal.clearStorageTarget(context);

        HiredPathTarget target = bestClearingTarget(level, villager, pos, area, buildCenter);
        if (target == null) {
            if (recordWorkPathFailure(level, villager, pos)) {
                BuilderTaskState.setBlocked(context.state(), "path_blocked");
            }
            HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, pos);
            return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                    "target", HiredWorkerBrain.formatPos(pos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        prepareBreakingTarget(level, context, villager, target);
        if (!canMineFromCurrentPosition(level, villager, target)) {
            context.setProgressTicks(0);
            MovementResult movementResult = moveToClearingTarget(level, villager, target, area, buildCenter);
            if (movementResult == MovementResult.BLOCKED) {
                if (recordWorkPathFailure(level, villager, pos)) {
                    BuilderTaskState.setBlocked(context.state(), "path_blocked");
                }
                HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
                setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, pos);
                return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                        "target", HiredWorkerBrain.formatPos(pos),
                        "structure", BuilderTaskState.structureLabel(context.state())));
            }
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, pos);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
            return WorkResult.progressed("interaction.work.builder.moving_to_site", Map.of(
                    "target", HiredWorkerBrain.formatPos(pos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), villager, tool);
        if (!context.canStoreOutputs(drops)) {
            DepositResult depositResult = depositOutputsForFullInventory(level, context, villager, BUILD_WALK_SPEED);
            if (depositResult == DepositResult.MOVING) {
                setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
                return WorkResult.progressed("interaction.work.builder.output_full_depositing", BuilderTaskState.replacements(context.state()));
            }
            if (!context.canStoreOutputs(drops)) {
                BuilderTaskState.setBlocked(context.state(), "output_inventory_full");
                HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, pos);
                return WorkResult.idle("interaction.work.builder.output_full_blocked", BuilderTaskState.replacements(context.state()));
            }
        }

        holdWorkPosition(villager, target);
        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WORKING, pos);
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.BUILDING);

        int needed = actualBreakProgressGoal(level, pos, tool);
        int progress = context.progressTicks() + 1;
        if (progress < needed) {
            context.setProgressTicks(progress);
            swingWorkTool(villager);
            showBreakProgress(level, villager, pos, progress, needed);
            return WorkResult.progressed("interaction.work.builder.clearing_obstruction", Map.of(
                    "target", HiredWorkerBrain.formatPos(pos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        context.setProgressTicks(0);
        for (ItemStack drop : drops) {
            if (!context.storeOutputAfterDepositIfFull(villager, drop).isEmpty()) {
                BuilderTaskState.setBlocked(context.state(), "output_inventory_full");
                HiredWorkerBrain.setFailure(context, "output_inventory_full", 0L);
                setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, pos);
                return WorkResult.idle("interaction.work.builder.output_full_blocked", BuilderTaskState.replacements(context.state()));
            }
        }
        faceBlock(villager, target);
        swingWorkTool(villager);
        EnchantmentHelper.onHitBlock(level, tool, villager, villager, EquipmentSlot.MAINHAND, target.hitPos(), state, ignored -> {
        });
        level.destroyBlock(pos, false, villager);
        level.destroyBlockProgress(villager.getId(), pos, -1);
        damageTool(context, villager, tool, level, state, pos);
        HiredPathMemory.rememberRecent(level, pos);
        clearWorkPathFailure(villager, pos);
        clearActiveBreakingTarget(level, context, villager);
        BuilderTaskState.clearMaterialBatch(context.state());
        return WorkResult.progressed("interaction.work.builder.cleared_obstruction", Map.of(
                "target", HiredWorkerBrain.formatPos(pos),
                "structure", BuilderTaskState.structureLabel(context.state())));
    }

    private HiredPathTarget bestClearingTarget(
            ServerLevel level,
            Villager villager,
            BlockPos pos,
            HiredWorkArea area,
            BlockPos buildCenter) {
        HiredPathResult result = clearingPathResult(level, villager, pos, area, buildCenter);
        return result.reachesDestination() ? result.target() : null;
    }

    private MovementResult moveToClearingTarget(
            ServerLevel level,
            Villager villager,
            HiredPathTarget target,
            HiredWorkArea area,
            BlockPos buildCenter) {
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        if (!movementFilter.test(target.approachPos())) {
            return MovementResult.BLOCKED;
        }
        if (canMineFromCurrentPosition(level, villager, target)) {
            holdWorkPosition(villager, target);
            return MovementResult.READY;
        }
        BlockPos navigationTarget = villager.getNavigation().getTargetPos();
        if (!villager.getNavigation().isDone() && target.approachPos().equals(navigationTarget)) {
            if (HiredPathMemory.isNavigationBlocked(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()))) {
                stopWorkNavigation(villager);
                return MovementResult.BLOCKED;
            }
            return MovementResult.MOVING;
        }
        Path path = villager.getNavigation().createPath(target.approachPos(), 0);
        if (path != null
                && path.canReach()
                && HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, movementFilter)
                && VillagerTaskNavigationUtil.moveToHiredPath(
                        villager,
                        path,
                        target.approachPos(),
                        BUILD_WALK_SPEED,
                        BUILD_WALK_CLOSE_ENOUGH)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return MovementResult.MOVING;
        }
        if (villager.distanceToSqr(target.approachPos().getCenter()) <= 2.25D
                && settleIntoApproach(villager, target, BUILD_WALK_SPEED)) {
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return MovementResult.MOVING;
        }
        HiredPathMemory.clearNavigationProgress(villager);
        return MovementResult.BLOCKED;
    }

    private WorkResult ensurePlacementToolAction(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            PlacementGroup group) {
        BuilderStructureScanner.BuilderToolAction action = requiredToolAction(level, group);
        if (action == BuilderStructureScanner.BuilderToolAction.NONE) {
            return null;
        }
        ToolStorageResult toolResult = equipBestToolOrCollectFromStorage(
                level,
                villager,
                context,
                stack -> BuilderStructureScanner.matchesToolAction(stack, action),
                stack -> 1.0D,
                BUILD_WALK_SPEED);
        if (toolResult.status() == ToolStorageStatus.READY || toolResult.status() == ToolStorageStatus.COLLECTED) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            return null;
        }
        if (toolResult.status() == ToolStorageStatus.MOVING) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.progressed("interaction.work.status.collecting_tool");
        }
        clearActiveBreakingTarget(level, context, villager);
        if (toolResult.status() == ToolStorageStatus.UNREACHABLE) {
            BuilderTaskState.setBlocked(context.state(), "tool_storage_unreachable");
            HiredWorkerBrain.setFailure(context, "tool_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, toolResult.storagePos());
            return WorkResult.idle("interaction.work.status.tool_storage_unreachable");
        }
        if (toolResult.status() == ToolStorageStatus.INVENTORY_FULL) {
            WorkResult outputDumpResult = makeRoomForToolStorage(level, villager, context);
            if (outputDumpResult != null) {
                return outputDumpResult;
            }
            BuilderTaskState.setBlocked(context.state(), "tool_inventory_full");
            HiredWorkerBrain.setFailure(context, "tool_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, toolResult.storagePos());
            return WorkResult.idle("interaction.work.status.tool_inventory_full");
        }
        BuilderTaskState.setBlocked(context.state(), "missing_placement_tool");
        HiredWorkerBrain.setFailure(context, "missing_placement_tool", 0L);
        setTaskState(context, HiredWorkerTaskState.PAUSED_MISSING_TOOL, group.materialPart().worldPos());
        return WorkResult.idle("interaction.work.builder.missing_placement_tool", Map.of(
                "target", HiredWorkerBrain.formatPos(group.materialPart().worldPos()),
                "structure", BuilderTaskState.structureLabel(context.state()),
                "tool", toolActionLabel(action)));
    }

    private static String toolActionLabel(BuilderStructureScanner.BuilderToolAction action) {
        return switch (action) {
            case AXE_STRIP -> "axe";
            case SHOVEL_FLATTEN -> "shovel";
            case HOE_TILL -> "hoe";
            case NONE -> "tool";
        };
    }

    private BuilderStructureScanner.BuilderToolAction requiredToolAction(ServerLevel level, PlacementGroup group) {
        for (PlacementPart part : group.parts()) {
            BuilderStructureScanner.BuildBlock block = part.block();
            if (!block.requiresToolAction()) {
                continue;
            }
            BlockState current = level.getBlockState(part.worldPos());
            if (!BuilderStructureScanner.sameSchematicState(current, block.state())) {
                return block.toolAction();
            }
        }
        return BuilderStructureScanner.BuilderToolAction.NONE;
    }

    private PlacementPart firstPlacementObstruction(ServerLevel level, PlacementGroup group) {
        List<PlacementPart> obstructions = placementObstructions(level, group);
        return obstructions.isEmpty() ? null : obstructions.getFirst();
    }

    private List<PlacementPart> placementObstructions(ServerLevel level, PlacementGroup group) {
        List<PlacementPart> obstructions = new ArrayList<>();
        for (PlacementPart part : group.parts()) {
            if (BuilderSitePlanner.requiresClearingBeforePlacement(level, part.worldPos(), part.block().state())) {
                obstructions.add(part);
            }
        }
        return obstructions;
    }

    private PlacementPart bestReachablePlacementObstruction(
            ServerLevel level,
            Villager villager,
            PlacementGroup group,
            HiredWorkArea area,
            BlockPos buildCenter) {
        ReachableObstruction reachable = reachablePlacementObstruction(level, villager, group, area, buildCenter, 0);
        return reachable == null ? null : reachable.obstruction();
    }

    private int actualBreakProgressGoal(ServerLevel level, BlockPos pos, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness <= 0.0F) {
            return 1;
        }
        float speed = Math.max(0.001F, effectiveDestroySpeed(tool, state));
        return Math.max(1, (int) Math.ceil(hardness * 30.0F / speed));
    }

    private PlacementGroup placementGroup(
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BuilderStructureScanner.BuildBlock block) {
        BuilderStructureScanner.BuildBlock primary = primaryBlock(plan, block);
        List<PlacementPart> parts = new ArrayList<>();
        parts.add(new PlacementPart(primary, plan.worldPos(origin, primary)));
        BuilderStructureScanner.BuildBlock linked = linkedBlock(plan, primary);
        if (linked != null && linked != primary) {
            parts.add(new PlacementPart(linked, plan.worldPos(origin, linked)));
        }
        return new PlacementGroup(primary, parts);
    }

    private BuilderStructureScanner.BuildBlock primaryBlock(
            BuilderStructureScanner.StructurePlan plan,
            BuilderStructureScanner.BuildBlock block) {
        if (block.state().getBlock() instanceof DoorBlock
                && block.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            BuilderStructureScanner.BuildBlock linked = blockAt(plan, block.localPos().below());
            return linked == null ? block : linked;
        }
        if (block.state().getBlock() instanceof BedBlock
                && block.state().getValue(BedBlock.PART) == BedPart.HEAD) {
            Direction facing = block.state().getValue(BedBlock.FACING);
            BuilderStructureScanner.BuildBlock linked = blockAt(plan, block.localPos().relative(facing.getOpposite()));
            return linked == null ? block : linked;
        }
        return block;
    }

    private BuilderStructureScanner.BuildBlock linkedBlock(
            BuilderStructureScanner.StructurePlan plan,
            BuilderStructureScanner.BuildBlock primary) {
        if (primary.state().getBlock() instanceof DoorBlock
                && primary.state().getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            return blockAt(plan, primary.localPos().above());
        }
        if (primary.state().getBlock() instanceof BedBlock
                && primary.state().getValue(BedBlock.PART) == BedPart.FOOT) {
            Direction facing = primary.state().getValue(BedBlock.FACING);
            return blockAt(plan, primary.localPos().relative(facing));
        }
        return null;
    }

    private BuilderStructureScanner.BuildBlock blockAt(
            BuilderStructureScanner.StructurePlan plan,
            BlockPos localPos) {
        for (BuilderStructureScanner.BuildBlock candidate : plan.blocks()) {
            if (candidate.localPos().equals(localPos)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean placementGroupNeedsMaterial(ServerLevel level, PlacementGroup group) {
        BlockState current = level.getBlockState(group.materialPart().worldPos());
        return group.materialBlock().requiresMaterial()
                && !BuilderStructureScanner.sameSchematicState(current, group.materialBlock().state())
                && !BuilderStructureScanner.canTransformExisting(current, group.materialBlock().state());
    }

    private boolean canBuildFromCurrentPosition(
            Villager villager,
            BlockPos worldPos,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return isInsideOrNearBuildSite(villager, plan, origin)
                && horizontalDistanceSqr(villager.getX(), villager.getZ(), worldPos) <= BUILD_REACH_SQR;
    }

    private boolean canUseBuildTargetFromCurrentPosition(
            ServerLevel level,
            Villager villager,
            HiredPathTarget target,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            boolean requireReach) {
        if (!canBuildFromCurrentPosition(villager, target.blockPos(), plan, origin)) {
            return false;
        }
        return !requireReach || canMineFromCurrentPosition(level, villager, target);
    }

    private static boolean isNearBuildSite(
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildBorderSqr(villager.getX(), villager.getZ(), plan, origin)
                <= BUILD_SITE_BORDER_SAFE_DISTANCE_SQR;
    }

    private static boolean isNearBuildSite(
            BlockPos pos,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildBorderSqr(pos.getX() + 0.5D, pos.getZ() + 0.5D, plan, origin)
                <= BUILD_SITE_BORDER_SAFE_DISTANCE_SQR;
    }

    private static boolean isInsideOrNearBuildSite(
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildFootprintSqr(villager.getX(), villager.getZ(), plan, origin)
                <= BUILD_SITE_BORDER_SAFE_DISTANCE_SQR;
    }

    private static boolean isInsideOrNearBuildSite(
            BlockPos pos,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildFootprintSqr(pos.getX() + 0.5D, pos.getZ() + 0.5D, plan, origin)
                <= BUILD_SITE_BORDER_SAFE_DISTANCE_SQR;
    }

    private static double horizontalDistanceToBuildFootprintSqr(
            double x,
            double z,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        BlockPos min = plan.worldMin(origin);
        BlockPos max = plan.worldMax(origin);
        double minX = Math.min(min.getX(), max.getX());
        double maxX = Math.max(min.getX(), max.getX()) + 1.0D;
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxZ = Math.max(min.getZ(), max.getZ()) + 1.0D;
        double clampedX = Math.max(minX, Math.min(x, maxX));
        double clampedZ = Math.max(minZ, Math.min(z, maxZ));
        double dx = x - clampedX;
        double dz = z - clampedZ;
        return dx * dx + dz * dz;
    }

    private static double horizontalDistanceToBuildBorderSqr(
            double x,
            double z,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        BlockPos min = plan.worldMin(origin);
        BlockPos max = plan.worldMax(origin);
        double minX = Math.min(min.getX(), max.getX());
        double maxX = Math.max(min.getX(), max.getX()) + 1.0D;
        double minZ = Math.min(min.getZ(), max.getZ());
        double maxZ = Math.max(min.getZ(), max.getZ()) + 1.0D;
        boolean insideX = x >= minX && x <= maxX;
        boolean insideZ = z >= minZ && z <= maxZ;
        if (insideX && insideZ) {
            double distanceToBorder = Math.min(
                    Math.min(x - minX, maxX - x),
                    Math.min(z - minZ, maxZ - z));
            return distanceToBorder * distanceToBorder;
        }
        double dx = insideX ? 0.0D : x < minX ? minX - x : x - maxX;
        double dz = insideZ ? 0.0D : z < minZ ? minZ - z : z - maxZ;
        return dx * dx + dz * dz;
    }

    private static double horizontalDistanceSqr(BlockPos pos, BlockPos target) {
        return horizontalDistanceSqr(pos.getX() + 0.5D, pos.getZ() + 0.5D, target);
    }

    private static double horizontalDistanceSqr(double x, double z, BlockPos target) {
        double dx = x - (target.getX() + 0.5D);
        double dz = z - (target.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private boolean builderIntersectsPlacement(ServerLevel level, Villager villager, BlockPos worldPos, BlockState state) {
        if (state.getCollisionShape(level, worldPos, CollisionContext.of(villager)).isEmpty()) {
            return false;
        }
        return new AABB(worldPos).intersects(villager.getBoundingBox().inflate(-0.02D));
    }

    private WorkResult moveOutOfSchematicIfNeeded(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        if (isNearBuildSite(villager, plan, origin)) {
            return null;
        }
        List<BlockPos> placementCollisionPositions = placementCollisionPositions(level, plan, origin);
        if (placementCollisionPositions.isEmpty()
                || !bodyIntersectsAnyPlacement(villager, villager.getBoundingBox().inflate(-0.02D), placementCollisionPositions)) {
            return null;
        }

        if (moveAwayFromPlacements(
                level,
                villager,
                area,
                buildCenter,
                villager.blockPosition(),
                placementCollisionPositions,
                BUILD_APPROACH_RADIUS)) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, buildCenter);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
            return WorkResult.progressed("interaction.work.builder.moving_to_site", Map.of(
                    "target", HiredWorkerBrain.formatPos(buildCenter),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        BuilderTaskState.setBlocked(context.state(), "blocked_entity");
        HiredWorkerBrain.setFailure(context, "blocked_entity", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, villager.blockPosition());
        return WorkResult.idle("interaction.work.builder.blocked_entity", Map.of(
                "target", HiredWorkerBrain.formatPos(villager.blockPosition()),
                "structure", BuilderTaskState.structureLabel(context.state())));
    }

    private WorkResult moveOutOfBuildInteriorIfNeeded(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredWorkArea area,
            BlockPos buildCenter,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin,
            BuilderStructureScanner.BuildBlock nextBlock) {
        if (nextBlock.localPos().getY() <= plan.localMin().getY()
                || isNearBuildSite(villager, plan, origin)) {
            return null;
        }

        List<BlockPos> placementCollisionPositions = placementCollisionPositions(level, plan, origin);
        if (moveAwayFromPlacements(
                level,
                villager,
                area,
                buildCenter,
                villager.blockPosition(),
                placementCollisionPositions,
                BUILD_APPROACH_RADIUS,
                candidate -> isNearBuildSite(candidate, plan, origin))) {
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_TARGET, buildCenter);
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.TRAVELING_TO_SITE);
            return WorkResult.progressed("interaction.work.builder.moving_to_site", Map.of(
                    "target", HiredWorkerBrain.formatPos(buildCenter),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        BuilderTaskState.setBlocked(context.state(), "path_blocked");
        HiredWorkerBrain.setFailure(context, "path_blocked", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, villager.blockPosition());
        return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                "target", HiredWorkerBrain.formatPos(villager.blockPosition()),
                "structure", BuilderTaskState.structureLabel(context.state())));
    }

    private List<BlockPos> placementCollisionPositions(
            ServerLevel level,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        List<BlockPos> positions = new ArrayList<>();
        for (BuilderStructureScanner.BuildBlock block : plan.blocks()) {
            BlockPos worldPos = plan.worldPos(origin, block);
            if (!block.state().getCollisionShape(level, worldPos, CollisionContext.empty()).isEmpty()) {
                positions.add(worldPos);
            }
        }
        return positions;
    }

    private boolean moveAwayFromPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkArea area,
            BlockPos buildCenter,
            BlockPos placementPos) {
        return moveAwayFromPlacements(
                level,
                villager,
                area,
                buildCenter,
                placementPos,
                List.of(placementPos),
                SELF_PLACEMENT_CLEAR_RADIUS,
                ignored -> true);
    }

    private boolean moveAwayFromPlacements(
            ServerLevel level,
            Villager villager,
            HiredWorkArea area,
            BlockPos buildCenter,
            BlockPos anchor,
            List<BlockPos> placementPositions,
            int searchRadius) {
        return moveAwayFromPlacements(
                level,
                villager,
                area,
                buildCenter,
                anchor,
                placementPositions,
                searchRadius,
                ignored -> true);
    }

    private boolean moveAwayFromPlacements(
            ServerLevel level,
            Villager villager,
            HiredWorkArea area,
            BlockPos buildCenter,
            BlockPos anchor,
            List<BlockPos> placementPositions,
            int searchRadius,
            Predicate<BlockPos> candidateFilter) {
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        Predicate<BlockPos> safeCandidateFilter = candidateFilter == null ? ignored -> true : candidateFilter;
        List<ClearSpotCandidate> candidates = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                anchor.offset(-searchRadius, -1, -searchRadius),
                anchor.offset(searchRadius, 1, searchRadius))) {
            BlockPos safeCandidate = candidate.immutable();
            AABB candidateBody = bodyAt(villager, safeCandidate);
            if (!movementFilter.test(safeCandidate)
                    || !safeCandidateFilter.test(safeCandidate)
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, safeCandidate)
                    || bodyIntersectsAnyPlacement(villager, candidateBody, placementPositions)
                    || !level.noCollision(villager, candidateBody)) {
                continue;
            }
            candidates.add(new ClearSpotCandidate(safeCandidate, villager.distanceToSqr(safeCandidate.getCenter())));
        }
        if (candidates.isEmpty()) {
            return false;
        }
        candidates.sort(Comparator.comparingDouble(ClearSpotCandidate::distanceSqr));

        for (ClearSpotCandidate candidate : candidates) {
            Path path = villager.getNavigation().createPath(candidate.pos(), 0);
            if (path == null
                    || !path.canReach()
                    || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, movementFilter)) {
                continue;
            }
            boolean moved = VillagerTaskNavigationUtil.moveToHiredPath(
                    villager,
                    path,
                    candidate.pos(),
                    BUILD_WALK_SPEED,
                    BUILD_WALK_CLOSE_ENOUGH);
            if (moved) {
                HiredPathMemory.rememberNavigationProgress(
                        level,
                        villager,
                        candidate.pos(),
                        villager.distanceToSqr(candidate.pos().getCenter()));
                return true;
            }
            HiredPathMemory.clearNavigationProgress(villager);
        }

        for (ClearSpotCandidate candidate : candidates) {
            if (canMoveDirectlyToClearSpot(level, villager, candidate.pos(), placementPositions, movementFilter)
                    && moveDirectlyToClearSpot(level, villager, candidate.pos())) {
                return true;
            }
        }
        return false;
    }

    private boolean bodyIntersectsAnyPlacement(Villager villager, AABB body, List<BlockPos> placementPositions) {
        for (BlockPos placementPos : placementPositions) {
            if (body.intersects(new AABB(placementPos))) {
                return true;
            }
        }
        return false;
    }

    private boolean moveDirectlyToClearSpot(ServerLevel level, Villager villager, BlockPos target) {
        if (villager.distanceToSqr(target.getCenter()) > 16.0D) {
            return false;
        }
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        villager.getMoveControl().setWantedPosition(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                BUILD_WALK_SPEED);
        VillagerTaskNavigationUtil.setHiredWalkTarget(villager, target, BUILD_WALK_SPEED, BUILD_WALK_CLOSE_ENOUGH);
        HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
        return true;
    }

    private boolean canMoveDirectlyToClearSpot(
            ServerLevel level,
            Villager villager,
            BlockPos target,
            List<BlockPos> placementPositions,
            Predicate<BlockPos> movementFilter) {
        if (villager.distanceToSqr(target.getCenter()) > 16.0D) {
            return false;
        }
        Vec3 start = villager.position();
        Vec3 end = target.getCenter();
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) * 2.0D));
        for (int step = 1; step <= steps; step++) {
            double progress = step / (double) steps;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (target.getY() - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!movementFilter.test(pos)) {
                return false;
            }
            AABB body = bodyAtPosition(villager, x, y, z);
            if (bodyIntersectsAnyPlacement(villager, body, placementPositions) || !level.noCollision(villager, body)) {
                return false;
            }
        }
        return true;
    }

    private AABB bodyAtPosition(Villager villager, double centerX, double y, double centerZ) {
        AABB current = villager.getBoundingBox();
        double halfWidth = current.getXsize() * 0.5D;
        return new AABB(
                centerX - halfWidth,
                y,
                centerZ - halfWidth,
                centerX + halfWidth,
                y + current.getYsize(),
                centerZ + halfWidth);
    }

    private AABB bodyAt(Villager villager, BlockPos pos) {
        AABB current = villager.getBoundingBox();
        double halfWidth = current.getXsize() * 0.5D;
        double centerX = pos.getX() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;
        return new AABB(
                centerX - halfWidth,
                pos.getY(),
                centerZ - halfWidth,
                centerX + halfWidth,
                pos.getY() + current.getYsize(),
                centerZ + halfWidth);
    }

    private boolean placeBlock(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            PlacementGroup group) {
        boolean consumesMaterial = placementGroupNeedsMaterial(level, group);
        BuilderStructureScanner.BuildBlock materialBlock = group.materialBlock();
        if (consumesMaterial && context.inventory().findSupply(materialBlock::materialMatches).isEmpty()) {
            return false;
        }
        BuilderStructureScanner.BuilderToolAction toolAction = requiredToolAction(level, group);
        ItemStack actionTool = toolAction == BuilderStructureScanner.BuilderToolAction.NONE
                ? ItemStack.EMPTY
                : context.inventory().equipBestTool(
                        stack -> BuilderStructureScanner.matchesToolAction(stack, toolAction),
                        stack -> 1.0D);
        if (toolAction != BuilderStructureScanner.BuilderToolAction.NONE && actionTool.isEmpty()) {
            return false;
        }
        faceBlock(villager, group.materialPart().worldPos());
        swingWorkTool(villager);

        for (PlacementPart part : group.parts()) {
            BlockPos worldPos = part.worldPos();
            BuilderStructureScanner.BuildBlock block = part.block();
            BlockState current = level.getBlockState(worldPos);
            if (BuilderStructureScanner.sameSchematicState(current, block.state())) {
                continue;
            }
            BlockState sourceState = BuilderStructureScanner.toolSourceState(block.state());
            if (block.requiresToolAction()
                    && sourceState != null
                    && !BuilderStructureScanner.canTransformExisting(current, block.state())) {
                boolean placedSource = level.setBlock(worldPos, sourceState, Block.UPDATE_ALL);
                if (!placedSource) {
                    return false;
                }
            }
            boolean placed = level.setBlock(worldPos, block.state(), Block.UPDATE_ALL);
            if (!placed) {
                return false;
            }
            if (block.requiresToolAction()) {
                damageTool(context, villager, actionTool);
            }
            if (block.blockEntityTag() != null && level.getBlockEntity(worldPos) instanceof BlockEntity blockEntity) {
                CompoundTag tag = sanitizeBuiltBlockEntityTag(block.blockEntityTag());
                tag.putInt("x", worldPos.getX());
                tag.putInt("y", worldPos.getY());
                tag.putInt("z", worldPos.getZ());
                blockEntity.loadWithComponents(tag, level.registryAccess());
                blockEntity.setChanged();
            }
        }
        if (consumesMaterial) {
            context.inventory().consumeSupply(materialBlock::materialMatches, 1);
        }
        return true;
    }

    private static CompoundTag sanitizeBuiltBlockEntityTag(CompoundTag source) {
        CompoundTag tag = source.copy();
        tag.remove("LootTable");
        tag.remove("LootTableSeed");
        return tag;
    }

    private WorkResult finishBuild(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.StructurePlan plan) {
        Map<String, String> replacements = BuilderTaskState.replacements(context.state());
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.DEPOSITING_LEFTOVERS);
        depositLeftoverMaterials(villager, context.inventory(), plan);
        clearActiveBreakingTarget(level, context, villager);
        clearBuildSiteIntermediateNavigation(context);
        BuilderTaskState.jobId(context.state()).ifPresent(jobId ->
                ConstructionBlueprintItem.completeMatchingBlueprints(level, jobId));
        BuilderTaskState.clearTask(context.state());
        HiredWorkerBrain.clearFailure(context);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        setTaskState(context, HiredWorkerTaskState.IDLE);
        return WorkResult.completed("interaction.work.builder.complete", replacements);
    }

    private void depositLeftoverMaterials(
            Villager villager,
            HiredJobInventory inventory,
            BuilderStructureScanner.StructurePlan plan) {
        for (int slot : inventory.supplySlots()) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !isBuildMaterial(plan, stack)) {
                continue;
            }
            ItemStack remainder = AssignedStorageService.depositStack(villager, stack.copy());
            int moved = stack.getCount() - remainder.getCount();
            if (moved <= 0) {
                continue;
            }
            stack.shrink(moved);
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                inventory.setChanged();
            }
        }
    }

    private boolean isBuildMaterial(BuilderStructureScanner.StructurePlan plan, ItemStack stack) {
        for (BuilderStructureScanner.MaterialRequirement material : plan.materials()) {
            if (BuilderStructureScanner.sameMaterial(stack, material.item())) {
                return true;
            }
        }
        return false;
    }

    private enum MovementResult {
        READY,
        MOVING,
        BLOCKED
    }

    private record PlacementPart(BuilderStructureScanner.BuildBlock block, BlockPos worldPos) {
    }

    private record PlacementGroup(BuilderStructureScanner.BuildBlock materialBlock, List<PlacementPart> parts) {
        private PlacementPart materialPart() {
            return this.parts.getFirst();
        }
    }

    private record BuilderApproachCandidate(BlockPos pos, double score) {
    }

    private record ClearSpotCandidate(BlockPos pos, double distanceSqr) {
    }

    private record ReachableObstruction(PlacementGroup group, PlacementPart obstruction, double score) {
    }

    private record BuildSiteIntermediate(BlockPos pos, double score) {
    }

    private record MaterialBatch(int endIndex, List<BuilderStructureScanner.MaterialRequirement> materials) {
    }

    public record MissingMaterials(List<String> missing) {
        public static MissingMaterials of(BuilderStructureScanner.BuildBlock block) {
            if (block == null || !block.requiresMaterial()) {
                return new MissingMaterials(List.of());
            }
            return new MissingMaterials(List.of("1x " + block.requiredItem().getHoverName().getString()));
        }

        public boolean ready() {
            return this.missing.isEmpty();
        }

        public String summary() {
            if (this.missing.isEmpty()) {
                return "none";
            }
            return String.join(", ", this.missing.subList(0, Math.min(5, this.missing.size())));
        }
    }
}
