package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand;
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

    public enum Kind { PRODUCTION, PROJECT }
    public record Demand(UUID consumer, UUID area, Kind kind, String name, WorkSupplyRequirement requirement,
                         long supplied, long missing, long immediateMissing, List<BlockPos> sources, String blocker) {
        public Demand { sources = List.copyOf(sources); }
    }

    public static final class Snapshot {
        private final ServerLevel level;
        private final SettlementRecord town;
        private final SettlementSupplyStock.Snapshot stock;
        private final Map<BlockPos, List<Stock>> stores = new LinkedHashMap<>();
        private final Map<UUID, List<ItemStack>> carried = new LinkedHashMap<>();
        private final List<Demand> demands = new ArrayList<>();
        private boolean unknown;
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

        public List<Demand> demands() { return List.copyOf(this.demands); }
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
            if (this.unknown) return 0;
            var stocks = store(depot.position());
            if (stocks == null) return 0;
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
                for (var stock : result) {
                    var facts = this.stock.observe(key, stock.sample, null);
                    if (!facts.known()) this.unknown = true;
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
            long remaining = Math.max(0, requirement.quantity() - progress);
            remaining = consume(privateSupplies, requirement, remaining);
            for (var worker : workers) remaining = consume(carried(worker), requirement, remaining);
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
                        remaining = Math.max(0, remaining - credit * value);
                    }
                    long amount = Math.min(stock.remaining, (remaining + value - 1) / value);
                    stock.remaining -= amount;
                    stock.used += amount;
                    remaining = Math.max(0, remaining - amount * value);
                }
            }
            if (!known && remaining > 0) this.unknown = true;
            String reason = !known && remaining > 0 ? "storage_unavailable" : remaining > 0 ? "missing_materials" : "";
            if (!blocker.isBlank()) reason = blocker;
            this.demands.add(new Demand(consumer, area, kind, name, requirement,
                    requirement.quantity() - remaining, !known && remaining > 0 ? -1 : remaining,
                    !known && remaining > 0 ? -1 : Math.max(0, immediate - (requirement.quantity() - remaining)), sources, reason));
        }

        private void unknown(UUID consumer, UUID area, Kind kind, String name, List<BlockPos> sources, String blocker) {
            this.unknown = true;
            this.demands.add(new Demand(consumer, area, kind, name,
                    new WorkSupplyRequirement("unknown", List.of(), 0, WorkSupplyRequirement.Unit.ITEMS, stack -> false, null),
                    0, -1, -1, sources, blocker));
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
                    var supplies = new ArrayList<ItemStack>();
                    long burn = 0;
                    long uses = 0;
                    long pending = allocation.remaining();
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
                            pending = Math.max(0, pending - (order.matches(items.get(2)) ? items.get(2).getCount() : 0));
                            supplies.add(items.get(0).copy());
                            supplies.add(items.get(1).copy());
                            boolean fast = !furnace.block().equals(net.minecraft.resources.ResourceLocation.withDefaultNamespace("furnace"));
                            burn = (Math.max(0, tag.getInt("BurnTime")) + Math.max(0, tag.getInt("CookTime"))) * (fast ? 2L : 1L);
                        } else if (stand != null && !stand.conflict()) {
                            var items = stand.items(this.level.registryAccess());
                            var contents = stand.save().getCompound("Contents");
                            if (this.level.hasChunkAt(allocation.station()) && this.level.getBlockEntity(allocation.station()) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity live) {
                                contents = live.saveWithoutMetadata(this.level.registryAccess());
                                net.minecraft.world.ContainerHelper.loadAllItems(contents, items, this.level.registryAccess());
                            }
                            var route = com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog.find(this.level,
                                    order.recipe().brewingItem(), order.recipe().potion()).orElse(null);
                            int step = route == null ? -1 : SettlementBackgroundBrewing.currentStep(this.level, items, route);
                            if (step < 0) { unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, "station_batch_missing"); continue; }
                            int bottles = (int) items.subList(0, 3).stream().filter(stack -> !stack.isEmpty()).count();
                            supplies.add(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.POTION,
                                    net.minecraft.world.item.alchemy.Potions.WATER).copyWithCount(bottles));
                            for (int index = 0; index < step; index++) supplies.add(new ItemStack(route.ingredients().get(index)));
                            supplies.add(items.get(3).copy());
                            supplies.add(items.get(4).copy());
                            uses = step + Math.max(0, contents.getInt("Fuel")) + (contents.getInt("BrewTime") > 0 ? 1 : 0);
                        } else {
                            unknown(order.id(), area.id(), Kind.PRODUCTION, area.name(), sources, "station_unavailable");
                            continue;
                        }
                    }
                    describe(area, order, pending, sources, allocation.worker() == null ? List.of() : List.of(allocation.worker()), supplies, burn, uses);
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
                    privateSupplies, need.unit() == WorkSupplyRequirement.Unit.BURN_TICKS ? burn
                            : need.unit() == WorkSupplyRequirement.Unit.BREW_USES ? uses
                            : need.unit() == WorkSupplyRequirement.Unit.WATER_FILLS && water ? need.quantity() : 0,
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
        record Consumer(int priority, UUID id, Runnable run) { }
        var consumers = new ArrayList<Consumer>();
        for (var queue : town.productionQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area != null) consumers.add(new Consumer(area.priority(), area.id(), () -> snapshot.production(area, queue)));
        }
        for (var project : town.projects().values()) consumers.add(new Consumer(project.priority(), project.id(), () -> snapshot.project(project)));
        consumers.sort(Comparator.comparingInt(Consumer::priority).reversed().thenComparing(Consumer::id));
        consumers.forEach(consumer -> consumer.run().run());
        return snapshot;
    }

    private static long consume(List<ItemStack> items, WorkSupplyRequirement requirement, long remaining) {
        for (var stack : items) {
            long value = requirement.value(stack);
            if (value <= 0 || remaining <= 0) continue;
            int count = (int) Math.min(stack.getCount(), (remaining + value - 1) / value);
            stack.shrink(count);
            remaining = Math.max(0, remaining - count * value);
        }
        return remaining;
    }

    private static final class Stock {
        final BlockPos position;
        final ItemStack sample;
        long count;
        long claimed;
        long remaining;
        long used;
        final Map<UUID, Long> credited = new LinkedHashMap<>();
        Stock(BlockPos position, ItemStack sample, long count) { this.position = position; this.sample = sample; this.count = count; }
    }
}
