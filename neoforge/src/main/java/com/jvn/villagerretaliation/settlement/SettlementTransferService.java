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
        var allocated = queue.allocations();
        if (!queue.edit(id, edit, index)) return "Transfer has undelivered cargo or is unavailable";
        for (var allocation : allocated) {
            if (!allocation.started() && queue.allocation(allocation.worker()) == null) {
                com.jvn.villagerretaliation.inventory.AssignedStorageService.releaseCourierClaims(allocation.worker());
            }
        }
        return "";
    }

    public static HiredTransferQueue pending(SettlementRecord town, UUID worker) {
        return town.transferQueues().values().stream().filter(queue -> {
            var allocation = queue.allocation(worker);
            return allocation != null && allocation.started();
        }).findFirst().orElse(null);
    }

    public static SettlementDepot depot(SettlementRecord town, HiredTransferQueue.Endpoint endpoint) {
        var depot = endpoint.depot() == null ? null : town.depots().get(endpoint.depot());
        var area = depot == null ? null : town.areas().get(depot.area());
        return area == null || !area.enabled() || !depot.position().equals(endpoint.position()) ? null : depot;
    }

    public static boolean destination(SettlementRecord town, UUID area, HiredTransferQueue.Order order) {
        var destination = depot(town, order.destination());
        return destination != null && destination.purpose().accepts(order.sample())
                && SettlementWorksiteService.links(town, area).permitsOutput(destination.id());
    }

    public static boolean endpoints(SettlementRecord town, UUID area, HiredTransferQueue.Order order) {
        var links = SettlementWorksiteService.links(town, area);
        var destination = depot(town, order.destination());
        return depot(town, order.source()) != null && destination != null
                && links.permitsSupply(order.source().depot()) && links.permitsOutput(order.destination().depot())
                && destination.purpose().accepts(order.sample());
    }

    public static void bind(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.Villager villager,
                            SettlementRecord town, HiredTransferQueue.Order order) {
        SettlementLogisticsService.bind(villager, new SettlementHaulTask(
                order.source().depot(), order.destination().depot(), order.sample(), order.amount(), false));
        SettlementDepotService.synchronizeStorage(level, villager, town);
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
