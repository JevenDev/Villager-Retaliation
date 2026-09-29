package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredAnimalBreedingTargets;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorker;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementWorkerConfigurationService {
    private SettlementWorkerConfigurationService() {
    }

    public static SettlementWorkerSettings snapshot(ServerLevel level, SettlementRecord town, UUID resident) {
        var area = town.workplace(town.duties().get(resident));
        if (area == null || area.type().role() == null) {
            return null;
        }
        var settings = town.effectiveWorkerSettings(resident);
        if (settings != null) {
            return settings;
        }
        var worker = town.workers().get(resident);
        CompoundTag state = level.getEntity(resident) instanceof Villager villager ? HiredWorkStateStore.state(villager).copy()
                : worker != null && worker.area().equals(area.id()) ? worker.settings().copy() : new CompoundTag();
        state.putBoolean("Enabled", true);
        return SettlementWorkerSettings.capture(area.type().role(), state);
    }

    public static boolean setEnabled(ServerPlayer player, SettlementRecord town, UUID resident, UUID areaId, boolean enabled) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || !areaId.equals(town.duties().get(resident))) {
            return false;
        }
        var settings = snapshot(player.serverLevel(), town, resident);
        return settings != null && configure(player, town, resident, areaId,
                settings.with(SettlementWorkerSetting.ENABLED, enabled ? 1 : 0));
    }

    public static boolean configure(ServerPlayer player, SettlementRecord town, UUID resident, UUID areaId,
                                    SettlementWorkerSettings settings) {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !town.dimension().equals(player.level().dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || settings == null || areaId == null || !areaId.equals(town.duties().get(resident))
                || !settings.logs().isEmpty() && !HiredLoggingFilters.options().containsAll(settings.logs())
                || !settings.animals().isEmpty() && !HiredAnimalBreedingTargets.options().containsAll(settings.animals())) {
            return false;
        }
        var area = town.workplace(areaId);
        if (area == null || area.type().role() != settings.role()) {
            return false;
        }
        var level = player.serverLevel();
        Villager villager = level.getEntity(resident) instanceof Villager loaded ? loaded : null;
        if (villager != null && (!villager.isAlive() || villager.isBaby()
                || SettlementWorkforceService.hasPersonalCommitment(villager)
                || SettlementWorkforceService.duty(villager).filter(duty -> duty.town() == town && duty.area().id().equals(areaId)).isEmpty())) {
            return false;
        }
        if (!town.setWorkerSettings(player.getUUID(), resident, settings)) {
            return false;
        }
        if (villager != null) {
            SettlementBackgroundService.onActive(villager);
            SettlementBackgroundService.onUnloaded(villager);
        } else {
            var worker = town.workers().get(resident);
            if (worker != null && worker.area().equals(areaId)) {
                town.effectiveWorkerSettings(resident).apply(worker.settings(), area.enabled());
            }
        }
        return true;
    }

    static void apply(Villager villager, SettlementRecord town, SettlementArea area) {
        if (SettlementWorkforceService.hasPersonalCommitment(villager)) {
            return;
        }
        var settings = town.effectiveWorkerSettings(villager.getUUID());
        var state = HiredWorkStateStore.state(villager);
        if (settings == null) {
            state.putBoolean("Enabled", area.enabled());
            return;
        }
        int oldMode = SettlementWorkerSetting.MINING_MODE.read(state);
        if (settings.apply(state, area.enabled())) {
            var level = (ServerLevel) villager.level();
            HiredWorkStateStore.pauseWork(level, villager, area.type().role(),
                    state.getBoolean("Enabled") ? "interaction.work.status.waiting_tick" : "interaction.work.status.paused", Map.of());
            if (settings.role() == com.jvn.villagerretaliation.interaction.HiredVillagerRole.MINING
                    && oldMode != SettlementWorkerSetting.MINING_MODE.read(state)) {
                MiningWorker.resetForModeChange(level, villager, HiredWorkSession.active(level, villager).context(), HiredMiningMode.fromState(state));
            }
            HiredWorkSession.invalidate(villager);
        }
    }
}
