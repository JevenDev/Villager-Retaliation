package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.progress.ProgressHistoryService;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementContributionService {
    private SettlementContributionService() { }

    public static int contributeHeld(ServerPlayer player, VillageAllegianceId id, int maximum) {
        if (player == null || !player.isAlive() || player.isSpectator() || maximum <= 0 || maximum > 64) return 0;
        var level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        if (data.readOnly()) return 0;
        var town = data.get(id).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location()) || !town.contains(player.blockPosition())
                || !town.permits(player.getUUID(), SettlementPermission.LOGISTICS)) return 0;
        var stack = player.getMainHandItem();
        if (stack.isEmpty()) return 0;
        int offered = Math.min(maximum, stack.getCount());
        var depots = SettlementStock.supplies(town).stream().filter(depot -> level.hasChunkAt(depot.position())
                && player.distanceToSqr(depot.position().getCenter()) <= 64
                && level.getBlockEntity(depot.position()) instanceof com.jvn.villagerretaliation.block.SettlementDepotBlockEntity block
                && block.inventory() == depot && block.stillValid(player)).toList();
        for (var depot : depots) SettlementDepotService.observeRules(level, depot);
        var remainder = SettlementStock.deposit(depots, stack.copyWithCount(offered));
        int delivered = offered - remainder.getCount();
        if (delivered > 0) {
            var contribution = stack.copyWithCount(delivered);
            stack.shrink(delivered);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            ProgressHistoryService.itemsContributed(level, player, town.id(), contribution, delivered);
        }
        return delivered;
    }
}
