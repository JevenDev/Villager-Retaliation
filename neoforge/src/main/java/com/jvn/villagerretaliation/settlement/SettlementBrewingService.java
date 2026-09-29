package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementBrewingService {
    private static final String AREA_TAG = "SettlementBrewingArea";
    private static final String VERSION_TAG = "VillagerRetaliationSettlementBrewingVersion";

    private SettlementBrewingService() {
    }

    public static SettlementBrewingOrder snapshot(ServerLevel level, SettlementRecord town, UUID resident) {
        var area = town.areas().get(town.duties().get(resident));
        if (area == null || area.type() != SettlementAreaType.BREWERY) {
            return null;
        }
        var worker = town.workers().get(resident);
        if (level.getEntity(resident) instanceof Villager villager
                && (worker == null || worker.brewingVersion() <= version(villager))) {
            return SettlementBrewingOrder.read(HiredWorkStateStore.state(villager));
        }
        return worker != null && worker.area().equals(area.id()) && worker.role() == HiredVillagerRole.BREWING
                ? SettlementBrewingOrder.read(worker.settings()) : null;
    }

    public static boolean configure(ServerPlayer player, SettlementRecord town, UUID resident, UUID areaId,
                                    SettlementBrewingOrder order) {
        if (player == null || town == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || !areaId.equals(town.duties().get(resident)) || town.productionQueues().containsKey(areaId)) {
            return false;
        }
        var area = town.areas().get(areaId);
        var level = player.serverLevel();
        if (area == null || area.type() != SettlementAreaType.BREWERY
                || order != null && HiredBrewingRecipeCatalog.find(level, order.item(), order.potion()).isEmpty()) {
            return false;
        }
        Villager villager = level.getEntity(resident) instanceof Villager loaded ? loaded : null;
        if (villager != null) {
            if (!villager.isAlive() || villager.isBaby() || SettlementWorkforceService.hasPersonalCommitment(villager)
                    || SettlementWorkforceService.duty(villager).filter(duty -> duty.town() == town && duty.area().id().equals(areaId)).isEmpty()) {
                return false;
            }
            SettlementBackgroundService.onActive(villager);
            SettlementBackgroundService.onUnloaded(villager);
        }
        var worker = town.workers().get(resident);
        if (worker == null || !worker.area().equals(areaId) || worker.role() != HiredVillagerRole.BREWING) {
            return false;
        }
        worker.brewingOrder(order);
        town.changed();
        if (villager != null) {
            restore(worker, villager);
            HiredWorkStateStore.pauseWork(level, villager, HiredVillagerRole.BREWING,
                    "interaction.work.status.waiting_tick", Map.of());
            SettlementBackgroundService.onUnloaded(villager);
        }
        return true;
    }

    static long version(Villager villager) {
        return villager.getPersistentData().getLong(VERSION_TAG);
    }

    static void restore(SettlementWorkerState worker, Villager villager) {
        if (worker.role() != HiredVillagerRole.BREWING || version(villager) >= worker.brewingVersion()
                || SettlementWorkforceService.hasPersonalCommitment(villager)
                || SettlementWorkforceService.duty(villager).filter(duty -> duty.area().id().equals(worker.area())
                        && duty.area().type() == SettlementAreaType.BREWERY).isEmpty()) {
            return;
        }
        var state = HiredWorkStateStore.state(villager);
        BrewingWorker.clearOrder(state);
        var order = SettlementBrewingOrder.read(worker.settings());
        if (order != null) {
            order.apply(state);
        }
        state.putUUID(AREA_TAG, worker.area());
        state.remove("NextWorkGameTime");
        villager.getPersistentData().putLong(VERSION_TAG, worker.brewingVersion());
        HiredWorkSession.invalidate(villager);
    }

    static void reconcile(Villager villager) {
        var state = HiredWorkStateStore.state(villager);
        if (state.hasUUID(AREA_TAG) && (SettlementWorkforceService.hasPersonalCommitment(villager)
                || SettlementWorkforceService.duty(villager).filter(duty -> duty.area().id().equals(state.getUUID(AREA_TAG))
                        && duty.area().type() == SettlementAreaType.BREWERY).isEmpty())) {
            if (!state.hasUUID("BrewingOrderContractId")) {
                BrewingWorker.clearOrder(state);
            }
            state.remove(AREA_TAG);
            HiredWorkSession.invalidate(villager);
        }
    }
}
