package com.jvn.villagerretaliation.exploration;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "villagerretaliation")
public final class MapObservationEvents {
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 80 != 1 || !player.isAlive()) return;
        PacketDistributor.sendToPlayer(player, observation(player));
    }
    static MapObservationPayload observation(ServerPlayer player) {
        var level = player.serverLevel(); var pos = player.blockPosition();
        String id = "", name = "";
        // isVillage uses occupied POI distance data. Do not locate structures, generate chunks or send centers/rosters.
        if (player.isAlive() && !player.isSpectator() && level.hasChunkAt(pos)) {
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var village = registry.peekAt(level, pos).or(() -> level.isVillage(pos) ? registry.discoverAt(level, pos) : java.util.Optional.empty()).flatMap(registry::canonicalRecord).orElse(null);
            if (village != null && !village.archived()) {
                id = village.id().toString(); name = village.displayName();
                com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService.discover(player, village.id());
            }
        }
        return new MapObservationPayload(MapWorldIdentity.get(level), level.dimension().location(), pos, id, name);
    }
}
