package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementBoundsPayload;
import com.jvn.villagerretaliation.network.SettlementBoundsRequestPayload;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementBoundsService {
    private SettlementBoundsService() { }

    public static void request(ServerPlayer player, SettlementBoundsRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementBoundsRequestPayload.TYPE.id(), 10L)) return;
        PacketDistributor.sendToPlayer(player, snapshot(player, request.town(), request.requestId()));
    }

    static SettlementBoundsPayload snapshot(ServerPlayer player, UUID id, int requestId) {
        var dimension = player.level().dimension().location();
        var town = SettlementSavedData.get(player.serverLevel()).get(new VillageAllegianceId(id)).orElse(null);
        if (!player.isAlive() || player.isSpectator() || town == null || !town.dimension().equals(dimension)
                || !town.permits(player.getUUID(), SettlementPermission.VIEW)) {
            return new SettlementBoundsPayload(id, dimension, requestId, false, List.of());
        }
        var center = player.chunkPosition();
        var plots = town.territory().plots().stream().map(ChunkPos::new)
                .filter(plot -> Math.abs((long) plot.x - center.x) <= 5 && Math.abs((long) plot.z - center.z) <= 5)
                .sorted(java.util.Comparator.comparingLong(ChunkPos::toLong)).toList();
        return new SettlementBoundsPayload(id, dimension, requestId, true, plots);
    }
}
