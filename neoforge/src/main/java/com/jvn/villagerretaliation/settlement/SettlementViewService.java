package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
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
    private static final Map<UUID, Session> SESSIONS = new java.util.LinkedHashMap<>();
    private static final Map<MinecraftServer, Budget> BUDGETS = new java.util.IdentityHashMap<>();
    private static final int CHUNKS_PER_TICK = 2;
    private static final long SESSION_TIMEOUT = 400L;

    private SettlementViewService() {
    }

    public static void openCurrent(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementViewOpenPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 10L)) return;
        var level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var town = currentTown(player);
        if (town == null) {
            PacketDistributor.sendToPlayer(player, new com.jvn.villagerretaliation.network.SettlementViewOpenResultPayload(request.requestId(), null));
            return;
        }
        var summary = new com.jvn.villagerretaliation.network.SettlementManagementRequestPayload(town.id().value(),
                com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page.SUMMARY, 0, "", request.requestId(), false);
        data.simulation.requests.submit(player, summary, snapshot -> {
            if (player.serverLevel() != level || currentTown(player) != town || snapshot.header() == null
                    || !snapshot.header().id().equals(town.id().value())) {
                PacketDistributor.sendToPlayer(player, new com.jvn.villagerretaliation.network.SettlementViewOpenResultPayload(request.requestId(), null));
                return;
            }
            PacketDistributor.sendToPlayer(player, new com.jvn.villagerretaliation.network.SettlementViewOpenResultPayload(request.requestId(), snapshot));
            open(player, town, request.requestId(), player.getX(), player.getZ());
        });
    }

    static SettlementRecord currentTown(ServerPlayer player) {
        var town = SettlementSavedData.get(player.serverLevel())
                .at(player.serverLevel().dimension().location(), player.blockPosition()).orElse(null);
        return town != null && eligible(player, town) ? town : null;
    }

    public static boolean open(ServerPlayer player, VillageAllegianceId townId) {
        SettlementRecord town = SettlementSavedData.get(player.serverLevel()).get(townId).orElse(null);
        return town != null && open(player, town, 0, town.center().getX() + 0.5D, town.center().getZ() + 0.5D);
    }

    private static boolean open(ServerPlayer player, SettlementRecord town, int requestId, double x, double z) {
        if (!eligible(player, town)) {
            return false;
        }
        close(player.getUUID(), true);
        var session = new Session(player, town, SettlementViewWindow.around(player.serverLevel(), town, x, z, 32.0F),
                player.serverLevel().getGameTime());
        session.requestId = requestId;
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
        SettlementViewWindow window = SettlementViewWindow.around(session.level, session.town, payload.x(), payload.z(), payload.height());
        changeWindow(session, window);
        sendState(session, true);
        session.level.getChunkSource().move(player);
    }

    public static void projectPreview(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementProjectPreviewRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session()) || !eligible(player, session.town)
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 6L)) return;
        var project = session.town.projects().get(request.project());
        var preview = project == null || !project.unfinished() || !session.town.owns(project) ? null
                : SettlementBuildingPlanner.projectPreview(session.level, session.town, project, request.requestId());
        PacketDistributor.sendToPlayer(player, new com.jvn.villagerretaliation.network.SettlementProjectPreviewPayload(
                session.id, request.project(), request.requestId(), preview));
    }

    public static void overview(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementViewOverviewRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session()) || !eligible(player, session.town)
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 1L)) return;
        session.overview.submit(session, request, page -> PacketDistributor.sendToPlayer(player, page));
    }

    public static void command(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementViewBatchPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session != null && session.id.equals(request.session()) && eligible(player, session.town)
                && ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 2L)) session.commands.submit(session, request);
    }

    public static void wall(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementWallRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session())
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 2L)) return;
        var result = session.walls.request(session.level, session.id,
                eligible(player, session.town) && session.town.permits(player.getUUID(), SettlementPermission.BUILD),
                pos -> session.town.contains(pos) && session.window.contains(pos)
                        && session.sent.contains(new ChunkPos(pos)) && allowed(session, new ChunkPos(pos)), request, session.town.id(), player);
        PacketDistributor.sendToPlayer(player, result);
    }

    public static void infrastructure(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementInfrastructureRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session())
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 2L)) return;
        var result = session.infrastructure.request(player, session.town, session.id,
                eligible(player, session.town) && session.town.permits(player.getUUID(), SettlementPermission.BUILD),
                pos -> session.town.contains(pos) && session.window.contains(pos)
                        && session.sent.contains(new ChunkPos(pos)) && allowed(session, new ChunkPos(pos)), request);
        PacketDistributor.sendToPlayer(player, result);
    }

    public static void farm(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementFarmRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session())
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 2L)) return;
        var result = session.farms.request(player, session.town, session.id,
                eligible(player, session.town) && session.town.permits(player.getUUID(), SettlementPermission.BUILD)
                        && session.town.permits(player.getUUID(), SettlementPermission.STAFF),
                pos -> session.town.contains(pos) && session.window.contains(pos)
                        && session.sent.contains(new ChunkPos(pos)) && allowed(session, new ChunkPos(pos)), request);
        PacketDistributor.sendToPlayer(player, result);
    }

    public static void path(ServerPlayer player, com.jvn.villagerretaliation.network.SettlementPathRequestPayload request) {
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.id.equals(request.session())
                || !ServerboundRequestLimiter.tryAcquire(player, request.type().id(), 2L)) return;
        var result = session.paths.request(session.level, session.id,
                eligible(player, session.town) && session.town.permits(player.getUUID(), SettlementPermission.BUILD),
                pos -> session.town.contains(pos) && session.window.contains(pos)
                        && session.sent.contains(new ChunkPos(pos)) && allowed(session, new ChunkPos(pos)), request, session.town, player);
        PacketDistributor.sendToPlayer(player, result);
    }

    public static void tick(MinecraftServer server) {
        var budget = budget(server);
        budget.begin(server.overworld().getGameTime());
        var sessions = new ArrayList<Session>();
        for (var session : List.copyOf(SESSIONS.values())) {
            if (session.player.getServer() != server) continue;
            if (server.getPlayerList().getPlayer(session.player.getUUID()) != session.player
                    || !eligible(session.player, session.town)
                    || session.level.getGameTime() - session.lastRequest > SESSION_TIMEOUT) {
                close(session.player.getUUID(), true);
            } else sessions.add(session);
        }
        stream(sessions, budget);
    }

    static void stream(List<Session> sessions, Budget budget) {
        if (sessions.isEmpty()) return;
        int commandBudget = 16;
        int start = Math.floorMod(budget.cursor, sessions.size());
        for (int index = 0; index < sessions.size(); index++) {
            var session = sessions.get((start + index) % sessions.size());
            prepare(session, budget);
            int before = session.loads;
            load(session, budget);
            if (session.loads > before) budget.cursor = (start + index + 1) % sessions.size();
        }
        start = Math.floorMod(budget.sendCursor, sessions.size());
        for (int index = 0; index < sessions.size(); index++) {
            var session = sessions.get((start + index) % sessions.size());
            int before = session.sends;
            send(session, budget);
            if (session.sends > before) budget.sendCursor = (start + index + 1) % sessions.size();
        }
        start = (int) Math.floorMod(budget.tick, sessions.size());
        for (int index = 0; index < sessions.size(); index++) {
            var session = sessions.get((start + index) % sessions.size());
            commandBudget -= session.commands.tick(session, commandBudget);
        }
    }

    static void update(Session session) {
        var budget = budget(session.level.getServer());
        budget.begin(session.level.getServer().overworld().getGameTime());
        update(session, budget);
    }

    static void update(Session session, Budget budget) {
        prepare(session, budget);
        load(session, budget);
        send(session, budget);
    }

    private static void prepare(Session session, Budget budget) {
        if (session.level.getGameTime() % 10L == 0L) {
            var window = SettlementViewWindow.around(session.level, session.town, session.window.x(), session.window.z(), session.window.height());
            var plots = SettlementPlotService.snapshot(session.player, session.town, window);
            if (!window.equals(session.window) || !plots.equals(session.plots)) {
                changeWindow(session, window);
                sendState(session, true);
            }
        }
        if (session.budgetTick != budget.tick) {
            session.budgetTick = budget.tick;
            session.loads = 0;
            session.sends = 0;
        }
    }

    private static void load(Session session, Budget budget) {
        var chunks = session.window.chunks();
        int start = Math.floorMod(session.chunkCursor, chunks.size());
        for (int scanned = 0; scanned < chunks.size() && session.loads < CHUNKS_PER_TICK; scanned++) {
            var position = chunks.get((start + scanned) % chunks.size());
            if (session.held.contains(position) || !allowed(session, position)) continue;
            if (budget.acquire(session, position)) {
                session.loads++;
                session.chunkCursor = (start + scanned + 1) % chunks.size();
            }
        }
    }

    private static void send(Session session, Budget budget) {
        int sent = 0;
        for (ChunkPos position : session.window.chunks()) {
            if (!allowed(session, position)) continue;
            boolean normal = session.player.getChunkTrackingView().contains(position.x, position.z);
            if (normal) session.remote.remove(position);
            if (!session.held.contains(position) || session.sent.contains(position) && (normal || session.remote.contains(position))) continue;
            if (session.sends >= CHUNKS_PER_TICK || budget.sends >= sendLimit()) break;
            var chunk = session.level.getChunkSource().getChunkNow(position.x, position.z);
            if (chunk == null) continue;
            if (!normal) {
                PacketDistributor.sendToPlayer(session.player, new SettlementViewChunkPayload(session.id,
                        new ClientboundLevelChunkWithLightPacket(chunk, session.level.getLightEngine(), null, null)));
                session.remote.add(position);
                budget.sends++;
            }
            session.sent.add(position);
            session.sends++;
            sent++;
        }
        if (sent > 0 || session.level.getGameTime() % 20L == 0L) {
            session.level.getChunkSource().move(session.player);
            sendState(session, true);
        }
    }

    private static void changeWindow(Session session, SettlementViewWindow window) {
        for (var position : List.copyOf(session.held)) {
            if (!window.chunks().contains(position)) budget(session.level.getServer()).release(session, position);
        }
        session.sent.retainAll(window.chunks());
        session.remote.retainAll(window.chunks());
        session.window = window;
    }

    private static int chunkLimit() {
        return Math.clamp(VillagerRetaliationConfig.SETTLEMENT_VIEW_CHUNKS.get(), 1, 4096);
    }

    private static int loadLimit() {
        return Math.clamp(VillagerRetaliationConfig.SETTLEMENT_VIEW_LOADS.get(), 1, 64);
    }

    private static int sendLimit() {
        return Math.clamp(VillagerRetaliationConfig.SETTLEMENT_VIEW_SENDS.get(), 1, 64);
    }

    static Budget budget(MinecraftServer server) {
        return BUDGETS.computeIfAbsent(server, ignored -> new Budget());
    }

    public static Metrics metrics(MinecraftServer server) {
        var budget = budget(server);
        int viewers = 0;
        int queued = 0;
        for (var session : SESSIONS.values()) {
            if (session.level.getServer() != server) continue;
            viewers++;
            queued += session.window.chunks().size() - session.sent.size();
        }
        return new Metrics(viewers, budget.leases.size(), queued, budget.loads, budget.sends);
    }

    public record Metrics(int viewers, int chunks, int queued, int loads, int sends) {
    }

    private record Resource(ServerLevel level, ChunkPos position) {
    }

    private static final class Lease {
        final long createdAt;
        final Set<UUID> viewers = new HashSet<>();

        Lease(long now) {
            this.createdAt = now;
        }
    }

    static final class Budget {
        final UUID id = UUID.randomUUID();
        final Map<Resource, Lease> leases = new java.util.LinkedHashMap<>();
        long tick = Long.MIN_VALUE;
        int loads;
        int sends;
        int cursor;
        int sendCursor;

        void begin(long now) {
            if (this.tick == now) return;
            this.tick = now;
            this.loads = 0;
            this.sends = 0;
            while (this.leases.size() > chunkLimit()) evict(this.leases.keySet().iterator().next());
        }

        boolean acquire(Session session, ChunkPos position) {
            var key = new Resource(session.level, position);
            var lease = this.leases.get(key);
            if (lease == null) {
                if (this.loads >= loadLimit()) return false;
                if (this.leases.size() >= chunkLimit()) {
                    var oldest = this.leases.entrySet().iterator().next();
                    if (this.tick - oldest.getValue().createdAt < 40L) return false;
                    evict(oldest.getKey());
                }
                session.level.getChunkSource().addRegionTicket(TICKET, position, 2, this.id, true);
                lease = new Lease(this.tick);
                this.leases.put(key, lease);
                this.loads++;
            }
            lease.viewers.add(session.player.getUUID());
            session.held.add(position);
            return true;
        }

        void release(Session session, ChunkPos position) {
            var key = new Resource(session.level, position);
            var lease = this.leases.get(key);
            session.held.remove(position);
            session.sent.remove(position);
            if (lease == null) return;
            lease.viewers.remove(session.player.getUUID());
            if (lease.viewers.isEmpty()) {
                this.leases.remove(key);
                session.level.getChunkSource().removeRegionTicket(TICKET, position, 2, this.id, true);
            }
        }

        void evict(Resource key) {
            var lease = this.leases.remove(key);
            if (lease == null) return;
            // leases rotate under pressure so a stationary or repeatedly reopened view cannot monopolize tickets
            for (var viewer : lease.viewers) {
                var session = SESSIONS.get(viewer);
                if (session != null && session.level == key.level()) {
                    session.held.remove(key.position());
                    session.sent.remove(key.position());
                }
            }
            key.level().getChunkSource().removeRegionTicket(TICKET, key.position(), 2, this.id, true);
        }
    }

    public static boolean tracks(ServerPlayer player, Entity entity) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && entity.level() == session.level && eligible(player, session.town)
                && session.window.contains(entity.blockPosition()) && session.sent.contains(entity.chunkPosition())
                && allowed(session, entity.chunkPosition()) && session.level.getWorldBorder().isWithinBounds(entity.blockPosition());
    }

    public static List<ServerPlayer> viewers(ServerLevel level, ChunkPos position) {
        List<ServerPlayer> players = new ArrayList<>();
        for (Session session : SESSIONS.values()) {
            if (session.level == level && session.sent.contains(position) && session.window.chunks().contains(position)
                    && allowed(session, position) && eligible(session.player, session.town)) {
                players.add(session.player);
            }
        }
        return players;
    }

    private static boolean allowed(Session session, ChunkPos position) {
        return SettlementViewWindow.nearTerritory(session.town, position) && SettlementViewWindow.intersects(session.level.getWorldBorder(), position)
                && SettlementSavedData.get(session.level).owner(session.town.dimension(), position.x, position.z)
                        .map(session.town.id()::equals).orElse(true);
    }

    static boolean active(SettlementRecord town) {
        return SESSIONS.values().stream().anyMatch(session -> session.town == town);
    }

    static void closeTown(SettlementRecord town) {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.town == town) close(session.player.getUUID(), true);
        }
    }

    public static void close(UUID player, boolean notify) {
        Session session = SESSIONS.remove(player);
        if (session == null) {
            return;
        }
        for (ChunkPos position : List.copyOf(session.held)) budget(session.level.getServer()).release(session, position);
        session.overview.clear();
        session.commands.clear();
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
            if (session.player.getServer() == server) close(session.player.getUUID(), false);
        }
        var budget = BUDGETS.remove(server);
        if (budget != null) {
            for (var key : List.copyOf(budget.leases.keySet())) budget.evict(key);
        }
    }

    static Session session(UUID player) {
        return SESSIONS.get(player);
    }

    static boolean eligible(ServerPlayer player, SettlementRecord town) {
        return player.isAlive() && !player.isRemoved() && !player.isSpectator() && player.hurtTime == 0
                && player.level().dimension().location().equals(town.dimension())
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.territory().active()
                && (player.level().getWorldBorder().isWithinBounds(town.center())
                        || town.territory().plots().stream().anyMatch(position -> SettlementViewWindow.intersects(player.level().getWorldBorder(), new ChunkPos(position))))
                && town.permits(player.getUUID(), SettlementPermission.VIEW);
    }

    private static void sendState(Session session, boolean active) {
        session.plots = SettlementPlotService.snapshot(session.player, session.town, session.window);
        PacketDistributor.sendToPlayer(session.player, new SettlementViewStatePayload(session.id, session.town.id().value(),
                session.town.dimension(), session.requestId, session.window, active, session.plots,
                session.window.chunks().size() - session.sent.size()));
    }

    static final class Session {
        final UUID id = UUID.randomUUID();
        final ServerPlayer player;
        final ServerLevel level;
        final SettlementRecord town;
        final Set<ChunkPos> held = new HashSet<>();
        final Set<ChunkPos> sent = new HashSet<>();
        final Set<ChunkPos> remote = new HashSet<>();
        final SettlementViewCommands commands = new SettlementViewCommands();
        final SettlementViewOverview overview = new SettlementViewOverview();
        final SettlementPathSession paths = new SettlementPathSession();
        final SettlementInfrastructureSession infrastructure = new SettlementInfrastructureSession();
        final SettlementFarmSession farms = new SettlementFarmSession();
        final SettlementWallSession walls = new SettlementWallSession();
        SettlementViewWindow window;
        com.jvn.villagerretaliation.network.SettlementPlotSnapshot plots;
        int requestId;
        long lastRequest;
        long budgetTick = Long.MIN_VALUE;
        int loads;
        int sends;
        int chunkCursor;

        private Session(ServerPlayer player, SettlementRecord town, SettlementViewWindow window, long now) {
            this.player = player;
            this.level = player.serverLevel();
            this.town = town;
            this.window = window;
            this.lastRequest = now;
        }
    }
}
