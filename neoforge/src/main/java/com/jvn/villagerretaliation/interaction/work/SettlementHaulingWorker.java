package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.settlement.SettlementDepot;
import com.jvn.villagerretaliation.settlement.SettlementLogisticsService;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class SettlementHaulingWorker {
    private SettlementHaulingWorker() {
    }

    static WorkResult tryTick(ServerLevel level, Villager villager, HiredWorkContext context) {
        if (!com.jvn.villagerretaliation.interaction.VillagerWorkAuthority.resolve(level, villager).civic()) return null;
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().type() != com.jvn.villagerretaliation.settlement.SettlementAreaType.DEPOT) return null;
        if (com.jvn.villagerretaliation.settlement.SettlementGuardService.configuredRoute(duty.town(), duty.area(), villager.getUUID()) != null) return null;
        return tick(level, villager, context, duty);
    }

    private static WorkResult tick(ServerLevel level, Villager villager, HiredWorkContext context,
                           SettlementWorkforceService.Duty duty) {
        var task = SettlementLogisticsService.prepare(level, villager, duty.town());
        if (task == null) {
            VillagerTaskNavigationUtil.stopHiredNavigation(villager);
            HiredWorkerBrain.setState(context, context.inventory().hasOutputItems()
                    ? HiredWorkerTaskState.PAUSED_STORAGE_FULL : HiredWorkerTaskState.WAITING_FOR_MATERIALS);
            return WorkResult.idle(context.inventory().hasOutputItems()
                    ? "interaction.work.hauling.no_space" : "interaction.work.hauling.no_demand");
        }
        List<ItemStack> cargo = cargo(context);
        boolean delivering = !cargo.isEmpty();
        SettlementDepot destination = duty.town().depots().get(delivering ? task.destination() : task.source());
        AssignedStorageService.reconcileCourierClaims(villager, cargo);
        int limit = HiredVillagerRoles.courierTransferLimit(context.aptitude());
        if (!delivering && AssignedStorageService.reserveCourierTransferClaims(villager, destination.position(), limit) <= 0) {
            SettlementLogisticsService.clear(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.WAITING_FOR_MATERIALS);
            return WorkResult.idle("interaction.work.hauling.no_demand");
        }
        HiredStorageNavigationGoal.Result movement = HiredStorageNavigationGoal.moveToStorageTarget(
                level, context, villager, destination.position(), 0.5D);
        if (movement == HiredStorageNavigationGoal.Result.MOVING) {
            HiredWorkerBrain.setStorageTarget(context, destination.position());
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.MOVING_TO_STORAGE, destination.position());
            return WorkResult.progressed(delivering
                    ? "interaction.work.courier.moving_to_output" : "interaction.work.courier.moving_to_input");
        }
        if (movement == HiredStorageNavigationGoal.Result.FAILED) {
            SettlementLogisticsService.failed(villager, destination.id());
            HiredStorageNavigationGoal.clearStorageTarget(context);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.FAILED_COOLDOWN, destination.position());
            return WorkResult.idle("interaction.work.hauling.unreachable");
        }
        HiredStorageNavigationGoal.clearStorageTarget(context);
        HiredWorkerBrain.setState(context, HiredWorkerTaskState.COLLECTING_OUTPUT, destination.position());
        if (!delivering) {
            AssignedStorageService.reconcileCourierClaims(villager, cargo);
            int moved = AssignedStorageService.transferCourierItemsAtAssignedStorage(
                    villager, destination.position(), limit, context.inventory()::insertOutput);
            if (moved <= 0) {
                SettlementLogisticsService.clear(villager);
                return WorkResult.idle("interaction.work.hauling.no_demand");
            }
            HiredWorkAnimation.useItem(level, villager, task.sample());
            return WorkResult.progressed("interaction.work.courier.collected_items");
        }
        int before = cargo.stream().mapToInt(ItemStack::getCount).sum();
        while (context.inventory().depositCourierOutputToAssignedStorageAt(destination.position(), task::matches)) {
        }
        int moved = before - cargo(context).stream().mapToInt(ItemStack::getCount).sum();
        if (moved <= 0) {
            SettlementLogisticsService.clear(villager);
            HiredWorkerBrain.setState(context, HiredWorkerTaskState.PAUSED_STORAGE_FULL);
            return WorkResult.idle("interaction.work.hauling.no_space");
        }
        SettlementDepot source = duty.town().depots().get(task.source());
        double distance = source == null ? 1.0D : Math.max(1.0D, Math.sqrt(source.position().distSqr(destination.position())));
        SettlementLogisticsService.clear(villager);
        HiredWorkAnimation.useItem(level, villager, task.sample());
        return WorkResult.completedWithPractice("interaction.work.courier.delivered_items", HiredWorkPractice.courier(moved, distance));
    }

    private static List<ItemStack> cargo(HiredWorkContext context) {
        return context.inventory().collectOutputItems().stream().map(HiredJobInventory.OutputStack::stack).toList();
    }
}
