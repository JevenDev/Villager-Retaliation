package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class SettlementAllocationService {
    private final Map<VillageAllegianceId, Entry> entries = new HashMap<>();
    private final Map<UUID, PersonalProgress> personal = new HashMap<>();

    SettlementAllocationService() { }

    public record Decision(int allowance, UUID competingConsumer, String blocker) { }

    static void schedule(ServerLevel level, SettlementRecord town, List<Runnable> work) {
        work.add(SettlementWork.lazy(() -> {
            request(level, town);
            var entry = SettlementSavedData.get(level).allocation.entries.get(town.id());
            return SettlementWork.repeat(() -> advance(entry));
        }));
    }

    static void request(ServerLevel level, SettlementRecord town) {
        var service = SettlementSavedData.get(level).allocation;
        var entry = service.entries.compute(town.id(), (id, previous) ->
                previous == null || previous.town != town ? new Entry(town) : previous);
        var configuration = Configuration.capture(town);
        var execution = Execution.capture(level, town);
        long version = entry.version;
        if (entry.pending != null && version == entry.planningVersion && configuration.equals(entry.planning) && execution.equals(entry.planningExecution)) return;
        if (entry.ready != null && entry.configuration.priorityChanged(configuration)) releaseUnstarted(level, town);
        entry.planning = configuration;
        entry.planningExecution = execution;
        entry.planningVersion = version;
        entry.pending = SettlementDemandService.plan(level, town, ready -> {
            if (version == entry.version && configuration.equals(Configuration.capture(town)) && execution.equals(Execution.capture(level, town))) {
                entry.ready = ready;
                entry.readyVersion = version;
                entry.configuration = configuration;
                entry.execution = execution;
            }
        });
        SettlementSavedData.get(level).simulation.enqueue(new Key(town.id(), entry), SettlementWork.repeat(() -> advance(entry)));
    }

    private static boolean advance(Entry entry) {
        var pending = entry.pending;
        if (pending == null) return true;
        pending.run();
        if (SettlementWork.complete(pending) && entry.pending == pending) entry.pending = null;
        return entry.pending == null;
    }

    public static Decision inspect(ServerLevel level, SettlementRecord town, UUID worker, UUID consumer,
                                   BlockPos position, ItemStack sample) {
        return inspect(level, town, worker, consumer, position, null, sample);
    }

    static Decision inspect(ServerLevel level, SettlementRecord town, UUID worker, UUID consumer,
                            BlockPos position, BlockPos destination, ItemStack sample) {
        if (sample.isEmpty()) return new Decision(0, null, "");
        if (!SettlementPolicyExecution.acquisitionAllowed(town, worker, consumer)) return new Decision(0, null, "policy_suspended");
        if (SettlementSavedData.get(level).readOnly() || !town.territory().active()) return new Decision(0, null, "allocation_unavailable");
        var ready = ready(level, town);
        if (ready == null) return new Decision(0, null, "allocation_pending");
        var canonical = SettlementSupplyStock.positions(level, position).getFirst();
        var snapshot = ready.refresh(canonical);
        var consumers = new HashSet<>(consumers(town, worker, consumer, destination == null ? canonical : null));
        if (worker != null && consumer == null) consumers.addAll(snapshot.consumers(worker));
        if (worker != null && consumer == null && destination == null) {
            var area = town.duties().get(worker);
            boolean needsEquipment = area != null && snapshot.demands().stream().anyMatch(demand ->
                    demand.consumer().equals(worker) && demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.EQUIPMENT
                            && demand.sources().contains(canonical) && demand.requirement().value(sample) > 0
                            && demand.supply().committed() < demand.requirement().quantity());
            if (needsEquipment && snapshot.demands().stream().anyMatch(demand -> demand.consumer().equals(area)
                    && demand.kind() == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.EQUIPMENT
                    && demand.sources().contains(canonical) && demand.requirement().value(sample) > 0)) consumers.add(area);
        }
        if (destination != null) {
            var target = SettlementSupplyStock.positions(level, destination).getFirst();
            var area = worker == null ? null : town.duties().get(worker);
            var transfers = area == null ? null : town.transferQueues().get(area);
            if (consumer == null && transfers != null) {
                var assigned = transfers.allocation(worker);
                for (var order : transfers.executionOrders()) if ((assigned == null || assigned.order().equals(order.id()))
                        && order.matches(sample) && SettlementSupplyStock.sameStorage(level, order.source().position(), canonical)
                        && SettlementSupplyStock.sameStorage(level, order.destination().position(), target)) consumers.add(order.id());
            }
            for (var demand : snapshot.demands()) if (demand.sources().contains(target) && demand.requirement().value(sample) > 0)
                consumers.add(demand.consumer());
        }
        long own = worker == null ? 0 : Math.max(0, AssignedStorageService.reservedItemCount(level, position, null,
                VillagerFilterPolicy.TransferOperation.PROVIDE, sample) - AssignedStorageService.reservedItemCount(level, position, worker,
                VillagerFilterPolicy.TransferOperation.PROVIDE, sample));
        if (worker != null && level.getEntity(worker) instanceof net.minecraft.world.entity.npc.Villager villager) {
            var access = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService.access(level, villager);
            var transfer = access.queue() == null ? null : access.queue().allocation(worker);
            var order = transfer == null ? null : access.queue().order(transfer.order());
            if (transfer != null && !transfer.started() && order.matches(sample)
                    && SettlementSupplyStock.sameStorage(level, order.source().position(), position)) own = Math.max(own, transfer.amount());
        }
        long allowed = snapshot.allocation().allowance(consumers, canonical, sample) + own;
        var competing = snapshot.allocation().competitor(consumers, canonical, sample);
        return new Decision((int) Math.min(Integer.MAX_VALUE, allowed), competing,
                allowed > 0 ? "" : competing == null ? "missing_materials" : "allocated_elsewhere");
    }

    static SettlementDemandService.Snapshot ready(ServerLevel level, SettlementRecord town) {
        if (SettlementSavedData.get(level).readOnly() || !town.territory().active()) return null;
        var entry = SettlementSavedData.get(level).allocation.entries.get(town.id());
        if (entry == null || entry.town != town || entry.ready == null || entry.readyVersion != entry.version
                || !Configuration.capture(town).equals(entry.configuration) || !Execution.capture(level, town).equals(entry.execution)) {
            request(level, town);
            return null;
        }
        return entry.ready;
    }

    public static int allowance(ServerLevel level, UUID worker, BlockPos position, ItemStack sample, int maximum, List<ItemStack> additional) {
        return allowance(level, worker, position, sample, maximum, additional, null);
    }

    public static int allowance(ServerLevel level, UUID worker, BlockPos position, ItemStack sample,
                                int maximum, List<ItemStack> additional, UUID consumer) {
        if (maximum <= 0 || worker == null) return maximum;
        var town = SettlementSavedData.get(level).at(level.dimension().location(), position).orElse(null);
        if (town == null || !linked(level, town, position)) return maximum;
        BlockPos destination = null;
        if (level.getEntity(worker) instanceof net.minecraft.world.entity.npc.Villager villager) {
            var access = com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService.access(level, villager);
            var transfer = access.queue() == null ? null : access.queue().allocation(worker);
            if (transfer != null && !transfer.started()) destination = access.queue().order(transfer.order()).destination().position();
            else {
                var task = SettlementLogisticsService.task(villager);
                var depot = task == null ? null : town.depots().get(task.destination());
                if (depot != null) destination = depot.position();
            }
        }
        var decision = inspect(level, town, worker, consumer, position, destination, sample);
        long reserved = additional.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, sample)).mapToLong(ItemStack::getCount).sum();
        return (int) Math.min(maximum, Math.max(0, decision.allowance() - reserved));
    }

    public static void personalChanged(ServerLevel level, net.minecraft.world.entity.npc.Villager worker,
                                       com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue queue) {
        var service = SettlementSavedData.get(level).allocation;
        var progress = new PersonalProgress(queue.configurationRevision(),
                queue.orders().stream().map(order -> new OrderProgress(order.id(), order.produced())).toList(),
                queue.allocations().stream().filter(allocation -> allocation.started()).toList());
        if (progress.equals(service.personal.put(worker.getUUID(), progress))) return;
        var towns = new HashSet<VillageAllegianceId>();
        for (var link : com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.get(level).assignedTo(worker.getUUID())) {
            if (!link.dimension().equals(level.dimension())) continue;
            var town = SettlementSavedData.get(level).at(level.dimension().location(), link.pos()).orElse(null);
            if (town == null || !linked(level, town, link.pos()) || !towns.add(town.id())) continue;
            var entry = service.entries.get(town.id());
            if (entry != null) entry.version++;
        }
    }

    public static int deliveryAllowance(ServerLevel level, UUID worker, BlockPos source, BlockPos destination, ItemStack sample, int maximum) {
        var town = SettlementSavedData.get(level).at(level.dimension().location(), source).orElse(null);
        if (town == null || !linked(level, town, source)) return maximum;
        return Math.min(maximum, inspect(level, town, worker, null, source, destination, sample).allowance());
    }

    static boolean linked(ServerLevel level, SettlementRecord town, BlockPos position) {
        if (town.depots().values().stream().anyMatch(depot -> SettlementSupplyStock.sameStorage(level, depot.position(), position))) return true;
        return town.worksitePlans().values().stream().flatMap(plan -> plan.settings().storage().entries().stream())
                .anyMatch(link -> link.purpose().supplies() && SettlementSupplyStock.sameStorage(level, link.position(), position))
                || town.workerSettings().values().stream().flatMap(settings -> settings.storage().entries().stream())
                .anyMatch(link -> link.purpose().supplies() && SettlementSupplyStock.sameStorage(level, link.position(), position));
    }

    private static Set<UUID> consumers(SettlementRecord town, UUID worker, UUID requested, BlockPos position) {
        var consumers = new HashSet<UUID>();
        if (requested != null) consumers.add(requested);
        if (worker == null) return consumers;
        consumers.add(worker);
        var area = town.duties().get(worker);
        if (area != null && requested == null) {
            var project = town.assignedProject(worker);
            if (project != null) consumers.add(project.id());
            var queue = town.productionQueues().get(area);
            var allocation = queue == null ? null : queue.allocation(worker);
            if (allocation != null) consumers.add(allocation.order());
            else if (queue != null) queue.executionOrders().forEach(order -> consumers.add(order.id()));
        }
        for (var depot : town.depots().values()) if (depot.position().equals(position) && depot.purpose().providesSupplies()
                && town.duties().containsKey(worker)) consumers.add(depot.id());
        return Set.copyOf(consumers);
    }

    private static void releaseUnstarted(ServerLevel level, SettlementRecord town) {
        for (var worker : town.duties().keySet()) {
            if (level.getEntity(worker) instanceof net.minecraft.world.entity.npc.Villager villager
                    && SettlementLogisticsService.task(villager) != null
                    && !com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager).hasOutputItems()) {
                SettlementLogisticsService.clear(villager);
            }
        }
        for (var queue : town.productionQueues().values()) for (var allocation : queue.allocations()) {
            if (allocation.started()) continue;
            HiredProductionStations.get(level).release(allocation.id());
            queue.release(allocation.id());
            if (allocation.worker() != null) AssignedStorageService.releaseProductionInputs(allocation.worker());
        }
        for (var queue : town.transferQueues().values()) for (var allocation : queue.allocations()) {
            if (allocation.started()) continue;
            queue.releasePlanned(allocation.worker());
            AssignedStorageService.releaseCourierClaims(allocation.worker());
        }
    }

    static void retain(ServerLevel level, Set<VillageAllegianceId> towns) {
        SettlementSavedData.get(level).allocation.entries.keySet().retainAll(towns);
    }

    private record Key(VillageAllegianceId town, Entry entry) { }

    private static final class Entry {
        final SettlementRecord town;
        Configuration configuration;
        Configuration planning;
        Execution execution;
        Execution planningExecution;
        long version;
        long readyVersion;
        long planningVersion;
        SettlementDemandService.Snapshot ready;
        Runnable pending;
        Entry(SettlementRecord town) { this.town = town; }
    }

    private record QueueConfiguration(UUID owner, long revision) { }
    private record OrderProgress(UUID id, long produced) { }
    private record PersonalProgress(long configuration, List<OrderProgress> orders,
                                    List<com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Allocation> started) { }
    private record ProjectProgress(UUID id, int placed, long crewRevision) { }
    private record Execution(long foodReserve, List<OrderProgress> orders,
                             List<com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Allocation> started,
                             List<ProjectProgress> projects) {
        static Execution capture(ServerLevel level, SettlementRecord town) {
            return new Execution(SettlementFoodService.reserveNutrition(level, town),
                    town.productionQueues().values().stream().flatMap(queue -> queue.orders().stream())
                            .map(order -> new OrderProgress(order.id(), order.produced())).toList(),
                    town.productionQueues().values().stream().flatMap(queue -> queue.allocations().stream())
                            .filter(allocation -> allocation.started()).toList(),
                    town.projects().values().stream().map(project -> new ProjectProgress(project.id(), project.placed(), project.crewRevision())).toList());
        }
    }
    private record ProjectConfiguration(UUID id, boolean active, int priority) { }
    private record DepotConfiguration(UUID id, UUID area, BlockPos position, SettlementDepotPurpose purpose,
                                      int priority, List<SettlementDepot.StockTarget> targets) { }

    private record Configuration(Map<UUID, SettlementArea> areas, Map<UUID, SettlementWorksitePlan> plans,
                                 Map<UUID, SettlementWorkerSettings> settings, Map<UUID, UUID> duties,
                                 List<QueueConfiguration> production, List<QueueConfiguration> transfers,
                                 List<ProjectConfiguration> projects, List<DepotConfiguration> depots,
                                 UUID leader, Map<UUID, Set<SettlementPermission>> delegates, long supplyRevision, Map<UUID, Integer> policyPriorities,
                                 Map<UUID, SettlementEquipmentPlan> equipmentPlans) {
        boolean priorityChanged(Configuration next) {
            if (!this.equipmentPlans.equals(next.equipmentPlans)) return true;
            for (var policy : this.policyPriorities.entrySet())
                if (next.policyPriorities.containsKey(policy.getKey()) && !policy.getValue().equals(next.policyPriorities.get(policy.getKey()))) return true;
            for (var area : this.areas.values()) {
                var updated = next.areas.get(area.id());
                if (updated != null && area.priority() != updated.priority()) return true;
            }
            for (var project : this.projects) if (next.projects.stream()
                    .anyMatch(updated -> updated.id().equals(project.id()) && updated.priority() != project.priority())) return true;
            for (var depot : this.depots) if (next.depots.stream()
                    .anyMatch(updated -> updated.id().equals(depot.id()) && updated.priority() != depot.priority())) return true;
            return false;
        }

        static Configuration capture(SettlementRecord town) {
            return new Configuration(Map.copyOf(town.areas()), Map.copyOf(town.worksitePlans()), Map.copyOf(town.workerSettings()), Map.copyOf(town.duties()),
                    town.productionQueues().values().stream().map(queue -> new QueueConfiguration(queue.owner(), queue.configurationRevision())).toList(),
                    town.transferQueues().values().stream().map(queue -> new QueueConfiguration(queue.owner(), queue.revision())).toList(),
                    town.projects().values().stream().map(project -> new ProjectConfiguration(project.id(), project.active(), project.priority())).toList(),
                    town.depots().values().stream().map(depot -> new DepotConfiguration(depot.id(), depot.area(), depot.position(), depot.purpose(),
                            depot.priority(), depot.stockTargets())).toList(), town.leader(), Map.copyOf(town.delegates()), town.supplyRevision(),
                    town.supplyPolicies().values().stream().collect(java.util.stream.Collectors.toMap(SettlementSupplyPolicy::id,
                            policy -> SettlementPolicyService.priority(town, policy))), Map.copyOf(town.equipmentPlans()));
        }
    }
}
