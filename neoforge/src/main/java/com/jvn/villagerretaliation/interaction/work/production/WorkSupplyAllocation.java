package com.jvn.villagerretaliation.interaction.work.production;

import java.util.ArrayList;
import java.util.Comparator;
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
        var prepared = prepare(requests, stocks);
        return prepared.finish(prepared.input.calculate());
    }

    public static Prepared prepare(List<Request> requests, List<Stock> stocks) {
        var preparation = begin(requests, stocks);
        preparation.advance(Integer.MAX_VALUE);
        return preparation.finish();
    }

    public static Preparation begin(List<Request> requests, List<Stock> stocks) {
        return new Preparation(List.copyOf(requests), List.copyOf(stocks));
    }

    public static final class Preparation {
        private final List<Request> requests;
        private final List<Stock> stocks;
        private final long[][] values;
        private int request;
        private int stock;

        private Preparation(List<Request> requests, List<Stock> stocks) {
            this.requests = requests;
            this.stocks = stocks;
            this.values = new long[requests.size()][stocks.size()];
            if (stocks.isEmpty()) this.request = requests.size();
        }

        public boolean advance(int pairs) {
            if (pairs <= 0) throw new IllegalArgumentException("Positive capture budget required");
            while (this.request < this.requests.size() && pairs-- > 0) {
                var demand = this.requests.get(this.request).demand();
                var offer = this.stocks.get(this.stock);
                if (demand.sources().contains(offer.position())) this.values[this.request][this.stock] = demand.requirement().value(offer.sample());
                if (++this.stock == this.stocks.size()) {
                    this.stock = 0;
                    this.request++;
                }
            }
            return this.request == this.requests.size();
        }

        public Prepared finish() {
            if (this.request != this.requests.size()) throw new IllegalStateException("Supply capture is incomplete");
            return prepared(this.requests, this.stocks, this.values);
        }
    }

    private static Prepared prepared(List<Request> requests, List<Stock> stocks, long[][] values) {
        var entries = new ArrayList<Entry>();
        var floors = new java.util.HashMap<Floor, Integer>();
        for (int index = 0; index < requests.size(); index++) {
            var request = requests.get(index);
            var demand = request.demand();
            var requirement = demand.requirement();
            var alternatives = requirement.alternatives();
            int floor = -1;
            if (requirement.unit() == WorkSupplyRequirement.Unit.ITEMS && alternatives.size() == 1) {
                floor = floors.computeIfAbsent(new Floor(Set.copyOf(demand.sources()), alternatives.getFirst()), ignored -> floors.size());
            }
            entries.add(new Entry(requirement.quantity(), demand.committed(), request.started(), demand.protectedMinimum(),
                    demand.targetReserve(), demand.kind() == WorkSupplyDemand.Kind.STOCK_RESERVE, floor));
        }
        var order = new ArrayList<Integer>();
        var ids = new java.util.HashSet<UUID>();
        for (int index = 0; index < requests.size(); index++) {
            if (!ids.add(requests.get(index).demand().id())) throw new IllegalArgumentException("Duplicate allocation requirement");
            order.add(index);
        }
        order.sort(Comparator.comparingInt((Integer index) -> requests.get(index).demand().priority()).reversed()
                .thenComparing(index -> requests.get(index).demand().consumer()).thenComparing(index -> requests.get(index).demand().id()));
        var amounts = stocks.stream().map(stock -> new Amount(stock.available(), stock.reserved(), stock.incoming(), stock.known())).toList();
        return new Prepared(List.copyOf(requests), List.copyOf(stocks), new Input(List.copyOf(entries), List.copyOf(order), amounts, values));
    }

    public static final class Prepared {
        private final List<Request> requests;
        private final List<Stock> stocks;
        private final Input input;

        private Prepared(List<Request> requests, List<Stock> stocks, Input input) {
            this.requests = requests;
            this.stocks = stocks;
            this.input = input;
        }

        public java.util.concurrent.Callable<Calculation> task() {
            // only numeric data crosses the worker boundary, even stack copies can invoke mod callbacks
            return this.input::calculate;
        }

        public Result finish(Calculation calculation) {
            if (calculation.input != this.input) throw new IllegalArgumentException("Mismatched allocation calculation");
            var balances = this.stocks.stream().map(Balance::new).toList();
            for (int index = 0; index < balances.size(); index++) {
                balances.get(index).available = calculation.available[index];
                balances.get(index).incoming = calculation.incoming[index];
            }
            var grants = new ArrayList<Grant>();
            for (var assigned : calculation.grants) {
                var demand = this.requests.get(assigned.request).demand();
                var stock = this.stocks.get(assigned.stock);
                grants.add(new Grant(demand.id(), demand.consumer(), stock.position(), stock.sample(), assigned.count, assigned.incoming));
            }
            var result = new ArrayList<WorkSupplyDemand>();
            for (int index = 0; index < this.requests.size(); index++) {
                var demand = this.requests.get(index).demand();
                var state = calculation.states.get(index);
                var blocker = demand.blocker();
                long missing = state.quantity - demand.committed() - state.available - state.incoming;
                boolean known = demand.known() || state.quantity > 0 && missing <= 0;
                if (known && missing <= 0 && (blocker.code().equals("missing_materials") || blocker.code().equals("storage_unavailable")))
                    blocker = WorkSupplyDemand.Blocker.of("");
                if (known && missing > 0 && blocker.code().isEmpty()) blocker = WorkSupplyDemand.Blocker.of("missing_materials");
                if (missing > 0 && (blocker.code().isEmpty() || blocker.code().equals("missing_materials"))) {
                    for (var grant : calculation.grants) {
                        var competitor = this.requests.get(grant.request).demand().consumer();
                        if (!competitor.equals(demand.consumer()) && this.input.values[index][grant.stock] > 0) {
                            blocker = new WorkSupplyDemand.Blocker("allocated_elsewhere", WorkSupplyDemand.Action.INSPECT_COMPETITOR, competitor);
                            break;
                        }
                    }
                }
                result.add(new WorkSupplyDemand(demand.id(), demand.consumer(), demand.kind(), demand.requirement().withQuantity(state.quantity),
                        Math.min(demand.immediate(), state.quantity), demand.targetReserve(), Math.min(demand.protectedMinimum(), state.quantity),
                        demand.priority(), state.available, Math.min(demand.committed(), state.quantity), state.incoming, demand.proposed(),
                        known, demand.sources(), demand.destination(), blocker));
            }
            return new Result(result, grants, balances);
        }
    }

    private record Entry(long quantity, long committed, long started, long minimum, long target, boolean reserve, int floor) { }
    private record Amount(long available, long reserved, long incoming, boolean known) { }
    private record Assigned(int request, int stock, long count, boolean incoming) { }

    private record Input(List<Entry> entries, List<Integer> order, List<Amount> stocks, long[][] values) {
        Calculation calculate() {
            var result = new Calculation(this);
            for (int index : this.order) result.supply(index, this.entries.get(index).started);
            for (int index : this.order) result.supply(index, this.entries.get(index).minimum);
            for (int index : this.order) if (!this.entries.get(index).reserve) result.supply(index, this.entries.get(index).quantity);
            var floors = new java.util.HashMap<Integer, Long>();
            for (int index : this.order) {
                var entry = this.entries.get(index);
                if (!entry.reserve) continue;
                long covered = 0;
                for (int stock = 0; stock < this.stocks.size(); stock++) {
                    var amount = this.stocks.get(stock);
                    covered += this.values[index][stock] * (amount.reserved + amount.available - result.available[stock]
                            + amount.incoming - result.incoming[stock]);
                }
                if (entry.floor >= 0) {
                    covered = Math.max(covered, floors.getOrDefault(entry.floor, 0L));
                    floors.merge(entry.floor, entry.target, Math::max);
                }
                var state = result.states.get(index);
                state.quantity = Math.max(0, entry.target - covered);
                result.supply(index, state.quantity);
            }
            return result;
        }
    }

    public static final class Calculation {
        private final Input input;
        private final List<State> states;
        private final long[] available;
        private final long[] incoming;
        private final List<Assigned> grants = new ArrayList<>();

        private Calculation(Input input) {
            this.input = input;
            this.states = input.entries.stream().map(State::new).toList();
            this.available = input.stocks.stream().mapToLong(Amount::available).toArray();
            this.incoming = input.stocks.stream().mapToLong(Amount::incoming).toArray();
        }

        private void supply(int index, long target) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Supply planning interrupted");
            var state = this.states.get(index);
            var entry = this.input.entries.get(index);
            for (boolean incoming : List.of(false, true)) {
                long remaining = Math.max(0, target - entry.committed - state.available - state.incoming);
                for (int stock = 0; stock < this.input.stocks.size(); stock++) {
                    if (remaining == 0) break;
                    long value = this.input.values[index][stock];
                    if (!this.input.stocks.get(stock).known || value <= 0) continue;
                    long count = Math.min(incoming ? this.incoming[stock] : this.available[stock], (remaining - 1) / value + 1);
                    if (count <= 0) continue;
                    long credit = Math.min(state.quantity - entry.committed - state.available - state.incoming, count * value);
                    if (incoming) {
                        this.incoming[stock] -= count;
                        state.incoming += credit;
                    } else {
                        this.available[stock] -= count;
                        state.available += credit;
                    }
                    remaining = Math.max(0, remaining - credit);
                    this.grants.add(new Assigned(index, stock, count, incoming));
                }
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
        long quantity;
        long available;
        long incoming;
        State(Entry entry) { this.quantity = entry.quantity; }
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
