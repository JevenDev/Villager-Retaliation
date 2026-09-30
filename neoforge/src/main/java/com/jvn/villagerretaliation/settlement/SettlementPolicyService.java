package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

public final class SettlementPolicyService {
    private SettlementPolicyService() { }

    static Runnable cycle(ServerLevel level, SettlementRecord town) {
        var policies = town.supplyPolicies().values().stream()
                .sorted(Comparator.comparing((SettlementSupplyPolicy policy) -> policy.configuration().target().kind()
                        != SettlementSupplyPolicy.TargetKind.FOOD)
                        .thenComparing(Comparator.comparingInt((SettlementSupplyPolicy policy) -> priority(town, policy)).reversed())
                        .thenComparing(SettlementSupplyPolicy::id)).toList();
        var work = new ArrayList<Runnable>();
        var quantities = new HashMap<Output, Long>();
        var claimed = new HashSet<UUID>();
        var snapshots = new ArrayList<SettlementDemandService.Snapshot>(1);
        work.add(SettlementWork.each(policies, policy -> {
            SettlementPolicyOrders.retire(level, town, policy);
            if (policy.state() == SettlementSupplyPolicy.State.REMOVING && policy.bindings().isEmpty()) town.removeSupplyPolicy(policy.id());
            else if (policy.state() == SettlementSupplyPolicy.State.ACTIVE && !SettlementPolicyExecution.authorized(town, policy))
                suspend(level, town, policy, "policy_permission_changed");
            else if (policy.state() != SettlementSupplyPolicy.State.ACTIVE) SettlementPolicyOrders.stop(level, town, policy);
        }));
        work.add(SettlementWork.lazy(() -> SettlementDemandService.plan(level, town, snapshots::add)));
        for (var policy : policies) work.add(SettlementWork.lazy(() -> {
            if (town.supplyPolicies().get(policy.id()) != policy || policy.state() != SettlementSupplyPolicy.State.ACTIVE) return () -> {};
            var target = policy.configuration().target();
            var project = target.consumer() == null ? null : town.projects().get(target.consumer());
            boolean produced = target.consumer() != null && town.productionQueues().values().stream()
                    .anyMatch(queue -> queue.order(target.consumer()) != null && queue.order(target.consumer()).complete());
            if (produced || project != null && project.status() == SettlementProject.Status.COMPLETED) {
                policy.state(SettlementSupplyPolicy.State.COMPLETE, policy.authority(), "", level.getGameTime());
                SettlementPolicyOrders.stop(level, town, policy);
                return () -> {};
            }
            String targetBlocker = SettlementPolicyValidation.target(level, town, policy);
            if (!targetBlocker.isEmpty()) {
                suspend(level, town, policy, targetBlocker);
                return () -> {};
            }
            var jobs = new ArrayList<Runnable>();
            var errors = new ArrayList<String>();
            var planned = new ArrayList<SettlementPolicyPlanner.Plan>(1);
            var localClaims = new HashSet<>(claimed);
            long revision = policy.revision();
            jobs.add(SettlementWork.each(policy.configuration().steps(), step -> {
                String blocker = SettlementPolicyValidation.step(level, town, policy, step);
                if (!blocker.isEmpty()) errors.add(blocker);
            }));
            jobs.add(SettlementWork.lazy(() -> errors.isEmpty()
                    ? SettlementPolicyPlanner.plan(level, town, policy, snapshots.getFirst(), localClaims, planned::add) : () -> {}));
            jobs.add(() -> {
                if (policy.revision() != revision || policy.state() != SettlementSupplyPolicy.State.ACTIVE) return;
                if (!errors.isEmpty()) suspend(level, town, policy, errors.getFirst());
                else if (!planned.getFirst().blocker().isEmpty()) suspend(level, town, policy, planned.getFirst().blocker());
                else {
                    claimed.addAll(localClaims);
                    policy.blocker("");
                }
            });
            jobs.add(SettlementWork.lazy(() -> {
                if (policy.revision() != revision || policy.state() != SettlementSupplyPolicy.State.ACTIVE || planned.isEmpty()) return () -> {};
                return SettlementWork.each(planned.getFirst().steps().reversed(), step -> {
                    if (policy.revision() != revision || policy.state() != SettlementSupplyPolicy.State.ACTIVE) return;
                    if (!SettlementPolicyExecution.authorized(town, policy)) {
                        suspend(level, town, policy, "policy_permission_changed");
                        return;
                    }
                    var snapshot = snapshots.getFirst();
                    if (!snapshot.current(policy.configuration().target().consumer())
                            || policy.bindings().stream().anyMatch(binding -> binding.order() != null && !snapshot.current(binding.order()))) {
                        policy.blocker("policy_rechecking");
                        SettlementPolicyOrders.stop(level, town, policy);
                        return;
                    }
                    String blocker = reconcile(level, town, policy, step, quantities);
                    if (blocker.equals("policy_order_suspended")) suspend(level, town, policy, blocker);
                    else if (!blocker.isEmpty()) policy.blocker(blocker);
                    var binding = SettlementPolicyOrders.current(policy, step.definition());
                    if (binding != null && binding.order() != null) {
                        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
                            var queue = town.productionQueues().get(binding.step().area());
                            if (queue != null && queue.order(binding.order()) != null) snapshot.acceptProduction(queue, queue.order(binding.order()));
                        } else {
                            var queue = town.transferQueues().get(binding.step().area());
                            if (queue != null && queue.order(binding.order()) != null) snapshot.acceptTransfer(queue, queue.order(binding.order()));
                        }
                    }
                });
            }));
            return new SettlementWork(jobs);
        }));
        return new SettlementWork(work);
    }

    private static String reconcile(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                                    SettlementPolicyPlanner.Step plan, Map<Output, Long> quantities) {
        var step = plan.definition();
        var binding = SettlementPolicyOrders.current(policy, step);
        var output = plan.output();
        var facts = SettlementSupplyStock.observe(level, town, step.destination(), output, null);
        if (!facts.known()) {
            SettlementPolicyOrders.stop(level, town, policy);
            return "output_storage_unavailable";
        }
        int capacity = SettlementPolicyValidation.capacity(level, town, step.destination(), output);
        if (capacity < 0) return "output_storage_unavailable";
        long reserved = AssignedStorageService.reservedItemsExcluding(level, step.destination(), java.util.Set.of(),
                VillagerFilterPolicy.TransferOperation.RECEIVE, stack -> ItemStack.isSameItemSameComponents(stack, output));
        long free = Math.max(0, capacity - Math.max(0, facts.incoming() - reserved));
        int batch = step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION ? output.getCount() : 1;
        long arrived = Math.max(0, facts.projected() - plan.projected());
        long completed = Math.max(0, SettlementPolicyOrders.progress(town, binding) - plan.progress());
        long needed = Math.max(0, plan.requested() - Math.max(arrived, completed));
        long requested = Math.min(needed, free / batch * batch);
        var key = new Output(SettlementSupplyStock.positions(level, step.destination()).getFirst(), output);
        long previous = quantities.getOrDefault(key, 0L);
        requested = Math.min(requested, Math.max(0, free - previous) / batch * batch);
        long outstanding = SettlementPolicyOrders.outstanding(town, binding);
        long retained = Math.min(outstanding, plan.retained() + Math.max(0, outstanding - plan.outstanding()));
        long quantity = Math.min(HiredProductionOrder.MAX_AMOUNT,
                facts.stored() + Math.max(0, facts.incoming() - outstanding) + retained + previous + requested);
        int target = requested == 0 && retained == 0 ? 0 : (int) Math.max(1, quantity);
        String blocker = SettlementPolicyOrders.reconcile(level, town, policy, step, target,
                (int) Math.min(HiredProductionOrder.MAX_AMOUNT, requested + retained));
        if (!blocker.isEmpty()) return blocker;
        quantities.put(key, previous + requested);
        return requested < needed ? "output_full" : "";
    }

    static int priority(SettlementRecord town, SettlementSupplyPolicy policy) {
        int priority = policy.configuration().priority();
        var consumer = policy.configuration().target().consumer();
        if (consumer == null) return priority;
        if (policy.configuration().target().kind() == SettlementSupplyPolicy.TargetKind.EQUIPMENT) {
            var area = town.areas().get(town.duties().getOrDefault(consumer, consumer));
            if (area != null) {
                var equipment = town.equipmentPlans().get(area.id());
                priority = Math.max(priority, equipment != null && equipment.authorized(town)
                        ? equipment.configuration().priority() : area.priority());
            }
        }
        var project = town.projects().get(consumer);
        if (project != null) return Math.max(priority, project.priority());
        for (var queue : town.productionQueues().values()) {
            if (queue.order(consumer) == null) continue;
            var area = town.areas().get(queue.owner());
            if (area != null) priority = Math.max(priority, area.priority());
        }
        return priority;
    }

    private static void suspend(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy, String blocker) {
        policy.state(SettlementSupplyPolicy.State.SUSPENDED, policy.authority(), blocker, level.getGameTime());
        SettlementPolicyOrders.stop(level, town, policy);
    }

    private record Output(BlockPos position, ItemStack sample) {
        Output { sample = sample.copyWithCount(1); }
        @Override public boolean equals(Object other) {
            return other instanceof Output output && position.equals(output.position) && ItemStack.isSameItemSameComponents(sample, output.sample);
        }
        @Override public int hashCode() { return 31 * position.hashCode() + ItemStack.hashItemAndComponents(sample); }
    }
}
