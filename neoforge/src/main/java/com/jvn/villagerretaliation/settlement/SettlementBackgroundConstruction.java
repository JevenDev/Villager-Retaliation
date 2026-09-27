package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredVillagerWorkService;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

final class SettlementBackgroundConstruction {
    private static final String POSITION = "SettlementConstructionPosition";

    private SettlementBackgroundConstruction() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var project = town.activeProject(worker.id());
        var area = town.areas().get(worker.area());
        if (project == null || area == null || !area.enabled() || !project.area().equals(worker.area()) || !project.within(project.surveyArea(area))) {
            worker.status("no_construction_project");
            return;
        }
        if (!town.dimension().equals(level.dimension().location()) || level.isOutsideBuildHeight(project.min())
                || level.isOutsideBuildHeight(project.max()) || !level.getWorldBorder().isWithinBounds(project.min())
                || !level.getWorldBorder().isWithinBounds(project.max())) {
            worker.status("construction_outside_world");
            return;
        }
        var site = project.site();
        if (site == null || !site.matches(project.surveyArea(area))) {
            worker.status("construction_needs_observation");
            return;
        }
        SettlementConstructionSiteService.refresh(level, site);
        if (site.conflict()) {
            worker.status("construction_conflict");
            return;
        }
        var entry = BuilderStructureCatalog.byId(level.getServer(), project.structure()).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(level, entry, project.rotation()).orElse(null);
        if (plan == null || !plan.worldMin(project.origin()).equals(project.min()) || !plan.worldMax(project.origin()).equals(project.max())
                || plan.blocks().size() != project.total()
                || plan.blocks().size() > Math.max(128, VillagerRetaliationConfig.HIRED_BUILDER_MAX_BLOCKS.get())) {
            worker.status("construction_plan_changed");
            return;
        }
        if (worker.status().equals("output_full")
                && (!worker.settings().contains("AutoDepositOutputs") || worker.settings().getBoolean("AutoDepositOutputs"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier,
                        SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList(), false)) return;
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var state = worker.settings();
        project.restore(state);
        var before = state.copy();
        var inventory = HiredJobInventory.getJobInventory(owner);
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved)
                .filter(depot -> project.automatic() || BuilderWorker.builderMaterialStorageFilter(plan, project.origin()).test(depot.position())).toList();
        var budget = new SettlementWorkBudget(worker, elapsed, multiplier);
        var view = new SettlementConstructionView(level, site);
        try {
            for (var block : plan.blocks()) {
                var pos = plan.worldPos(project.origin(), block);
                var current = site.getBlockState(pos);
                if (!BuilderStructureScanner.sameSchematicState(current, block.state())
                        && !BuilderStructureScanner.canTransformExisting(current, block.state())
                        && !BuilderSitePlanner.safeReplaceable(view, pos, current)
                        && !MiningBlockRules.isBuilderClearableObstruction(site, pos, current)) {
                    blocked(worker, "construction_blocked_existing", "blocked_existing");
                    return;
                }
            }
            for (int action = 0; action < 32; action++) {
                int index = BuilderTaskState.placedIndex(state);
                while (index < plan.blocks().size() && BuilderStructureScanner.sameSchematicState(
                        site.getBlockState(plan.worldPos(project.origin(), plan.blocks().get(index))), plan.blocks().get(index).state())) index++;
                BuilderTaskState.setPlacedIndex(state, index);
                if (index >= plan.blocks().size()) {
                    if (depositLeftovers(level, town, worker, inventory, plan, budget)) return;
                    project.observe(state);
                    town.finishProject(project.id(), SettlementProject.Status.COMPLETED);
                    town.recordEvent(level.getGameTime(), SettlementEvent.Type.PROJECT_COMPLETED, project.label());
                    ConstructionBlueprintItem.completeMatchingBlueprints(level, project.id());
                    BuilderTaskState.clearTask(state);
                    state.remove(POSITION);
                    worker.status("completed_construction");
                    return;
                }
                var group = BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().get(index));
                var obstruction = obstruction(site, view, plan, project);
                BlockPos target = obstruction == null ? group.materialPart().worldPos() : obstruction;
                BlockPos start = state.contains(POSITION) ? BlockPos.of(state.getLong(POSITION)) : worker.position();
                if (project.automatic() && !site.area().contains(start)) {
                    var entryPosition = SettlementConstructionRoutes.entry(site, project, start);
                    if (entryPosition == null) {
                        blocked(worker, "construction_needs_observation", "site_unloaded");
                        return;
                    }
                    if (!budget.spend(16.0D * Math.sqrt(start.distSqr(entryPosition)))) {
                        worker.status("traveling_to_construction");
                        return;
                    }
                    start = entryPosition;
                    state.putLong(POSITION, start.asLong());
                }
                var route = SettlementConstructionRoutes.route(site, project, plan, start, target, owner, obstruction != null);
                if (route.isEmpty()) {
                    blocked(worker, "construction_unreachable", "path_blocked");
                    return;
                }
                double travel = (route.size() - 1) * 16.0D;
                if (obstruction != null) {
                    if (!clear(level, worker, site, owner, inventory, depots, budget, obstruction, route.getLast(), travel)) return;
                    state.putLong(POSITION, route.getLast().asLong());
                    continue;
                }
                var changes = new ArrayList<SettlementConstructionPlacement.Part>();
                var toolAction = BuilderStructureScanner.BuilderToolAction.NONE;
                for (var part : group.parts()) {
                    var current = site.getBlockState(part.worldPos());
                    if (BuilderStructureScanner.sameSchematicState(current, part.block().state())) continue;
                    if (!BuilderSitePlanner.safeReplaceable(view, part.worldPos(), current)
                            && !BuilderStructureScanner.canTransformExisting(current, part.block().state())) {
                        blocked(worker, "construction_blocked_existing", "blocked_existing");
                        return;
                    }
                    if (part == group.materialPart() && !BuilderStructureScanner.canTransformExisting(current, part.block().state())
                            && !part.block().state().canSurvive(view, part.worldPos())) {
                        blocked(worker, "construction_blocked_support", "blocked_support");
                        return;
                    }
                    if (part.block().requiresToolAction()) toolAction = part.block().toolAction();
                    changes.add(new SettlementConstructionPlacement.Part(part.worldPos(), current, part.block().state(), part.block().blockEntityTag()));
                }
                if (changes.isEmpty()) continue;
                var material = group.materialBlock();
                var current = site.getBlockState(group.materialPart().worldPos());
                boolean consumes = material.requiresMaterial() && !BuilderStructureScanner.sameSchematicState(current, material.state())
                        && !BuilderStructureScanner.canTransformExisting(current, material.state());
                if (consumes && inventory.findSupply(material::materialMatches).isEmpty()) {
                    inventory.promoteOutputToSupply(material::materialMatches, 1);
                    if (inventory.findSupply(material::materialMatches).isEmpty()) {
                        fetch(level, worker, inventory, depots, material, site, project, plan, index, budget);
                        return;
                    }
                }
                ItemStack tool = ItemStack.EMPTY;
                if (toolAction != BuilderStructureScanner.BuilderToolAction.NONE) {
                    var requiredAction = toolAction;
                    tool = inventory.equipBestTool(stack -> BuilderStructureScanner.matchesToolAction(stack, requiredAction), stack -> 1.0D);
                    if (tool.isEmpty()) {
                        SettlementBackgroundTools.fetchTool(level, worker, inventory, depots,
                                stack -> BuilderStructureScanner.matchesToolAction(stack, requiredAction), stack -> 1.0D, "placement_tool", budget);
                        BuilderTaskState.setBlocked(state, "missing_placement_tool");
                        return;
                    }
                }
                if (!budget.spend(travel + interval(worker))) {
                    worker.status("building");
                    return;
                }
                if (consumes && !inventory.consumeSupplyExactly(material::materialMatches, 1)) throw new IllegalStateException("Verified construction material disappeared");
                site.queue(new SettlementConstructionPlacement(changes));
                for (var part : group.parts()) {
                    if (part.block().requiresToolAction() && changes.stream().anyMatch(change -> change.position().equals(part.worldPos()))) {
                        VillagerEquipmentDurability.hurtTool(tool, owner, EquipmentSlot.MAINHAND);
                    }
                }
                inventory.syncMainHandEquipment();
                state.putLong(POSITION, route.getLast().asLong());
                BuilderTaskState.setPlacedIndex(state, index + 1);
                BuilderTaskState.setPhase(state, BuilderBuildPhase.BUILDING);
                BuilderTaskState.clearMissingMaterials(state);
                worker.practice(level, HiredWorkPractice.builderPlacement(material.state()));
                worker.status("built_block");
            }
        } catch (SettlementConstructionView.Unavailable unavailable) {
            blocked(worker, "construction_needs_observation", "site_unloaded");
        } finally {
            project.observe(state);
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory()) || !before.equals(state)) worker.changed();
            worker.inventorySnapshot(snapshot);
            town.changed();
            SettlementConstructionSiteService.refresh(level, site);
        }
    }

    private static BlockPos obstruction(SettlementConstructionSite site, SettlementConstructionView view,
                                        BuilderStructureScanner.StructurePlan plan, SettlementProject project) {
        for (var block : plan.blocks()) {
            var pos = plan.worldPos(project.origin(), block);
            var current = site.getBlockState(pos);
            if (!BuilderStructureScanner.sameSchematicState(current, block.state())
                    && !BuilderStructureScanner.canTransformExisting(current, block.state())
                    && !BuilderSitePlanner.safeReplaceable(view, pos, current)
                    && MiningBlockRules.isBuilderClearableObstruction(site, pos, current)) return pos;
        }
        return null;
    }

    private static boolean clear(ServerLevel level, SettlementWorkerState worker, SettlementConstructionSite site, Villager owner,
                                 HiredJobInventory inventory, List<SettlementDepot> depots, SettlementWorkBudget budget,
                                 BlockPos pos, BlockPos stance, double travel) {
        if (com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isUnsafeMiningTarget(site, site::observed, pos, stance, worker.position())) {
            blocked(worker, "construction_unsafe", "path_blocked");
            return false;
        }
        var block = site.getBlockState(pos);
        var tool = inventory.equipBestTool(stack -> MiningBlockRules.isUsableBuilderClearingTool(stack, block), stack -> MiningBlockRules.destroySpeed(stack, block));
        if (tool.isEmpty()) {
            SettlementBackgroundTools.fetchTool(level, worker, inventory, depots, stack -> MiningBlockRules.isUsableBuilderClearingTool(stack, block),
                    stack -> MiningBlockRules.destroySpeed(stack, block), "clear_tool", budget);
            return false;
        }
        var drops = Block.getDrops(block, level, pos, null, owner, tool);
        if (!inventory.canStoreOutputs(drops)) {
            blocked(worker, "output_full", "output_inventory_full");
            return false;
        }
        int work = Math.max(1, (int) Math.ceil(block.getDestroySpeed(site, pos) * 30.0D / Math.max(0.001D, MiningBlockRules.destroySpeed(tool, block))));
        if (!budget.spend(travel + work * interval(worker))) {
            worker.status("clearing_construction");
            return false;
        }
        site.queue(new SettlementConstructionPlacement(List.of(new SettlementConstructionPlacement.Part(pos, block, block.getFluidState().createLegacyBlock(), null))));
        drops.forEach(inventory::insertOutput);
        if (!VillagerEquipmentDurability.mineBlock(tool, level, block, pos, owner)) VillagerEquipmentDurability.hurtTool(tool, owner, EquipmentSlot.MAINHAND);
        inventory.syncMainHandEquipment();
        worker.practice(level, HiredWorkPractice.mining(site, pos, block));
        worker.status("cleared_construction");
        return true;
    }

    private static void fetch(ServerLevel level, SettlementWorkerState worker, HiredJobInventory inventory, List<SettlementDepot> depots,
                              BuilderStructureScanner.BuildBlock material, SettlementConstructionSite site, SettlementProject project,
                              BuilderStructureScanner.StructurePlan plan, int startIndex, SettlementWorkBudget budget) {
        var batch = BuilderWorker.nextMaterialBatch(site, inventory, plan, project.origin(), startIndex);
        BuilderTaskState.setMaterialBatchEndIndex(worker.settings(), batch.endIndex());
        var supplies = SettlementProductionInventory.supplies(level, worker, SettlementBackgroundTools.items(inventory), depots);
        for (var depot : depots) {
            var consumed = new ArrayList<>(java.util.Collections.nCopies(supplies.size(), 0));
            var received = new ArrayList<ItemStack>();
            for (var requirement : batch.materials()) {
                int missing = requirement.count() - BuilderWorker.countCarriedMaterial(inventory, requirement.item());
                for (int index = 0; index < supplies.size() && missing > 0; index++) {
                    var source = supplies.get(index);
                    if (source.depot() != depot || !BuilderStructureScanner.sameMaterial(source.available(), requirement.item())) continue;
                    int count = Math.min(missing, source.available().getCount());
                    consumed.set(index, count);
                    received.add(source.available().copyWithCount(count));
                    missing -= count;
                }
            }
            if (received.isEmpty() || !SettlementProductionInventory.allowed(level, supplies, consumed)) continue;
            if (!inventory.canStorePlainSupplies(received)) {
                blocked(worker, "supply_full", "material_inventory_full");
                return;
            }
            if (!budget.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(depot.position())))) {
                worker.status("fetching_construction_materials");
                return;
            }
            for (int index = 0; index < supplies.size(); index++) {
                int count = consumed.get(index);
                if (count <= 0) continue;
                var source = supplies.get(index);
                if (!inventory.insertSupplyFromStorage(source.available().copyWithCount(count)).isEmpty()) {
                    throw new IllegalStateException("Verified construction supply capacity disappeared");
                }
                depot.removeItem(source.slot(), count);
            }
            BuilderTaskState.setPhase(worker.settings(), BuilderBuildPhase.COLLECTING_MATERIALS);
            worker.status("fetched_construction_materials");
            return;
        }
        BuilderTaskState.setMissingMaterials(worker.settings(), material.requiredItem().getHoverName().getString());
        blocked(worker, "missing_construction_materials", "missing_materials");
    }

    private static boolean depositLeftovers(ServerLevel level, SettlementRecord town, SettlementWorkerState worker,
                                            HiredJobInventory inventory, BuilderStructureScanner.StructurePlan plan, SettlementWorkBudget budget) {
        for (int slot : inventory.supplySlots()) {
            var stack = inventory.getItem(slot);
            if (stack.isEmpty() || plan.materials().stream().noneMatch(material -> BuilderStructureScanner.sameMaterial(stack, material.item()))) continue;
            for (var depot : SettlementStock.outputs(town)) {
                int count = Math.min(stack.getCount(), SettlementLogisticsService.framedAllowance(level, depot, stack,
                        com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.RECEIVE, true));
                count = Math.min(count, SettlementStock.capacity(List.of(depot), stack));
                if (count <= 0) continue;
                BuilderTaskState.setPhase(worker.settings(), BuilderBuildPhase.DEPOSITING_LEFTOVERS);
                worker.status("depositing_construction_leftovers");
                if (budget.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(depot.position())))) {
                    var remainder = depot.deposit(stack.copyWithCount(count));
                    stack.shrink(count - remainder.getCount());
                    inventory.setChanged();
                }
                return true;
            }
        }
        return false;
    }

    private static int interval(SettlementWorkerState worker) {
        int efficiency = HiredVillagerWorkService.calculateEfficiencyPercent(VillagerRetaliationConfig.HIRED_WORK_BASE_EFFICIENCY_PERCENT.get(),
                HiredVillagerRoles.roleCadencePercent(worker.role(), worker.aptitude()), 0, false,
                VillagerRetaliationConfig.HIRED_WORK_MINIMUM_EFFICIENCY_PERCENT.get(), VillagerRetaliationConfig.HIRED_WORK_MAXIMUM_EFFICIENCY_PERCENT.get());
        return HiredVillagerWorkService.effectiveWorkTickInterval(efficiency);
    }

    private static void blocked(SettlementWorkerState worker, String status, String reason) {
        worker.status(status);
        BuilderTaskState.setBlocked(worker.settings(), reason);
    }
}
