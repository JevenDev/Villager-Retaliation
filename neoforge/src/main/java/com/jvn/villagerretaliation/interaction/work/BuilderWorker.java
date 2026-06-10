package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public final class BuilderWorker extends AbstractBlockWorker {
    private static final double BUILD_WALK_SPEED = 0.52D;
    private static final int BUILD_WALK_CLOSE_ENOUGH = 1;
    private static final int MATERIAL_TRANSFER_BATCH = 16;

    @Override
    public HiredVillagerRole role() {
        return HiredVillagerRole.BUILDER;
    }

    @Override
    public WorkResult tick(ServerLevel level, Villager villager, ServerPlayer hirer, HiredWorkContext context) {
        if (!BuilderTaskState.hasTask(context.state())) {
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
        HiredWorkArea area = areaFromContext(context);
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
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.WAITING_FOR_MATERIALS);
            setTaskState(context, HiredWorkerTaskState.PAUSED_NO_STORAGE);
            return WorkResult.idle("interaction.work.builder.waiting_materials", Map.of(
                    "materials", missing.summary(),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        BuilderStructureScanner.BuildBlock block = plan.get().blocks().get(index);
        BlockPos worldPos = plan.get().worldPos(origin, block);
        MaterialResult materialResult = ensureMaterial(level, villager, context, block);
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
            BuilderTaskState.setPhase(context.state(), BuilderBuildPhase.BLOCKED);
            return WorkResult.idle("interaction.work.builder.materials_unreachable", BuilderTaskState.replacements(context.state()));
        }

        BuilderSitePlanner.PlacementCheck placementCheck = BuilderSitePlanner.canPlaceAt(level, villager, worldPos, block.state());
        if (!placementCheck.valid()) {
            BuilderTaskState.setBlocked(context.state(), placementCheck.statusKey());
            return WorkResult.idle(placementCheck.statusKey(), Map.of(
                    "target", HiredWorkerBrain.formatPos(worldPos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        HiredPathTarget target = bestWorkTarget(level, villager, worldPos);
        if (target == null) {
            if (recordWorkPathFailure(level, villager, worldPos)) {
                BuilderTaskState.setBlocked(context.state(), "path_blocked");
            }
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, worldPos);
            return WorkResult.idle("interaction.work.builder.path_blocked", Map.of(
                    "target", HiredWorkerBrain.formatPos(worldPos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        prepareBreakingTarget(level, context, villager, target);
        MovementResult movementResult = moveToBuildTarget(level, villager, context, target, plan.get(), origin);
        if (movementResult == MovementResult.BLOCKED) {
            if (recordWorkPathFailure(level, villager, worldPos)) {
                BuilderTaskState.setBlocked(context.state(), "path_blocked");
            }
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

        if (!placeBlock(level, villager, context, block, worldPos)) {
            BuilderTaskState.setBlocked(context.state(), "placement_failed");
            setTaskState(context, HiredWorkerTaskState.FAILED_COOLDOWN, worldPos);
            return WorkResult.idle("interaction.work.builder.placement_failed", Map.of(
                    "target", HiredWorkerBrain.formatPos(worldPos),
                    "structure", BuilderTaskState.structureLabel(context.state())));
        }

        clearWorkPathFailure(villager, worldPos);
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
        setTaskState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION);
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
            return MaterialResult.READY;
        }
        BlockPos storage = AssignedStorageService.nearestAssignedStoragePosContaining(level, villager, block::materialMatches);
        if (storage == null) {
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
            setTaskState(context, HiredWorkerTaskState.MOVING_TO_STORAGE);
            return MaterialResult.MOVING;
        }
        if (moveResult == HiredStorageNavigationGoal.Result.FAILED) {
            return MaterialResult.UNREACHABLE;
        }
        faceBlock(villager, storage);
        int moved = AssignedStorageService.transferItemsAtAssignedStorage(
                villager,
                storage,
                block::materialMatches,
                MATERIAL_TRANSFER_BATCH,
                stack -> context.inventory().insertSupply(stack));
        if (moved <= 0) {
            return MaterialResult.INVENTORY_FULL;
        }
        swingWorkTool(villager);
        return context.inventory().findSupply(block::materialMatches).isEmpty()
                ? MaterialResult.MISSING
                : MaterialResult.READY;
    }

    private MovementResult moveToBuildTarget(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            HiredPathTarget target,
            BuilderStructureScanner.StructurePlan plan,
            BlockPos origin) {
        if (canMineFromCurrentPosition(level, villager, target)) {
            holdWorkPosition(villager, target);
            return MovementResult.READY;
        }

        BlockPos buildCenter = origin.offset(plan.localCenter());
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
        HiredWorkArea area = areaFromContext(context);
        if (path == null
                || !path.canReach()
                || !HiredMoveToBlockFaceJob.pathStaysInsideFilter(path, pos -> BuilderSitePlanner.movementAllowed(area, buildCenter, pos))) {
            return MovementResult.BLOCKED;
        }
        boolean moved = villager.getNavigation().moveTo(path, BUILD_WALK_SPEED);
        if (moved) {
            villager.getBrain().setMemory(
                    MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new BlockPosTracker(target.approachPos()), (float) BUILD_WALK_SPEED, BUILD_WALK_CLOSE_ENOUGH));
            HiredPathMemory.rememberNavigationProgress(
                    level,
                    villager,
                    target.approachPos(),
                    villager.distanceToSqr(target.approachPos().getCenter()));
            return MovementResult.MOVING;
        }
        return MovementResult.BLOCKED;
    }

    private boolean placeBlock(
            ServerLevel level,
            Villager villager,
            HiredWorkContext context,
            BuilderStructureScanner.BuildBlock block,
            BlockPos worldPos) {
        BlockState current = level.getBlockState(worldPos);
        if (current.equals(block.state())) {
            return true;
        }
        if (block.requiresMaterial() && context.inventory().findSupply(block::materialMatches).isEmpty()) {
            return false;
        }
        faceBlock(villager, worldPos);
        swingWorkTool(villager);
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
        if (block.requiresMaterial()) {
            context.inventory().consumeSupply(block::materialMatches, 1);
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
        BuilderTaskState.clearTask(context.state());
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

    private HiredWorkArea areaFromContext(HiredWorkContext context) {
        return new HiredWorkArea(
                context.workCenter(),
                context.workMin(),
                context.workMax(),
                context.radius(),
                context.verticalRadius(),
                context.hasWorkArea(),
                context.hasWorkArea());
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
