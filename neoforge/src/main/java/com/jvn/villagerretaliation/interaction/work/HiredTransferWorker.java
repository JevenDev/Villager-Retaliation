package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.settlement.SettlementLogisticsService;
import com.jvn.villagerretaliation.settlement.SettlementTransferService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class HiredTransferWorker {
    private HiredTransferWorker() {
    }

    static WorkResult tick(ServerLevel level, Villager villager, HiredWorkContext context) {
        var access = HiredTransferService.access(level, villager);
        if (!access.configured()) return null;
        if (access.queue() == null) return blocked(level, villager, context, access.blocker());
        var queue = access.queue();
        var allocation = queue.allocation(villager.getUUID());
        if (allocation == null && context.inventory().hasOutputItems()) return null;
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (allocation == null) {
            for (var order : queue.orders()) {
                if (queue.available(order.id()) <= 0) continue;
                if (context.state().getCompound("TransferRetryAfter").getLong(order.id().toString()) > level.getGameTime()) continue;
                String invalid = validate(level, villager, queue, order, false);
                if (!invalid.isEmpty()) {
                    queue.blocker(order.id(), invalid);
                    continue;
                }
                allocation = queue.reserve(villager.getUUID(), order.id(), HiredVillagerRoles.courierTransferLimit(context.aptitude()));
                if (allocation == null) continue;
                bind(level, villager, order);
                AssignedStorageService.releaseCourierClaims(villager);
                int count = AssignedStorageService.reserveCourierTransferClaims(villager, order.source().position(), allocation.amount());
                if (count > 0) {
                    queue.resizePlanned(allocation.id(), count);
                    allocation = queue.allocation(villager.getUUID());
                    queue.blocker(order.id(), "");
                    break;
                }
                queue.releasePlanned(villager.getUUID());
                AssignedStorageService.releaseCourierClaims(villager);
                allocation = null;
                queue.blocker(order.id(), "transfer_supply_or_capacity");
            }
        }
        if (allocation == null) {
            if (duty != null) {
                SettlementLogisticsService.clear(villager);
                return null;
            }
            String reason = queue.orders().stream().filter(HiredTransferQueue.Order::runnable)
                    .map(HiredTransferQueue.Order::blocker).filter(value -> !value.isEmpty()).findFirst().orElse("");
            if (!reason.isEmpty()) return blocked(level, villager, context, reason);
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.clearFailure(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
            return WorkResult.idle("interaction.work.transfer.waiting");
        }
        var order = queue.order(allocation.order());
        String invalid = validate(level, villager, queue, order, allocation.started());
        if (!invalid.isEmpty()) return blocked(level, villager, context, queue, order, invalid);
        bind(level, villager, order);
        if (allocation.started()) {
            if (cargo(context, order) < allocation.amount()) {
                return blocked(level, villager, context, queue, order, "transfer_cargo_missing");
            }
            AssignedStorageService.reconcileCourierClaims(villager, context.inventory().collectOutputItems().stream()
                    .map(output -> output.stack()).filter(order::matches).toList());
        } else {
            AssignedStorageService.releaseCourierClaims(villager);
            int count = AssignedStorageService.reserveCourierTransferClaims(villager, order.source().position(), allocation.amount());
            if (count <= 0) return blocked(level, villager, context, queue, order, "transfer_supply_or_capacity");
            queue.resizePlanned(allocation.id(), count);
            allocation = queue.allocation(villager.getUUID());
        }
        var position = (allocation.started() ? order.destination() : order.source()).position();
        var movement = HiredStorageNavigationGoal.moveToStorageTarget(level, context, villager, position, 0.5D);
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            return blocked(level, villager, context, queue, order, "transfer_unreachable");
        }
        HiredWorkerBrain.clearFailure(context);
        queue.blocker(order.id(), "");
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.setStorageTarget(context, position);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, position);
            return WorkResult.progressed(allocation.started()
                    ? "interaction.work.courier.moving_to_output" : "interaction.work.courier.moving_to_input");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, position);
        if (!allocation.started()) {
            AssignedStorageService.reconcileCourierClaims(villager, java.util.List.of());
            int moved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                    villager, position, allocation.amount(), context.inventory()::insertOutput);
            if (moved <= 0) return blocked(level, villager, context, queue, order, "transfer_supply_or_capacity");
            queue.picked(allocation.id(), moved);
            HiredWorkAnimation.useItem(level, villager, order.sample());
            return WorkResult.progressed("interaction.work.courier.collected_items");
        }
        int moved = 0;
        while ((allocation = queue.allocation(villager.getUUID())) != null && allocation.started()) {
            int before = cargo(context, order);
            if (!context.inventory().depositCourierOutputToAssignedStorageAt(position, order::matches)) break;
            int delivered = before - cargo(context, order);
            if (delivered <= 0) break;
            queue.delivered(allocation.id(), delivered);
            moved += delivered;
        }
        if (moved <= 0) return blocked(level, villager, context, queue, order, "transfer_destination_full");
        AssignedStorageService.reconcileCourierClaims(villager, context.inventory().collectOutputItems().stream()
                .map(output -> output.stack()).filter(order::matches).toList());
        if (queue.allocation(villager.getUUID()) == null && duty != null) {
            SettlementLogisticsService.clear(villager);
            com.jvn.villagerretaliation.settlement.SettlementDepotService.synchronizeStorage(level, villager, duty.town());
        }
        HiredWorkAnimation.useItem(level, villager, order.sample());
        return WorkResult.completedWithPractice("interaction.work.courier.delivered_items",
                HiredWorkPractice.courier(moved, Math.sqrt(order.source().position().distSqr(order.destination().position()))));
    }

    private static String validate(ServerLevel level, Villager villager, HiredTransferQueue queue, HiredTransferQueue.Order order, boolean delivering) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        boolean permitted;
        if (duty == null) {
            permitted = HiredTransferService.personalEndpoint(level, villager, order.destination(), false)
                    && (delivering || HiredTransferService.personalEndpoint(level, villager, order.source(), true));
        } else if (delivering) {
            permitted = SettlementTransferService.destination(duty.town(), duty.area().id(), order)
                    && SettlementTransferService.destination(duty.town(), queue.owner(), order);
        } else permitted = SettlementTransferService.endpoints(duty.town(), duty.area().id(), order);
        if (!permitted) return "transfer_storage_changed";
        return !delivering && !level.hasChunkAt(order.source().position()) || !level.hasChunkAt(order.destination().position())
                ? "transfer_storage_unloaded" : "";
    }

    private static void bind(ServerLevel level, Villager villager, HiredTransferQueue.Order order) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) SettlementTransferService.bind(level, villager, duty.town(), order);
    }

    private static int cargo(HiredWorkContext context, HiredTransferQueue.Order order) {
        return context.inventory().collectOutputItems().stream().map(output -> output.stack())
                .filter(order::matches).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
    }

    private static WorkResult blocked(ServerLevel level, Villager villager, HiredWorkContext context,
                                      HiredTransferQueue queue, HiredTransferQueue.Order order, String reason) {
        queue.blocker(order.id(), reason);
        var allocation = queue.allocation(villager.getUUID());
        if (allocation != null && !allocation.started()) {
            var retry = new net.minecraft.nbt.CompoundTag();
            var previous = context.state().getCompound("TransferRetryAfter");
            for (var pending : queue.orders()) {
                long time = previous.getLong(pending.id().toString());
                if (time > level.getGameTime()) retry.putLong(pending.id().toString(), time);
            }
            retry.putLong(order.id().toString(), level.getGameTime() + 100L);
            context.state().put("TransferRetryAfter", retry);
        }
        queue.releasePlanned(villager.getUUID());
        return blocked(level, villager, context, reason);
    }

    private static WorkResult blocked(ServerLevel level, Villager villager, HiredWorkContext context, String reason) {
        AssignedStorageService.releaseCourierClaims(villager);
        VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.setFailure(context, reason, level.getGameTime() + 100L);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.AWAITING_INSTRUCTION, null);
        return WorkResult.idle("interaction.work.transfer.blocked");
    }
}
