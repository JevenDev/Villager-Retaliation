package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementViewChunkPayload;
import com.jvn.villagerretaliation.network.SettlementViewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementViewStatePayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementViewService {
    private static final TicketType<UUID> TICKET = TicketType.create("villagerretaliation_settlement_view", Comparator.<UUID>naturalOrder());
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final int CHUNKS_PER_TICK = 2;
    private static final long SESSION_TIMEOUT = 400L;

    private SettlementViewService() {
    }

    public static boolean open(ServerPlayer player, VillageAllegianceId townId) {
        SettlementRecord town = SettlementSavedData.get(player.serverLevel()).get(townId).orElse(null);
        if (town == null || !eligible(player, town)) {
            return false;
        }
        close(player.getUUID(), true);
        var session = new Session(player, town, SettlementViewWindow.around(town, town.center().getX() + 0.5D,
                town.center().getZ() + 0.5D, 32.0F), player.serverLevel().getGameTime());
        SESSIONS.put(player.getUUID(), session);
        sendState(session, true);
        return true;
    }

    public static void request(ServerPlayer player, SettlementViewRequestPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(payload.session())) {
            return;
        }
        if (payload.close()) {
            close(player.getUUID(), true);
            return;
        }
        if (!eligible(player, session.town)) {
            close(player.getUUID(), true);
            return;
        }
        if (payload.requestId() <= session.requestId
                || !ServerboundRequestLimiter.tryAcquire(player, SettlementViewRequestPayload.TYPE.id(), 2L)) {
            return;
        }
        session.requestId = payload.requestId();
        session.lastRequest = session.level.getGameTime();
        SettlementViewWindow window = SettlementViewWindow.around(session.town, payload.x(), payload.z(), payload.height());
        for (ChunkPos position : List.copyOf(session.held)) {
            if (!window.chunks().contains(position)) {
                session.level.getChunkSource().removeRegionTicket(TICKET, position, 2, session.id, true);
                session.held.remove(position);
                session.sent.remove(position);
                session.remote.remove(position);
            }
        }
        session.window = window;
        sendState(session, true);
        session.level.getChunkSource().move(player);
    }

    public static void overview(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session()) || !eligible(player, session.town)
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 1L)) return;
        var page = session.overview.page(session, request);
        if (page != null) PacketDistributor.sendToPlayer(player, page);
    }

    public static void tick(MinecraftServer server) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.player.getServer() != server) {
                continue;
            }
            if (server.getPlayerList().getPlayer(session.player.getUUID()) != session.player
                    || !eligible(session.player, session.town)
                    || session.level.getGameTime() - session.lastRequest > SESSION_TIMEOUT) {
                close(session.player.getUUID(), true);
                continue;
            }
            update(session);
        }
    }

    static void update(Session session) {
        if (session.level.getGameTime() % 10L == 0L) {
            var plots = SettlementPlotService.snapshot(session.player, session.town, session.window);
            if (!plots.equals(session.plots)) {
                session.window = SettlementViewWindow.around(session.town, session.window.x(), session.window.z(), session.window.height());
                sendState(session, true);
            }
        }
        int tickets = 0;
        for (ChunkPos position : session.window.chunks()) {
            if (!session.held.contains(position)) {
                session.level.getChunkSource().addRegionTicket(TICKET, position, 2, session.id, true);
                session.held.add(position);
                if (++tickets >= CHUNKS_PER_TICK) {
                    break;
                }
            }
        }
        int sent = 0;
        for (ChunkPos position : session.window.chunks()) {
            boolean normal = session.player.getChunkTrackingView().contains(position.x, position.z);
            if (normal) {
                session.remote.remove(position);
            }
            if (!session.held.contains(position) || session.sent.contains(position) && (normal || session.remote.contains(position))) {
                continue;
            }
            var chunk = session.level.getChunkSource().getChunkNow(position.x, position.z);
            if (chunk == null) {
                continue;
            }
            if (!normal) {
                PacketDistributor.sendToPlayer(session.player, new SettlementViewChunkPayload(session.id,
                        new ClientboundLevelChunkWithLightPacket(chunk, session.level.getLightEngine(), null, null)));
                session.remote.add(position);
            }
            session.sent.add(position);
            if (++sent >= CHUNKS_PER_TICK) {
                break;
            }
        }
        if (sent > 0 || session.level.getGameTime() % 20L == 0L) {
            session.level.getChunkSource().move(session.player);
        }
    }

    public static boolean tracks(ServerPlayer player, Entity entity) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && entity.level() == session.level && eligible(player, session.town)
                && session.window.contains(entity.blockPosition()) && session.sent.contains(entity.chunkPosition());
    }

    public static List<ServerPlayer> viewers(ServerLevel level, ChunkPos position) {
        List<ServerPlayer> players = new ArrayList<>();
        for (Session session : SESSIONS.values()) {
            if (session.level == level && session.sent.contains(position) && eligible(session.player, session.town)) {
                players.add(session.player);
            }
        }
        return players;
    }

    public static void close(UUID player, boolean notify) {
        Session session = SESSIONS.remove(player);
        if (session == null) {
            return;
        }
        for (ChunkPos position : session.held) {
            session.level.getChunkSource().removeRegionTicket(TICKET, position, 2, session.id, true);
        }
        session.held.clear();
        session.sent.clear();
        session.remote.clear();
        session.level.getChunkSource().move(session.player);
        if (notify && session.player.connection != null && session.player.connection.isAcceptingMessages()) {
            sendState(session, false);
        }
    }

    public static void clear(MinecraftServer server) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.player.getServer() == server) {
                close(session.player.getUUID(), false);
            }
        }
    }

    static Session session(UUID player) {
        return SESSIONS.get(player);
    }

    private static boolean eligible(ServerPlayer player, SettlementRecord town) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && player.hurtTime == 0
                && player.level().dimension().location().equals(town.dimension())
                && town.permits(player.getUUID(), SettlementPermission.VIEW);
    }

    private static void sendState(Session session, boolean active) {
        session.plots = SettlementPlotService.snapshot(session.player, session.town, session.window);
        PacketDistributor.sendToPlayer(session.player, new SettlementViewStatePayload(session.id, session.town.id().value(),
                session.town.dimension(), session.requestId, session.window, active, session.plots));
    }

    static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final ServerLevel level;
        final SettlementRecord town;
        final Set<ChunkPos> held = new HashSet<>();
        final Set<ChunkPos> sent = new HashSet<>();
        final Set<ChunkPos> remote = new HashSet<>();
        final SettlementViewOverview overview = new SettlementViewOverview();
        SettlementViewWindow window;
        com.jvn.villagerretaliation.network.SettlementPlotSnapshot plots;
        int requestId;
        long lastRequest;

        private Session(ServerPlayer player, SettlementRecord town, SettlementViewWindow window, long now) {
            this.player = player;
            this.level = player.serverLevel();
            this.town = town;
            this.window = window;
            this.lastRequest = now;
        }
    }
}
