package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementFarmJobService {
    private SettlementFarmJobService() {}

    private static Villager loaded(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof Villager villager && !villager.isRemoved() ? villager : null;
    }

    private static boolean authorized(SettlementRecord town, SettlementFarmJob job) {
        return town.permits(job.issuer(), SettlementPermission.BUILD) && town.permits(job.issuer(), SettlementPermission.STAFF);
    }

    public static String blocker(Villager villager, CompoundTag state) {
        var job = find(villager, state);
        if (job == null) return "";
        var town = SettlementWorkforceService.home((ServerLevel) villager.level(), villager).orElseThrow();
        if (!job.unfinished() || job.status() != SettlementProject.Status.ACTIVE || !job.worker().equals(villager.getUUID())
                || !job.area().equals(town.duties().get(villager.getUUID())) || !authorized(town, job))
            return "interaction.work.farmland.invalid";
        return job.paused() ? "interaction.work.farmland.paused" : "";
    }

    private static SettlementFarmJob find(Villager villager, CompoundTag state) {
        var work = state.getCompound(SettlementFarmWork.TAG);
        return !work.hasUUID("Task") ? null : SettlementWorkforceService.home((ServerLevel) villager.level(), villager)
                .map(town -> town.farmJobs().get(work.getUUID("Task"))).orElse(null);
    }

    public static void reconcile(Villager villager) {
        var state = HiredWorkStateStore.state(villager);
        if (!HiredTemporaryWorkService.active(state, HiredTemporaryTask.Kind.PREPARE_FARM)) return;
        var job = find(villager, state);
        if (job == null) return;
        var town = SettlementWorkforceService.home((ServerLevel) villager.level(), villager).orElseThrow();
        if (job.unfinished() && job.status() == SettlementProject.Status.ACTIVE && job.worker().equals(villager.getUUID())
                && job.area().equals(town.duties().get(villager.getUUID())) && authorized(town, job)) {
            var queue = HiredTemporaryWorkService.queue(state);
            if (!queue.owner().equals(town.id().value()) && queue.current().id().equals(job.id())
                    && com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get((ServerLevel) villager.level())
                            .canonical(new com.jvn.villagerretaliation.allegiance.VillageAllegianceId(queue.owner())).filter(town.id()::equals).isPresent()) {
                new HiredTemporaryQueue(town.id().value(), queue.issuer(), queue.worksite(), queue.role(),
                        queue.homeMin(), queue.homeMax(), queue.tasks()).store(state);
                HiredWorkSession.invalidate(villager);
            }
            return;
        }
        HiredTemporaryWorkService.clearOrders(state);
        com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        HiredWorkSession.invalidate(villager);
    }

    public static void observe(ServerLevel level, Villager villager, CompoundTag state, WorkResult result) {
        var town = SettlementWorkforceService.home(level, villager).orElse(null);
        var job = find(villager, state);
        if (town == null || job == null || !job.unfinished() || !job.worker().equals(villager.getUUID())) return;
        job.work(state.getCompound(SettlementFarmWork.TAG));
        job.blocker(result.progressed() ? "" : result.status());
        if (result.completed()) job.finish(true);
        town.changed();
    }

    static void tick(ServerLevel level, SettlementRecord town) {
        if (SettlementSavedData.get(level).readOnly()) return;
        adoptLegacy(level, town);
        for (var job : town.farmJobs().values().stream().filter(SettlementFarmJob::unfinished)
                .sorted(Comparator.comparingInt(SettlementFarmJob::priority).reversed()).toList()) {
            var area = town.areas().get(job.area());
            if (!authorized(town, job) || area == null || area.type() != SettlementAreaType.FARM) {
                detach(level, town, job);
                job.pending();
                job.blocker("Farm assignment or permission changed");
                town.changed();
                continue;
            }
            if (job.status() == SettlementProject.Status.ACTIVE) {
                var worker = loaded(level, job.worker());
                var state = worker == null ? town.workers().containsKey(job.worker()) ? town.workers().get(job.worker()).settings() : null
                        : HiredWorkStateStore.state(worker);
                if (matches(job, state)) {
                    job.work(state.getCompound(SettlementFarmWork.TAG));
                    if (worker == null) job.blocker("Farmer unloaded. Preparation resumes when loaded");
                    else if (!job.area().equals(town.duties().get(job.worker())) || !worker.isAlive()
                            || SettlementWorkforceService.home(level, worker).filter(home -> home == town).isEmpty()) {
                        detach(level, town, job);
                        job.pending();
                    } else if (job.paused()) job.blocker("Farm preparation paused");
                    else if (!area.enabled()) job.blocker("Farm plot disabled");
                    town.changed();
                    continue;
                }
                if (job.remaining().cells().isEmpty()) job.finish(true);
                else {
                    job.pending();
                    job.blocker("Farmer assignment changed. Waiting for a farmer");
                }
                town.changed();
            }
            if (job.status() != SettlementProject.Status.PENDING || job.paused() || !area.enabled()) continue;
            var assignment = SettlementFarmWork.worker(level, town, job.remaining(), false, job.requested());
            if (assignment.worker() == null) job.blocker(assignment.error());
            else activate(level, town, job, assignment.worker());
            town.changed();
        }
    }

    private static void adoptLegacy(ServerLevel level, SettlementRecord town) {
        for (var duty : town.duties().entrySet()) {
            var area = town.workplace(duty.getValue());
            if (area == null || area.type() != SettlementAreaType.FARM) continue;
            var villager = loaded(level, duty.getKey());
            var state = villager != null ? HiredWorkStateStore.state(villager)
                    : town.workers().containsKey(duty.getKey()) ? town.workers().get(duty.getKey()).settings() : null;
            if (state == null) continue;
            var work = state.getCompound(SettlementFarmWork.TAG);
            if (!work.hasUUID("Task") || town.farmJobs().containsKey(work.getUUID("Task"))) continue;
            var queue = HiredTemporaryWorkService.queue(state);
            if (queue == null || queue.current().kind() != HiredTemporaryTask.Kind.PREPARE_FARM
                    || !queue.current().id().equals(work.getUUID("Task")) || !area.id().equals(queue.worksite())) continue;
            var job = new SettlementFarmJob(area.id(), queue.issuer(), queue.current().min(), queue.current().max(), area.priority(), work);
            job.activate(duty.getKey());
            town.addFarmJob(job);
        }
    }

    static boolean activate(ServerLevel level, SettlementRecord town, SettlementFarmJob job, Villager villager) {
        var area = town.workplace(job.area());
        if (!authorized(town, job) || job.paused() || !job.unfinished() || area == null || !area.enabled()) return false;
        var state = HiredWorkStateStore.state(villager);
        if (state.contains(HiredTemporaryQueue.STATE_TAG) || state.contains(HiredTemporaryQueue.PENDING_TAG)) return false;
        var task = new HiredTemporaryTask(job.id(), HiredTemporaryTask.Kind.PREPARE_FARM, job.min(), job.max(), 1);
        if (!SettlementExpeditions.permits(level, town, task)
                || !SettlementWorkforceService.assignFarmJob(level, town, job, villager)) return false;
        state = HiredWorkStateStore.state(villager);
        var home = com.jvn.villagerretaliation.interaction.HiredWorkArea.fromState(state, villager.blockPosition());
        new HiredTemporaryQueue(town.id().value(), job.issuer(), job.area(), HiredVillagerRole.FARMING,
                home.min(), home.max(), List.of(task)).store(state);
        state.put(SettlementFarmWork.TAG, job.work().copy());
        job.work(state.getCompound(SettlementFarmWork.TAG));
        job.activate(villager.getUUID());
        HiredWorkSession.invalidate(villager);
        town.recordWorker(SettlementWorkerState.capture(level, villager,
                new SettlementWorkforceService.Duty(town, town.workplace(job.area())), level.getGameTime()));
        town.changed();
        return true;
    }

    private static boolean matches(SettlementFarmJob job, CompoundTag state) {
        if (state == null) return false;
        var work = state.getCompound(SettlementFarmWork.TAG);
        return work.hasUUID("Task") && job.id().equals(work.getUUID("Task"));
    }

    private static void detach(ServerLevel level, SettlementRecord town, SettlementFarmJob job) {
        var worker = town.workers().get(job.worker());
        var villager = loaded(level, job.worker());
        if (villager != null) {
            var state = HiredWorkStateStore.state(villager);
            if (matches(job, state)) {
                job.work(state.getCompound(SettlementFarmWork.TAG).copy());
                HiredTemporaryWorkService.clearOrders(state);
                com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil.stopHiredNavigation(villager);
                HiredWorkSession.invalidate(villager);
                var area = worker == null ? null : town.workplace(worker.area());
                if (area != null) {
                    worker.observe(level, villager, new SettlementWorkforceService.Duty(town, area), level.getGameTime());
                    worker.changed();
                }
                return;
            }
        }
        if (worker != null && matches(job, worker.settings())) {
            if (villager == null) job.work(worker.settings().getCompound(SettlementFarmWork.TAG).copy());
            HiredTemporaryWorkService.clearOrders(worker.settings());
            worker.changed();
        }
    }

    public static boolean configure(ServerPlayer player, SettlementRecord town, UUID id, long revision,
                                    int priority, boolean paused, UUID requested) {
        var job = town.farmJobs().get(id);
        if (!canManage(player, town) || job == null || !job.unfinished() || job.revision() != revision
                || priority < 0 || priority > 9 || requested == null) return false;
        if (!requested.equals(SettlementFarmJob.AUTOMATIC)) {
            var candidate = SettlementFarmWork.worker(player.serverLevel(), town, job.remaining(), true, requested);
            if (candidate.worker() == null && !requested.equals(job.worker())) return false;
        }
        if (!requested.equals(job.requested()) && !requested.equals(job.worker())) {
            detach(player.serverLevel(), town, job);
            job.pending();
        }
        job.configure(player.getUUID(), priority, paused, requested);
        town.changed();
        tick(player.serverLevel(), town);
        return true;
    }

    public static boolean cancel(ServerPlayer player, SettlementRecord town, UUID id) {
        var job = town.farmJobs().get(id);
        if (!canManage(player, town) || job == null || !job.unfinished()) return false;
        detach(player.serverLevel(), town, job);
        job.finish(false);
        town.changed();
        return true;
    }

    private static boolean canManage(ServerPlayer player, SettlementRecord town) {
        return player != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.serverLevel().dimension().location())
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && town.permits(player.getUUID(), SettlementPermission.BUILD) && town.permits(player.getUUID(), SettlementPermission.STAFF);
    }
}
