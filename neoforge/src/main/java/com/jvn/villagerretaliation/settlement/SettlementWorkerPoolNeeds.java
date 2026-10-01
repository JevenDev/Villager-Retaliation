package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

final class SettlementWorkerPoolNeeds {
    private final ServerLevel level;
    private final SettlementRecord town;
    private final SettlementDemandService.Snapshot snapshot;
    private final List<SettlementDemandService.Demand> demands;
    private final Map<UUID, Need> areas = new HashMap<>();

    SettlementWorkerPoolNeeds(ServerLevel level, SettlementRecord town, SettlementDemandService.Snapshot snapshot) {
        this.level = level;
        this.town = town;
        this.snapshot = snapshot;
        this.demands = snapshot.demands();
    }

    boolean current() {
        return this.demands.stream().allMatch(demand -> this.snapshot.current(demand.consumer()));
    }

    Need area(UUID id) {
        if (id == null) return Need.NONE;
        return this.areas.computeIfAbsent(id, this::inspect);
    }

    private Need inspect(UUID id) {
        var area = this.town.workplace(id);
        if (area == null || !area.enabled()) return Need.NONE;
        if (area.type() == SettlementAreaType.CONSTRUCTION) {
            var need = new Need(false, false, area.priority(), null);
            for (var project : this.town.projects().values()) {
                if (!project.area().equals(id) && project.assistants().stream().noneMatch(worker -> id.equals(this.town.duties().get(worker)))) continue;
                if (project.active()) need = need.merge(new Need(true, false, project.priority(), project.id()));
                else if (project.status() == SettlementProject.Status.COMPLETED) need = need.completed(project.id());
            }
            return need;
        }
        var production = this.town.productionQueues().get(id);
        var transfers = this.town.transferQueues().get(id);
        var gathering = this.town.managedGathering(id);
        Need need = production == null && transfers == null && gathering.isEmpty()
                ? new Need(true, false, area.priority(), null) : new Need(false, false, area.priority(), null);
        if (production != null) for (var order : production.orders()) {
            long stock = SettlementProductionStock.count(this.level, this.town, production, order, this.snapshot.stock());
            boolean pending = production.pending(order.id()) > 0;
            if (pending || stock >= 0 && production.available(order.id(), stock) > 0)
                need = need.merge(order(id, order.id()));
            else if (order.complete()) need = need.completed(order.id());
        }
        if (transfers != null) for (var order : transfers.orders()) {
            var facts = this.snapshot.stock().observe(order.destination().position(), order.sample(), null);
            long stock = order.mode() == HiredTransferQueue.Mode.FINITE ? 0 : facts.known() ? facts.stored() + facts.incoming() : -1;
            if (order.pending() > 0 || stock >= 0 && transfers.available(order.id(), stock) > 0)
                need = need.merge(order(id, order.id()));
            else if (order.complete()) need = need.completed(order.id());
        }
        for (var entry : gathering) {
            var policy = entry.policy();
            var binding = entry.binding();
            if (policy.state() != SettlementSupplyPolicy.State.ACTIVE || !SettlementPolicyExecution.authorized(this.town, policy)
                    || policy.blocker().equals("policy_rechecking") || binding == null
                    || binding.state() != SettlementSupplyPolicy.BindingState.ACTIVE && binding.state() != SettlementSupplyPolicy.BindingState.PARKED
                    || !policy.configuration().steps().contains(binding.step())) continue;
            var facts = this.snapshot.stock().observe(entry.step().destination(), entry.step().sample(), null);
            if (binding.state() == SettlementSupplyPolicy.BindingState.ACTIVE && binding.target() > 0
                    && binding.gathered() < binding.ceiling() && facts.known() && facts.projected() < binding.target())
                need = need.merge(policy(policy));
            else if (facts.known()) need = need.completed(binding.id());
        }
        if (need.active() && area.type().foodProduction()) {
            for (var demand : this.demands) if (demand.kind() == WorkSupplyDemand.Kind.FOOD && protectedShortage(demand.supply()))
                need = need.merge(new Need(true, true, area.priority(), demand.supply().id()));
        }
        return need;
    }

    private Need order(UUID area, UUID order) {
        var managed = this.town.managedSupplyOrder(order);
        var need = managed == null ? new Need(true, false, SettlementPolicyExecution.priority(this.town, area, order), order)
                : policy(managed.policy());
        for (var demand : this.demands) if (demand.consumer().equals(order) && protectedShortage(demand.supply()))
            need = need.merge(new Need(true, true, need.priority(), demand.supply().id()));
        return need;
    }

    private Need policy(SettlementSupplyPolicy policy) {
        int priority = SettlementPolicyService.priority(this.town, policy);
        var target = policy.configuration().target();
        UUID consumer = target.kind() == SettlementSupplyPolicy.TargetKind.STOCK ? policy.id() : target.consumer();
        var need = new Need(true, false, priority, policy.id());
        for (var demand : this.demands) {
            if (!demand.consumer().equals(consumer) || target.requirement() != null && !target.requirement().equals(demand.supply().id())
                    || target.kind() == SettlementSupplyPolicy.TargetKind.EQUIPMENT && demand.kind() != WorkSupplyDemand.Kind.EQUIPMENT) continue;
            var supply = demand.supply();
            if (supply.known() && supply.missing() > 0)
                need = need.merge(new Need(true, protectedShortage(supply), priority, supply.id()));
        }
        return need;
    }

    private static boolean protectedShortage(WorkSupplyDemand demand) {
        return demand.known() && demand.protectedMinimum() > demand.supplied() + demand.incoming();
    }

    record Need(boolean active, boolean protectedShortage, int priority, UUID demand) {
        static final Need NONE = new Need(false, false, 0, null);

        Need merge(Need other) {
            if (!this.active || other.protectedShortage && !this.protectedShortage
                    || other.protectedShortage == this.protectedShortage && (other.priority > this.priority
                    || other.priority == this.priority && other.demand != null && (this.demand == null || other.demand.compareTo(this.demand) < 0)))
                return other;
            return this;
        }

        Need completed(UUID consumer) {
            return this.active || this.demand != null ? this : new Need(false, false, this.priority, consumer);
        }
    }

    static Urgency urgency(SettlementRecord town, SettlementWorkerPool.Staffing staffing, Need need, int current) {
        var plan = town.worksitePlans().get(staffing.area());
        int desired = need.active() ? plan == null ? 1 : plan.desiredStaff() : 0;
        int target = Math.clamp(desired, staffing.minimum(), staffing.maximum());
        int missing = Math.max(0, target - current);
        return missing == 0 ? Urgency.NONE : new Urgency(need.protectedShortage(), need.priority(), missing);
    }

    record Urgency(boolean protectedShortage, int priority, int missing) implements Comparable<Urgency> {
        static final Urgency NONE = new Urgency(false, 0, 0);

        @Override
        public int compareTo(Urgency other) {
            if (this.missing == 0 || other.missing == 0) return Boolean.compare(this.missing > 0, other.missing > 0);
            int comparison = Boolean.compare(this.protectedShortage, other.protectedShortage);
            if (comparison == 0) comparison = Integer.compare(this.priority, other.priority);
            return comparison != 0 ? comparison : Integer.compare(this.missing, other.missing);
        }
    }
}
