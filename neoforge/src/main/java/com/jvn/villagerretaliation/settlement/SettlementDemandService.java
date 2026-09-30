package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
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
        private final List<Demand> demands = new ArrayList<>();
        private final net.minecraft.server.level.ServerPlayer viewer;

        private Snapshot(ServerLevel level, SettlementRecord town, net.minecraft.server.level.ServerPlayer viewer) {
            this.level = level;
            this.viewer = viewer;
            this.town = town;
            this.stock = new SettlementSupplyStock.Snapshot(level, town);
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
                if (this.level.getEntity(id) instanceof Villager villager) {
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
            long remaining = Math.max(0, requirement.quantity() - progress);
            remaining = consume(privateSupplies, requirement, remaining);
            for (var worker : workers) remaining = consume(carried(worker), requirement, remaining);
            long committed = requirement.quantity() - remaining;
            long available = 0;
            boolean known = true;
            for (var position : sources) {
                var stocks = store(position);
                if (stocks == null) { known = false; continue; }
                for (var stock : stocks) {
                    long value = requirement.value(stock.sample);
                    if (value <= 0 || remaining <= 0) continue;
                    for (var worker : workers) {
                        long credit = Math.min(owned(stock, List.of(worker)), (remaining + value - 1) / value);
                        stock.credited.merge(worker, credit, Long::sum);
                        committed += Math.min(remaining, credit * value);
                        remaining = Math.max(0, remaining - credit * value);
                    }
                    long amount = Math.min(stock.remaining, (remaining + value - 1) / value);
                    stock.remaining -= amount;
                    stock.used += amount;
                    available += Math.min(remaining, amount * value);
                    remaining = Math.max(0, remaining - amount * value);
                }
            }
            long incoming = 0;
            for (var position : sources) {
                var stocks = store(position);
                if (stocks == null) continue;
                for (var stock : stocks) {
                    long value = requirement.value(stock.sample);
                    if (value <= 0) continue;
                    if (!stock.known) known = false;
                    long count = remaining == 0 ? 0 : Math.min(stock.incoming, (remaining - 1) / value + 1);
                    stock.incoming -= count;
                    incoming += Math.min(remaining, count * value);
                    remaining = Math.max(0, remaining - count * value);
                }
            }
            String reason = !known && remaining > 0 ? "storage_unavailable" : remaining > 0 ? "missing_materials" : "";
            if (!blocker.isBlank()) reason = blocker;
            this.demands.add(new Demand(area, name, new WorkSupplyDemand(
                    WorkSupplyDemand.identity(this.level.registryAccess(), consumer, kind, requirement), consumer, kind, requirement,
                    Math.min(immediate, requirement.quantity()), reserve, kind == Kind.FOOD ? reserve : 0, priority,
                    available, committed, incoming, 0, known || remaining == 0, sources, destination,
                    WorkSupplyDemand.Blocker.of(reason))));
        }

        private void unknown(UUID consumer, UUID area, Kind kind, String name, List<BlockPos> sources, String blocker) {
            var requirement = new WorkSupplyRequirement("unknown_" + blocker, List.of(), 0, WorkSupplyRequirement.Unit.ITEMS, stack -> false, null);
            this.demands.add(new Demand(area, name, new WorkSupplyDemand(
                    WorkSupplyDemand.identity(this.level.registryAccess(), consumer, kind, requirement), consumer, kind, requirement,
                    0, 0, 0, priority(consumer, area), 0, 0, 0, 0, false, sources, destination(consumer, area, kind),
                    WorkSupplyDemand.Blocker.of(blocker))));
        }

        private int priority(UUID consumer, UUID area) {
            var project = this.town.projects().get(consumer);
            if (project != null) return project.priority();
            var workplace = this.town.areas().get(area);
            return workplace == null ? 5 : workplace.priority();
        }

        private BlockPos destination(UUID consumer, UUID area, Kind kind) {
            if (kind == Kind.PROJECT) return this.town.projects().get(consumer).origin();
            var queue = this.town.productionQueues().get(area);
            return kind == Kind.PRODUCTION && queue != null && queue.order(consumer) != null ? queue.order(consumer).storage() : null;
        }

        private void food() {
            long target = SettlementFoodService.reserveNutrition(this.level, this.town);
            if (target == 0) return;
            var sources = SettlementStock.supplies(this.town).stream()
                    .map(depot -> SettlementSupplyStock.positions(this.level, depot.position()).getFirst()).distinct().toList();
            var requirement = SettlementFoodService.requirement(target);
            add(this.town.id().value(), this.town.id().value(), Kind.FOOD,
                    net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.food_reserve").getString(), requirement,
                    sources, List.of(), new ArrayList<>(), 0, target, "");
        }

        private void stockReserves() {
            for (var depot : SettlementStock.depots(this.town)) for (var target : depot.stockTargets()) {
                var position = SettlementSupplyStock.positions(this.level, depot.position()).getFirst();
                var stocks = store(position);
                long covered = stocks == null ? 0 : stocks.stream().filter(stock -> HiredProductionStock.matches(stock.sample, target.sample()))
                        .mapToLong(stock -> stock.claimed + stock.used).sum();
                long additional = Math.max(0, target.count() - covered);
                add(depot.id(), depot.area(), Kind.STOCK_RESERVE, target.sample().getHoverName().getString(),
                        WorkSupplyRequirement.exact("stock_reserve", target.sample(), additional),
                        List.of(position), List.of(), new ArrayList<>(), 0, additional, "", target.count(), depot.priority(), position);
            }
        }

        private void production(SettlementArea area, HiredProductionQueue queue) {
            var sources = SettlementSupplyStock.storages(this.level, this.town, area.id(), false);
            var workers = workers(area.id());
            var plan = this.town.worksitePlans().get(area.id());
            boolean enabled = area.enabled() && (plan == null || plan.settings().enabled());
            for (var order : queue.orders()) {
                boolean active = enabled && order.runnable();
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
                            new ArrayList<>(progress.supplies()), progress.burn(), progress.uses());
                }
                describe(area, order, amount, sources, workers, new ArrayList<>(), 0, 0);
            }
        }

        private void describe(SettlementArea area, HiredProductionOrder order, long amount, List<BlockPos> sources,
                              List<UUID> workers, List<ItemStack> privateSupplies, long burn, long uses) {
            if (amount <= 0) return;
            var available = available(sources, workers);
            available.addAll(privateSupplies);
            var survey = this.town.surveys().get(area.id());
            boolean water = survey != null && survey.waterSources().stream().anyMatch(position ->
                    !this.level.hasChunkAt(position) || SettlementBuildingSurvey.waterSource(this.level.getBlockState(position)));
            var plan = WorkProductionDemand.describe(this.level, order.recipe(), amount, available, water);
            if (!plan.blocker().isEmpty()) { unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, plan.blocker()); return; }
            var next = WorkProductionDemand.describe(this.level, order.recipe(),
                    Math.min(amount, order.recipe().recipe() == null ? 3 : order.output().getCount()), available, water);
            for (var need : plan.requirements()) add(order.id(), area.id(), Kind.PRODUCTION, area.name(), need, sources, workers,
                    privateSupplies, WorkSupplyEvaluation.progress(need, burn, uses, water),
                    next.requirements().stream().filter(value -> value.unit() == need.unit() && value.key().equals(need.key())
                            && (value.alternatives().isEmpty() || value.alternatives().stream().anyMatch(sample -> need.value(sample) > 0)))
                            .mapToLong(WorkSupplyRequirement::quantity).sum(), order.blocker());
        }

        private void project(SettlementProject project) {
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
        snapshot.food();
        record Consumer(int priority, UUID id, Runnable run) { }
        var consumers = new ArrayList<Consumer>();
        for (var queue : town.productionQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area != null) consumers.add(new Consumer(area.priority(), area.id(), () -> snapshot.production(area, queue)));
        }
        for (var project : town.projects().values()) consumers.add(new Consumer(project.priority(), project.id(), () -> snapshot.project(project)));
        consumers.sort(Comparator.comparingInt(Consumer::priority).reversed().thenComparing(Consumer::id));
        consumers.forEach(consumer -> consumer.run().run());
        snapshot.stockReserves();
        return snapshot;
    }

    private static long consume(List<ItemStack> items, WorkSupplyRequirement requirement, long remaining) {
        return WorkSupplyEvaluation.consume(items, requirement, remaining);
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
