package com.jvn.villagerretaliation.exploration;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.MapLocationRequest;
import com.jvn.villagerretaliation.network.MapLocationsPayload;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.party.PartySavedData;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "villagerretaliation")
public final class MapLocationService {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player);
    }

    public static void sync(ServerPlayer player) { sync(player, null); }

    private static void sync(ServerPlayer player, UUID acknowledgedSave) {
        if (player.connection == null || !player.connection.hasChannel(MapLocationsPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new MapLocationsPayload(MapLocationSavedData.get(player.serverLevel()).locations(player.getUUID()), acknowledgedSave));
    }

    public static void handle(ServerPlayer player, MapLocationRequest request) {
        if (!player.server.isSameThread()) return;
        var key = VillagerRetaliation.id("map_location_" + request.action().name().toLowerCase(java.util.Locale.ROOT));
        if (!ServerboundRequestLimiter.tryAcquire(player, key, request.action() == MapLocationRequest.Action.SHARE ? 40 : 2)) {
            message(player, "wait");
            if (request.action() == MapLocationRequest.Action.SAVE) sync(player, request.location().id());
            return;
        }
        var data = MapLocationSavedData.get(player.serverLevel());
        switch (request.action()) {
            case VIEW -> { }
            case SAVE -> {
                var location = request.location();
                var existing = data.locations(player.getUUID()).stream().filter(entry -> entry.id().equals(location.id())).findFirst().orElse(null);
                boolean validDimension = existing == null ? location.dimension().equals(player.level().dimension().location())
                        : location.dimension().equals(existing.dimension());
                if (!validDimension || !data.put(player.getUUID(), location)) {
                    message(player, "save_failed", MapLocation.MAX_LOCATIONS);
                }
            }
            case DELETE -> data.remove(player.getUUID(), request.selected());
            case SHARE -> {
                var recipients = recipients(player, request.audience(), request.recipient());
                if (recipients.isEmpty()) { message(player, "no_recipients"); break; }
                int copies = 0;
                for (UUID recipient : recipients) {
                    int added = data.share(player.getUUID(), recipient, request.selected());
                    copies += added;
                    var online = player.server.getPlayerList().getPlayer(recipient);
                    if (online != null && added > 0) {
                        sync(online);
                        message(online, "received", player.getDisplayName(), added);
                    }
                }
                message(player, "shared", copies);
            }
        }
        sync(player, request.action() == MapLocationRequest.Action.SAVE ? request.location().id() : null);
    }

    static LinkedHashSet<UUID> recipients(ServerPlayer player, MapLocationRequest.Audience audience, UUID recipient) {
        var result = new LinkedHashSet<UUID>();
        switch (audience) {
            case PARTY -> PartySavedData.get(player.serverLevel()).partyForPlayer(player.getUUID())
                    .ifPresent(party -> result.addAll(party.playerIds()));
            case ONLINE -> player.server.getPlayerList().getPlayers().forEach(online -> result.add(online.getUUID()));
            case PLAYER -> {
                if (recipient != null && player.server.getPlayerList().getPlayer(recipient) != null) result.add(recipient);
            }
        }
        result.remove(player.getUUID());
        return result;
    }

    private static void message(ServerPlayer player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("map.villagerretaliation.location." + key, arguments), false);
    }
}
