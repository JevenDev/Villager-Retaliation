package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyAllocation;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

final class SettlementPolicyUrgency {
    private SettlementPolicyUrgency() { }

    static Runnable plan(ServerLevel level, SettlementRecord town, List<WorkSupplyAllocation.Request> requests,
                         List<WorkSupplyAllocation.Stock> offers, SettlementSupplyStock.Snapshot stock,
                         java.util.function.BiFunction<UUID, Long, List<WorkSupplyRequirement>> productionInputs,
                         java.util.function.Consumer<WorkSupplyAllocation.Result> complete) {
        var state = new State(level, town, requests, offers, stock, productionInputs);
        var jobs = new ArrayList<Runnable>();
        jobs.add(state::allocate);
        var claimed = new HashSet<UUID>();
        town.supplyPolicies().values().stream()
                .filter(policy -> policy.state() == SettlementSupplyPolicy.State.ACTIVE && SettlementPolicyExecution.authorized(town, policy))
                .sorted(Comparator.comparingInt((SettlementSupplyPolicy policy) -> SettlementPolicyService.priority(town, policy)).reversed()
                        .thenComparing(SettlementSupplyPolicy::id))
                .forEach(policy -> jobs.add(SettlementWork.lazy(() -> state.policy(policy, claimed))));
        jobs.add(() -> complete.accept(state.result));
        return new SettlementWork(jobs);
    }

    private static final class State {
        final ServerLevel level;
        final SettlementRecord town;
        final Map<UUID, WorkSupplyAllocation.Request> requests = new LinkedHashMap<>();
        final List<WorkSupplyAllocation.Stock> offers;
        final SettlementSupplyStock.Snapshot stock;
        final java.util.function.BiFunction<UUID, Long, List<WorkSupplyRequirement>> productionInputs;
        WorkSupplyAllocation.Result result;

        State(ServerLevel level, SettlementRecord town, List<WorkSupplyAllocation.Request> requests,
              List<WorkSupplyAllocation.Stock> offers, SettlementSupplyStock.Snapshot stock,
              java.util.function.BiFunction<UUID, Long, List<WorkSupplyRequirement>> productionInputs) {
            this.level = level;
            this.town = town;
            requests.forEach(request -> this.requests.put(request.demand().id(), request));
            this.offers = offers;
            this.stock = stock;
            this.productionInputs = productionInputs;
        }

        void allocate() {
            this.result = WorkSupplyAllocation.allocate(List.copyOf(this.requests.values()), this.offers);
        }

        Runnable policy(SettlementSupplyPolicy policy, java.util.Set<UUID> claimed) {
            var graph = SettlementPolicyGraph.inspect(policy.configuration());
            if (!graph.valid()) return () -> {};
            var amounts = new HashMap<UUID, Long>();
            var steps = new HashMap<UUID, SettlementSupplyPolicy.Step>();
            graph.producersFirst().forEach(step -> steps.put(step.id(), step));
            var target = policy.configuration().target();
            var consumer = target.kind() == SettlementSupplyPolicy.TargetKind.STOCK ? policy.id() : target.consumer();
            for (var edge : policy.configuration().connections()) {
                if (edge.consumer() != null || claimed.contains(edge.requirement())) continue;
                var demand = this.result.demands().stream().filter(value -> value.id().equals(edge.requirement())
                        && value.consumer().equals(consumer) && (target.requirement() == null || target.requirement().equals(value.id())))
                        .findFirst().orElse(null);
                if (demand == null || demand.protectedMinimum() == 0) continue;
                long amount = needed(demand, policy, steps.get(edge.producer()));
                amounts.merge(edge.producer(), amount, Long::sum);
                claimed.add(edge.requirement());
            }
            return SettlementWork.each(graph.producersFirst().reversed(), step -> {
                long amount = Math.min(HiredProductionOrder.MAX_AMOUNT, amounts.getOrDefault(step.id(), 0L));
                var binding = SettlementPolicyOrders.current(policy, step);
                if (amount <= 0 || binding == null || binding.order() == null
                        || !SettlementPolicyExecution.mayStart(this.town, binding.order())) return;
                var inputs = step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION ? this.productionInputs.apply(binding.order(), amount)
                        : SettlementPolicyPlanner.inputs(this.level, this.town, step, amount).stream().map(SettlementPolicyPlanner.Input::requirement).toList();
                boolean changed = false;
                for (var input : inputs) for (var request : List.copyOf(this.requests.values())) {
                    var demand = request.demand();
                    if (!demand.consumer().equals(binding.order()) || !demand.requirement().sameGroup(input)) continue;
                    long minimum = Math.min(demand.requirement().quantity(), Math.max(demand.protectedMinimum(), request.started() + input.quantity()));
                    if (minimum == demand.protectedMinimum()) continue;
                    var inherited = new WorkSupplyDemand(demand.id(), demand.consumer(), demand.kind(), demand.requirement(),
                            demand.immediate(), demand.targetReserve(), minimum, demand.priority(), demand.availableNow(), demand.committed(),
                            demand.incoming(), demand.proposed(), demand.known(), demand.sources(), demand.destination(), demand.blocker());
                    this.requests.put(demand.id(), new WorkSupplyAllocation.Request(inherited, request.started()));
                    changed = true;
                }
                if (changed) allocate();
                for (var edge : policy.configuration().connections()) {
                    if (!step.id().equals(edge.consumer())) continue;
                    var demand = this.result.demands().stream().filter(value -> value.consumer().equals(binding.order())
                            && SettlementPolicyPlanner.inputId(this.level, step.id(), value.requirement()).equals(edge.requirement()))
                            .findFirst().orElse(null);
                    if (demand != null) amounts.merge(edge.producer(), needed(demand, policy, steps.get(edge.producer())), Long::sum);
                }
            });
        }

        long unstarted(SettlementSupplyPolicy.Binding binding) {
            if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
                var queue = this.town.productionQueues().get(binding.step().area());
                return queue == null ? 0 : queue.allocations().stream().filter(value -> !value.started() && value.order().equals(binding.order()))
                        .mapToLong(com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue.Allocation::remaining).sum();
            }
            var queue = this.town.transferQueues().get(binding.step().area());
            return queue == null ? 0 : queue.allocations().stream().filter(value -> !value.started() && value.order().equals(binding.order()))
                    .mapToLong(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::amount).sum();
        }

        long needed(WorkSupplyDemand demand, SettlementSupplyPolicy policy, SettlementSupplyPolicy.Step producer) {
            if (!demand.known() || demand.protectedMinimum() == 0) return 0;
            var output = SettlementPolicyPlanner.output(this.level, producer);
            long value = demand.requirement().value(output);
            var destination = SettlementSupplyStock.positions(this.level, producer.destination()).getFirst();
            if (value <= 0 || !demand.sources().contains(destination)) return 0;
            var binding = SettlementPolicyOrders.current(policy, producer);
            long own = binding == null || binding.order() == null ? 0 : Math.min(unstarted(binding),
                    this.stock.observe(destination, output, null).contributors().stream()
                            .filter(contribution -> binding.order().equals(contribution.order()))
                            .mapToLong(SettlementSupplyStock.Contribution::quantity).sum()) * value;
            long remaining = Math.max(0, demand.protectedMinimum() - demand.supplied() - Math.max(0, demand.incoming() - own));
            return remaining == 0 ? 0 : (remaining - 1) / value + 1;
        }
    }
}
