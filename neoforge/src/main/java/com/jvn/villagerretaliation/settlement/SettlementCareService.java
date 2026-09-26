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
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var roster = registry.residentRoster(town.id(), now);
        var members = new HashSet<java.util.UUID>();
        boolean housingShortage = town.housingCapacity() < roster.size();
        for (var member : roster) {
            members.add(member.id());
            Villager resident = findResident(level.getServer(), member.id());
            SettlementResidentState state = town.residents().get(member.id());
            if (resident != null && resident.isAlive() && resident.level() instanceof ServerLevel residentLevel
                    && residentLevel.isPositionEntityTicking(resident.blockPosition())) {
                if (state == null) {
                    state = SettlementResidentState.capture(resident, town, now);
                    town.recordResident(state);
                } else {
                    int elapsed = (int) Math.clamp(now - state.checkedAt(), 0L, 200L);
                    state.restore(resident);
                    state.observe(resident, town, now);
                    state.observeShortage(elapsed, housingShortage);
                }
            } else if (state != null) {
                int elapsed = state.advance(level, town, now);
                state.observeShortage(elapsed, housingShortage);
                if (state.present() && state.age() >= 0 && !member.adult()) {
                    registry.addOrUpdateResident(town.id(), member.id(), true, member.nitwit(), now);
                }
            }
        }
        for (var id : List.copyOf(town.residents().keySet())) {
            if (!members.contains(id)) {
                town.removeResident(id);
            }
        }
        if (!roster.isEmpty()) {
            town.changed();
        }
    }

    public static void onActive(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || !villager.isAlive()) {
            return;
        }
        SettlementRecord town = SettlementWorkforceService.home(level, villager).orElse(null);
        if (town == null) {
            return;
        }
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
