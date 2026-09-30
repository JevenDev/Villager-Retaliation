package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import java.util.UUID;

public final class SettlementPolicyExecution {
    private SettlementPolicyExecution() { }

    static void configure(SettlementRecord town, HiredProductionQueue queue) {
        queue.executionPolicy(order -> mayStart(town, order), order -> priority(town, queue.owner(), order), order -> ceiling(town, order));
    }

    static void configure(SettlementRecord town, HiredTransferQueue queue) {
        queue.executionPolicy(order -> mayStart(town, order), order -> priority(town, queue.owner(), order), order -> ceiling(town, order));
    }

    static long ceiling(SettlementRecord town, UUID order) {
        var managed = town.managedSupplyOrder(order);
        if (managed == null) return Long.MAX_VALUE;
        return managed.policy().bindings().stream().filter(binding -> binding.id().equals(managed.binding()))
                .mapToLong(SettlementSupplyPolicy.Binding::ceiling).findFirst().orElse(0);
    }

    public static boolean authorized(SettlementRecord town, SettlementSupplyPolicy policy) {
        return town.permits(policy.authority(), SettlementPermission.STAFF)
                && town.permits(policy.authority(), SettlementPermission.LOGISTICS);
    }

    public static boolean mayStart(SettlementRecord town, UUID order) {
        var managed = town.managedSupplyOrder(order);
        if (managed == null) return true;
        var policy = managed.policy();
        var binding = policy.bindings().stream().filter(value -> value.id().equals(managed.binding())).findFirst().orElse(null);
        return town.territory().active() && policy.state() == SettlementSupplyPolicy.State.ACTIVE && !policy.blocker().equals("policy_rechecking") && authorized(town, policy)
                && binding != null && binding.state() == SettlementSupplyPolicy.BindingState.ACTIVE
                && policy.configuration().steps().contains(binding.step());
    }

    public static boolean acquisitionAllowed(SettlementRecord town, UUID worker, UUID consumer) {
        var order = consumer;
        var area = worker == null ? null : town.duties().get(worker);
        if (order == null && area != null) {
            var production = town.productionQueues().get(area);
            var allocation = production == null ? null : production.allocation(worker);
            if (allocation != null) order = allocation.order();
            else {
                var transfers = town.transferQueues().get(area);
                var transfer = transfers == null ? null : transfers.allocation(worker);
                if (transfer != null) order = transfer.order();
            }
        }
        if (order == null || mayStart(town, order)) return true;
        var managed = town.managedSupplyOrder(order);
        if (managed == null || worker == null) return false;
        var binding = managed.policy().bindings().stream().filter(value -> value.id().equals(managed.binding())).findFirst().orElse(null);
        var queue = binding == null ? null : town.productionQueues().get(binding.step().area());
        var allocation = queue == null ? null : queue.allocation(worker);
        return allocation != null && allocation.started() && allocation.order().equals(order);
    }

    static int priority(SettlementRecord town, UUID area, UUID order) {
        var managed = town.managedSupplyOrder(order);
        if (managed != null) return SettlementPolicyService.priority(town, managed.policy());
        var workplace = town.areas().get(area);
        return workplace == null ? 5 : workplace.priority();
    }
}
