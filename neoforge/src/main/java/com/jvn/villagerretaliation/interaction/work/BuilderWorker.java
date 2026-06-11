package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
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
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BuilderWorker extends AbstractBlockWorker {
    private static final double BUILD_WALK_SPEED = 0.52D;
    private static final int BUILD_WALK_CLOSE_ENOUGH = 1;
    private static final int MAX_BUILD_TARGETS_TO_PATHFIND = 128;
    private static final double BUILD_REACH = 12.0D;
    private static final double BUILD_REACH_SQR = BUILD_REACH * BUILD_REACH;
    private static final double BUILD_SITE_REACH_ACTIVATION_RADIUS = 6.0D;
    private static final double BUILD_SITE_REACH_ACTIVATION_RADIUS_SQR = BUILD_SITE_REACH_ACTIVATION_RADIUS * BUILD_SITE_REACH_ACTIVATION_RADIUS;
    private static final int BUILD_APPROACH_RADIUS = 12;
    private static final int BUILD_APPROACH_VERTICAL_SEARCH = 4;
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
        HiredWorkArea area = context.hasWorkArea() ? context.workArea() : null;
        BuilderSitePlanner.SiteResult siteResult = BuilderSitePlanner.validateSite(
                level,
                hirer,
                villager,
                area,
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

        MissingMaterials missing = missingMaterials(villager, context.inventory(), plan.get(), index);
        if (!missing.ready()) {
            return waitForMaterialsAtAssignedStorage(level, villager, context, missing);
        }

        BuilderStructureScanner.BuildBlock block = plan.get().blocks().get(index);
        BlockPos worldPos = plan.get().worldPos(origin, block);
        PlacementGroup placementGroup = placementGroup(plan.get(), origin, block);
        MaterialResult materialResult = placementGroupNeedsMaterial(level, placementGroup)
                ? ensureMaterial(level, villager, context, placementGroup.materialBlock())
                : MaterialResult.READY;
        if (materialResult == MaterialResult.MOVING) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.COLLECTING_MATERIALS);
            return WorkResult.idle("interaction.work.builder.collecting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (materialResult == MaterialResult.INVENTORY_FULL) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.builder.material_inventory_full", BuilderTaskState.replacements(context.state()));
        }
        if (materialResult == MaterialResult.MISSING) {
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.builder.waiting_materials", BuilderTaskState.replacements(context.state()));
        }
        if (materialResult == MaterialResult.UNREACHABLE) {
            BuilderTaskState.setBlocked(context.state(), "builder_material_storage_unreachable");
            return WorkResult.idle("interaction.work.builder.materials_unreachable", BuilderTaskState.replacements(context.state()));
        }

        BlockPos buildCenter = origin.offset(plan.get().localCenter());
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

        if (!canBuildFromCurrentPosition(villager, worldPos, plan.get(), origin)) {
            HiredPathTarget target = bestBuildTarget(level, villager, worldPos, area, buildCenter, plan.get(), origin);
            if (target == null) {
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
    public void stop(ServerLevel level, Villager villager, HiredWorkContext context) {
        clearActiveBreakingTarget(level, context, villager);
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
        count += AssignedStorageService.countItems(villager, stack -> BuilderStructureScanner.sameMaterial(stack, required));
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
        List<String> missing = new ArrayList<>();
        for (BuilderStructureScanner.MaterialRequirement material : remainingMaterials(plan, startIndex)) {
            int available = countAvailableMaterial(villager, inventory, material.item());
            if (available < material.count()) {
                missing.add((material.count() - available) + "x " + material.itemName());
            }
        }
        return new MissingMaterials(missing);
    }

    private WorkResult waitForMaterialsAtAssignedStorage(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            MissingMaterials missing) {
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
        Map<String, String> replacements = Map.of(
                "materials", missing.summary(),
                "structure", BuilderTaskState.structureLabel(context.state()));
        if (!AssignedStorageService.hasAssignedStorage(level, villager)) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "missing_builder_materials", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return WorkResult.idle("interaction.work.builder.waiting_materials", replacements);
        }

        BlockPos storage = AssignedStorageService.nearestAssignedStoragePos(level, villager);
        if (storage == null) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "missing_builder_materials", 0L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return WorkResult.idle("interaction.work.builder.waiting_materials", replacements);
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
            return WorkResult.progressed("interaction.work.builder.moving_to_material_storage", replacements);
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "builder_material_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return WorkResult.idle("interaction.work.builder.materials_unreachable", replacements);
        }

        HiredWorkerBrain.clearFailure(context);
        setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return WorkResult.idle("interaction.work.builder.waiting_materials_at_storage", replacements);
    }

    private static List<BuilderStructureScanner.MaterialRequirement> remainingMaterials(
            BuilderStructureScanner.StructurePlan plan,
            int startIndex) {
        List<BuilderStructureScanner.MaterialRequirement> materials = new ArrayList<>();
        int start = Math.clamp(startIndex, 0, plan.blocks().size());
        for (int i = start; i < plan.blocks().size(); i++) {
            BuilderStructureScanner.BuildBlock block = plan.blocks().get(i);
            if (!block.requiresMaterial()) {
                continue;
            }
            int existing = indexOfMaterial(materials, block.requiredItem());
            if (existing >= 0) {
                BuilderStructureScanner.MaterialRequirement material = materials.get(existing);
                materials.set(existing, new BuilderStructureScanner.MaterialRequirement(material.item(), material.count() + 1));
            } else {
                materials.add(new BuilderStructureScanner.MaterialRequirement(block.requiredItem().copyWithCount(1), 1));
            }
        }
        return materials;
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
        Optional<BuilderStructureCatalog.Entry> entry = BuilderStructureCatalog.byId(structureId.get());
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
            if (!level.getBlockState(plan.worldPos(origin, block)).equals(block.state())) {
                break;
            }
            index++;
        }
        BuilderTaskState.setPlacedIndex(state, index);
        return index;
    }

    private MaterialResult ensureMaterial(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.BuildBlock block) {
        if (!block.requiresMaterial() || !context.inventory().findSupply(block::materialMatches).isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return MaterialResult.READY;
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, block::materialMatches);
        if (storage == null) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setFailure(context, "missing_builder_materials", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
            return MaterialResult.MISSING;
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
            return MaterialResult.MOVING;
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            HiredWorkerBrain.setFailure(context, "builder_material_storage_unreachable", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, storage);
            return MaterialResult.UNREACHABLE;
        }
        faceBlock(villager, storage);
        int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storage,
                block::materialMatches,
                block.requiredItem().getMaxStackSize(),
                stack -> context.inventory().insertSupply(stack));
        if (moved <= 0) {
            HiredWorkerBrain.setFailure(context, "builder_material_inventory_full", level.getGameTime() + 100L);
            setTaskState(context, HiredWorkerTaskState.PAUSED_FULL_INVENTORY, storage);
            return MaterialResult.INVENTORY_FULL;
        }
        swingWorkTool(villager);
        if (!context.inventory().findSupply(block::materialMatches).isEmpty()) {
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.clearFailure(context);
            return MaterialResult.READY;
        }
        HiredWorkerBrain.setFailure(context, "missing_builder_materials", level.getGameTime() + 100L);
        setTaskState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
        return MaterialResult.MISSING;
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
        Predicate<BlockPos> approachFilter = pos -> movementFilter.test(pos) && isNearBuildSite(pos, plan, origin);
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
            double score = candidate.score() + path.getNodeCount() * 1.5D;
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
        HiredPathTarget currentTarget = target;
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        Predicate<BlockPos> approachFilter = pos -> movementFilter.test(pos) && isNearBuildSite(pos, plan, origin);
        if (!movementFilter.test(currentTarget.blockPos()) || !approachFilter.test(currentTarget.approachPos())) {
            return MovementResult.BLOCKED;
        }

        if (canBuildFromCurrentPosition(villager, currentTarget.blockPos(), plan, origin)) {
            holdWorkPosition(villager, currentTarget);
            return MovementResult.READY;
        }

        if (villager.distanceToSqr(currentTarget.approachPos().getCenter()) <= 2.25D
                && !canBuildFromCurrentPosition(villager, currentTarget.blockPos(), plan, origin)) {
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
        if (canBuildFromCurrentPosition(villager, currentTarget.blockPos(), plan, origin)) {
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
        boolean moved = villager.getNavigation().moveTo(path, BUILD_WALK_SPEED);
        if (moved) {
            villager.getBrain().setMemory(
                    MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(currentTarget.approachPos()), (float) BUILD_WALK_SPEED, BUILD_WALK_CLOSE_ENOUGH));
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
        return group.materialBlock().requiresMaterial()
                && !level.getBlockState(group.materialPart().worldPos()).equals(group.materialBlock().state());
    }

    private boolean canBuildFromCurrentPosition(
            Villager villager,
            BlockPos worldPos,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return isNearBuildSite(villager, plan, origin)
                && horizontalDistanceSqr(villager.getX(), villager.getZ(), worldPos) <= BUILD_REACH_SQR;
    }

    private static boolean isNearBuildSite(
            Villager villager,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildFootprintSqr(villager.getX(), villager.getZ(), plan, origin)
                <= BUILD_SITE_REACH_ACTIVATION_RADIUS_SQR;
    }

    private static boolean isNearBuildSite(
            BlockPos pos,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        return horizontalDistanceToBuildFootprintSqr(pos.getX() + 0.5D, pos.getZ() + 0.5D, plan, origin)
                <= BUILD_SITE_REACH_ACTIVATION_RADIUS_SQR;
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
        return new AABB(worldPos).intersects(villager.getBoundingBox().inflate(0.05D));
    }

    private boolean moveAwayFromPlacement(
            ServerLevel level,
            Villager villager,
            HiredWorkArea area,
            BlockPos buildCenter,
            BlockPos placementPos) {
        Predicate<BlockPos> movementFilter = pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                placementPos.offset(-SELF_PLACEMENT_CLEAR_RADIUS, -1, -SELF_PLACEMENT_CLEAR_RADIUS),
                placementPos.offset(SELF_PLACEMENT_CLEAR_RADIUS, 1, SELF_PLACEMENT_CLEAR_RADIUS))) {
            BlockPos safeCandidate = candidate.immutable();
            if (!movementFilter.test(safeCandidate)
                    || horizontalDistanceSqr(safeCandidate, placementPos) > BUILD_REACH_SQR
                    || !HiredMoveToBlockFaceJob.isValidApproachPosition(level, safeCandidate)
                    || bodyAt(villager, safeCandidate).intersects(new AABB(placementPos))
                    || !level.noCollision(villager, bodyAt(villager, safeCandidate))) {
                continue;
            }
            double distance = villager.distanceToSqr(safeCandidate.getCenter());
            if (distance < bestDistance) {
                best = safeCandidate;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }

        Path path = villager.getNavigation().createPath(best, 0);
        if (path == null
                || !path.canReach()
                || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(level, path, movementFilter)) {
            return moveDirectlyToClearSpot(level, villager, best);
        }
        boolean moved = villager.getNavigation().moveTo(path, BUILD_WALK_SPEED);
        if (!moved) {
            HiredPathMemory.clearNavigationProgress(villager);
            return moveDirectlyToClearSpot(level, villager, best);
        }
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(best), (float) BUILD_WALK_SPEED, BUILD_WALK_CLOSE_ENOUGH));
        HiredPathMemory.rememberNavigationProgress(level, villager, best, villager.distanceToSqr(best.getCenter()));
        return true;
    }

    private boolean moveDirectlyToClearSpot(ServerLevel level, Villager villager, BlockPos target) {
        if (villager.distanceToSqr(target.getCenter()) > 16.0D) {
            return false;
        }
        villager.getMoveControl().setWantedPosition(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                BUILD_WALK_SPEED);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new BlockPosTracker(target), (float) BUILD_WALK_SPEED, BUILD_WALK_CLOSE_ENOUGH));
        HiredPathMemory.rememberNavigationProgress(level, villager, target, villager.distanceToSqr(target.getCenter()));
        return true;
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
        faceBlock(villager, group.materialPart().worldPos());
        swingWorkTool(villager);

        for (PlacementPart part : group.parts()) {
            BlockPos worldPos = part.worldPos();
            BuilderStructureScanner.BuildBlock block = part.block();
            BlockState current = level.getBlockState(worldPos);
            if (current.equals(block.state())) {
                continue;
            }
            boolean placed = level.setBlock(worldPos, block.state(), Block.UPDATE_ALL);
            if (!placed) {
                return false;
            }
            if (block.blockEntityTag() != null && level.getBlockEntity(worldPos) instanceof BlockEntity blockEntity) {
                CompoundTag tag = block.blockEntityTag().copy();
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

    private WorkResult finishBuild(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.StructurePlan plan) {
        Map<String, String> replacements = BuilderTaskState.replacements(context.state());
        BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.DEPOSITING_LEFTOVERS);
        depositLeftoverMaterials(villager, context.inventory(), plan);
        clearActiveBreakingTarget(level, context, villager);
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

    private enum MaterialResult {
        READY,
        MOVING,
        MISSING,
        UNREACHABLE,
        INVENTORY_FULL
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

    public record MissingMaterials(List<String> missing) {
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
