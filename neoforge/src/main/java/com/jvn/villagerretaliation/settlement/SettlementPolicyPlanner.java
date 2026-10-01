package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.WorkProductionDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementPolicyPlanner {
    private SettlementPolicyPlanner() { }

    record Input(UUID id, WorkSupplyRequirement requirement, List<BlockPos> sources, long available,
                 long committed, long incoming, long missing, boolean known) {
        Input { sources = List.copyOf(sources); }
    }

    record Step(SettlementSupplyPolicy.Step definition, ItemStack output, long proposed, long requested, long retained,
                long projected, long outstanding, long progress, List<Input> inputs, String blocker) {
        Step {
            output = output.copy();
            inputs = List.copyOf(inputs);
        }
        @Override public ItemStack output() { return output.copy(); }
    }

    record Plan(UUID policy, long revision, List<Input> target, List<Step> steps, String blocker) {
        Plan {
            target = List.copyOf(target);
            steps = List.copyOf(steps);
        }
    }

    static UUID inputId(ServerLevel level, UUID step, WorkSupplyRequirement requirement) {
        return WorkSupplyDemand.identity(level.registryAccess(), step, WorkSupplyDemand.Kind.PRODUCTION, requirement);
    }

    static Runnable plan(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                         SettlementDemandService.Snapshot snapshot, Set<UUID> claimed, java.util.function.Consumer<Plan> complete) {
        var configuration = policy.configuration();
        var graph = SettlementPolicyGraph.inspect(configuration);
        if (!graph.valid()) return () -> complete.accept(new Plan(policy.id(), policy.revision(), List.of(), List.of(), graph.blocker()));
        var view = policy.state() == SettlementSupplyPolicy.State.ACTIVE ? snapshot : snapshot.policyDraft(policy.id(), configuration);
        var target = targets(level, town, policy, view);
        var outputs = new HashMap<UUID, ItemStack>();
        var templates = new HashMap<UUID, List<Input>>();
        var proposed = new HashMap<UUID, Long>();
        var requested = new HashMap<UUID, Long>();
        var retained = new HashMap<UUID, Long>();
        var plans = new LinkedHashMap<UUID, Step>();
        var blockers = new LinkedHashMap<UUID, String>();
        var purchaseErrors = new ArrayList<String>();
        var jobs = new ArrayList<Runnable>();
        jobs.add(SettlementWork.each(graph.producersFirst(), step -> {
            var output = output(level, step);
            outputs.put(step.id(), output);
            var requirements = inputs(level, town, step, Math.max(1, output.getCount()));
            templates.put(step.id(), requirements);
            if (output.isEmpty()) blockers.put(step.id(), "recipe_unavailable");
        }));
        if (configuration.purchases().enabled()) jobs.add(SettlementWork.each(configuration.purchases().approvals(), approval -> {
            var inputs = approval.consumer() == null ? target : templates.getOrDefault(approval.consumer(), List.of());
            var input = inputs.stream().filter(value -> value.id().equals(approval.requirement())).findFirst().orElse(null);
            String failure = SettlementPurchaseService.validate(level, town, approval, input);
            if (!failure.isEmpty()) purchaseErrors.add(failure);
        }));
        jobs.add(() -> {
            for (var edge : configuration.connections()) {
                var inputs = edge.consumer() == null ? target : templates.get(edge.consumer());
                var input = inputs.stream().filter(value -> value.id().equals(edge.requirement())).findFirst().orElse(null);
                var producer = configuration.steps().stream().filter(step -> step.id().equals(edge.producer())).findFirst().orElseThrow();
                if (input == null || input.requirement().value(outputs.get(edge.producer())) <= 0)
                    blockers.put(edge.producer(), "policy_connection_invalid");
                else if (input.sources().stream().noneMatch(source -> SettlementSupplyStock.sameStorage(level, source, producer.destination())))
                    blockers.put(edge.producer(), "policy_destination_unlinked");
                else if (edge.consumer() == null) {
                    long quantity = input.known() ? units(input.missing(), input.requirement().value(outputs.get(edge.producer()))) : 0;
                    proposed.merge(edge.producer(), quantity, Long::sum);
                    retained.merge(edge.producer(), units(input.incoming(), input.requirement().value(outputs.get(edge.producer()))), Long::sum);
                    if (!claimed.contains(input.id())) requested.merge(edge.producer(), quantity, Long::sum);
                }
            }
        });
        jobs.add(SettlementWork.each(graph.producersFirst().reversed(), step -> {
            var output = outputs.get(step.id());
            long amount = Math.min(HiredProductionOrder.MAX_AMOUNT, proposed.getOrDefault(step.id(), 0L));
            var required = inputs(level, town, step, amount);
            var binding = SettlementPolicyOrders.current(policy, step);
            var actual = actualInputs(level, town, step, binding, view);
            for (var edge : configuration.connections()) {
                if (!step.id().equals(edge.consumer())) continue;
                var producerOutput = outputs.get(edge.producer());
                var input = required.stream().filter(value -> value.id().equals(edge.requirement())).findFirst().orElse(null);
                if (input != null) proposed.merge(edge.producer(), units(input.requirement().quantity(), input.requirement().value(producerOutput)), Long::sum);
                var available = actual.stream().filter(value -> value.id().equals(edge.requirement())).findFirst().orElse(null);
                if (available != null && available.known() && !claimed.contains(nativeRequirement(level, binding, available.requirement()))) {
                    requested.merge(edge.producer(), units(available.missing(), available.requirement().value(producerOutput)), Long::sum);
                    retained.merge(edge.producer(), units(available.incoming(), available.requirement().value(producerOutput)), Long::sum);
                }
            }
            String blocker = blockers.getOrDefault(step.id(), "");
            if (blocker.isEmpty() && proposed.getOrDefault(step.id(), 0L) > HiredProductionOrder.MAX_AMOUNT) blocker = "policy_target_limit";
            var facts = view.stock().observe(step.destination(), output, null);
            long outstanding = binding == null || binding.order() == null ? 0 : facts.contributors().stream()
                    .filter(contribution -> binding.order().equals(contribution.order())).mapToLong(SettlementSupplyStock.Contribution::quantity).sum();
            plans.put(step.id(), new Step(step, output, amount, requested.getOrDefault(step.id(), 0L), retained.getOrDefault(step.id(), 0L),
                    facts.projected(), outstanding, binding != null && step.kind() == SettlementSupplyPolicy.StepKind.GATHER
                            ? view.gathered(binding.id()) : SettlementPolicyOrders.progress(town, binding), actual, blocker));
        }));
        jobs.add(() -> {
            String blocker = !purchaseErrors.isEmpty() ? purchaseErrors.getFirst()
                    : target.stream().anyMatch(input -> !input.known()) ? "storage_unavailable"
                    : plans.values().stream().map(Step::blocker).filter(value -> !value.isEmpty()).findFirst().orElse("");
            for (var edge : configuration.connections()) {
                if (!blocker.isEmpty()) break;
                if (edge.consumer() == null) claimed.add(edge.requirement());
                else {
                    var step = configuration.steps().stream().filter(value -> value.id().equals(edge.consumer())).findFirst().orElseThrow();
                    var binding = SettlementPolicyOrders.current(policy, step);
                    if (binding == null) continue;
                    var input = templates.get(step.id()).stream().filter(value -> value.id().equals(edge.requirement())).findFirst().orElse(null);
                    if (input != null) claimed.add(nativeRequirement(level, binding, input.requirement()));
                }
            }
            complete.accept(new Plan(policy.id(), policy.revision(), target,
                    graph.producersFirst().stream().map(step -> plans.get(step.id())).toList(), blocker));
        });
        return new SettlementWork(jobs);
    }

    static List<Input> targets(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                               SettlementDemandService.Snapshot snapshot) {
        var target = policy.configuration().target();
        var consumer = target.kind() == SettlementSupplyPolicy.TargetKind.STOCK ? policy.id() : target.consumer();
        var result = snapshot.demands().stream().filter(demand -> demand.consumer().equals(consumer)
                && (target.kind() != SettlementSupplyPolicy.TargetKind.EQUIPMENT || demand.kind() == WorkSupplyDemand.Kind.EQUIPMENT)
                && (target.requirement() == null || target.requirement().equals(demand.supply().id())))
                .map(demand -> input(demand.supply().id(), demand.supply())).toList();
        if (!result.isEmpty()) return result;
        if (target.kind() == SettlementSupplyPolicy.TargetKind.STOCK) {
            var requirement = WorkSupplyRequirement.exact("policy_stock", target.sample(), target.amount());
            var facts = snapshot.stock().observe(target.storage(), target.sample(), null);
            var id = WorkSupplyDemand.identity(level.registryAccess(), policy.id(), WorkSupplyDemand.Kind.STOCK_RESERVE, requirement);
            return List.of(new Input(id, requirement, List.of(target.storage()), Math.max(0, facts.available()), 0,
                    Math.max(0, facts.incoming()), facts.known() ? Math.max(0, target.amount() - facts.projected()) : target.amount(), facts.known()));
        }
        if (target.kind() == SettlementSupplyPolicy.TargetKind.FOOD && target.consumer().equals(town.id().value())) {
            var requirement = SettlementFoodService.requirement(0);
            return List.of(new Input(WorkSupplyDemand.identity(level.registryAccess(), target.consumer(), WorkSupplyDemand.Kind.FOOD, requirement),
                    requirement, SettlementStock.supplies(town).stream().map(SettlementDepot::position).toList(), 0, 0, 0, 0, true));
        }
        for (var queue : town.productionQueues().values()) {
            var order = queue.order(target.consumer());
            if (order == null) continue;
            var requirements = WorkProductionDemand.describe(level, order.recipe(), Math.max(1, order.output().getCount()), List.of(), water(level, town, queue.owner()));
            return requirements.requirements().stream().map(requirement -> new Input(
                    WorkSupplyDemand.identity(level.registryAccess(), order.id(), WorkSupplyDemand.Kind.PRODUCTION, requirement),
                    requirement.withQuantity(0), SettlementSupplyStock.storages(level, town, queue.owner(), false), 0, 0, 0, 0, true))
                    .filter(input -> target.requirement() == null || target.requirement().equals(input.id())).toList();
        }
        return List.of();
    }

    static List<Input> inputs(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy.Step step, long amount) {
        if (step.kind() == SettlementSupplyPolicy.StepKind.GATHER) return List.of();
        if (step.kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
            var requirement = WorkSupplyRequirement.exact("policy_delivery", step.sample(), amount);
            return List.of(new Input(inputId(level, step.id(), requirement), requirement, List.of(step.source()), 0, 0, 0, amount, true));
        }
        var requirements = WorkProductionDemand.describe(level, step.recipe(), amount, List.of(), water(level, town, step.area()));
        var sources = SettlementSupplyStock.storages(level, town, step.area(), false);
        return requirements.requirements().stream().map(requirement -> new Input(inputId(level, step.id(), requirement), requirement,
                sources, 0, 0, 0, requirement.quantity(), requirements.blocker().isEmpty())).toList();
    }

    private static List<Input> actualInputs(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy.Step step,
                                            SettlementSupplyPolicy.Binding binding, SettlementDemandService.Snapshot snapshot) {
        if (binding == null || binding.state() != SettlementSupplyPolicy.BindingState.ACTIVE || binding.order() == null) return List.of();
        if (step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION)
            return snapshot.demands().stream().filter(demand -> demand.consumer().equals(binding.order()))
                    .map(demand -> input(inputId(level, step.id(), demand.requirement()), demand.supply())).toList();
        var queue = town.transferQueues().get(step.area());
        var order = queue == null ? null : queue.order(binding.order());
        if (order == null) return List.of();
        snapshot.acceptTransfer(queue, order);
        long stock = SettlementTransferService.stock(level, town, order);
        long pending = queue.allocations().stream().filter(allocation -> allocation.order().equals(order.id()) && !allocation.started())
                .mapToLong(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::amount).sum();
        long required = stock < 0 ? order.amount() : queue.available(order.id(), stock) + pending;
        var source = SettlementSupplyStock.positions(level, step.source()).getFirst();
        var destination = SettlementSupplyStock.positions(level, step.destination()).getFirst();
        var refreshed = snapshot.refresh(source);
        var consumers = new HashSet<UUID>();
        consumers.add(order.id());
        for (var demand : refreshed.demands()) if (demand.sources().contains(destination) && demand.requirement().value(order.sample()) > 0)
            consumers.add(demand.consumer());
        var facts = refreshed.stock().observe(source, order.sample(), null);
        long available = facts.known() ? Math.min(required, Math.min(facts.stored(),
                refreshed.allocation().allowance(consumers, source, order.sample()) + pending)) : 0;
        long incoming = facts.known() ? Math.min(required - available, refreshed.allocation().incomingAllowance(consumers, source, order.sample())) : 0;
        var requirement = WorkSupplyRequirement.exact("policy_delivery", step.sample(), required);
        return List.of(new Input(inputId(level, step.id(), requirement), requirement, List.of(source), available, 0, incoming,
                Math.max(0, required - available - incoming), stock >= 0 && facts.known()));
    }

    private static UUID nativeRequirement(ServerLevel level, SettlementSupplyPolicy.Binding binding, WorkSupplyRequirement requirement) {
        return WorkSupplyDemand.identity(level.registryAccess(), binding.order(), WorkSupplyDemand.Kind.PRODUCTION, requirement);
    }

    private static Input input(UUID id, WorkSupplyDemand demand) {
        return new Input(id, demand.requirement(), demand.sources(), demand.availableNow(), demand.committed(), demand.incoming(), demand.missing(), demand.known());
    }

    static ItemStack output(ServerLevel level, SettlementSupplyPolicy.Step step) {
        return step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION ? step.recipe().output(level) : step.sample();
    }

    static boolean water(ServerLevel level, SettlementRecord town, UUID area) {
        var survey = town.surveys().get(area);
        return survey != null && survey.waterSources().stream().anyMatch(position -> !level.hasChunkAt(position)
                || SettlementBuildingSurvey.waterSource(level.getBlockState(position)));
    }

    private static long units(long quantity, long value) {
        return quantity <= 0 || value <= 0 ? 0 : (quantity - 1) / value + 1;
    }
}
