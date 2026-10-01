package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionDemand;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.PartyContainerLootService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class SettlementPersonalSupply {
    private SettlementPersonalSupply() { }

    public static View inspect(ServerLevel level, Villager worker, ServerPlayer viewer, List<BlockPos> sources) {
        var result = new View();
        var towns = new LinkedHashMap<SettlementRecord, java.util.Set<BlockPos>>();
        for (var source : sources) {
            var town = SettlementSavedData.get(level).at(level.dimension().location(), source).orElse(null);
            if (town == null || !SettlementAllocationService.linked(level, town, source)) continue;
            if (AssignedStorageService.containerLoaded(level, source) && !PartyContainerLootService.isAvailable(level, source, viewer)) continue;
            towns.computeIfAbsent(town, ignored -> new LinkedHashSet<>()).add(SettlementSupplyStock.positions(level, source).getFirst());
        }
        for (var entry : towns.entrySet()) {
            var town = entry.getKey();
            var ready = SettlementAllocationService.ready(level, town);
            if (ready == null) {
                result.blocker = SettlementSavedData.get(level).readOnly() || !town.territory().active()
                        ? "allocation_unavailable" : result.blocker.isEmpty() ? "allocation_pending" : result.blocker;
                continue;
            }
            var snapshot = ready.refresh();
            var demands = snapshot.demands();
            for (var demand : demands) {
                if (!demand.area().equals(worker.getUUID()) || demand.kind() != WorkSupplyDemand.Kind.PRODUCTION) continue;
                var details = result.requirements.computeIfAbsent(demand.supply().id(), ignored -> new Details());
                details.priority = Math.max(details.priority, demand.supply().priority());
                details.protectedMinimum = Math.max(details.protectedMinimum, demand.supply().protectedMinimum());
                for (var grant : snapshot.allocation().grants()) {
                    if (!entry.getValue().contains(grant.source())) continue;
                    long value = demand.requirement().value(grant.sample());
                    if (value <= 0) continue;
                    if (grant.requirement().equals(demand.supply().id())) {
                        if (grant.incoming()) details.incoming = Math.addExact(details.incoming, Math.multiplyExact(grant.count(), value));
                    } else if (!grant.consumer().equals(demand.consumer())) {
                        details.contested = true;
                        if (details.competitor != null || !town.permits(viewer.getUUID(), SettlementPermission.VIEW)) continue;
                        var other = demands.stream().filter(candidate -> candidate.supply().id().equals(grant.requirement())).findFirst().orElse(null);
                        if (other == null) continue;
                        String name = other.name();
                        details.competitor = new HiredProductionDemand.Competitor(town.id().value(), other.consumer(),
                                name.length() > 128 ? name.substring(0, 128) : name, other.supply().priority(),
                                other.supply().protectedMinimum(), other.requirement().unit());
                    }
                }
            }
        }
        return result;
    }

    public static final class View {
        private final Map<UUID, Details> requirements = new LinkedHashMap<>();
        private String blocker = "";

        private View() { }

        public HiredProductionDemand.Requirement explain(WorkSupplyDemand demand) {
            var details = this.requirements.get(demand.id());
            long required = demand.requirement().quantity();
            long remaining = Math.max(0, required - demand.supplied());
            long incoming = details == null ? 0 : Math.min(remaining, details.incoming);
            if (details != null) details.incoming -= incoming;
            boolean covered = remaining == incoming;
            boolean known = covered || demand.known() && this.blocker.isEmpty();
            String reason = covered ? incoming > 0 ? "incoming" : "" : !this.blocker.isEmpty() ? this.blocker
                    : !demand.known() ? demand.blocker().code() : details != null && details.contested ? "allocated_elsewhere" : demand.blocker().code();
            long protectedMinimum = details == null ? 0 : Math.min(required, details.protectedMinimum);
            if (details != null) details.protectedMinimum -= protectedMinimum;
            var original = HiredProductionDemand.Requirement.from(demand);
            return new HiredProductionDemand.Requirement(original.id(), original.order(), original.name(), original.unit(),
                    required, original.supplied(), known ? Math.max(0, remaining - incoming) : -1,
                    original.availableNow(), original.committed(), incoming, new HiredProductionDemand.Allocation(protectedMinimum,
                    demand.targetReserve(), details == null ? demand.priority() : details.priority, reason,
                    reason.equals("allocated_elsewhere") && details != null ? details.competitor : null));
        }
    }

    private static final class Details {
        long incoming;
        long protectedMinimum;
        int priority;
        boolean contested;
        HiredProductionDemand.Competitor competitor;
    }
}
