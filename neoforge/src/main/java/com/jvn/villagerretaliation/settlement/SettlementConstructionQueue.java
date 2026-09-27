package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementConstructionQueue {
    private SettlementConstructionQueue() {
    }

    public static void dispatch(ServerLevel level, SettlementRecord town) {
        if (!town.dimension().equals(level.dimension().location())
                || town.projects().values().stream().noneMatch(project -> project.automatic() && project.unfinished())) return;
        var builders = new LinkedHashMap<UUID, Villager>();
        for (var resident : List.copyOf(town.duties().keySet())) {
            var area = town.areas().get(town.duties().get(resident));
            var settings = town.workerSettings().get(resident);
            if (area == null || !area.enabled() || area.type() != SettlementAreaType.CONSTRUCTION
                    || settings != null && !settings.enabled()) continue;
            var builder = SettlementConstructionService.resolveBuilder(level, town, resident);
            if (builder == null || !builder.isAlive() || builder.isBaby()
                    || SettlementWorkforceService.hasPersonalCommitment(builder)
                    || !HiredVillagerRoles.availableRoles(level, builder).contains(HiredVillagerRole.BUILDER)) continue;
            SettlementBackgroundService.onActive(builder);
            builders.put(resident, builder);
        }
        for (var depot : SettlementStock.supplies(town)) SettlementDepotService.observeRules(level, depot);
        var reserved = new LinkedHashMap<SettlementDepot, List<ItemStack>>();
        for (var project : town.projects().values()) {
            if (!project.active()) continue;
            var builder = builders.get(project.builder());
            if (builder != null) SettlementConstructionService.observe(builder, HiredWorkStateStore.state(builder));
            if (project.automatic() && builder == null) {
                pause(level, town, project, "builder_unavailable", "");
                continue;
            }
            var allocation = allocate(level, town, project, builder, reserved);
            if (project.automatic() && !allocation.ready()) {
                pause(level, town, project, allocation.reason(), allocation.missing());
                continue;
            }
            reserved = allocation.reserved();
        }
        for (var project : town.projects().values()) {
            if (!project.automatic() || !project.unfinished() || project.active()) continue;
            var free = builders.values().stream().filter(builder -> town.activeProject(builder.getUUID()) == null
                    && !BuilderTaskState.hasTask(HiredWorkStateStore.state(builder))).toList();
            Allocation allocation = allocate(level, town, project, free.isEmpty() ? null : free.getFirst(), reserved);
            Villager chosen = null;
            for (var builder : free) {
                var candidate = allocate(level, town, project, builder, reserved);
                if (candidate.ready()) {
                    chosen = builder;
                    allocation = candidate;
                    break;
                }
            }
            if (!allocation.ready()) {
                project.waiting(allocation.reason(), allocation.missing());
            } else if (chosen == null) {
                project.waiting("no_builder", "");
            } else if (town.projects().values().stream().filter(SettlementProject::active).count() >= town.stage().projects()) {
                project.waiting("project_limit", "");
            } else {
                project.assign(chosen.getUUID(), town.duties().get(chosen.getUUID()));
                var state = HiredWorkStateStore.state(chosen);
                project.restore(state);
                state.remove("NextWorkGameTime");
                state.remove("ProgressTicks");
                HiredWorkSession.invalidate(chosen);
                SettlementDepotService.synchronizeStorage(level, chosen, town);
                SettlementBackgroundService.onUnloaded(chosen);
                reserved = allocation.reserved();
                town.recordEvent(level.getGameTime(), SettlementEvent.Type.PROJECT_STARTED, project.label());
            }
        }
        town.changed();
    }

    private static void pause(ServerLevel level, SettlementRecord town, SettlementProject project, String reason, String missing) {
        UUID resident = project.builder();
        project.pause(reason);
        project.waiting(reason, missing);
        if (resident != null && level.getEntity(resident) instanceof Villager builder) SettlementConstructionService.reconcile(builder);
        var worker = town.workers().get(resident);
        if (worker != null && project.id().equals(BuilderTaskState.jobId(worker.settings()).orElse(null))) {
            BuilderTaskState.clearTask(worker.settings());
            worker.settings().remove("SettlementConstructionPosition");
        }
    }

    private static Allocation allocate(ServerLevel level, SettlementRecord town, SettlementProject project, Villager builder,
                                       LinkedHashMap<SettlementDepot, List<ItemStack>> previous) {
        var reserved = new LinkedHashMap<SettlementDepot, List<ItemStack>>();
        previous.forEach((depot, items) -> reserved.put(depot, new ArrayList<>(items)));
        var entry = BuilderStructureCatalog.byId(level.getServer(), project.structure()).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(level, entry, project.rotation()).orElse(null);
        if (plan == null || plan.blocks().size() != project.total() || !plan.worldMin(project.origin()).equals(project.min())
                || !plan.worldMax(project.origin()).equals(project.max())) return new Allocation(false, "missing_structure", "", previous);
        if (project.site() != null && project.site().conflict()) return new Allocation(false, "world_conflict", "", previous);
        var requirements = new ArrayList<BuilderStructureScanner.MaterialRequirement>();
        for (int index = project.placed(); index < plan.blocks().size(); index++) {
            var block = plan.blocks().get(index);
            var pos = plan.worldPos(project.origin(), block);
            var site = project.site();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            var current = site != null && site.pending() && site.observed(pos) ? site.getBlockState(pos)
                    : chunk != null ? chunk.getBlockState(pos) : site != null && site.observed(pos) ? site.getBlockState(pos) : null;
            if (current == null) return new Allocation(false, "site_unloaded", "", previous);
            if (BuilderStructureScanner.sameSchematicState(current, block.state())
                    || BuilderStructureScanner.canTransformExisting(current, block.state())) continue;
            var view = chunk != null ? level : new SettlementConstructionView(level, site);
            if (!com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner.safeReplaceable(view, pos, current)
                    && !com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isBuilderClearableObstruction(view, pos, current))
                return new Allocation(false, "blocked_existing", "", previous);
            if (!block.requiresMaterial()) continue;
            int match = -1;
            for (int i = 0; i < requirements.size(); i++) {
                if (BuilderStructureScanner.sameMaterial(requirements.get(i).item(), block.requiredItem())) { match = i; break; }
            }
            if (match < 0) requirements.add(new BuilderStructureScanner.MaterialRequirement(block.requiredItem(), 1));
            else {
                var old = requirements.get(match);
                requirements.set(match, new BuilderStructureScanner.MaterialRequirement(old.item(), old.count() + 1));
            }
        }
        var inventory = builder == null ? null : HiredJobInventory.getJobInventory(builder);
        var state = builder == null ? null : HiredWorkStateStore.state(builder);
        boolean storage = state == null || !state.contains("UseAssignedStorageForSupplies") || state.getBoolean("UseAssignedStorageForSupplies");
        var depots = storage ? SettlementStock.supplies(town).stream().filter(SettlementDepot::rulesObserved)
                .filter(depot -> project.automatic() || BuilderWorker.builderMaterialStorageFilter(plan, project.origin()).test(depot.position())).toList() : List.<SettlementDepot>of();
        var missing = new ArrayList<BuilderStructureScanner.MaterialRequirement>();
        for (var requirement : requirements) {
            int needed = requirement.count() - (inventory == null ? 0 : BuilderWorker.countCarriedMaterial(inventory, requirement.item()));
            if (inventory != null) {
                for (int slot : inventory.outputSlots()) {
                    if (BuilderStructureScanner.sameMaterial(inventory.getItem(slot), requirement.item())) needed -= inventory.getItem(slot).getCount();
                }
            }
            for (var depot : depots) {
                if (needed <= 0) break;
                var claims = reserved.computeIfAbsent(depot, ignored -> new ArrayList<>());
                for (int slot = 0; slot < depot.getContainerSize() && needed > 0; slot++) {
                    var sample = depot.getItem(slot);
                    if (!BuilderStructureScanner.sameMaterial(sample, requirement.item())) continue;
                    int used = claims.stream().filter(item -> ItemStack.isSameItemSameComponents(item, sample)).mapToInt(ItemStack::getCount).sum();
                    int stock = depot.count(item -> ItemStack.isSameItemSameComponents(item, sample)) - used
                            - AssignedStorageService.reservedItemCount(level, depot.position(), null, VillagerFilterPolicy.TransferOperation.PROVIDE, sample);
                    int allowance = AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                            sample, VillagerFilterPolicy.TransferOperation.PROVIDE, claims);
                    int count = Math.max(0, Math.min(needed, Math.min(stock, allowance)));
                    if (count > 0) {
                        claims.add(sample.copyWithCount(count));
                        needed -= count;
                    }
                }
            }
            if (needed > 0) missing.add(new BuilderStructureScanner.MaterialRequirement(requirement.item(), needed));
        }
        return new Allocation(missing.isEmpty(), missing.isEmpty() ? "" : "missing_materials",
                missing.isEmpty() ? "" : BuilderStructureScanner.materialSummary(level.getServer(), missing, 64), reserved);
    }

    private record Allocation(boolean ready, String reason, String missing, LinkedHashMap<SettlementDepot, List<ItemStack>> reserved) {
    }
}
