package com.jvn.villagerretaliation.interaction.work.production;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class WorkSupplyAllocation {
    private WorkSupplyAllocation() { }

    public record Stock(BlockPos position, ItemStack sample, long available, long reserved, long incoming, boolean known) {
        public Stock {
            if (position == null || sample == null || sample.isEmpty() || available < 0 || reserved < 0 || incoming < 0)
                throw new IllegalArgumentException("Invalid allocation stock");
            position = position.immutable();
            sample = sample.copyWithCount(1);
        }
        @Override public ItemStack sample() { return sample.copy(); }
    }

    public record Request(WorkSupplyDemand demand, long started) {
        public Request {
            if (demand == null || started < 0 || started > demand.requirement().quantity())
                throw new IllegalArgumentException("Invalid allocation request");
        }
    }

    public record Grant(UUID requirement, UUID consumer, BlockPos source, ItemStack sample, long count, boolean incoming) {
        public Grant { sample = sample.copyWithCount(1); }
        @Override public ItemStack sample() { return sample.copy(); }
    }

    public static final class Result {
        private final List<WorkSupplyDemand> demands;
        private final List<Grant> grants;
        private final List<Balance> balances;

        private Result(List<WorkSupplyDemand> demands, List<Grant> grants, List<Balance> balances) {
            this.demands = List.copyOf(demands);
            this.grants = List.copyOf(grants);
            this.balances = balances;
        }

        public List<WorkSupplyDemand> demands() { return this.demands; }
        public List<Grant> grants() { return this.grants; }

        public long allowance(Set<UUID> consumers, BlockPos source, ItemStack sample) {
            long count = this.balances.stream().filter(balance -> balance.stock.known() && balance.matches(source, sample))
                    .mapToLong(balance -> balance.available).sum();
            return count + this.grants.stream().filter(grant -> !grant.incoming() && consumers.contains(grant.consumer())
                            && grant.source().equals(source) && ItemStack.isSameItemSameComponents(grant.sample(), sample))
                    .mapToLong(Grant::count).sum();
        }

        public long incomingAllowance(Set<UUID> consumers, BlockPos source, ItemStack sample) {
            long count = this.balances.stream().filter(balance -> balance.stock.known() && balance.matches(source, sample))
                    .mapToLong(balance -> balance.incoming).sum();
            return count + this.grants.stream().filter(grant -> grant.incoming() && consumers.contains(grant.consumer())
                            && grant.source().equals(source) && ItemStack.isSameItemSameComponents(grant.sample(), sample))
                    .mapToLong(Grant::count).sum();
        }

        public UUID competitor(Set<UUID> consumers, BlockPos source, ItemStack sample) {
            return this.grants.stream().filter(grant -> !grant.incoming() && !consumers.contains(grant.consumer())
                            && grant.source().equals(source) && ItemStack.isSameItemSameComponents(grant.sample(), sample))
                    .map(Grant::consumer).findFirst().orElse(null);
        }
    }

    public static Result allocate(List<Request> requests, List<Stock> stocks) {
        var balances = stocks.stream().map(Balance::new).toList();
        var states = new LinkedHashMap<UUID, State>();
        for (var request : requests) {
            var previous = states.putIfAbsent(request.demand().id(), new State(request));
            if (previous != null) throw new IllegalArgumentException("Duplicate allocation requirement");
        }
        var ordered = new ArrayList<>(states.values());
        ordered.sort(Comparator.comparingInt((State state) -> state.demand.priority()).reversed()
                .thenComparing(state -> state.demand.consumer()).thenComparing(state -> state.demand.id()));
        var grants = new ArrayList<Grant>();
        for (var state : ordered) supply(state, state.started, balances, grants);
        for (var state : ordered) supply(state, state.demand.protectedMinimum(), balances, grants);
        for (var state : ordered) if (state.demand.kind() != WorkSupplyDemand.Kind.STOCK_RESERVE)
            supply(state, state.demand.requirement().quantity(), balances, grants);
        var floors = new java.util.HashMap<Floor, Long>();
        for (var state : ordered) if (state.demand.kind() == WorkSupplyDemand.Kind.STOCK_RESERVE) {
            long covered = 0;
            for (var balance : balances) {
                if (!state.demand.sources().contains(balance.stock.position())) continue;
                long value = state.demand.requirement().value(balance.stock.sample());
                if (value > 0) covered += value * (balance.stock.reserved() + balance.stock.available() - balance.available
                        + balance.stock.incoming() - balance.incoming);
            }
            var alternatives = state.demand.requirement().alternatives();
            if (state.demand.requirement().unit() == WorkSupplyRequirement.Unit.ITEMS && alternatives.size() == 1) {
                var floor = new Floor(Set.copyOf(state.demand.sources()), alternatives.getFirst());
                covered = Math.max(covered, floors.getOrDefault(floor, 0L));
                floors.merge(floor, state.demand.targetReserve(), Math::max);
            }
            state.quantity = Math.max(0, state.demand.targetReserve() - covered);
            supply(state, state.quantity, balances, grants);
        }
        var result = new ArrayList<WorkSupplyDemand>();
        for (var state : states.values()) {
            var demand = state.demand;
            var blocker = demand.blocker();
            long missing = state.quantity - demand.committed() - state.available - state.incoming;
            boolean known = demand.known() || state.quantity > 0 && missing <= 0;
            if (known && missing <= 0 && (blocker.code().equals("missing_materials") || blocker.code().equals("storage_unavailable")))
                blocker = WorkSupplyDemand.Blocker.of("");
            if (known && missing > 0 && blocker.code().isEmpty()) blocker = WorkSupplyDemand.Blocker.of("missing_materials");
            if (missing > 0 && (blocker.code().isEmpty() || blocker.code().equals("missing_materials"))) {
                var competitor = grants.stream().filter(grant -> !grant.consumer().equals(demand.consumer())
                                && demand.sources().contains(grant.source()) && demand.requirement().value(grant.sample()) > 0)
                        .map(Grant::consumer).findFirst().orElse(null);
                if (competitor != null) blocker = new WorkSupplyDemand.Blocker(
                        "allocated_elsewhere", WorkSupplyDemand.Action.INSPECT_COMPETITOR, competitor);
            }
            result.add(new WorkSupplyDemand(demand.id(), demand.consumer(), demand.kind(), demand.requirement().withQuantity(state.quantity),
                    Math.min(demand.immediate(), state.quantity), demand.targetReserve(), Math.min(demand.protectedMinimum(), state.quantity),
                    demand.priority(), state.available, Math.min(demand.committed(), state.quantity), state.incoming, demand.proposed(),
                    known, demand.sources(), demand.destination(), blocker));
        }
        return new Result(result, grants, balances);
    }

    private static void supply(State state, long target, List<Balance> balances, List<Grant> grants) {
        for (boolean incoming : List.of(false, true)) {
            long remaining = Math.max(0, target - state.demand.committed() - state.available - state.incoming);
            for (var balance : balances) {
                if (remaining == 0) break;
                if (!balance.stock.known() || !state.demand.sources().contains(balance.stock.position())) continue;
                long value = state.demand.requirement().value(balance.stock.sample());
                if (value <= 0) continue;
                long count = Math.min(incoming ? balance.incoming : balance.available, (remaining - 1) / value + 1);
                if (count <= 0) continue;
                long credit = Math.min(state.quantity - state.demand.committed() - state.available - state.incoming, count * value);
                if (incoming) {
                    balance.incoming -= count;
                    state.incoming += credit;
                } else {
                    balance.available -= count;
                    state.available += credit;
                }
                remaining = Math.max(0, remaining - credit);
                grants.add(new Grant(state.demand.id(), state.demand.consumer(), balance.stock.position(), balance.stock.sample(), count, incoming));
            }
        }
    }

    private record Floor(Set<BlockPos> sources, ItemStack sample) {
        @Override public boolean equals(Object other) {
            return other instanceof Floor floor && sources.equals(floor.sources) && ItemStack.isSameItemSameComponents(sample, floor.sample);
        }
        @Override public int hashCode() { return 31 * sources.hashCode() + ItemStack.hashItemAndComponents(sample); }
    }

    private static final class State {
        final WorkSupplyDemand demand;
        final long started;
        long quantity;
        long available;
        long incoming;
        State(Request request) {
            this.demand = request.demand();
            this.started = request.started();
            this.quantity = this.demand.requirement().quantity();
        }
    }

    private static final class Balance {
        final Stock stock;
        long available;
        long incoming;
        Balance(Stock stock) {
            this.stock = stock;
            this.available = stock.available();
            this.incoming = stock.incoming();
        }
        boolean matches(BlockPos source, ItemStack sample) {
            return this.stock.position().equals(source) && ItemStack.isSameItemSameComponents(this.stock.sample(), sample);
        }
    }
}
