package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation;
import com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkProductionProgress;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyEvaluation;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementDemandService {
    private SettlementDemandService() { }

    public record Demand(UUID area, String name, WorkSupplyDemand supply) {
        public UUID consumer() { return supply.consumer(); }
        public Kind kind() { return supply.kind(); }
        public WorkSupplyRequirement requirement() { return supply.requirement(); }
        public long supplied() { return supply.supplied(); }
        public long missing() { return supply.missing(); }
        public long immediateMissing() { return supply.immediateMissing(); }
        public List<BlockPos> sources() { return supply.sources(); }
        public String blocker() { return supply.blocker().code(); }
    }

    public static final class Snapshot {
        private final ServerLevel level;
        private final SettlementRecord town;
        private final SettlementSupplyStock.Snapshot stock;
        private final Map<BlockPos, List<Stock>> stores = new LinkedHashMap<>();
        private final Map<UUID, List<ItemStack>> carried = new LinkedHashMap<>();
        private final java.util.Set<UUID> equipmentWorkers = new java.util.LinkedHashSet<>();
        private final java.util.Set<UUID> equipmentReserves = new java.util.LinkedHashSet<>();
        private final Map<com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed, WorkSupplyRequirement> equipmentRequirements = new LinkedHashMap<>();
        private final List<Demand> demands = new ArrayList<>();
        private final List<Definition> definitions = new ArrayList<>();
        private final Map<UUID, List<ProductionInput>> productionInputs = new LinkedHashMap<>();
        private final Map<UUID, Long> started = new LinkedHashMap<>();
        private final Map<UUID, com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue> transfers = new LinkedHashMap<>();
        private WorkSupplyAllocation.Result allocation;
        private final Map<UUID, Long> gathered = new LinkedHashMap<>();
        private final Map<UUID, java.util.function.BooleanSupplier> consumersCurrent = new LinkedHashMap<>();
        private final net.minecraft.server.level.ServerPlayer viewer;

        private Snapshot(ServerLevel level, SettlementRecord town, net.minecraft.server.level.ServerPlayer viewer) {
            this.level = level;
            this.viewer = viewer;
            this.town = town;
            this.stock = new SettlementSupplyStock.Snapshot(level, town);
            for (var policy : town.supplyPolicies().values()) for (var binding : policy.bindings()) this.gathered.put(binding.id(), binding.gathered());
        }

        public boolean visible(BlockPos position) {
            return this.viewer == null || !AssignedStorageService.containerLoaded(this.level, position)
                    || com.jvn.villagerretaliation.inventory.PartyContainerLootService.isAvailable(this.level, position, this.viewer);
        }

        public List<Demand> demands() {
            var combined = new LinkedHashMap<UUID, Demand>();
            for (var demand : this.demands) combined.merge(demand.supply().id(), demand,
                    (first, second) -> new Demand(first.area(), first.name(), first.supply().merge(second.supply())));
            return List.copyOf(combined.values());
        }

        SettlementSupplyStock.Snapshot stock() { return this.stock; }

        WorkSupplyAllocation.Result allocation() { return this.allocation; }

        java.util.Set<UUID> consumers(UUID worker) {
            return this.definitions.stream().filter(definition -> definition.workers().contains(worker))
                    .map(definition -> definition.request().consumer()).collect(java.util.stream.Collectors.toSet());
        }

        long gathered(UUID binding) { return this.gathered.getOrDefault(binding, 0L); }

        boolean current(UUID consumer) {
            var check = this.consumersCurrent.get(consumer);
            return check == null || check.getAsBoolean();
        }

        void acceptProduction(HiredProductionQueue queue, HiredProductionOrder order) {
            var allocations = queue.allocations().stream().filter(value -> value.order().equals(order.id())).toList();
            this.consumersCurrent.put(order.id(), () -> queue.order(order.id()) == order
                    && allocations.equals(queue.allocations().stream().filter(value -> value.order().equals(order.id())).toList()));
        }

        void acceptTransfer(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue queue,
                            com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Order order) {
            var allocations = queue.allocations().stream().filter(value -> value.order().equals(order.id())).toList();
            this.consumersCurrent.put(order.id(), () -> queue.order(order.id()) == order
                    && allocations.equals(queue.allocations().stream().filter(value -> value.order().equals(order.id())).toList()));
        }

        Snapshot policyDraft(UUID policy, SettlementSupplyPolicy.Configuration configuration) {
            var draft = new Snapshot(this.level, this.town, this.viewer);
            draft.equipmentRequirements.putAll(this.equipmentRequirements);
            draft.productionInputs.putAll(this.productionInputs);
            for (var definition : this.definitions) if (definition.request().kind() == Kind.FOOD) draft.evaluate(definition);
            draft.animalFeed();
            this.equipmentWorkers.forEach(draft::equipment);
            this.equipmentReserves.forEach(draft::equipmentReserve);
            refreshTransfers(draft);
            for (var definition : this.definitions)
                if (!definition.request().requirement().key().startsWith("animal_feed:")
                        && definition.request().kind() != Kind.EQUIPMENT && definition.request().kind() != Kind.FOOD
                        && !this.transfers.containsKey(definition.request().consumer())
                        && (definition.request().kind() != Kind.STOCK_RESERVE || !definition.request().consumer().equals(policy))) draft.evaluate(definition);
            draft.policyReserve(policy, configuration);
            draft.started.putAll(this.started);
            draft.consumersCurrent.putAll(this.consumersCurrent);
            draft.allocate();
            return draft;
        }

        Snapshot refresh() {
            return refresh(null);
        }

        Snapshot refresh(BlockPos source) {
            var refreshed = new Snapshot(this.level, this.town, this.viewer);
            refreshed.equipmentRequirements.putAll(this.equipmentRequirements);
            refreshed.productionInputs.putAll(this.productionInputs);
            for (var definition : this.definitions) if (definition.request().kind() == Kind.FOOD) refreshed.evaluate(definition);
            refreshed.animalFeed();
            this.equipmentWorkers.forEach(refreshed::equipment);
            this.equipmentReserves.forEach(refreshed::equipmentReserve);
            refreshTransfers(refreshed);
            for (var definition : this.definitions)
                if (!definition.request().requirement().key().startsWith("animal_feed:")
                        && definition.request().kind() != Kind.EQUIPMENT && definition.request().kind() != Kind.FOOD
                        && !this.transfers.containsKey(definition.request().consumer())) refreshed.evaluate(definition);
            if (source != null) refreshed.store(source);
            refreshed.started.putAll(this.started);
            refreshed.allocate();
            return refreshed;
        }

        private void allocate() {
            var work = allocationWork();
            while (!SettlementWork.complete(work)) work.run();
        }

        private Runnable allocationWork() {
            var metadata = new LinkedHashMap<UUID, Demand>();
            demands().forEach(demand -> metadata.put(demand.supply().id(), demand));
            var offers = this.stores.values().stream().filter(java.util.Objects::nonNull).flatMap(List::stream)
                    .map(stock -> new WorkSupplyAllocation.Stock(stock.position, stock.sample, stock.remaining,
                            stock.claimed, stock.incoming, stock.known)).toList();
            return SettlementPolicyUrgency.plan(this.level, this.town, metadata.values().stream()
                    .map(demand -> new WorkSupplyAllocation.Request(demand.supply(), this.started.getOrDefault(demand.supply().id(), 0L))).toList(),
                    offers, this.stock, this::protectedInputs, result -> applyAllocation(metadata, result));
        }

        private List<WorkSupplyRequirement> protectedInputs(UUID order, long amount) {
            var inputs = new ArrayList<WorkSupplyRequirement>();
            for (var part : this.productionInputs.getOrDefault(order, List.of())) {
                long count = Math.min(amount, part.amount());
                if (count == 0) break;
                var plan = WorkProductionDemand.describe(this.level, part.recipe(), count, part.supplies(), part.water());
                for (var need : plan.requirements()) {
                    int index = -1;
                    for (int candidate = 0; candidate < inputs.size(); candidate++)
                        if (inputs.get(candidate).sameGroup(need)) { index = candidate; break; }
                    if (index < 0) inputs.add(need);
                    else inputs.set(index, need.withQuantity(inputs.get(index).quantity() + need.quantity()));
                }
                amount -= count;
            }
            return inputs;
        }

        private record ProductionInput(com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe recipe,
                                       long amount, List<ItemStack> supplies, boolean water) {
            ProductionInput { supplies = supplies.stream().map(ItemStack::copy).toList(); }
        }

        private void applyAllocation(Map<UUID, Demand> metadata, WorkSupplyAllocation.Result result) {
            this.allocation = result;
            this.demands.clear();
            for (var demand : this.allocation.demands()) {
                var original = metadata.get(demand.id());
                this.demands.add(new Demand(original.area(), original.name(), demand));
            }
            for (var grant : this.allocation.grants()) {
                var stocks = store(grant.source());
                if (stocks == null) continue;
                for (var stock : stocks) if (HiredProductionStock.matches(stock.sample, grant.sample())) {
                    if (grant.incoming()) stock.incoming -= grant.count();
                    else {
                        stock.remaining -= grant.count();
                        stock.used += grant.count();
                    }
                    break;
                }
            }
        }

        public long covered(BlockPos position, ItemStack sample) {
            var stocks = store(position);
            return stocks == null ? 0 : stocks.stream().filter(stock -> HiredProductionStock.matches(stock.sample, sample))
                    .mapToLong(stock -> stock.used).sum();
        }

        public void sold(SettlementDepot depot, ItemStack sample) {
            var stocks = store(depot.position());
            if (stocks == null) return;
            for (var stock : stocks) if (HiredProductionStock.matches(sample, stock.sample)) {
                stock.count = Math.max(0, stock.count - sample.getCount());
                stock.remaining = Math.max(0, stock.remaining - sample.getCount());
            }
        }

        public int sellable(SettlementDepot depot, ItemStack sample) {
            if (this.demands.stream().anyMatch(demand -> !demand.supply().known()
                    && (demand.requirement().alternatives().isEmpty() || demand.requirement().value(sample) > 0))) return 0;
            var stocks = store(depot.position());
            if (stocks == null) return 0;
            if (stocks.stream().anyMatch(stock -> !stock.known && HiredProductionStock.matches(sample, stock.sample))) return 0;
            long stored = 0;
            long retained = 0;
            for (var stock : stocks) if (HiredProductionStock.matches(sample, stock.sample)) {
                stored += stock.count;
                retained += stock.claimed + stock.used;
            }
            retained = Math.max(retained, Math.max(0, depot.stockTarget(sample)));
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, stored - retained));
        }

        private List<Stock> store(BlockPos position) {
            var key = SettlementSupplyStock.positions(this.level, position).getFirst();
            if (this.stores.containsKey(key)) return this.stores.get(key);
            var contents = visible(key) ? SettlementSupplyStock.contents(this.level, this.town, key) : null;
            List<Stock> result = null;
            if (contents != null) {
                result = new ArrayList<>();
                for (var stack : contents) {
                    var previous = result.stream().filter(stock -> HiredProductionStock.matches(stock.sample, stack)).findFirst().orElse(null);
                    if (previous != null) previous.count += stack.getCount();
                    else result.add(new Stock(key, stack.copyWithCount(1), stack.getCount()));
                }
                for (var sample : this.stock.incomingSamples(key)) {
                    if (result.stream().noneMatch(value -> HiredProductionStock.matches(value.sample, sample)))
                        result.add(new Stock(key, sample.copyWithCount(1), 0));
                }
                for (var stock : result) {
                    var facts = this.stock.observe(key, stock.sample, null);
                    stock.known = facts.known();
                    stock.incoming = Math.max(0, facts.incoming());
                    stock.claimed = Math.min(stock.count, Math.max(0, facts.outgoing()));
                    var depot = this.town.depots().values().stream().filter(value -> value.position().equals(key)).findFirst().orElse(null);
                    long allowance = depot == null ? AssignedStorageService.unreservedFramedAllowance(this.level, key, stock.sample,
                            VillagerFilterPolicy.TransferOperation.PROVIDE)
                            : AssignedStorageService.observedTransferAllowance(this.level, key, depot, depot.observedRules(), stock.sample,
                                    VillagerFilterPolicy.TransferOperation.PROVIDE);
                    stock.remaining = Math.max(0, Math.min(stock.count - stock.claimed, allowance));
                }
            }
            this.stores.put(key, result);
            return result;
        }

        private long owned(Stock stock, List<UUID> workers) {
            long total = 0;
            for (var worker : workers) {
                long reserved = AssignedStorageService.reservedItemCount(this.level, stock.position, null,
                        VillagerFilterPolicy.TransferOperation.PROVIDE, stock.sample)
                        - AssignedStorageService.reservedItemCount(this.level, stock.position, worker,
                                VillagerFilterPolicy.TransferOperation.PROVIDE, stock.sample);
                total += Math.max(0, reserved - stock.credited.getOrDefault(worker, 0L));
            }
            return Math.min(stock.claimed, total);
        }

        private List<UUID> workers(UUID area) {
            return this.town.duties().entrySet().stream().filter(entry -> area.equals(entry.getValue()))
                    .map(Map.Entry::getKey).sorted().toList();
        }

        private List<ItemStack> carried(UUID worker) {
            return this.carried.computeIfAbsent(worker, id -> {
                var result = new ArrayList<ItemStack>();
                if (this.level.getEntity(id) instanceof Villager villager
                        && (!this.town.workers().containsKey(id) || this.level.isPositionEntityTicking(villager.blockPosition()))) {
                    var inventory = HiredJobInventory.getJobInventory(villager);
                    for (int slot : inventory.supplySlots()) result.add(inventory.getItem(slot).copy());
                } else {
                    var state = this.town.workers().get(id);
                    if (state != null) {
                        var items = state.items(this.level.registryAccess());
                        for (int slot : state.supplySlots()) result.add(items.get(slot).copy());
                    }
                }
                return result;
            });
        }

        private List<ItemStack> available(List<BlockPos> sources, List<UUID> workers) {
            var result = new ArrayList<ItemStack>();
            for (var worker : workers) result.addAll(carried(worker).stream().map(ItemStack::copy).toList());
            for (var position : sources) {
                var stocks = store(position);
                if (stocks != null) for (var stock : stocks) {
                    long count = stock.remaining + owned(stock, workers);
                    if (count > 0) result.add(stock.sample.copyWithCount((int) Math.min(Integer.MAX_VALUE, count)));
                }
            }
            return result;
        }

        private void add(UUID consumer, UUID area, Kind kind, String name, WorkSupplyRequirement requirement,
                         List<BlockPos> sources, List<UUID> workers, List<ItemStack> privateSupplies, long progress, long immediate, String blocker) {
            add(consumer, area, kind, name, requirement, sources, workers, privateSupplies, progress, immediate, blocker,
                    kind == Kind.FOOD ? requirement.quantity() : 0, priority(consumer, area), destination(consumer, area, kind));
        }

        private void add(UUID consumer, UUID area, Kind kind, String name, WorkSupplyRequirement requirement,
                         List<BlockPos> sources, List<UUID> workers, List<ItemStack> privateSupplies, long progress, long immediate, String blocker,
                         long reserve, int priority, BlockPos destination) {
            var request = new WorkSupplyDemand(WorkSupplyDemand.identity(this.level.registryAccess(), consumer, kind, requirement),
                    consumer, kind, requirement, Math.min(immediate, requirement.quantity()), reserve, kind == Kind.FOOD ? reserve : 0,
                    priority, 0, 0, 0, 0, true, sources, destination, WorkSupplyDemand.Blocker.of(blocker));
            evaluate(new Definition(area, name, request, workers, privateSupplies, progress), privateSupplies);
        }

        private void evaluate(Definition definition) {
            evaluate(definition, definition.privateSupplies().stream().map(ItemStack::copy).toList());
        }

        private void evaluate(Definition definition, List<ItemStack> privateSupplies) {
            this.definitions.add(definition);
            var request = definition.request();
            var requirement = request.requirement();
            var workers = definition.workers();
            var sources = request.sources();
            long remaining = Math.max(0, requirement.quantity() - definition.progress());
            remaining = consume(privateSupplies, requirement, remaining);
            for (var worker : workers) remaining = consume(carried(worker), requirement, remaining);
            long committed = requirement.quantity() - remaining;
            boolean known = request.known();
            for (var position : sources) {
                var stocks = store(position);
                if (stocks == null) { known = false; continue; }
                for (var stock : stocks) {
                    long value = requirement.value(stock.sample);
                    if (value <= 0 || remaining <= 0) continue;
                    if (!stock.known) known = false;
                    for (var worker : workers) {
                        long credit = Math.min(owned(stock, List.of(worker)), (remaining + value - 1) / value);
                        stock.credited.merge(worker, credit, Long::sum);
                        committed += Math.min(remaining, credit * value);
                        remaining = Math.max(0, remaining - credit * value);
                    }
                }
            }
            String reason = !known && remaining > 0 ? "storage_unavailable" : remaining > 0 ? "missing_materials" : "";
            if (!request.blocker().code().isBlank()) reason = request.blocker().code();
            this.demands.add(new Demand(definition.area(), definition.name(), new WorkSupplyDemand(
                    request.id(), request.consumer(), request.kind(), requirement, request.immediate(), request.targetReserve(),
                    request.protectedMinimum(), request.priority(), 0, committed, 0, request.proposed(),
                    known || requirement.quantity() > 0 && remaining == 0, sources, request.destination(),
                    WorkSupplyDemand.Blocker.of(reason))));
        }

        private void unknown(UUID consumer, UUID area, Kind kind, String name, List<BlockPos> sources, String blocker) {
            var requirement = new WorkSupplyRequirement("unknown_" + blocker, List.of(), 0, WorkSupplyRequirement.Unit.ITEMS, stack -> false, null);
            evaluate(new Definition(area, name, new WorkSupplyDemand(
                    WorkSupplyDemand.identity(this.level.registryAccess(), consumer, kind, requirement), consumer, kind, requirement,
                    0, 0, 0, priority(consumer, area), 0, 0, 0, 0, false, sources, destination(consumer, area, kind),
                    WorkSupplyDemand.Blocker.of(blocker)), List.of(), List.of(), 0));
        }

        private int priority(UUID consumer, UUID area) {
            var project = this.town.projects().get(consumer);
            if (project != null) return project.priority();
            return SettlementPolicyExecution.priority(this.town, area, consumer);
        }

        private BlockPos destination(UUID consumer, UUID area, Kind kind) {
            if (kind == Kind.PROJECT) return this.town.projects().get(consumer).origin();
            var queue = this.town.productionQueues().get(area);
            return kind == Kind.PRODUCTION && queue != null && queue.order(consumer) != null ? queue.order(consumer).storage() : null;
        }

        private void equipment(UUID worker) {
            this.equipmentWorkers.add(worker);
            var equipment = SettlementEquipmentDemand.describe(this.level, this.town, worker);
            if (equipment == null) return;
            for (var need : equipment.needs()) {
                var requirement = this.equipmentRequirements.computeIfAbsent(need.equipment(), value -> value.requirement(1)).withQuantity(need.amount());
                var request = new WorkSupplyDemand(WorkSupplyDemand.identity(this.level.registryAccess(), worker, Kind.EQUIPMENT, requirement),
                        worker, Kind.EQUIPMENT, requirement, Math.min(1, need.amount()), need.reserve(), need.essential(),
                        equipment.priority() < 0 ? priority(worker, equipment.area()) : equipment.priority(),
                        0, 0, 0, 0, true, equipment.sources(), null, WorkSupplyDemand.Blocker.of(""));
                evaluate(new Definition(equipment.area(), equipment.name(), request, List.of(worker), equipment.owned(), 0), equipment.owned());
            }
        }

        private void equipmentReserve(UUID areaId) {
            this.equipmentReserves.add(areaId);
            var plan = this.town.equipmentPlans().get(areaId);
            var area = this.town.workplace(areaId);
            var settings = this.town.worksitePlans().get(areaId);
            if (plan == null || !plan.authorized(this.town) || area == null || !area.enabled()
                    || settings == null || !settings.settings().enabled()) return;
            var sources = SettlementSupplyStock.storages(this.level, this.town, areaId, false);
            var workers = workers(areaId).stream().filter(this.equipmentWorkers::contains).toList();
            for (var selection : plan.configuration().selections()) {
                if (selection.spares() == 0 || selection.kind() == com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.Kind.AMMUNITION) continue;
                var need = com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.of(selection.kind());
                var requirement = this.equipmentRequirements.computeIfAbsent(need, value -> value.requirement(1)).withQuantity(selection.spares());
                var request = new WorkSupplyDemand(WorkSupplyDemand.identity(this.level.registryAccess(), areaId, Kind.EQUIPMENT, requirement),
                        areaId, Kind.EQUIPMENT, requirement, 0, selection.spares(), selection.protectedMinimum() ? selection.spares() : 0,
                        plan.configuration().priority(), 0, 0, 0, 0, true, sources, null,
                        WorkSupplyDemand.Blocker.of(""));
                evaluate(new Definition(areaId, area.name(), request, workers, List.of(), 0));
            }
        }

        private void food() {
            long target = SettlementFoodService.reserveNutrition(this.level, this.town);
            this.consumersCurrent.put(this.town.id().value(), () -> target == SettlementFoodService.reserveNutrition(this.level, this.town));
            if (target == 0) return;
            var sources = SettlementStock.supplies(this.town).stream()
                    .map(depot -> SettlementSupplyStock.positions(this.level, depot.position()).getFirst()).distinct().toList();
            var requirement = SettlementFoodService.requirement(target);
            add(this.town.id().value(), this.town.id().value(), Kind.FOOD,
                    net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.food_reserve").getString(), requirement,
                    sources, List.of(), new ArrayList<>(), 0, target, "");
        }

        private void animalFeed() {
            if (this.town.areas().values().stream().noneMatch(area -> area.enabled() && area.crops().demand())) return;
            for (var area : this.town.areas().values()) if (area.type() == SettlementAreaType.ANIMAL_PEN) {
                var sources = SettlementSupplyStock.storages(this.level, this.town, area.id(), false);
                for (var need : SettlementBreedingDemand.describe(this.level, this.town, area))
                    add(area.id(), area.id(), Kind.PRODUCTION, area.name(), need.requirement(), sources,
                            need.workers(), new ArrayList<>(), 0, need.requirement().quantity(), "");
            }
        }

        private void stockReserves() {
            for (var depot : SettlementStock.depots(this.town)) for (var target : depot.stockTargets()) {
                var position = SettlementSupplyStock.positions(this.level, depot.position()).getFirst();
                add(depot.id(), depot.area(), Kind.STOCK_RESERVE, target.sample().getHoverName().getString(),
                        WorkSupplyRequirement.exact("stock_reserve", target.sample(), target.count()),
                        List.of(position), List.of(), new ArrayList<>(), 0, target.count(), "", target.count(), depot.priority(), position);
            }
        }

        private void policyReserves() {
            for (var policy : this.town.supplyPolicies().values()) {
                if (policy.state() != SettlementSupplyPolicy.State.ACTIVE || !SettlementPolicyExecution.authorized(this.town, policy)) continue;
                policyReserve(policy.id(), policy.configuration());
            }
        }

        private void policyReserve(UUID id, SettlementSupplyPolicy.Configuration configuration) {
            var target = configuration.target();
            if (target.kind() != SettlementSupplyPolicy.TargetKind.STOCK) return;
            var position = SettlementSupplyStock.positions(this.level, target.storage()).getFirst();
            add(id, this.town.id().value(), Kind.STOCK_RESERVE, configuration.name(),
                    WorkSupplyRequirement.exact("policy_stock", target.sample(), target.amount()),
                    List.of(position), List.of(), new ArrayList<>(), 0, target.amount(), "", target.amount(), configuration.priority(), position);
        }

        private void refreshTransfers(Snapshot target) {
            this.transfers.forEach((id, queue) -> {
                var area = this.town.areas().get(queue.owner());
                var order = queue.order(id);
                if (area != null && order != null) target.transfer(area, queue, order);
            });
        }

        private void transfer(SettlementArea area, com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue queue,
                              com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Order order) {
            this.transfers.put(order.id(), queue);
            if (this.town.managedSupplyOrder(order.id()) == null || !SettlementPolicyExecution.mayStart(this.town, order.id())) return;
            var plan = this.town.worksitePlans().get(area.id());
            if (!area.enabled() || plan != null && !plan.settings().enabled()) return;
            acceptTransfer(queue, order);
            long stock = SettlementTransferService.stock(this.level, this.town, order);
            long pending = queue.allocations().stream().filter(value -> value.order().equals(order.id()) && !value.started())
                    .mapToLong(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::amount).sum();
            long amount = stock < 0 ? order.amount() : queue.available(order.id(), stock) + pending;
            if (amount <= 0) return;
            var sources = SettlementSupplyStock.positions(this.level, order.source().position()).subList(0, 1);
            add(order.id(), area.id(), Kind.PRODUCTION, area.name(),
                    WorkSupplyRequirement.exact("policy_delivery", order.sample(), amount), sources, List.of(), new ArrayList<>(),
                    pending, Math.min(1, amount), stock < 0 ? "output_storage_unavailable" : "", 0,
                    priority(order.id(), area.id()), order.destination().position());
        }

        private void production(SettlementArea area, HiredProductionQueue queue, HiredProductionOrder order) {
            acceptProduction(queue, order);
            var sources = SettlementSupplyStock.storages(this.level, this.town, area.id(), false);
            var workers = workers(area.id());
            var plan = this.town.worksitePlans().get(area.id());
            boolean enabled = area.enabled() && (plan == null || plan.settings().enabled());
            boolean active = enabled && queue.runnable(order.id());
            long stock = order.mode() == HiredProductionOrder.Mode.MAINTAIN_STOCK
                    ? SettlementProductionStock.count(this.level, this.town, queue, order, this.stock) : 0;
            if (active && stock < 0) unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, "output_storage_unavailable");
            long amount = !active || stock < 0 ? 0 : order.mode() == HiredProductionOrder.Mode.CONTINUOUS
                    ? order.output().getCount() : queue.available(order.id(), stock);
            for (var allocation : queue.allocations()) {
                if (!allocation.order().equals(order.id())) continue;
                if (allocation.started() && allocation.station() == null) continue;
                var progress = new WorkProductionProgress(allocation.remaining(), List.of(), 0, 0, "");
                if (allocation.started() && allocation.station() != null) {
                    var furnace = this.town.furnaces().get(allocation.station());
                    var stand = this.town.brewingStands().get(allocation.station());
                    if (furnace != null && !furnace.conflict()) {
                        var items = furnace.items(this.level.registryAccess());
                        var tag = furnace.save().getCompound("Contents");
                        if (this.level.hasChunkAt(allocation.station()) && this.level.getBlockEntity(allocation.station()) instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity live) {
                            tag = live.saveWithoutMetadata(this.level.registryAccess());
                            net.minecraft.world.ContainerHelper.loadAllItems(tag, items, this.level.registryAccess());
                        }
                        progress = WorkProductionProgress.furnace(order, allocation.remaining(), items, tag,
                                !furnace.block().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("furnace")));
                    } else if (stand != null && !stand.conflict()) {
                        var items = stand.items(this.level.registryAccess());
                        var contents = stand.save().getCompound("Contents");
                        if (this.level.hasChunkAt(allocation.station()) && this.level.getBlockEntity(allocation.station()) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity live) {
                            contents = live.saveWithoutMetadata(this.level.registryAccess());
                            net.minecraft.world.ContainerHelper.loadAllItems(contents, items, this.level.registryAccess());
                        }
                        progress = WorkProductionProgress.brewing(this.level, order, allocation.remaining(), items, contents);
                    } else {
                        unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, "station_unavailable");
                        continue;
                    }
                }
                if (!progress.blocker().isEmpty()) {
                    unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, progress.blocker());
                    continue;
                }
                describe(area, order, progress.remaining(), sources, allocation.worker() == null ? List.of() : List.of(allocation.worker()),
                        new ArrayList<>(progress.supplies()), progress.burn(), progress.uses(), allocation.started());
            }
            describe(area, order, amount, sources, workers, new ArrayList<>(), 0, 0, false);
        }

        private void describe(SettlementArea area, HiredProductionOrder order, long amount, List<BlockPos> sources,
                              List<UUID> workers, List<ItemStack> privateSupplies, long burn, long uses, boolean started) {
            var survey = this.town.surveys().get(area.id());
            boolean water = survey != null && survey.waterSources().stream().anyMatch(position ->
                    !this.level.hasChunkAt(position) || SettlementBuildingSurvey.waterSource(this.level.getBlockState(position)));
            describe(area.id(), area.name(), order, amount, sources, workers, privateSupplies, burn, uses, started, water);
        }

        private void describe(UUID area, String name, HiredProductionOrder order, long amount, List<BlockPos> sources,
                              List<UUID> workers, List<ItemStack> privateSupplies, long burn, long uses, boolean started, boolean water) {
            if (amount <= 0) return;
            var available = available(sources, workers);
            available.addAll(privateSupplies);
            var plan = WorkProductionDemand.describe(this.level, order.recipe(), amount, available, water);
            if (!plan.blocker().isEmpty()) { unknown(order.id(), area, Kind.PRODUCTION, name, sources, plan.blocker()); return; }
            if (!started) this.productionInputs.computeIfAbsent(order.id(), ignored -> new ArrayList<>())
                    .add(new ProductionInput(order.recipe(), amount, available, water));
            var next = WorkProductionDemand.describe(this.level, order.recipe(),
                    Math.min(amount, order.recipe().recipe() == null ? 3 : order.output().getCount()), available, water);
            for (var need : plan.requirements()) {
                if (started) this.started.merge(WorkSupplyDemand.identity(this.level.registryAccess(), order.id(), Kind.PRODUCTION, need),
                        need.quantity(), Long::sum);
                add(order.id(), area, Kind.PRODUCTION, name, need, sources, workers,
                    privateSupplies, WorkSupplyEvaluation.progress(need, burn, uses, water),
                    next.requirements().stream().filter(value -> value.unit() == need.unit() && value.key().equals(need.key())
                            && (value.alternatives().isEmpty() || value.alternatives().stream().anyMatch(sample -> need.value(sample) > 0)))
                            .mapToLong(WorkSupplyRequirement::quantity).sum(), order.blocker(), 0, priority(order.id(), area), order.storage());
            }
        }

        private void personal(Villager worker, HiredProductionQueue queue, HiredProductionOrder order) {
            var state = com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(worker);
            boolean enabled = !state.contains("Enabled") || state.getBoolean("Enabled");
            boolean assigned = !state.contains("UseAssignedStorageForSupplies") || state.getBoolean("UseAssignedStorageForSupplies");
            var sources = com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.get(this.level).assignedTo(worker.getUUID()).stream()
                    .filter(link -> assigned && link.dimension().equals(this.level.dimension()) && supplies(link.purpose()))
                    .map(link -> SettlementSupplyStock.positions(this.level, link.pos()).getFirst()).distinct().toList();
            var waterPosition = BlockPos.of(state.getLong("BrewingCachedWaterPos"));
            boolean water = state.contains("BrewingCachedWaterPos") && this.level.hasChunkAt(waterPosition)
                    && this.level.getFluidState(waterPosition).is(net.minecraft.tags.FluidTags.WATER) && this.level.getFluidState(waterPosition).isSource();
            var workers = List.of(worker.getUUID());
            var name = worker.getName().getString();
            for (var allocation : queue.allocations()) {
                if (!allocation.order().equals(order.id()) || allocation.started() && allocation.station() == null) continue;
                var progress = WorkProductionProgress.loaded(this.level, order, allocation);
                if (!progress.blocker().isEmpty()) {
                    unknown(order.id(), worker.getUUID(), Kind.PRODUCTION, name, sources, progress.blocker());
                    continue;
                }
                describe(worker.getUUID(), name, order, progress.remaining(), sources, workers,
                        new ArrayList<>(progress.supplies()), progress.burn(), progress.uses(), allocation.started(), water);
            }
            long stock = HiredProductionStock.count(this.level, worker, queue, order);
            if (enabled && order.runnable() && stock < 0) {
                unknown(order.id(), worker.getUUID(), Kind.PRODUCTION, name, sources, "output_storage_unavailable");
                return;
            }
            long amount = !enabled || !order.runnable() || stock < 0 ? 0 : order.mode() == HiredProductionOrder.Mode.CONTINUOUS
                    ? order.output().getCount() : queue.available(order.id(), stock);
            describe(worker.getUUID(), name, order, amount, sources, workers, new ArrayList<>(), 0, 0, false, water);
        }

        private void project(SettlementProject project) {
            var status = project.status();
            int placed = project.placed();
            long revision = project.crewRevision();
            this.consumersCurrent.put(project.id(), () -> this.town.projects().get(project.id()) == project
                    && project.status() == status && project.placed() == placed && project.crewRevision() == revision);
            if (!project.active()) return;
            var area = this.town.areas().get(project.area());
            if (area == null || !area.enabled()) return;
            var sources = SettlementSupplyStock.storages(this.level, this.town, project.area(), false);
            var plan = SettlementConstructionService.scan(this.level, project.structure(), project.rotation());
            if (plan == null) { unknown(project.id(), project.area(), Kind.PROJECT, project.label(), sources, "prefab_unavailable"); return; }
            var requirements = new LinkedHashMap<net.minecraft.world.item.Item, Long>();
            var visited = new java.util.HashSet<BlockPos>();
            for (int index = 0; index < plan.blocks().size(); index++) {
                if (project.crew() == null ? index < project.placed() : project.crew().completed(index)) continue;
                var group = BuilderWorker.placementGroup(plan, project.origin(), plan.blocks().get(index));
                var material = group.materialBlock();
                var position = group.materialPart().worldPos();
                if (!visited.add(position) || !material.requiresMaterial()) continue;
                var current = this.level.hasChunkAt(position) ? this.level.getBlockState(position)
                        : project.site() != null && project.site().observed(position) ? project.site().getBlockState(position) : null;
                if (current != null && (BuilderStructureScanner.sameSchematicState(current, material.state())
                        || BuilderStructureScanner.canTransformExisting(current, material.state()))) continue;
                requirements.merge(material.requiredItem().getItem(), 1L, Long::sum);
            }
            var workers = new ArrayList<>(project.assistants());
            workers.add(project.builder());
            for (var entry : requirements.entrySet()) {
                var sample = new ItemStack(entry.getKey());
                add(project.id(), project.area(), Kind.PROJECT, project.label(),
                        new WorkSupplyRequirement("construction", List.of(sample), entry.getValue(), WorkSupplyRequirement.Unit.ITEMS,
                                stack -> BuilderStructureScanner.sameMaterial(stack, sample), null), sources, workers, new ArrayList<>(), 0, 1, project.blocker());
            }
        }
    }

    public static Snapshot inspect(ServerLevel level, SettlementRecord town) {
        return inspect(level, town, null);
    }

    public static Snapshot inspect(ServerLevel level, SettlementRecord town, net.minecraft.server.level.ServerPlayer viewer) {
        var snapshot = new Snapshot(level, town, viewer);
        for (var work : planning(snapshot, town)) {
            do { work.run(); } while (!SettlementWork.complete(work));
        }
        return snapshot;
    }

    static Runnable plan(ServerLevel level, SettlementRecord town, java.util.function.Consumer<Snapshot> ready) {
        return plan(level, town, null, ready);
    }

    static Runnable plan(ServerLevel level, SettlementRecord town, net.minecraft.server.level.ServerPlayer viewer,
                         java.util.function.Consumer<Snapshot> ready) {
        var snapshot = new Snapshot(level, town, viewer);
        var work = planning(snapshot, town);
        work.add(() -> ready.accept(snapshot));
        return new SettlementWork(work);
    }

    private static List<Runnable> planning(Snapshot snapshot, SettlementRecord town) {
        var work = new ArrayList<Runnable>();
        work.add(snapshot::food);
        work.add(snapshot::animalFeed);
        for (var entry : town.duties().entrySet()) {
            var area = town.workplace(entry.getValue());
            if (area != null && com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.supports(area.type().role()))
                work.add(() -> snapshot.equipment(entry.getKey()));
        }
        record Consumer(int priority, UUID id, Runnable run) { }
        var consumers = new ArrayList<Consumer>();
        for (var queue : town.productionQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area != null) for (var order : queue.orders())
                consumers.add(new Consumer(SettlementPolicyExecution.priority(town, area.id(), order.id()), order.id(), () -> snapshot.production(area, queue, order)));
        }
        for (var queue : town.transferQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area != null) for (var order : queue.orders())
                if (town.managedSupplyOrder(order.id()) != null) consumers.add(new Consumer(
                        SettlementPolicyExecution.priority(town, area.id(), order.id()), order.id(), () -> snapshot.transfer(area, queue, order)));
        }
        for (var worker : linkedPersonalWorkers(snapshot.level, town)) {
            work.add(() -> snapshot.equipment(worker.getUUID()));
            var role = com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.assignedRole(snapshot.level, worker);
            var access = com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(snapshot.level, worker, role);
            if (access.queue() == null || !access.blocker().isEmpty()) continue;
            for (var order : access.queue().orders())
                consumers.add(new Consumer(5, order.id(), () -> snapshot.personal(worker, access.queue(), order)));
        }
        for (var project : town.projects().values()) consumers.add(new Consumer(project.priority(), project.id(), () -> snapshot.project(project)));
        for (var area : town.equipmentPlans().keySet()) work.add(() -> snapshot.equipmentReserve(area));
        consumers.sort(Comparator.comparingInt(Consumer::priority).reversed().thenComparing(Consumer::id));
        consumers.forEach(consumer -> work.add(consumer.run()));
        work.add(snapshot::stockReserves);
        work.add(snapshot::policyReserves);
        work.add(SettlementWork.lazy(snapshot::allocationWork));
        return work;
    }

    static List<Villager> linkedPersonalWorkers(ServerLevel level, SettlementRecord town) {
        var positions = new java.util.LinkedHashSet<BlockPos>();
        town.depots().values().forEach(depot -> positions.add(depot.position()));
        town.worksitePlans().values().forEach(plan -> plan.settings().storage().entries().stream()
                .filter(link -> link.purpose().supplies()).forEach(link -> positions.add(link.position())));
        town.workerSettings().values().forEach(settings -> settings.storage().entries().stream()
                .filter(link -> link.purpose().supplies()).forEach(link -> positions.add(link.position())));
        var storage = com.jvn.villagerretaliation.inventory.AssignedStorageSavedData.get(level);
        return positions.stream().flatMap(position -> SettlementSupplyStock.positions(level, position).stream())
                .flatMap(position -> storage.assignedAt(level.dimension(), position).stream())
                .filter(link -> supplies(link.purpose())).map(link -> link.villagerId()).distinct().sorted()
                .filter(id -> !town.duties().containsKey(id)).map(level::getEntity).filter(Villager.class::isInstance).map(Villager.class::cast)
                .filter(worker -> worker.isAlive() && com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.resolve(level, worker).active()).toList();
    }

    private static boolean supplies(String purpose) {
        return purpose.equals(AssignedStorageService.GENERAL_PURPOSE) || purpose.equals(AssignedStorageService.SUPPLY_PURPOSE);
    }

    private static long consume(List<ItemStack> items, WorkSupplyRequirement requirement, long remaining) {
        return WorkSupplyEvaluation.consume(items, requirement, remaining);
    }

    private record Definition(UUID area, String name, WorkSupplyDemand request, List<UUID> workers,
                              List<ItemStack> privateSupplies, long progress) {
        private Definition {
            workers = List.copyOf(workers);
            privateSupplies = privateSupplies.stream().map(ItemStack::copy).toList();
        }
    }

    private static final class Stock {
        final BlockPos position;
        final ItemStack sample;
        long count;
        long claimed;
        long remaining;
        long used;
        long incoming;
        boolean known;
        final Map<UUID, Long> credited = new LinkedHashMap<>();
        Stock(BlockPos position, ItemStack sample, long count) { this.position = position; this.sample = sample; this.count = count; }
    }
}
