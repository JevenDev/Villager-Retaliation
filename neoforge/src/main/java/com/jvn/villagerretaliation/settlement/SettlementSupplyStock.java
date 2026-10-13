package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.inventory.PartyContainerLootService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementSupplyStock {
    private SettlementSupplyStock() { }

    public static final class Snapshot {
        private final ServerLevel level;
        private final SettlementRecord town;
        private final java.util.Map<BlockPos, java.util.Map<Product, Long>> contents = new java.util.HashMap<>();
        private final java.util.Map<Key, Facts> facts = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, List<Commitment>> incoming = new java.util.HashMap<>();
        private final java.util.Map<BlockPos, List<Commitment>> outgoing = new java.util.HashMap<>();
        private final Set<BlockPos> refreshed = new java.util.HashSet<>();

        public Snapshot(ServerLevel level, SettlementRecord town) {
            this.level = level;
            this.town = town;
            for (var queue : town.transferQueues().values()) for (var allocation : queue.allocations()) {
                var order = queue.order(allocation.order());
                var commitment = new Commitment(null, order.sample(), allocation.amount(),
                        new Contribution(order.id(), allocation.worker(), "transfer", allocation.amount()));
                add(this.incoming, order.destination().position(), commitment);
                if (!allocation.started()) add(this.outgoing, order.source().position(), commitment);
            }
            for (var queue : town.productionQueues().values()) for (var allocation : queue.allocations()) {
                var order = queue.order(allocation.order());
                if (order.storage() != null) add(this.incoming, order.storage(), new Commitment(queue, order.output(), allocation.remaining(),
                        new Contribution(order.id(), allocation.worker(), "production", allocation.remaining())));
            }
            for (var worker : town.workers().values()) {
                if (!town.duties().containsKey(worker.id())) continue;
                var loaded = level.getEntity(worker.id()) instanceof Villager villager ? villager : null;
                var destination = HiredProductionStock.destination(loaded == null ? worker.settings() : HiredWorkStateStore.state(loaded));
                if (destination == null) continue;
                var inventory = loaded == null ? null : HiredJobInventory.getJobInventory(loaded);
                var saved = loaded == null ? worker.items(level.registryAccess()) : null;
                for (int slot : loaded == null ? worker.outputSlots() : inventory.outputSlots()) {
                    var stack = loaded == null ? saved.get(slot) : inventory.getItem(slot);
                    if (!stack.isEmpty()) add(this.incoming, destination, new Commitment(null, stack, stack.getCount(),
                            new Contribution(null, worker.id(), "carried", stack.getCount())));
                }
            }
        }

        private void add(java.util.Map<BlockPos, List<Commitment>> index, BlockPos position, Commitment commitment) {
            index.computeIfAbsent(positions(this.level, position).getFirst(), ignored -> new ArrayList<>()).add(commitment);
        }

        public List<ItemStack> incomingSamples(BlockPos position) {
            var canonical = positions(this.level, position).getFirst();
            var result = new ArrayList<ItemStack>();
            for (var commitment : this.incoming.getOrDefault(canonical, List.of())) result.add(commitment.sample().copyWithCount(1));
            var personal = com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(this.level);
            for (var alias : positions(this.level, position)) {
                if (this.refreshed.add(alias)) personal.refresh(this.level, alias);
                result.addAll(personal.samples(alias));
            }
            return List.copyOf(result);
        }

        public Facts observe(BlockPos position, ItemStack sample, HiredProductionQueue excluded) {
            var canonical = positions(this.level, position).getFirst();
            var key = new Key(canonical, HiredProductionStock.product(sample).copyWithCount(1), excluded);
            if (!this.contents.containsKey(canonical)) this.contents.put(canonical, summarize(contents(this.level, this.town, canonical)));
            return this.facts.computeIfAbsent(key, ignored -> facts(canonical, sample, excluded));
        }

        private Facts facts(BlockPos position, ItemStack sample, HiredProductionQueue excluded) {
            var items = this.contents.get(position);
            if (items == null) return new Facts(-1, -1, -1, "storage_unavailable");
            long stored = items.getOrDefault(new Product(sample), 0L);
            long inbound = 0;
            long outbound = 0;
            var sources = new ArrayList<Contribution>();
            var receiveOwners = new java.util.HashSet<UUID>();
            var provideOwners = new java.util.HashSet<UUID>();
            var personal = com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(this.level);
            for (var alias : positions(this.level, position)) {
                if (this.refreshed.add(alias)) personal.refresh(this.level, alias);
                long count = personal.count(alias, sample, null);
                if (count < 0) return new Facts(stored, -1, -1, "incoming_unavailable");
                inbound += count;
                receiveOwners.addAll(personal.contributors(alias, sample));
                outbound += personal.outgoing(alias, sample);
                provideOwners.addAll(personal.providers(alias, sample));
                if (count > 0) sources.add(new Contribution(null, null, "personal", count));
            }
            for (var commitment : this.incoming.getOrDefault(position, List.of())) {
                if (excluded != null && commitment.queue() == excluded || !HiredProductionStock.matches(sample, commitment.sample())) continue;
                inbound += commitment.count();
                sources.add(commitment.source());
                if (commitment.source().worker() != null) receiveOwners.add(commitment.source().worker());
            }
            for (var commitment : this.outgoing.getOrDefault(position, List.of())) {
                if (!HiredProductionStock.matches(sample, commitment.sample())) continue;
                outbound += commitment.count();
                provideOwners.add(commitment.source().worker());
            }
            inbound += AssignedStorageService.reservedItemsExcluding(this.level, position, receiveOwners,
                    VillagerFilterPolicy.TransferOperation.RECEIVE, stack -> HiredProductionStock.matches(sample, stack));
            outbound += AssignedStorageService.reservedItemsExcluding(this.level, position, provideOwners,
                    VillagerFilterPolicy.TransferOperation.PROVIDE, stack -> HiredProductionStock.matches(sample, stack));
            return new Facts(stored, outbound, inbound, "available", sources);
        }
    }

    private static java.util.Map<Product, Long> summarize(List<ItemStack> contents) {
        if (contents == null) return null;
        var counts = new java.util.HashMap<Product, Long>();
        for (var stack : contents) counts.merge(new Product(stack), (long) stack.getCount(), Long::sum);
        return java.util.Map.copyOf(counts);
    }

    private record Product(ItemStack sample) {
        Product { sample = HiredProductionStock.product(sample).copyWithCount(1); }
        @Override public boolean equals(Object other) {
            return other instanceof Product product && ItemStack.isSameItemSameComponents(sample, product.sample);
        }
        @Override public int hashCode() { return ItemStack.hashItemAndComponents(sample); }
    }

    private record Commitment(HiredProductionQueue queue, ItemStack sample, long count, Contribution source) { }
    public record Contribution(UUID order, UUID worker, String kind, long quantity) { }

    private record Key(BlockPos position, ItemStack sample, HiredProductionQueue excluded) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && position.equals(key.position) && excluded == key.excluded
                    && ItemStack.isSameItemSameComponents(sample, key.sample);
        }
        @Override
        public int hashCode() { return java.util.Objects.hash(position, ItemStack.hashItemAndComponents(sample), excluded); }
    }

    public record Facts(long stored, long outgoing, long incoming, String status, List<Contribution> contributors) {
        public Facts { contributors = List.copyOf(contributors); }
        public Facts(long stored, long outgoing, long incoming, String status) { this(stored, outgoing, incoming, status, List.of()); }
        public boolean known() { return stored >= 0 && outgoing >= 0 && incoming >= 0; }
        public long available() { return known() ? Math.max(0, stored - outgoing) : -1; }
        public long projected() { return known() ? Math.max(0, stored - outgoing + incoming) : -1; }
    }

    public static List<BlockPos> positions(ServerLevel level, BlockPos position) {
        var positions = AssignedStorageService.containerPositions(level, position);
        return positions.isEmpty() ? List.of(position) : positions.stream().sorted().toList();
    }

    public static boolean sameStorage(ServerLevel level, BlockPos first, BlockPos second) {
        return first != null && second != null && (first.equals(second) || positions(level, first).contains(second));
    }

    public static Facts observe(ServerLevel level, SettlementRecord town, BlockPos position, ItemStack sample,
                                HiredProductionQueue excluded) {
        return new Snapshot(level, town).observe(position, sample, excluded);
    }

    public static List<ItemStack> contents(ServerLevel level, SettlementRecord town, BlockPos position) {
        var actor = SettlementStorageService.actor(level, town);
        var depot = town.depots().values().stream().filter(candidate -> candidate.position().equals(position)).findFirst().orElse(null);
        if (depot == null) return AssignedStorageService.observedContents(level, position, actor);
        var area = town.areas().get(depot.area());
        if (area == null || !town.owns(area) || !area.enabled()
                || AssignedStorageService.containerLoaded(level, position) && !PartyContainerLootService.isAvailable(level, position, actor)) return null;
        var contents = new ArrayList<ItemStack>();
        for (int slot = 0; slot < depot.getContainerSize(); slot++) if (!depot.getItem(slot).isEmpty()) contents.add(depot.getItem(slot).copy());
        return List.copyOf(contents);
    }

    public static List<BlockPos> storages(ServerLevel level, SettlementRecord town, UUID area, boolean output) {
        var workplace = town.workplace(area);
        var role = workplace == null ? null : workplace.type().role();
        var plan = town.worksitePlans().get(area);
        if (plan != null && !plan.settings().storage().automatic()) {
            var result = new LinkedHashSet<BlockPos>();
            for (var link : plan.settings().storage().entries()) {
                if (!(output ? link.purpose().outputs() : link.purpose().supplies())) continue;
                var depot = town.depots().values().stream().filter(value -> value.position().equals(link.position())).findFirst().orElse(null);
                if (depot != null && !depot.purpose().permitsRole(role)) continue;
                result.add(positions(level, link.position()).getFirst());
            }
            return List.copyOf(result);
        }
        var links = SettlementWorksiteService.links(town, area);
        return (output ? SettlementStock.outputs(town, role) : SettlementStock.supplies(town, role)).stream()
                .filter(depot -> output ? links.permitsOutput(depot.id()) : links.permitsSupply(depot.id()))
                .map(SettlementDepot::position).toList();
    }

    public static boolean workerOutput(ServerLevel level, SettlementRecord town, Villager worker, BlockPos position) {
        var area = town.duties().get(worker.getUUID());
        var workplace = town.workplace(area);
        if (workplace == null) return false;
        var storage = SettlementStorageService.effective(town, worker.getUUID());
        if (storage.automatic()) return SettlementStock.outputs(town, workplace.type().role()).stream().anyMatch(depot -> depot.position().equals(position)
                && SettlementWorksiteService.links(town, area, worker.getUUID()).permitsOutput(depot.id()));
        return storage.entries().stream().anyMatch(link -> link.purpose().outputs() && sameStorage(level, link.position(), position)
                && SettlementStorageService.permitted(level, town, workplace.type().role(), link));
    }

    public static boolean permitsOutput(ServerLevel level, SettlementRecord town, UUID area, BlockPos position) {
        if (position == null || !storages(level, town, area, true).stream().anyMatch(value -> sameStorage(level, value, position))) return false;
        if (town.depots().values().stream().anyMatch(depot -> depot.position().equals(position))) return true;
        var workplace = town.workplace(area);
        return workplace != null && SettlementStorageService.permitted(level, town, workplace.type().role(),
                new SettlementStorageLinks.Link(position, SettlementStorageLinks.Purpose.OUTPUT, 0));
    }

    public static long projected(ServerLevel level, SettlementRecord town, List<SettlementDepot> depots, ItemStack sample) {
        long total = 0;
        var snapshot = new Snapshot(level, town);
        var pool = new LinkedHashSet<BlockPos>();
        for (var depot : depots) {
            var position = positions(level, depot.position()).getFirst();
            if (!pool.add(position)) continue;
            var facts = snapshot.observe(position, sample, null);
            if (!facts.known()) return -1;
            total += facts.stored() - facts.outgoing() + facts.incoming();
        }
        return Math.max(0, total);
    }

}
