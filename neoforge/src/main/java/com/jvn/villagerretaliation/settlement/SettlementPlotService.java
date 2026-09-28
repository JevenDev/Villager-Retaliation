package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementPlotSnapshot;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementPlotService {
    private SettlementPlotService() {
    }

    public static String claim(ServerPlayer player, SettlementRecord town, int x, int z, boolean free, long revision, long quotedPrice) {
        if (player == null || !player.isAlive() || player.isSpectator() || town == null
                || !town.dimension().equals(player.level().dimension().location())) return "unavailable";
        var level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        if (town.territory().revision() != revision || SettlementPlotPricing.price(town.territory(), x, z) != quotedPrice) return "quote_changed";
        if (!SettlementTerritory.validChunk(x, z) || !level.getWorldBorder().isWithinBounds(new net.minecraft.world.level.ChunkPos(x, z))) return "world_border";
        String failure = failure(data, registry, town, player.getUUID(), x, z, free);
        if (!failure.isEmpty()) return failure;
        if (!free && !town.economy().withdraw(CurrencyAmount.of(quotedPrice, 1))) return "funds";
        town.territory().claim(x, z, free);
        data.claimed(registry, town);
        return "";
    }

    static String failure(SettlementSavedData data, VillageAllegianceRegistrySavedData registry, SettlementRecord town,
                          UUID actor, int x, int z, boolean free) {
        var territory = town.territory();
        if (!town.permits(actor, SettlementPermission.BUILD)
                || !free && !town.permits(actor, SettlementPermission.TREASURY)) return "permission";
        if (!territory.active()) return "inactive";
        if (!SettlementTerritory.validChunk(x, z)) return "world_border";
        if (territory.contains(x, z)) return "owned";
        if (data.reserved(registry, town, x, z)) return "reserved";
        if (!territory.adjacent(x, z)) return "adjacency";
        if (territory.plots().size() >= territory.capacity()) return town.stage().next().isPresent() ? "capacity" : "maximum_capacity";
        if (free && territory.freeClaims() == 0) return "credits";
        if (!free && town.economy().constructionFunds().compareTo(CurrencyAmount.of(SettlementPlotPricing.price(territory, x, z), 1)) < 0) return "funds";
        return "";
    }

    public static SettlementPlotSnapshot snapshot(ServerPlayer player, SettlementRecord town, SettlementViewWindow window) {
        var data = SettlementSavedData.get(player.serverLevel());
        var registry = VillageAllegianceRegistrySavedData.get(player.serverLevel());
        var plots = new ArrayList<SettlementPlotSnapshot.Plot>();
        for (var chunk : window.chunks()) {
            boolean owned = town.territory().contains(chunk.x, chunk.z);
            boolean eligible = !owned && town.territory().adjacent(chunk.x, chunk.z) && !data.reserved(registry, town, chunk.x, chunk.z);
            boolean inside = player.level().getWorldBorder().isWithinBounds(chunk);
            plots.add(new SettlementPlotSnapshot.Plot(chunk, owned, eligible && inside,
                    SettlementPlotPricing.price(town.territory(), chunk.x, chunk.z),
                    inside ? failure(data, registry, town, player.getUUID(), chunk.x, chunk.z, true) : "world_border",
                    inside ? failure(data, registry, town, player.getUUID(), chunk.x, chunk.z, false) : "world_border"));
        }
        return new SettlementPlotSnapshot(totals(town), plots);
    }

    public static SettlementPlotSnapshot.Totals totals(SettlementRecord town) {
        var territory = town.territory();
        return new SettlementPlotSnapshot.Totals(territory.plots().size(), territory.capacity(), territory.freeClaims(),
                territory.revision(), town.economy().constructionFunds(), territory.conflict());
    }
}
