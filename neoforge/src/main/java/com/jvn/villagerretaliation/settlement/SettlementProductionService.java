package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementProductionService {
    private SettlementProductionService() {
    }

    public static String append(ServerPlayer player, SettlementRecord town, UUID areaId, long revision, HiredProductionOrder requested) {
        if (!permits(player, town, areaId)) return "Worksite permission or assignment changed";
        var role = town.areas().get(areaId).type().role();
        var error = HiredProductionService.validate(player.serverLevel(), role, requested);
        if (!error.isEmpty()) return error;
        var links = SettlementWorksiteService.links(town, areaId);
        if (requested.storage() != null && SettlementStock.outputs(town).stream()
                .noneMatch(depot -> depot.position().equals(requested.storage()) && links.permitsOutput(depot.id()))) {
            return "Choose a permitted output depot";
        }
        var queue = town.productionQueues().get(areaId);
        if (queue == null ? revision != 0L : queue.configurationRevision() != revision) return "Production orders changed";
        if (queue == null) queue = town.beginProduction(player.getUUID(), areaId);
        if (queue == null || !queue.append(requested)) return "Production queue is full or the order already exists";
        return "";
    }

    public static String edit(ServerPlayer player, SettlementRecord town, UUID areaId, long revision,
                              UUID order, HiredProductionService.Edit edit, int index) {
        if (!permits(player, town, areaId)) return "Worksite permission or assignment changed";
        var queue = town.productionQueues().get(areaId);
        if (queue == null || queue.configurationRevision() != revision) return "Production orders changed";
        return HiredProductionService.edit(queue, order, edit, index) ? "" : "Order has unfinished production or is unavailable";
    }

    private static boolean permits(ServerPlayer player, SettlementRecord town, UUID areaId) {
        return player != null && player.isAlive() && !player.isSpectator() && town != null
                && town.dimension().equals(player.level().dimension().location())
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.permits(player.getUUID(), SettlementPermission.STAFF)
                && town.workplace(areaId) != null && town.worksitePlans().containsKey(areaId);
    }
}
