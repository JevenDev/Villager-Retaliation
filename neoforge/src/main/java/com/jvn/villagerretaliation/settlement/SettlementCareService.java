package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.HashSet;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementCareService {
    private SettlementCareService() {
    }

    static void update(ServerLevel level, SettlementRecord town, long now) {
        var jobs = new java.util.ArrayList<Runnable>();
        schedule(level, town, now, jobs);
        var work = new SettlementWork(jobs);
        while (!SettlementWork.complete(work)) work.run();
    }

    static void schedule(ServerLevel level, SettlementRecord town, long now, List<Runnable> jobs) {
        SettlementAllocationService.schedule(level, town, jobs);
        jobs.add(() -> SettlementHousingService.update(level, town));
        var roster = VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), now);
        // housing and census are fixed for this care cycle, membership is checked at each safe point
        boolean housingShortage = town.housingCapacity() < roster.size();
        for (var member : roster) {
            jobs.add(() -> updateResident(level, town, member.id(), now, housingShortage));
        }
        jobs.add(() -> {
            var members = new HashSet<java.util.UUID>();
            VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), now).forEach(member -> members.add(member.id()));
            for (var id : List.copyOf(town.residents().keySet())) {
                if (!members.contains(id)) town.removeResident(id);
            }
        });
    }

    private static void updateResident(ServerLevel level, SettlementRecord town, java.util.UUID id, long now, boolean housingShortage) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var member = registry.canonicalRecord(town.id()).map(record -> record.residents().get(id)).orElse(null);
        if (member == null) return;
        Villager resident = findResident(level.getServer(), id);
        SettlementResidentState state = town.residents().get(id);
        if (resident != null && resident.isAlive() && resident.level() instanceof ServerLevel residentLevel
                && residentLevel.isPositionEntityTicking(resident.blockPosition())) {
            SettlementHousingService.reconcile(resident);
            if (state == null) {
                state = SettlementResidentState.capture(resident, town, level.getGameTime());
                town.recordResident(state);
            } else {
                int elapsed = SettlementSimulationClock.elapsed(state.checkedAt(), now);
                state.restore(resident);
                state.observe(resident, town, level.getGameTime());
                state.observeShortage(elapsed, housingShortage);
            }
        } else if (state != null && town.autonomous()==null) {
            int elapsed = state.advance(level, town, now);
            state.observeShortage(elapsed, housingShortage);
            if (state.present() && state.age() >= 0 && !member.adult()) {
                registry.addOrUpdateResident(town.id(), member.id(), true, member.nitwit(), now);
            }
        }
        town.changed();
    }

    public static void onActive(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return;
        }
        SettlementRecord town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town == null) {
            return;
        }
        town.removeCourtship(villager.getUUID());
        if (!town.arrivals().containsKey(villager.getUUID())) SettlementHousingService.reconcile(villager);
        SettlementResidentState state = town.residents().get(villager.getUUID());
        if (state != null) {
            state.restore(villager);
        }
    }

    public static void onUnloaded(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        SettlementRecord town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town == null) {
            return;
        }
        var reason = villager.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            town.removeResident(villager.getUUID());
            town.removeDuty(villager.getUUID());
            return;
        }
        SettlementHousingService.observe(villager, town);
        SettlementHousingService.reconcile(villager);
        com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level).ensureProfile(level, villager);
        SettlementResidentState state = town.residents().get(villager.getUUID());
        if (state == null) {
            town.recordResident(SettlementResidentState.capture(villager, town, level.getGameTime()));
        } else {
            state.restore(villager);
            state.observe(villager, town, level.getGameTime());
            town.changed();
        }
    }

    private static Villager findResident(MinecraftServer server, java.util.UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof Villager villager) {
                return villager;
            }
        }
        return null;
    }
}
