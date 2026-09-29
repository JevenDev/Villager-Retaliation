package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementTransferService {
    private SettlementTransferService() {
    }

    public static String append(ServerPlayer player, SettlementRecord town, UUID area, long revision, HiredTransferQueue.Order requested) {
        if (!permits(player, town, area)) return "Courier worksite permission changed";
        var links = SettlementWorksiteService.links(town, area);
        if (depot(town, requested.source()) == null || depot(town, requested.destination()) == null
                || !links.permitsSupply(requested.source().depot()) || !links.permitsOutput(requested.destination().depot())
                || !depot(town, requested.destination()).purpose().accepts(requested.sample())) return "Choose permitted source and destination depots";
        var queue = town.transferQueues().get(area);
        if (queue == null ? revision != 0L : queue.revision() != revision) return "Transfer orders changed";
        if (queue == null) queue = town.beginTransfers(player.getUUID(), area);
        return queue != null && queue.append(requested) ? "" : "Transfer queue is full or the order is invalid";
    }

    public static String edit(ServerPlayer player, SettlementRecord town, UUID area, long revision, UUID id, HiredTransferQueue.Edit edit, int index) {
        if (!permits(player, town, area)) return "Courier worksite permission changed";
        var queue = town.transferQueues().get(area);
        if (queue == null || queue.revision() != revision) return "Transfer orders changed";
        return queue.edit(id, edit, index) ? "" : "Transfer has undelivered cargo or is unavailable";
    }

    public static SettlementDepot depot(SettlementRecord town, HiredTransferQueue.Endpoint endpoint) {
        var depot = endpoint.depot() == null ? null : town.depots().get(endpoint.depot());
        var area = depot == null ? null : town.areas().get(depot.area());
        return area == null || !area.enabled() || !depot.position().equals(endpoint.position()) ? null : depot;
    }

    public static boolean permits(ServerPlayer player, SettlementRecord town, UUID area) {
        return player != null && player.isAlive() && !player.isSpectator() && town != null
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.dimension().equals(player.level().dimension().location())
                && town.permits(player.getUUID(), SettlementPermission.STAFF) && town.permits(player.getUUID(), SettlementPermission.LOGISTICS)
                && town.workplace(area) != null && town.workplace(area).type() == SettlementAreaType.DEPOT
                && town.worksitePlans().containsKey(area);
    }
}
