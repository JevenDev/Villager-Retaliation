package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.sell.SellPriceResources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

final class SettlementPurchaseService {
    private SettlementPurchaseService() { }

    static String validate(ServerLevel level, SettlementRecord town, SettlementPurchasePlan.Approval approval,
                           SettlementPolicyPlanner.Input input) {
        if (input == null || !input.id().equals(approval.requirement())) return "policy_purchase_requirement";
        var sample = approval.sample();
        if (input.requirement().value(sample) <= 0 || SellPriceResources.definition(level.getServer(), sample).isEmpty())
            return "policy_purchase_commodity";
        var depot = depot(level, town, approval);
        return depot == null || input.sources().stream().noneMatch(source -> SettlementSupplyStock.sameStorage(level, source, depot.position()))
                ? "policy_purchase_depot" : "";
    }

    static SettlementDepot depot(ServerLevel level, SettlementRecord town, SettlementPurchasePlan.Approval approval) {
        return SettlementStock.supplies(town).stream()
                .filter(depot -> SettlementSupplyStock.sameStorage(level, depot.position(), approval.destination())).findFirst().orElse(null);
    }

    static WorkSupplyDemand demand(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                                   SettlementPurchasePlan.Approval approval, SettlementDemandService.Snapshot snapshot) {
        var demands = snapshot.demands().stream().map(SettlementDemandService.Demand::supply).toList();
        if (approval.consumer() == null) {
            var target = policy.configuration().target();
            var consumer = target.kind() == SettlementSupplyPolicy.TargetKind.STOCK ? policy.id() : target.consumer();
            if (!snapshot.current(consumer) || demands.stream().anyMatch(demand -> demand.consumer().equals(consumer) && !demand.known())
                    || target.requirement() != null && !target.requirement().equals(approval.requirement())) return null;
            return demands.stream()
                    .filter(demand -> demand.consumer().equals(consumer) && demand.id().equals(approval.requirement())
                            && switch (target.kind()) {
                                case STOCK -> demand.kind() == WorkSupplyDemand.Kind.STOCK_RESERVE;
                                case FOOD -> demand.kind() == WorkSupplyDemand.Kind.FOOD;
                                case EQUIPMENT -> demand.kind() == WorkSupplyDemand.Kind.EQUIPMENT;
                                case CONSUMER -> demand.kind() == WorkSupplyDemand.Kind.PRODUCTION || demand.kind() == WorkSupplyDemand.Kind.PROJECT;
                            }).findFirst().orElse(null);
        }
        var step = policy.configuration().steps().stream().filter(value -> value.id().equals(approval.consumer())).findFirst().orElse(null);
        var binding = step == null ? null : SettlementPolicyOrders.current(policy, step);
        if (binding == null || binding.state() != SettlementSupplyPolicy.BindingState.ACTIVE || binding.order() == null
                || !snapshot.current(binding.order()) || !SettlementPolicyExecution.mayStart(town, binding.order())
                || demands.stream().anyMatch(demand -> demand.consumer().equals(binding.order()) && !demand.known())) return null;
        return demands.stream()
                .filter(demand -> demand.consumer().equals(binding.order())
                        && SettlementPolicyPlanner.inputId(level, step.id(), demand.requirement()).equals(approval.requirement()))
                .findFirst().orElse(null);
    }

    static WorkSupplyDemand observe(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                                    SettlementPurchasePlan.Approval approval, SettlementDemandService.Snapshot snapshot, long now) {
        if (!active(level, town, policy) || !policy.configuration().purchases().approvals().contains(approval) || snapshot == null) {
            policy.purchaseShortage(approval.id(), null);
            return null;
        }
        var demand = demand(level, town, policy, approval, snapshot);
        if (demand == null || !demand.known() || demand.missing() <= 0
                || !validate(level, town, approval, new SettlementPolicyPlanner.Input(approval.requirement(), demand.requirement(),
                        demand.sources(), demand.availableNow(), demand.committed(), demand.incoming(), demand.missing(), demand.known())).isEmpty()) {
            policy.purchaseShortage(approval.id(), null);
            return null;
        }
        var previous = policy.purchaseShortages().get(approval.id());
        long since = previous == null || !previous.demand().equals(demand.id()) || now < previous.since()
                || demand.missing() < previous.missing() ? now : previous.since();
        policy.purchaseShortage(approval.id(), new SettlementSupplyPolicy.PurchaseShortage(demand.id(), since, demand.missing()));
        return demand;
    }

    static boolean active(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        return town.supplyPolicies().get(policy.id()) == policy && town.territory().active()
                && !SettlementSavedData.get(level).readOnly() && policy.state() == SettlementSupplyPolicy.State.ACTIVE
                && policy.configuration().purchases().enabled() && !policy.blocker().equals("policy_rechecking")
                && SettlementPolicyExecution.authorized(town, policy);
    }

    static Runnable work(ServerLevel level, SettlementRecord town, long now, int[] remaining) {
        var policies = town.supplyPolicies().values().stream().filter(policy -> policy.configuration().purchases().enabled())
                .sorted(Comparator.comparing(SettlementSupplyPolicy::id)).toList();
        if (policies.isEmpty()) return () -> {};
        var jobs = new ArrayList<Runnable>();
        var snapshots = new ArrayList<SettlementDemandService.Snapshot>(1);
        var grouped = new HashMap<UUID, List<Candidate>>();
        long[] revision = {town.supplyRevision()};
        SettlementAllocationService.schedule(level, town, jobs);
        jobs.add(() -> {
            var ready = SettlementAllocationService.ready(level, town);
            if (ready != null && revision[0] == town.supplyRevision()) snapshots.add(ready.refresh());
        });
        for (var policy : policies) jobs.add(SettlementWork.each(policy.configuration().purchases().approvals(), approval -> {
            var demand = observe(level, town, policy, approval, snapshots.isEmpty() ? null : snapshots.getFirst(), now);
            var shortage = policy.purchaseShortages().get(approval.id());
            if (demand == null || shortage == null || !shortage.eligible(now)) return;
            grouped.computeIfAbsent(demand.id(), ignored -> new ArrayList<>()).add(new Candidate(policy.id(), policy.revision(), approval.id(),
                    demand.id(), Math.max(demand.priority(), SettlementPolicyService.priority(town, policy)), protectedMissing(demand) > 0));
        }));
        jobs.add(SettlementWork.lazy(() -> {
            var groups = grouped.entrySet().stream().sorted(Comparator
                    .comparingInt((Map.Entry<UUID, List<Candidate>> entry) -> entry.getValue().stream().mapToInt(Candidate::priority).max().orElse(0))
                    .reversed().thenComparing(Map.Entry::getKey)).toList();
            var attempts = new ArrayList<Runnable>();
            for (boolean essential : List.of(true, false)) for (var group : groups) {
                var candidates = group.getValue().stream().sorted(Comparator.comparingInt(Candidate::priority).reversed()
                        .thenComparing(Candidate::policy).thenComparing(Candidate::approval)).toList();
                if (essential && candidates.stream().noneMatch(Candidate::essential)) continue;
                for (var candidate : candidates) attempts.add(SettlementWork.lazy(() -> {
                    if (remaining[0] <= 0 || town.supplyRevision() != revision[0]) return () -> {};
                    var work = new ArrayList<Runnable>();
                    if (SettlementAllocationService.ready(level, town) == null) SettlementAllocationService.schedule(level, town, work);
                    work.add(() -> dispatch(level, town, candidate, candidates, essential, now, remaining, revision));
                    return new SettlementWork(work);
                }));
            }
            return new SettlementWork(attempts);
        }));
        return new SettlementWork(jobs);
    }

    private static void dispatch(ServerLevel level, SettlementRecord town, Candidate candidate, List<Candidate> observers, boolean essential, long now,
                                 int[] remaining, long[] revision) {
        if (remaining[0] <= 0 || town.supplyRevision() != revision[0]) return;
        var policy = town.supplyPolicies().get(candidate.policy());
        if (policy == null || policy.revision() != candidate.revision()) return;
        var approval = policy.configuration().purchases().approvals().stream().filter(value -> value.id().equals(candidate.approval())).findFirst().orElse(null);
        if (approval == null) return;
        var ready = SettlementAllocationService.ready(level, town);
        var snapshot = ready == null ? null : ready.refresh();
        var demand = observe(level, town, policy, approval, snapshot, now);
        var shortage = policy.purchaseShortages().get(approval.id());
        if (demand == null || !demand.id().equals(candidate.demand()) || shortage == null || !shortage.eligible(now)) return;
        long missing = essential ? protectedMissing(demand) : demand.missing();
        long value = demand.requirement().value(approval.sample());
        if (missing <= 0 || value <= 0) return;
        long needed = (missing - 1) / value + 1;
        var result = SettlementTradeService.buyPolicy(level, town, policy, approval, needed, remaining[0]);
        if (result.count() == 0) return;
        remaining[0] -= result.count();
        var after = snapshot.refresh();
        var current = after.demands().stream().map(SettlementDemandService.Demand::supply)
                .filter(valueDemand -> valueDemand.id().equals(candidate.demand())).findFirst().orElse(null);
        for (var observer : observers) {
            var owner = town.supplyPolicies().get(observer.policy());
            if (owner == null || owner.revision() != observer.revision() || !active(level, town, owner)) continue;
            var previous = owner.purchaseShortages().get(observer.approval());
            if (previous == null || !previous.demand().equals(candidate.demand())) continue;
            owner.purchaseShortage(observer.approval(), current == null || !current.known() || current.missing() <= 0 ? null
                    : new SettlementSupplyPolicy.PurchaseShortage(current.id(), previous.since(), current.missing()));
        }
        String detail = result.count() + " " + approval.item();
        policy.event(level.getGameTime(), SettlementSupplyPolicy.EventKind.PURCHASED, detail.length() > 128 ? detail.substring(0, 128) : detail);
        revision[0] = town.supplyRevision();
    }

    private record Candidate(UUID policy, long revision, UUID approval, UUID demand, int priority, boolean essential) { }

    static long protectedMissing(WorkSupplyDemand demand) {
        return Math.max(0, demand.protectedMinimum() - demand.supplied() - demand.incoming());
    }
}
