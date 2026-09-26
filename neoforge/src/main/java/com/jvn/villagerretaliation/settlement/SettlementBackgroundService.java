package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementBackgroundService {
    private SettlementBackgroundService() {
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % 200L != 0L) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (SettlementRecord town : SettlementSavedData.get(level).settlements()) {
                if (town.dimension().equals(level.dimension().location())) {
                    update(level, town, now);
                }
            }
        }
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        SettlementFurnaceService.update(level, town, now);
        SettlementBrewingStandService.update(level, town, now);
        SettlementFieldService.update(level, town, now);
        SettlementFisheryService.update(level, town);
        SettlementForestService.update(level, town, now);
        SettlementMineService.update(level, town);
        for (SettlementDepot depot : town.depots().values()) {
            SettlementDepotService.observeRules(level, depot);
        }
        for (var assignment : List.copyOf(town.duties().entrySet())) {
            var worker = town.workers().get(assignment.getKey());
            var entity = level.getEntity(assignment.getKey());
            if (entity instanceof Villager villager && level.isPositionEntityTicking(villager.blockPosition())) {
                observe(level, villager, now);
                continue;
            }
            if (worker == null) {
                continue;
            }
            int elapsed = worker.elapsed(now);
            if (elapsed <= 0) {
                continue;
            }
            var area = town.areas().get(assignment.getValue());
            var care = town.residents().get(worker.id());
            var settings = town.workerSettings().get(worker.id());
            if (area != null && settings != null) {
                settings.apply(worker.settings(), area.enabled());
            }
            if (area == null || !area.enabled() || !area.id().equals(worker.area()) || area.type().role() != worker.role()
                    || care == null || !care.availableForWork()
                    || worker.settings().contains("Enabled") && !worker.settings().getBoolean("Enabled")
                    || !SettlementWorkforceService.workingHours(level.getDayTime(), worker.id(), worker.role())) {
                worker.status("resting");
                continue;
            }
            if (worker.unsafe()) {
                worker.status("unsafe");
                continue;
            }
            double multiplier = care.workMultiplier() * (town.housingCapacity() < town.residents().size() ? 0.75D : 1.0D);
            if (worker.role() == HiredVillagerRole.COURIER) {
                SettlementBackgroundHauling.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.SMELTER) {
                SettlementBackgroundSmelting.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.FISHING) {
                SettlementBackgroundFishing.tick(level, town, worker, elapsed, multiplier, now);
            } else if (worker.role() == HiredVillagerRole.LOGGING) {
                SettlementBackgroundLogging.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.MINING
                    && com.jvn.villagerretaliation.interaction.HiredMiningMode.fromState(worker.settings())
                    == com.jvn.villagerretaliation.interaction.HiredMiningMode.EXPOSED_ORES) {
                SettlementBackgroundMining.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.FARMING) {
                SettlementBackgroundFarming.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.BREWING) {
                SettlementBackgroundBrewing.tick(level, town, worker, elapsed, multiplier);
            } else if (worker.role() == HiredVillagerRole.CRAFTSMAN
                    || worker.role() == HiredVillagerRole.COOK && !SettlementBackgroundSmelting.tick(level, town, worker, elapsed, multiplier)) {
                SettlementBackgroundCrafting.tick(level, town, worker, elapsed, multiplier);
            }
        }
        if (!town.workers().isEmpty()) {
            town.changed();
        }
    }

    public static void onActive(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        SettlementWorkforceService.home(level, villager).ifPresent(town -> {
            var worker = town.workers().get(villager.getUUID());
            if (worker != null && worker.restore(villager)) {
                SettlementLogisticsService.clear(villager);
            }
            if (worker != null) {
                SettlementBackgroundFishing.finishReturnedCatch(level, villager);
                SettlementBackgroundFishing.returnExperience(level, villager, worker);
            }
            SettlementWorkforceService.duty(villager).ifPresent(duty ->
                    SettlementWorkerConfigurationService.apply(villager, town, duty.area()));
        });
        SettlementConstructionService.reconcile(villager);
        SettlementBrewingService.reconcile(villager);
    }

    public static void onUnloaded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (villager.getRemovalReason() == Entity.RemovalReason.KILLED || villager.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            SettlementWorkforceService.home(level, villager).ifPresent(town -> town.removeWorker(villager.getUUID()));
        } else {
            observe(level, villager, level.getGameTime());
        }
    }

    private static void observe(ServerLevel level, Villager villager, long now) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null) {
            return;
        }
        SettlementConstructionService.observe(villager, com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager));
        var worker = duty.town().workers().get(villager.getUUID());
        if (worker == null) {
            duty.town().recordWorker(SettlementWorkerState.capture(level, villager, duty, now));
        } else {
            worker.restore(villager);
            worker.observe(level, villager, duty, now);
            duty.town().changed();
        }
    }
}
