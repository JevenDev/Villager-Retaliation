package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentState;
import com.jvn.villagerretaliation.interaction.VillagerAssignmentStore;
import com.jvn.villagerretaliation.interaction.VillagerRecruitmentService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.villager.VillagerBehaviorSuppressionPolicy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class SettlementWorkforceService {
    private SettlementWorkforceService() {
    }

    public static Optional<SettlementRecord> home(ServerLevel level, Villager villager) {
        return VillageAllegianceApi.canonicalPrimary(level, villager)
                .flatMap(SettlementSavedData.get(level)::get);
    }

    public static boolean assign(ServerLevel level, ServerPlayer player, VillageAllegianceId townId,
                                 Villager villager, UUID areaId) {
        SettlementRecord town = SettlementSavedData.get(level).get(townId).orElse(null);
        if (town == null || player == null || villager == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || villager.level() != level || !villager.isAlive() || villager.isBaby()
                || !town.dimension().equals(level.dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || !VillageAllegianceApi.canonicalPrimary(level, villager).filter(townId::equals).isPresent()
                || hasPersonalCommitment(villager)) {
            return false;
        }
        SettlementArea area = town.areas().get(areaId);
        if (area == null || area.type().role() == null
                || !HiredVillagerRoles.availableRoles(level, villager).contains(area.type().role())
                || area.type() == SettlementAreaType.CONSTRUCTION && town.stage().projects() == 0
                || area.type() == SettlementAreaType.TRADING_POST
                        && town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) {
            return false;
        }
        if (!town.assignDuty(villager.getUUID(), areaId)) {
            return false;
        }
        reconcile(level, villager);
        return true;
    }

    public static boolean release(ServerLevel level, ServerPlayer player, VillageAllegianceId townId, UUID resident) {
        SettlementRecord town = SettlementSavedData.get(level).get(townId).orElse(null);
        if (town == null || player == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || !town.dimension().equals(level.dimension().location())
                || !town.leader().equals(player.getUUID()) || !town.removeDuty(resident)) {
            return false;
        }
        if (level.getEntity(resident) instanceof Villager villager) {
            reconcile(level, villager);
        }
        return true;
    }

    public static boolean canManageEquipment(ServerLevel level, Villager villager, ServerPlayer player) {
        if (player == null || villager == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || villager.level() != level || !villager.isAlive() || villager.isBaby()
                || hasPersonalCommitment(villager)) {
            return false;
        }
        return duty(villager).map(duty -> duty.town().permits(player.getUUID(), SettlementPermission.LOGISTICS)).orElse(false);
    }

    public static Optional<Duty> duty(Villager villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        UUID townId = VillagerAssignmentStore.civicSettlement(villager).orElse(null);
        UUID areaId = VillagerAssignmentStore.civicArea(villager).orElse(null);
        if (townId == null || areaId == null) {
            return Optional.empty();
        }
        SettlementRecord town = SettlementSavedData.get(level).get(new VillageAllegianceId(townId)).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location())
                || !areaId.equals(town.duties().get(villager.getUUID()))
                || !VillageAllegianceApi.canonicalPrimary(level, villager).filter(town.id()::equals).isPresent()) {
            return Optional.empty();
        }
        SettlementArea area = town.areas().get(areaId);
        return area == null || area.type().role() == null ? Optional.empty() : Optional.of(new Duty(town, area));
    }

    public static HiredVillagerRole role(Villager villager) {
        return duty(villager).map(duty -> duty.area().type().role()).orElse(null);
    }

    public static boolean onDuty(ServerLevel level, Villager villager) {
        return duty(villager).map(duty -> duty.area().enabled()
                && workingHours(level.getDayTime(), villager.getUUID(), duty.area().type().role())).orElse(false);
    }

    public static boolean workingHours(long dayTime, UUID resident, HiredVillagerRole role) {
        long time = Math.floorMod(dayTime, 24_000L);
        if (role == HiredVillagerRole.COMBAT) {
            long shift = (resident.getLeastSignificantBits() & 1L) == 0L ? 0L : 12_000L;
            return Math.floorMod(time - shift, 24_000L) < 12_000L;
        }
        return time >= 2_000L && time < 9_000L;
    }

    public static boolean blocksPersonalRecruitment(ServerLevel level, Villager villager) {
        if (VillagerAssignmentStore.snapshot(villager).state() == VillagerAssignmentState.CIVIC
                || home(level, villager).map(town -> town.duties().containsKey(villager.getUUID())).orElse(false)) {
            return true;
        }
        if (hasPersonalCommitment(villager) || !providesEssentialFood(villager)) {
            return false;
        }
        var id = VillageAllegianceApi.canonicalPrimary(level, villager).orElse(null);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.canonicalRecord(id).orElse(null);
        if (village == null || registry.isManaged(id)) {
            return false;
        }
        return village.activeAdultResidents(level.getGameTime()).stream()
                .filter(resident -> !resident.id().equals(villager.getUUID()))
                .map(resident -> level.getEntity(resident.id()))
                .filter(Villager.class::isInstance)
                .map(Villager.class::cast)
                .noneMatch(other -> other.isAlive() && providesEssentialFood(other) && !hasPersonalCommitment(other));
    }

    public static void reconcile(ServerLevel level, Villager villager) {
        var before = VillagerAssignmentStore.snapshot(villager);
        SettlementRecord town = home(level, villager).orElse(null);
        VillagerAssignmentStore.civicSettlement(villager)
                .filter(id -> town == null || !id.equals(town.id().value()))
                .flatMap(id -> SettlementSavedData.get(level).get(new VillageAllegianceId(id)))
                .ifPresent(previous -> previous.removeDuty(villager.getUUID()));
        UUID areaId = town == null ? null : town.duties().get(villager.getUUID());
        SettlementArea area = areaId == null ? null : town.areas().get(areaId);
        if (area == null && before.state() != VillagerAssignmentState.CIVIC) {
            return;
        }
        if (hasPersonalCommitment(villager) || villager.isBaby() || !villager.isAlive()) {
            if (town != null) {
                town.removeDuty(villager.getUUID());
            }
            area = null;
        }
        if (area == null || area.type().role() == null || !town.dimension().equals(level.dimension().location())) {
            if (before.state() == VillagerAssignmentState.CIVIC) {
                SettlementLogisticsService.resetAssignment(villager);
                HiredWorkStateStore.cancelWork(level, villager, before.role(), "interaction.work.status.paused", Map.of());
                VillagerAssignmentStore.unassign(villager);
                AssignedStorageService.removeAllAssignedStorage(level, villager);
                HiredWorkSession.invalidate(villager);
                VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
            }
            return;
        }
        var state = HiredWorkStateStore.state(villager);
        boolean assignmentChanged = before.state() != VillagerAssignmentState.CIVIC
                || !VillagerAssignmentStore.civicSettlement(villager).filter(town.id().value()::equals).isPresent()
                || !VillagerAssignmentStore.civicArea(villager).filter(area.id()::equals).isPresent()
                || before.role() != area.type().role();
        boolean boundsChanged = state.getLong(HiredWorkArea.WORK_MIN_POS_TAG) != area.min().asLong()
                || state.getLong(HiredWorkArea.WORK_MAX_POS_TAG) != area.max().asLong();
        if (assignmentChanged || boundsChanged) {
            SettlementLogisticsService.resetAssignment(villager);
            HiredWorkStateStore.cancelWork(level, villager, before.role(), "interaction.work.status.waiting_tick", Map.of());
            if (before.state() != VillagerAssignmentState.CIVIC) {
                AssignedStorageService.removeAllAssignedStorage(level, villager);
            }
            VillagerAssignmentStore.assignCivic(villager, town.id().value(), area.id(), area.type().role(), level.getGameTime());
            HiredWorkStateStore.initializeDefaults(state, villager);
            HiredWorkArea.fromBounds(area.min(), area.max(), true).save(state);
            state.putBoolean("Enabled", area.enabled());
            HiredWorkSession.invalidate(villager);
        } else if (state.getBoolean("Enabled") != area.enabled()) {
            state.putBoolean("Enabled", area.enabled());
        }
        if (assignmentChanged || Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), 200L) == 0L) {
            SettlementDepotService.synchronizeStorage(level, villager, town);
        }
    }

    private static boolean hasPersonalCommitment(Villager villager) {
        return HiredVillagerContractService.hasActiveOrPendingContract(villager)
                || VillagerAssignmentStore.snapshot(villager).state() == VillagerAssignmentState.HIRED
                || PartyVillagerContractService.hasPartyEntityReference(villager)
                || VillagerRecruitmentService.isFollowingAnyPlayer(villager);
    }

    private static boolean providesEssentialFood(Villager villager) {
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession == VillagerProfession.FARMER || profession == VillagerProfession.FISHERMAN
                || profession == VillagerProfession.BUTCHER;
    }

    public record Duty(SettlementRecord town, SettlementArea area) {
    }
}
