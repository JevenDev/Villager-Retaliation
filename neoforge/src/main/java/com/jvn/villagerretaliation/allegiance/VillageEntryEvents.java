package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.network.VillageEntryPayload;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "villagerretaliation")
public final class VillageEntryEvents {
    private static final Map<ServerPlayer, VillageEntryTracker> TRACKERS = new WeakHashMap<>();

    private VillageEntryEvents() { }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.isAlive() || player.isSpectator()) {
            TRACKERS.remove(player);
            return;
        }
        if (player.tickCount % 10 != 0) return;
        var level = player.serverLevel();
        var pos = player.blockPosition();
        if (!level.hasChunkAt(pos)) return;
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var village = registry.peekAt(level, pos)
                .or(() -> level.isVillage(pos) ? registry.discoverAt(level, pos) : java.util.Optional.empty())
                .flatMap(registry::canonicalRecord).filter(record -> !record.archived()).orElse(null);
        var tracker = TRACKERS.computeIfAbsent(player, ignored -> new VillageEntryTracker());
        if (tracker.update(level.dimension().location(), village == null ? null : village.id(),
                player.server.overworld().getGameTime()) && village != null) {
            PacketDistributor.sendToPlayer(player, new VillageEntryPayload(level.dimension().location(),
                    village.displayName(), VillageOpinion.of(level, village.id(), player.getUUID())));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity());
    }
}
