package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementBackgroundService {
    private SettlementBackgroundService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var jobs = new java.util.ArrayList<Runnable>();
        schedule(level, town, now, jobs);
        var work = new SettlementWork(jobs);
        while (!SettlementWork.complete(work)) work.run();
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        SettlementFurnaceService.schedule(level, town, now, jobs);
        SettlementBrewingStandService.schedule(level, town, now, jobs);
        SettlementFieldService.schedule(level, town, now, jobs);
        jobs.add(() -> SettlementFisheryService.update(level, town));
        jobs.add(() -> SettlementForestService.update(level, town, now));
        jobs.add(() -> SettlementMineService.update(level, town, now));
        jobs.add(() -> SettlementEntityDeathService.update(level, town, now));
        jobs.add(() -> SettlementHuntingTargetService.update(level, town, now));
        jobs.add(() -> SettlementLivestockService.update(level, town, now));
        jobs.add(() -> SettlementGroundItemService.update(level, town, now));
        jobs.add(SettlementWork.each(List.copyOf(town.workers().values()),
                worker -> SettlementBackgroundFishing.migrateExperience(level, town, worker)));
        jobs.add(() -> SettlementGroundExperienceService.update(level, town, now));
        jobs.add(() -> SettlementConstructionSiteService.update(level, town));
        jobs.add(town::maintainProjects);
        for (var depot : town.depots().values()) jobs.add(() -> SettlementDepotService.observeRules(level, depot));
        for (var resident : town.duties().keySet()) jobs.add(() -> updateWorker(level, town, resident, now));
        jobs.add(() -> SettlementProjectileService.update(level, town, now));
    }

    private static void updateWorker(ServerLevel level, SettlementRecord town, java.util.UUID resident, long now) {
        var assignedArea = town.duties().get(resident);
        var worker = town.workers().get(resident);
        var entity = level.getEntity(resident);
        if (entity instanceof Villager villager && level.isPositionEntityTicking(villager.blockPosition())) {
            observe(level, villager, level.getGameTime());
            return;
        }
        if (worker == null) {
            return;
        }
        int elapsed = worker.elapsed(now);
        if (elapsed <= 0) {
            return;
        }
        var area = town.workplace(assignedArea);
        var care = town.residents().get(worker.id());
        var settings = town.effectiveWorkerSettings(worker.id());
        if (area != null && settings != null) {
            settings.apply(worker.settings(), area.enabled());
            SettlementWorksiteService.links(town, area.id(), worker.id()).apply(worker.settings());
        }
        if (area == null || !area.enabled() || !area.id().equals(worker.area()) || area.type().role() != worker.role()
                || care == null || !care.availableForWork()
                || worker.settings().contains("Enabled") && !worker.settings().getBoolean("Enabled")
                || !SettlementWorkforceService.workingHours(level.getDayTime(), worker.id(), worker.role())) {
            worker.status("resting");
            return;
        }
        if (worker.unsafe()) {
            worker.status("unsafe");
            return;
        }
        if (worker.settings().contains(com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue.STATE_TAG)
                || worker.settings().contains(com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue.PENDING_TAG)) {
            worker.status("temporary_task_unloaded");
            return;
        }
        double multiplier = care.workMultiplier() * (town.housingCapacity() < town.residents().size() ? 0.75D : 1.0D);
        if (SettlementTransferService.pending(town, worker.id()) != null) {
            SettlementBackgroundTransfers.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.COURIER) {
            SettlementBackgroundHauling.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.SMELTER) {
            SettlementBackgroundSmelting.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.FISHING) {
            SettlementBackgroundFishing.tick(level, town, worker, elapsed, multiplier, now);
        } else if (worker.role() == HiredVillagerRole.HUNTING) {
            SettlementBackgroundHunting.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.ANIMAL_HANDLING) {
            SettlementBackgroundAnimalHandling.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.BUILDER) {
            SettlementBackgroundConstruction.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.LOGGING) {
            SettlementBackgroundLogging.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.MINING) {
            SettlementBackgroundMining.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.FARMING) {
            SettlementBackgroundFarming.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.BREWING) {
            SettlementBackgroundBrewing.tick(level, town, worker, elapsed, multiplier);
        } else if (worker.role() == HiredVillagerRole.CRAFTSMAN
                || worker.role() == HiredVillagerRole.COOK && !SettlementBackgroundSmelting.tick(level, town, worker, elapsed, multiplier)) {
            SettlementBackgroundCrafting.tick(level, town, worker, elapsed, multiplier);
        }
        town.changed();
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
            if (!java.util.Objects.equals(town.duties().get(villager.getUUID()),
                    com.jvn.villagerretaliation.interaction.VillagerAssignmentStore.civicArea(villager).orElse(null))) {
                SettlementWorkforceService.reconcile(level, villager);
            }
            if (worker != null) {
                SettlementBackgroundFishing.finishReturnedCatch(level, villager);
                SettlementBackgroundFishing.migrateExperience(level, town, worker);
            }
            SettlementWorkforceService.duty(villager).ifPresent(duty ->
                    SettlementWorkerConfigurationService.apply(villager, town, duty.area()));
        });
        SettlementScheduleService.reconcile(villager);
        SettlementConstructionService.reconcile(villager);
        SettlementBrewingService.reconcile(villager);
    }

    public static void onUnloaded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        if (villager.getRemovalReason() == Entity.RemovalReason.KILLED || villager.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            com.jvn.villagerretaliation.interaction.work.production.HiredProductionStations.get(level).workerRemoved(villager.getUUID());
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
        SettlementConstructionService.reconcile(villager);
        SettlementConstructionService.observe(villager, com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager));
        var worker = duty.town().workers().get(villager.getUUID());
        if (worker == null) {
            duty.town().recordWorker(SettlementWorkerState.capture(level, villager, duty, now));
        } else {
            worker.restore(villager);
            worker.observe(level, villager, duty, now);
            duty.town().changed();
        }
        SettlementLivestockService.observeWorker(level, duty.town(), duty.town().workers().get(villager.getUUID()));
    }
}
