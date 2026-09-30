package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementTransferService {
    private SettlementTransferService() {
    }

    public static String append(ServerPlayer player, SettlementRecord town, UUID area, long revision, HiredTransferQueue.Order requested) {
        if (!permits(player, town, area)) return "Courier worksite permission changed";
        if (!endpoints(player.serverLevel(), town, area, requested, null)) return "Choose permitted source and destination storage";
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
        if (order.source().depot() != null && order.destination().depot() != null) {
            SettlementLogisticsService.bind(villager, new SettlementHaulTask(
                    order.source().depot(), order.destination().depot(), order.sample(), order.amount(), false));
        } else SettlementLogisticsService.clear(villager);
        SettlementDepotService.synchronizeStorage(level, villager, town);
    }

    public static boolean endpoint(net.minecraft.server.level.ServerLevel level, SettlementRecord town, UUID area,
                                   HiredTransferQueue.Endpoint endpoint, boolean source, UUID worker) {
        var workplace = town.workplace(area);
        if (workplace == null) return false;
        var plan = town.worksitePlans().get(area);
        var configured = worker == null ? plan == null ? SettlementStorageLinks.AUTOMATIC : plan.settings().storage()
                : SettlementStorageService.effective(town, worker);
        var depot = depot(town, endpoint);
        if (endpoint.depot() != null && depot == null) return false;
        if (!configured.automatic()) return configured.entries().stream().anyMatch(link ->
                (source ? link.purpose().supplies() : link.purpose().outputs())
                && SettlementSupplyStock.sameStorage(level, endpoint.position(), link.position())
                && (depot != null ? depot.rulesObserved()
                        : SettlementStorageService.permitted(level, town, workplace.type().role(), link)));
        var links = worker == null ? SettlementWorksiteService.links(town, area) : SettlementWorksiteService.links(town, area, worker);
        return depot != null && (source ? links.permitsSupply(depot.id()) : links.permitsOutput(depot.id()));
    }

    public static boolean destination(net.minecraft.server.level.ServerLevel level, SettlementRecord town, UUID area,
                                      HiredTransferQueue.Order order, UUID worker) {
        var depot = depot(town, order.destination());
        return endpoint(level, town, area, order.destination(), false, worker)
                && (depot == null || depot.purpose().accepts(order.sample()));
    }

    public static boolean endpoints(net.minecraft.server.level.ServerLevel level, SettlementRecord town, UUID area,
                                    HiredTransferQueue.Order order, UUID worker) {
        return !SettlementSupplyStock.sameStorage(level, order.source().position(), order.destination().position())
                && endpoint(level, town, area, order.source(), true, worker)
                && destination(level, town, area, order, worker);
    }

    public static long stock(net.minecraft.server.level.ServerLevel level, SettlementRecord town, HiredTransferQueue.Order order) {
        if (order.mode() == HiredTransferQueue.Mode.FINITE) return 0;
        var facts = SettlementSupplyStock.observe(level, town, order.destination().position(), order.sample(), null);
        return facts.known() ? facts.stored() + facts.incoming() : -1;
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
