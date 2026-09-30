package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementPolicyOrders {
    private SettlementPolicyOrders() { }

    static SettlementSupplyPolicy.Binding current(SettlementSupplyPolicy policy, SettlementSupplyPolicy.Step step) {
        return policy.bindings().stream().filter(binding -> binding.state() != SettlementSupplyPolicy.BindingState.DRAINING
                && binding.step().equals(step)).findFirst().orElse(null);
    }

    static String reconcile(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                            SettlementSupplyPolicy.Step step, int target) {
        return reconcile(level, town, policy, step, target, target);
    }

    static String reconcile(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy,
                            SettlementSupplyPolicy.Step step, int target, int allowance) {
        var binding = current(policy, step);
        if (binding != null && binding.state() == SettlementSupplyPolicy.BindingState.SUSPENDED) return "policy_order_suspended";
        if (binding != null && stopped(town, binding)) {
            policy.binding(binding.execution(SettlementSupplyPolicy.BindingState.SUSPENDED, binding.target(), binding.ceiling()));
            policy.event(level.getGameTime(), SettlementSupplyPolicy.EventKind.SUSPENDED, "policy_order_suspended");
            return "policy_order_suspended";
        }
        if (target < 0 || target > HiredProductionOrder.MAX_AMOUNT || allowance < 0 || allowance > HiredProductionOrder.MAX_AMOUNT)
            return "policy_target_limit";
        if (binding == null) {
            if (target == 0 && step.kind() != SettlementSupplyPolicy.StepKind.GATHER) return "";
            if (policy.bindings().size() >= SettlementSupplyPolicy.MAX_BINDINGS) return "policy_binding_limit";
            UUID order = null;
            if (step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
                var queue = town.productionQueues().get(step.area());
                if (queue == null) queue = town.beginProduction(policy.authority(), step.area());
                if (queue == null) return "policy_worksite_unavailable";
                var requested = HiredProductionOrder.create(level, step.recipe(), HiredProductionOrder.Mode.MAINTAIN_STOCK, target, step.destination());
                if (!queue.append(requested)) return "policy_queue_full";
                order = requested.id();
            } else if (step.kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
                var queue = town.transferQueues().get(step.area());
                if (queue == null) queue = town.beginTransfers(policy.authority(), step.area());
                if (queue == null) return "policy_worksite_unavailable";
                var requested = delivery(level, town, step, target);
                if (!queue.append(requested)) return "policy_queue_full";
                order = requested.id();
            }
            binding = new SettlementSupplyPolicy.Binding(UUID.randomUUID(), step, order, SettlementSupplyPolicy.BindingState.ACTIVE, target);
            if (!policy.bind(binding)) throw new IllegalStateException("Validated managed binding could not be installed");
        }
        if (binding.target() > target || target == 0 || outstanding(town, binding) > allowance) releaseUnstarted(level, town, binding);
        if (step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            var queue = town.productionQueues().get(step.area());
            if (target > 0 && !queue.maintainTarget(binding.order(), target)) return "policy_order_suspended";
            if (!queue.pause(binding.order(), target == 0)) return "policy_order_suspended";
        } else if (step.kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
            var queue = town.transferQueues().get(step.area());
            if (target > 0 && !queue.maintainTarget(binding.order(), target)) return "policy_order_suspended";
            var order = queue.order(binding.order());
            if (order.paused() != (target == 0) && !queue.edit(binding.order(),
                    target == 0 ? HiredTransferQueue.Edit.PAUSE : HiredTransferQueue.Edit.RESUME, 0)) return "policy_order_suspended";
        }
        int batch = step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION ? step.recipe().output(level).getCount() : 1;
        long rounded = allowance == 0 ? 0 : ((allowance - 1L) / batch + 1) * batch;
        long progress = progress(town, binding);
        long ceiling = progress + Math.min(Long.MAX_VALUE - progress, rounded);
        policy.binding(binding.execution(target == 0 ? SettlementSupplyPolicy.BindingState.PARKED : SettlementSupplyPolicy.BindingState.ACTIVE,
                target, ceiling));
        return "";
    }

    static void rearm(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        for (var binding : policy.bindings()) {
            releaseUnstarted(level, town, binding);
            if (binding.state() == SettlementSupplyPolicy.BindingState.DRAINING
                    || binding.state() == SettlementSupplyPolicy.BindingState.SUSPENDED || stopped(town, binding)
                    || !policy.configuration().steps().contains(binding.step())) {
                policy.binding(binding.execution(SettlementSupplyPolicy.BindingState.DRAINING, 0, binding.ceiling()));
                continue;
            }
            policy.binding(binding.execution(SettlementSupplyPolicy.BindingState.PARKED, 0, progress(town, binding)));
            if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION)
                town.productionQueues().get(binding.step().area()).pause(binding.order(), true);
            else if (binding.step().kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
                var queue = town.transferQueues().get(binding.step().area());
                if (!queue.order(binding.order()).paused()) queue.edit(binding.order(), HiredTransferQueue.Edit.PAUSE, 0);
            }
        }
    }

    static void retire(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        for (var binding : policy.bindings()) {
            if (binding.state() != SettlementSupplyPolicy.BindingState.DRAINING && policy.state() != SettlementSupplyPolicy.State.REMOVING
                    && policy.configuration().steps().contains(binding.step())) continue;
            releaseUnstarted(level, town, binding);
            policy.binding(binding.execution(SettlementSupplyPolicy.BindingState.DRAINING, 0, binding.ceiling()));
            if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
                var queue = town.productionQueues().get(binding.step().area());
                if (queue != null && queue.order(binding.order()) != null) {
                    queue.pause(binding.order(), true);
                    if (!queue.remove(binding.order())) continue;
                }
            } else if (binding.step().kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
                var queue = town.transferQueues().get(binding.step().area());
                if (queue != null && queue.order(binding.order()) != null) {
                    if (!queue.order(binding.order()).paused()) queue.edit(binding.order(), HiredTransferQueue.Edit.PAUSE, 0);
                    if (!queue.edit(binding.order(), HiredTransferQueue.Edit.REMOVE, 0)) continue;
                }
            } else if (gatheringCargo(level, town, binding.step())) continue;
            policy.removeBinding(binding.id());
        }
    }

    static void stop(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        for (var binding : policy.bindings()) releaseUnstarted(level, town, binding);
    }

    static long progress(SettlementRecord town, SettlementSupplyPolicy.Binding binding) {
        if (binding == null) return 0;
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.GATHER) return binding.gathered();
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            var queue = town.productionQueues().get(binding.step().area());
            var order = queue == null ? null : queue.order(binding.order());
            return order == null ? 0 : order.produced();
        }
        var queue = town.transferQueues().get(binding.step().area());
        var order = queue == null ? null : queue.order(binding.order());
        return order == null ? 0 : order.picked();
    }

    static long outstanding(SettlementRecord town, SettlementSupplyPolicy.Binding binding) {
        if (binding == null || binding.order() == null) return 0;
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            var queue = town.productionQueues().get(binding.step().area());
            return queue == null ? 0 : queue.pending(binding.order());
        }
        var queue = town.transferQueues().get(binding.step().area());
        return queue == null ? 0 : queue.allocations().stream().filter(allocation -> allocation.order().equals(binding.order()))
                .mapToLong(HiredTransferQueue.Allocation::amount).sum();
    }

    static HiredTransferQueue.Order delivery(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy.Step step, int target) {
        return HiredTransferQueue.Order.create(endpoint(level, town, step.source()), endpoint(level, town, step.destination()),
                step.sample(), target, HiredTransferQueue.Mode.MAINTAIN_STOCK);
    }

    private static HiredTransferQueue.Endpoint endpoint(ServerLevel level, SettlementRecord town, BlockPos position) {
        var depot = town.depots().values().stream().filter(value -> value.position().equals(position)).findFirst().orElse(null);
        return new HiredTransferQueue.Endpoint(position, depot == null ? null : depot.id());
    }

    private static boolean stopped(SettlementRecord town, SettlementSupplyPolicy.Binding binding) {
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.GATHER) return false;
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            var queue = town.productionQueues().get(binding.step().area());
            var order = queue == null ? null : queue.order(binding.order());
            return order == null || order.cancelled() || order.mode() != HiredProductionOrder.Mode.MAINTAIN_STOCK
                    || !order.recipe().equals(binding.step().recipe()) || !binding.step().destination().equals(order.storage())
                    || order.paused() && binding.state() == SettlementSupplyPolicy.BindingState.ACTIVE;
        }
        var queue = town.transferQueues().get(binding.step().area());
        var order = queue == null ? null : queue.order(binding.order());
        return order == null || order.cancelled() || order.mode() != HiredTransferQueue.Mode.MAINTAIN_STOCK
                || !ItemStack.isSameItemSameComponents(order.sample(), binding.step().sample())
                || !binding.step().source().equals(order.source().position()) || !binding.step().destination().equals(order.destination().position())
                || order.paused() && binding.state() == SettlementSupplyPolicy.BindingState.ACTIVE;
    }

    private static void releaseUnstarted(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy.Binding binding) {
        if (binding.step().kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            var queue = town.productionQueues().get(binding.step().area());
            if (queue == null) return;
            for (var allocation : queue.allocations()) if (allocation.order().equals(binding.order()) && !allocation.started()) {
                HiredProductionStations.get(level).release(allocation.id());
                queue.release(allocation.id());
                if (allocation.worker() != null) AssignedStorageService.releaseProductionInputs(allocation.worker());
            }
        } else if (binding.step().kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
            var queue = town.transferQueues().get(binding.step().area());
            if (queue == null) return;
            for (var allocation : queue.allocations()) if (allocation.order().equals(binding.order()) && !allocation.started()) {
                queue.releasePlanned(allocation.worker());
                AssignedStorageService.releaseCourierClaims(allocation.worker());
            }
        }
    }

    private static boolean gatheringCargo(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy.Step step) {
        for (var entry : town.duties().entrySet()) {
            if (!entry.getValue().equals(step.area())) continue;
            if (level.getEntity(entry.getKey()) instanceof net.minecraft.world.entity.npc.Villager villager) {
                if (com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager).hasOutputItems()
                        || SettlementGatheringPolicy.busy(com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager))) return true;
            } else {
                var worker = town.workers().get(entry.getKey());
                if (worker != null) {
                    if (SettlementGatheringPolicy.busy(worker.settings())) return true;
                    var items = worker.items(level.registryAccess());
                    for (int slot : worker.outputSlots()) if (!items.get(slot).isEmpty()) return true;
                }
            }
        }
        return false;
    }
}
