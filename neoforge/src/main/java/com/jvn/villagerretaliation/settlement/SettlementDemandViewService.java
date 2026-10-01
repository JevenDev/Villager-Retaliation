package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.DemandControl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

final class SettlementDemandViewService {
    private SettlementDemandViewService() { }

    static List<Demand> demands(ServerLevel level, SettlementRecord town) {
        return demands(level, town, SettlementDemandService.inspect(level, town));
    }

    static List<Demand> demands(net.minecraft.server.level.ServerPlayer player, SettlementRecord town) {
        return demands(player.serverLevel(), town, SettlementDemandService.inspect(player.serverLevel(), town, player));
    }

    static List<Demand> demands(net.minecraft.server.level.ServerPlayer player, SettlementRecord town, net.minecraft.world.item.ItemStack sample) {
        return demands(player.serverLevel(), town, SettlementDemandService.inspect(player.serverLevel(), town, player), sample);
    }

    static List<Demand> demands(ServerLevel level, SettlementRecord town, SettlementDemandService.Snapshot snapshot) {
        return demands(level, town, snapshot, null);
    }

    static List<Demand> demands(ServerLevel level, SettlementRecord town, SettlementDemandService.Snapshot snapshot, net.minecraft.world.item.ItemStack sample) {
        var products = sample == null ? java.util.Set.<UUID>of() : town.productionQueues().values().stream()
                .flatMap(queue -> queue.orders().stream()).filter(order -> order.matches(sample))
                .map(com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder::id).collect(java.util.stream.Collectors.toSet());
        var result = new ArrayList<Demand>();
        for (var recovery : SettlementSupplyRecovery.inspect(level, town, snapshot)) {
            var demand = recovery.demand();
            var need = demand.requirement();
            if (sample != null && need.value(sample) <= 0 && !products.contains(demand.consumer())) continue;
            var supply = demand.supply();
            String requirement = com.jvn.villagerretaliation.interaction.work.production.WorkSupplyEvaluation.name(need);
            result.add(new Demand(supply.id(), bounded(demand.name(), 128), demand.consumer(), demand.area(), demand.kind(), control(town, demand),
                    bounded(requirement, 128), need.unit(), need.quantity(), demand.supplied(), demand.missing(), demand.immediateMissing(),
                    supply.availableNow(), supply.committed(), supply.incoming(), supply.proposed(), supply.targetReserve(), supply.protectedMinimum(), supply.priority(),
                    supply.blocker().competingConsumer(),
                    need.alternatives().stream().limit(16).toList(),
                    demand.sources().stream().limit(128).toList(), recovery.cause(), bounded(recovery.advice(), 512), recovery.focus(),
                    recovery.courier(), recovery.source(), recovery.destination(), recovery.sample()));
        }
        return List.copyOf(result);
    }

    private static DemandControl control(SettlementRecord town, SettlementDemandService.Demand demand) {
        return switch (demand.kind()) {
            case PROJECT -> DemandControl.PROJECT;
            case FOOD, STOCK_RESERVE -> DemandControl.RESERVE;
            case EQUIPMENT -> town.workplace(demand.area()) == null ? DemandControl.PERSONAL_EQUIPMENT
                    : demand.consumer().equals(demand.area()) ? DemandControl.EQUIPMENT : DemandControl.RESIDENT;
            case PRODUCTION -> {
                var transfers = town.transferQueues().get(demand.area());
                if (transfers != null && transfers.order(demand.consumer()) != null) yield DemandControl.TRANSFER;
                var production = town.productionQueues().get(demand.area());
                yield production != null && production.order(demand.consumer()) != null
                        ? DemandControl.PRODUCTION : DemandControl.PERSONAL_PRODUCTION;
            }
        };
    }

    private static String bounded(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
}
