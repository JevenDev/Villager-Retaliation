package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import java.util.LinkedHashMap;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementWorksiteService {
    private SettlementWorksiteService() {
    }

    public static boolean configure(ServerPlayer player, SettlementRecord town, UUID areaId,
                                    SettlementWorksitePlan plan, boolean adopt) {
        if (!canManage(player, town) || plan == null) return false;
        var area = town.areas().get(areaId);
        if (area == null || area.type().role() != plan.settings().role()
                || adopt == town.worksitePlans().containsKey(areaId)
                || !HiredLoggingFilters.options().containsAll(plan.settings().logs())
                || !HiredAnimalBreedingTargets.options().containsAll(plan.settings().animals())) return false;
        var retained = new LinkedHashMap<UUID, SettlementWorkerSettings>();
        if (adopt) {
            for (var duty : town.duties().entrySet()) {
                if (areaId.equals(duty.getValue())) {
                    var settings = SettlementWorkerConfigurationService.snapshot(player.serverLevel(), town, duty.getKey());
                    retained.put(duty.getKey(), settings.withOverrides(SettlementWorkerSettings.allOverrides(settings.role())));
                }
            }
        }
        if (!town.setWorksitePlan(player.getUUID(), areaId, plan)) return false;
        retained.forEach((resident, settings) -> town.setWorkerSettings(player.getUUID(), resident, settings));
        for (var duty : java.util.List.copyOf(town.duties().entrySet())) {
            if (!areaId.equals(duty.getValue())) continue;
            if (player.serverLevel().getEntity(duty.getKey()) instanceof Villager villager) {
                SettlementBackgroundService.onActive(villager);
                SettlementBackgroundService.onUnloaded(villager);
            } else {
                var worker = town.workers().get(duty.getKey());
                if (worker != null && areaId.equals(worker.area())) {
                    town.effectiveWorkerSettings(duty.getKey()).apply(worker.settings(), area.enabled());
                }
            }
        }
        return true;
    }

    public static boolean reset(ServerPlayer player, SettlementRecord town, UUID resident, UUID area, int mask) {
        if (!canManage(player, town) || !town.worksitePlans().containsKey(area)
                || !area.equals(town.duties().get(resident))) return false;
        var settings = SettlementWorkerConfigurationService.snapshot(player.serverLevel(), town, resident);
        if (settings == null || (mask & ~SettlementWorkerSettings.allOverrides(settings.role())) != 0) return false;
        return SettlementWorkerConfigurationService.configure(player, town, resident, area,
                settings.withOverrides(settings.overrides() & ~mask));
    }

    public static boolean fill(ServerPlayer player, SettlementRecord town, UUID area) {
        if (!canManage(player, town) || !town.worksitePlans().containsKey(area)) return false;
        SettlementAutonomyService.fill(player.serverLevel(), town, area);
        return true;
    }

    private static boolean canManage(ServerPlayer player, SettlementRecord town) {
        return player != null && player.isAlive() && !player.isSpectator() && town != null
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && town.dimension().equals(player.level().dimension().location())
                && town.permits(player.getUUID(), SettlementPermission.STAFF);
    }
}
