package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand;
import java.nio.charset.StandardCharsets;
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
        var counts = new java.util.HashMap<UUID, Integer>();
        for (var recovery : SettlementSupplyRecovery.inspect(level, town, snapshot)) {
            var demand = recovery.demand();
            var need = demand.requirement();
            int index = counts.merge(demand.consumer(), 1, Integer::sum);
            if (sample != null && need.value(sample) <= 0 && !products.contains(demand.consumer())) continue;
            var id = UUID.nameUUIDFromBytes((town.id().value() + ":demand:" + demand.consumer() + ":" + index).getBytes(StandardCharsets.UTF_8));
            String requirement = need.key().replace('_', ' ');
            if (!need.alternatives().isEmpty()) requirement = need.alternatives().getFirst().getHoverName().getString()
                    + (need.alternatives().size() > 1 ? " or compatible alternative" : "");
            result.add(new Demand(id, bounded(demand.name(), 128), demand.consumer(), demand.area(), demand.kind() == SettlementDemandService.Kind.PROJECT,
                    bounded(requirement, 128), need.unit(), need.quantity(), demand.supplied(), demand.missing(), demand.immediateMissing(), need.alternatives().stream().limit(16).toList(),
                    demand.sources().stream().limit(128).toList(), recovery.cause(), bounded(recovery.advice(), 512), recovery.focus(),
                    recovery.courier(), recovery.source(), recovery.destination(), recovery.sample()));
        }
        return List.copyOf(result);
    }

    private static String bounded(String value, int limit) { return value.length() <= limit ? value : value.substring(0, limit); }
}
