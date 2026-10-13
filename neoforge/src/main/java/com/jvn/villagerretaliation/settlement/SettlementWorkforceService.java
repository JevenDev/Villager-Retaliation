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

    public static boolean assignResident(ServerLevel level, ServerPlayer player, VillageAllegianceId townId, UUID resident, UUID areaId) {
        var town = SettlementSavedData.get(level).get(townId).orElse(null);
        if (town == null || resident == null || player == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || !town.dimension().equals(level.dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return false;
        for (var dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(resident);
            if (entity != null) return entity instanceof Villager villager && assign(level, player, townId, villager, areaId);
        }
        var care = town.residents().get(resident);
        if (care == null || !care.availableForWork() || town.departures().containsKey(resident)
                || !VillageAllegianceRegistrySavedData.get(level).canonicalRecord(townId)
                        .map(record -> record.residents().containsKey(resident)).orElse(false)) return false;
        var villager = care.context(level);
        return villager != null && assign(level, player, townId, villager, areaId);
    }

    public static boolean assign(ServerLevel level, ServerPlayer player, VillageAllegianceId townId,
                                 Villager villager, UUID areaId) {
        SettlementRecord town = SettlementSavedData.get(level).get(townId).orElse(null);
        if (town == null || player == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return false;
        if (!assign(level, town, villager, areaId)) return false;
        town.rememberManualDuty(villager.getUUID());
        return true;
    }

    static boolean fillAssignment(ServerLevel level, SettlementRecord town, Villager villager, UUID areaId) {
        if (villager == null || town.manuallyStaffed(villager.getUUID()) || town.duties().containsKey(villager.getUUID())
                || !assign(level, town, villager, areaId)) return false;
        town.rememberManualDuty(villager.getUUID());
        return true;
    }

    static boolean assignFromPool(ServerLevel level, SettlementRecord town, SettlementWorkerPool pool, SettlementWorkerPool.Move move) {
        if (!pool.authorized(town) || !java.util.Objects.equals(move.from(), town.duties().get(move.resident()))
                || !pool.history().isEmpty() && pool.history().getLast().at() == move.at()) return false;
        var candidate = SettlementWorkerPoolAssignment.inspect(level, town, pool, move.resident(), move.at());
        if (!candidate.eligible()
                || !SettlementWorkerPoolAssignment.destination(level, town, pool, candidate.villager(), move.to()).isEmpty()) return false;
        if (!assign(level, town, candidate.villager(), move.to())) return false;
        town.recordWorkerPoolMove(pool, move);
        SettlementPolicyManagementService.staffed(level, town, candidate.villager(), move);
        return true;
    }

    private static boolean assign(ServerLevel level, SettlementRecord town, Villager villager, UUID areaId) {
        if (villager == null || villager.level() != level || !villager.isAlive() || villager.isBaby()
                || !town.dimension().equals(level.dimension().location())
                || !VillageAllegianceApi.canonicalPrimary(level, villager).filter(town.id()::equals).isPresent()
                || hasPersonalCommitment(villager)) return false;
        SettlementArea area = town.workplace(areaId);
        if (area == null || area.type().role() == null
                || area.type() != SettlementAreaType.CONSTRUCTION && !HiredVillagerRoles.availableRoles(level, villager).contains(area.type().role())
                || area.type() == SettlementAreaType.CONSTRUCTION && town.stage().projects() == 0
                || area.type() == SettlementAreaType.TRADING_POST
                        && town.stage().ordinal() < SettlementStage.VILLAGE.ordinal()) {
            return false;
        }
        var previous = town.workers().get(villager.getUUID());
        if (previous != null) previous.restore(villager);
        if (!town.assignDuty(villager.getUUID(), areaId)) {
            return false;
        }
        reconcile(level, villager);
        var worker = SettlementWorkerState.capture(level, villager, new Duty(town, area), level.getGameTime());
        worker.changed();
        town.recordWorker(worker);
        worker.restore(villager);
        var care = town.residents().get(villager.getUUID());
        if (care == null) {
            town.recordResident(SettlementResidentState.capture(villager, town, level.getGameTime()));
        } else {
            care.remember(villager);
            town.changed();
        }
        return true;
    }

    public static boolean release(ServerLevel level, ServerPlayer player, VillageAllegianceId townId, UUID resident) {
        SettlementRecord town = SettlementSavedData.get(level).get(townId).orElse(null);
        if (town == null || player == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || !town.dimension().equals(level.dimension().location())
                || !town.leader().equals(player.getUUID()) || !town.duties().containsKey(resident)
                || town.activeProject(resident) != null) {
            return false;
        }
        Villager villager = level.getEntity(resident) instanceof Villager loaded ? loaded : null;
        var worker = town.workers().get(resident);
        if (villager != null && worker != null) worker.restore(villager);
        if (!town.removeDuty(resident)) return false;
        town.rememberManualDuty(resident);
        if (villager != null) {
            reconcile(level, villager);
            var care = town.residents().get(resident);
            if (care != null) care.remember(villager);
        }
        return true;
    }

    static boolean automaticStaffing(ServerPlayer player, SettlementRecord town, UUID resident) {
        if (player == null || !player.isAlive() || player.isSpectator() || town == null
                || SettlementSavedData.get(player.serverLevel()).readOnly()
                || !town.dimension().equals(player.serverLevel().dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return false;
        var members = VillageAllegianceRegistrySavedData.get(player.serverLevel())
                .residentRoster(town.id(), player.serverLevel().getGameTime());
        if (resident != null && (town.workerPoolFor(resident) == null
                || members.stream().noneMatch(member -> member.id().equals(resident)))) return false;
        for (var member : members) {
            if (town.workerPoolFor(member.id()) != null && (resident == null || resident.equals(member.id()))) {
                town.returnToAutomaticStaffing(player.getUUID(), member.id());
            }
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
        var assignment = VillagerAssignmentStore.civicAssignment(villager).orElse(null);
        if (assignment == null) return Optional.empty();
        UUID townId = assignment.settlement();
        UUID areaId = assignment.area();
        SettlementRecord town = SettlementSavedData.get(level).get(new VillageAllegianceId(townId)).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location())
                || !areaId.equals(town.duties().get(villager.getUUID()))
                || !VillageAllegianceApi.canonicalPrimary(level, villager).filter(town.id()::equals).isPresent()) {
            return Optional.empty();
        }
        SettlementArea area = town.workplace(areaId);
        return area == null || area.type().role() == null ? Optional.empty() : Optional.of(new Duty(town, area));
    }

    public static HiredVillagerRole role(Villager villager) {
        return duty(villager).map(duty -> duty.area().type().role()).orElse(null);
    }

    public static boolean onDuty(ServerLevel level, Villager villager) {
        return duty(villager).map(duty -> {
            var settings = duty.town().effectiveWorkerSettings(villager.getUUID());
            return duty.area().enabled() && (settings == null || settings.enabled())
                    && workingHours(level.getDayTime(), villager.getUUID(), duty.area().type().role());
        }).orElse(false);
    }

    public static boolean workingHours(long dayTime, UUID resident, HiredVillagerRole role) {
        long time = Math.floorMod(dayTime, 24_000L);
        if (role == HiredVillagerRole.COMBAT) {
            long shift = (resident.getLeastSignificantBits() & 1L) == 0L ? 2_000L : 14_000L;
            return Math.floorMod(time - shift, 24_000L) < 12_000L;
        }
        return time >= 2_000L && time < 9_000L;
    }

    public static boolean canTransferToPersonalHire(ServerLevel level, Villager villager, ServerPlayer player) {
        if (player == null || villager == null || !player.isAlive() || player.isSpectator()
                || player.level() != level || villager.level() != level || !villager.isAlive() || villager.isBaby()
                || level.getEntity(villager.getUUID()) != villager || hasPersonalCommitment(villager)) return false;
        return duty(villager).filter(duty -> duty.town().leader().equals(player.getUUID())
                && duty.town().activeProject(villager.getUUID()) == null).isPresent();
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
                .flatMap(id -> SettlementSavedData.get(level).get(new VillageAllegianceId(id)))
                .filter(previous -> town == null || !previous.id().equals(town.id()))
                .ifPresent(previous -> previous.removeDuty(villager.getUUID()));
        UUID areaId = town == null ? null : town.duties().get(villager.getUUID());
        SettlementArea area = areaId == null ? null : town.workplace(areaId);
        if (area == null && before.state() != VillagerAssignmentState.CIVIC) {
            SettlementScheduleService.reconcile(villager);
            return;
        }
        if (hasPersonalCommitment(villager) || villager.isBaby() || !villager.isAlive()) {
            if (town != null) {
                town.removeDuty(villager.getUUID());
            }
            area = null;
        }
        if (area == null || area.type().role() == null || !town.dimension().equals(level.dimension().location())) {
            SettlementScheduleService.reconcile(villager);
            if (before.state() == VillagerAssignmentState.CIVIC) {
                SettlementLogisticsService.resetAssignment(villager);
                HiredWorkStateStore.transferWork(level, villager, before.role(), "interaction.work.status.paused", Map.of());
                HiredWorkStateStore.state(villager).remove(SettlementWorksiteLinks.STATE_TAG);
                VillagerAssignmentStore.unassign(villager);
                SettlementBrewingService.reconcile(villager);
                AssignedStorageService.removeAllAssignedStorage(level, villager);
                HiredWorkSession.invalidate(villager);
                VillagerBehaviorSuppressionPolicy.restoreAfterRelease(level, villager);
            }
            return;
        }
        if (before.state() == VillagerAssignmentState.CIVIC && before.role() == area.type().role()
                && VillagerAssignmentStore.civicArea(villager).filter(area.id()::equals).isPresent()
                && VillagerAssignmentStore.civicSettlement(villager).filter(id -> !id.equals(town.id().value()))
                        .flatMap(id -> SettlementSavedData.get(level).get(new VillageAllegianceId(id)))
                        .filter(previous -> previous.id().equals(town.id())).isPresent()) {
            VillagerAssignmentStore.assignCivic(villager, town.id().value(), area.id(), area.type().role(), level.getGameTime());
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
            if (!assignmentChanged && before.role() == HiredVillagerRole.BUILDER) {
                HiredWorkStateStore.pauseWork(level, villager, before.role(), "interaction.work.status.waiting_tick", Map.of());
            } else {
                HiredWorkStateStore.transferWork(level, villager, before.role(), "interaction.work.status.waiting_tick", Map.of());
            }
            if (before.state() != VillagerAssignmentState.CIVIC) {
                AssignedStorageService.removeAllAssignedStorage(level, villager);
            }
            VillagerAssignmentStore.assignCivic(villager, town.id().value(), area.id(), area.type().role(), level.getGameTime());
            HiredWorkStateStore.initializeDefaults(state, villager);
            HiredWorkArea.fromBounds(area.min(), area.max(), true).save(state);
            state.putBoolean("Enabled", area.enabled());
            HiredWorkSession.invalidate(villager);
        }
        SettlementWorkerConfigurationService.apply(villager, town, area);
        SettlementScheduleService.reconcile(villager);
        SettlementBrewingService.reconcile(villager);
        if (assignmentChanged || Math.floorMod(level.getGameTime() + villager.getUUID().getLeastSignificantBits(), 200L) == 0L) {
            SettlementDepotService.synchronizeStorage(level, villager, town);
        }
    }

    static boolean hasPersonalCommitment(Villager villager) {
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
