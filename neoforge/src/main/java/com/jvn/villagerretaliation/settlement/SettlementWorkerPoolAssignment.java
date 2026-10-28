package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.duel.DuelService;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class SettlementWorkerPoolAssignment {
    private SettlementWorkerPoolAssignment() { }

    static Candidate inspect(ServerLevel level, SettlementRecord town, SettlementWorkerPool pool, UUID resident, long now) {
        if (!pool.authorized(town) || !pool.id().equals(town.workerPoolFor(resident))) return Candidate.blocked("pool_authority");
        return inspectEligibility(level, town, resident, now);
    }

    static Candidate inspectEligibility(ServerLevel level, SettlementRecord town, UUID resident, long now) {
        if (town.manuallyStaffed(resident)) return Candidate.blocked("pool_manual_duty");
        if (town.workerPoolCooldown(resident, now) > 0) return Candidate.blocked("pool_cooldown");
        if (town.arrivals().containsKey(resident)) return Candidate.blocked("pool_arrival");
        if (town.departures().containsKey(resident)) return Candidate.blocked("pool_departure");
        Villager villager = null;
        for (var dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(resident);
            if (entity == null) continue;
            if (dimension != level || !(entity instanceof Villager actual)) return Candidate.blocked("pool_resident_missing");
            villager = actual;
            break;
        }
        boolean active = villager != null && level.isPositionEntityTicking(villager.blockPosition());
        var care = town.residents().get(resident);
        if (villager == null) {
            if (care == null || !care.availableForWork()) return Candidate.blocked("pool_resident_unavailable");
            if (care.needsRecovery()) return Candidate.blocked("pool_recovery");
            villager = care.context(level);
        }
        if (villager == null || !villager.isAlive() || villager.isBaby()
                || !VillageAllegianceApi.canonicalPrimary(level, villager).filter(town.id()::equals).isPresent())
            return Candidate.blocked("pool_resident_unavailable");
        if (SettlementWorkforceService.hasPersonalCommitment(villager)) return Candidate.blocked("pool_personal_commitment");
        if (villager.getHealth() <= villager.getMaxHealth() / 2 || VillagerDownedService.isDowned(villager)
                || VillagerRecoveryService.isForcingRecovery(villager)) return Candidate.blocked("pool_recovery");
        if (villager.isSleeping()) return Candidate.blocked("pool_resting");
        if (villager.isTrading()) return Candidate.blocked("pool_trading");
        if (VillagerConversationService.isConversing(villager)) return Candidate.blocked("pool_conversation");
        if (villager.getTarget() != null || villager.getLastHurtByMob() != null || villager.isOnFire() || villager.isInLava()
                || DuelService.isParticipant(villager) || VillagerRetaliationVillagerBrainUtil.hasThreatMemories(villager.getBrain())
                || VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState(level, villager.getBrain()))
            return Candidate.blocked("pool_combat");
        var from = town.workplace(town.duties().get(resident));
        var worker = town.workers().get(resident);
        if (from != null && (worker == null || !worker.area().equals(from.id()))) return Candidate.blocked("pool_worker_unknown");
        if (from != null && (!from.enabled() || town.effectiveWorkerSettings(resident) != null
                && !town.effectiveWorkerSettings(resident).enabled())) return Candidate.blocked("pool_work_paused");
        if (from != null && !SettlementWorkforceService.workingHours(level.getDayTime(), resident, from.type().role()))
            return Candidate.blocked("pool_resting");
        boolean background = worker != null && (!active || worker.needsRestore(villager));
        if (background && care != null && care.needsRecovery()) return Candidate.blocked("pool_recovery");
        if (background && (worker.unsafe() || worker.huntingEngaged())) return Candidate.blocked("pool_combat");
        if (town.temporaryGuardOrders().containsKey(resident)) return Candidate.blocked("pool_temporary_task");
        CompoundTag state = HiredWorkStateStore.state(villager);
        if ((active || worker == null) && temporary(state) || background && temporary(worker.settings()))
            return Candidate.blocked("pool_temporary_task");
        if ((active || worker == null) && physicalAction(state) || background && physicalAction(worker.settings()))
            return Candidate.blocked("pool_physical_action");
        if (SettlementTransferService.pending(town, resident) != null) return Candidate.blocked("pool_delivery");
        if ((active || worker == null) && HiredJobInventory.getJobInventory(villager).hasOutputItems())
            return Candidate.blocked("pool_delivery");
        if (background) {
            var items = worker.items(level.registryAccess());
            if (worker.outputSlots().stream().anyMatch(slot -> !items.get(slot).isEmpty())) return Candidate.blocked("pool_delivery");
        }
        for (var project : town.projects().values()) {
            if (!project.includes(resident)) continue;
            if (project.active() && project.builder().equals(resident)) return Candidate.blocked("pool_construction");
            if (project.site() != null && project.site().pending()
                    || project.crew() != null && project.crew().claim(resident, level.getGameTime()) != null)
                return Candidate.blocked("pool_construction");
        }
        if (SettlementBrewingService.snapshot(level, town, resident) != null) return Candidate.blocked("pool_brewing_order");
        return new Candidate(villager, "");
    }

    private static boolean temporary(CompoundTag state) {
        return state.contains(HiredTemporaryQueue.STATE_TAG) || state.contains(HiredTemporaryQueue.PENDING_TAG);
    }

    private static boolean physicalAction(CompoundTag state) {
        return SettlementGatheringPolicy.busy(state) || LoggingHarvestPlan.pending(state);
    }

    static String destination(ServerLevel level, SettlementRecord town, SettlementWorkerPool pool, Villager villager, UUID destination) {
        if (SettlementSavedData.get(level).readOnly() || !town.dimension().equals(level.dimension().location())
                || !town.territory().active()) return "pool_worksite_unavailable";
        var area = town.workplace(destination);
        var limits = pool.configuration().worksites().stream().filter(site -> site.area().equals(destination)).findFirst().orElse(null);
        if (limits == null || area == null || area.type().role() == null || !area.enabled() || !town.owns(area)
                || town.worksitePlans().containsKey(destination) && !town.worksitePlans().get(destination).settings().enabled())
            return "pool_worksite_unavailable";
        if (count(town, destination) >= limits.maximum()) return "pool_staffing_cap";
        var from = town.duties().get(villager.getUUID());
        if (destination.equals(from)) return "pool_current_duty";
        var source = pool.configuration().worksites().stream().filter(site -> site.area().equals(from)).findFirst().orElse(null);
        if (source != null && count(town, from) <= source.minimum()) return "pool_staffing_minimum";
        if (!SettlementWorkforceService.availableRoles(level, villager).contains(area.type().role())) return "pool_role_unavailable";
        if (!SettlementWorkforceService.workingHours(level.getDayTime(), villager.getUUID(), area.type().role())) return "pool_resting";
        if (area.type() == SettlementAreaType.CONSTRUCTION && town.stage().projects() == 0
                || area.type() == SettlementAreaType.TRADING_POST && town.stage().ordinal() < SettlementStage.VILLAGE.ordinal())
            return "pool_worksite_unavailable";
        return "";
    }

    static int count(SettlementRecord town, UUID area) {
        return (int) town.duties().values().stream().filter(area::equals).count();
    }

    record Candidate(Villager villager, String blocker) {
        static Candidate blocked(String blocker) { return new Candidate(null, blocker); }
        boolean eligible() { return this.villager != null && this.blocker.isEmpty(); }
    }
}
