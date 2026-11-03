package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import com.jvn.villagerretaliation.diplomacy.route.RouteGroundService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

@net.neoforged.fml.common.EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementRouteService {
    private record Draft(ServerLevel level, List<UUID> jobs, long expires) { }
    private static final java.util.Map<ServerPlayer, Draft> DRAFTS = new java.util.WeakHashMap<>();
    static void closePreview(ServerPlayer player) {
        var draft = DRAFTS.remove(player); if (draft != null) draft.jobs.forEach(id -> DiplomaticRouteService.forget(draft.level, id));
    }
    @net.neoforged.bus.api.SubscribeEvent public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        var players = DRAFTS.keySet().stream().filter(player -> player.server == event.getServer()).toList(); players.forEach(SettlementRouteService::closePreview);
        CURSORS.remove(event.getServer());
    }
    private static final java.util.Map<net.minecraft.server.MinecraftServer, Integer> CURSORS = new java.util.WeakHashMap<>();
    @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        if (now % 200 == 0) DRAFTS.entrySet().stream().filter(entry -> entry.getKey().server == event.getServer() && (entry.getKey().hasDisconnected() || now >= entry.getValue().expires))
                .map(java.util.Map.Entry::getKey).toList().forEach(SettlementRouteService::closePreview);
        var data = SettlementSavedData.get(event.getServer().overworld()); if (!SettlementAidService.enabled(event.getServer().overworld()) || data.aid().routes.isEmpty()) return;
        var routes = List.copyOf(data.aid().routes.values());
        int cursor = CURSORS.getOrDefault(event.getServer(), 0);
        int total = routes.stream().mapToInt(route -> route.wear.length).sum();
        for (int count = 0; count < Math.min(64, total); count++) {
            int index = Math.floorMod(cursor++, total);
            for (var route : routes) {
                if (index >= route.wear.length) { index -= route.wear.length; continue; }
                if (route.pendingWear[index] > 0) {
                    var level = event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, route.dimension));
                    if (level != null) develop(level, route, index);
                }
                break;
            }
        }
        CURSORS.put(event.getServer(), Math.floorMod(cursor, total));
    }
    private SettlementRouteService() { }
    public record Preview(DiplomaticRoute path, String problem, BlockPos blocked) {
        public Preview(DiplomaticRoute path, String problem) { this(path, problem, null); }
    }
    static boolean manages(ServerPlayer player, SettlementRoute route) {
        if (route == null || !route.dimension.equals(player.level().dimension().location())) return false;
        var town = SettlementSavedData.get(player.serverLevel()).get(route.origin).orElse(null);
        return town != null && town.permits(player.getUUID(), SettlementPermission.DIPLOMACY) && town.permits(player.getUUID(), SettlementPermission.VIEW);
    }
    static boolean endpoints(ServerPlayer player, VillageAllegianceId origin, VillageAllegianceId destination) {
        var data = SettlementSavedData.get(player.serverLevel());
        var a = data.get(origin).orElse(null); var b = data.get(destination).orElse(null);
        return a != null && b != null && a != b && a.territory().active() && b.territory().active()
                && a.dimension().equals(player.level().dimension().location()) && a.dimension().equals(b.dimension())
                && a.permits(player.getUUID(), SettlementPermission.DIPLOMACY)
                && VillageDiplomacyService.visible(player, origin).containsKey(destination);
    }
    static Preview preview(ServerPlayer player, UUID draft, VillageAllegianceId origin, VillageAllegianceId destination, List<BlockPos> via) {
        return preview(player, draft, origin, destination, via, null);
    }
    static Preview preview(ServerPlayer player, UUID draft, VillageAllegianceId origin, VillageAllegianceId destination, List<BlockPos> via, SettlementRouteShape shape) {
        if (shape != null && !shape.points().equals(via)) return new Preview(null, "Route nodes do not match waypoints");
        if (!endpoints(player, origin, destination) || via.size() > 12) return new Preview(null, "Choose two available, known settlements");
        var level = player.serverLevel(); var data = SettlementSavedData.get(level);
        var a = data.get(origin).orElseThrow(); var b = data.get(destination).orElseThrow();
        var from = SettlementAidStock.depots(a, false).stream().findFirst().orElse(null);
        var to = SettlementAidStock.depots(b, true).stream().findFirst().orElse(null);
        if (from == null || to == null) return new Preview(null, "Both settlements need an available supply depot");
        if (shape != null && shape.curved()) return shapedPreview(player, draft, shape.endpoints(from.position(), to.position()), from.position(), to.position());
        var points = new ArrayList<BlockPos>(); points.add(from.position()); points.addAll(via); points.add(to.position());
        var jobs = new ArrayList<UUID>(); for (int i = 0; i < points.size() - 1; i++) jobs.add(job(draft, points, i));
        var previous = DRAFTS.get(player);
        if (previous != null && (!previous.jobs.equals(jobs) || previous.level != level)) closePreview(player);
        DRAFTS.put(player, new Draft(level, List.copyOf(jobs), level.getServer().overworld().getGameTime() + 6000));
        var steps = new ArrayList<DiplomaticRoute.Step>();
        for (int i = 0; i < points.size() - 1; i++) {
            var id = job(draft, points, i);
            try {
                var preparation = DiplomaticRouteService.prepareAccess(level, id, points.get(i), points.get(i + 1), false, i == 0 ? 2 : 0, i == points.size() - 2 ? 2 : 0);
                var path = DiplomaticRouteService.ready(level, id);
                if (path == null) return new Preview(null, preparation.blocker().isEmpty() ? "Surveying route, preview again shortly" : preparation.blocker());
                if (!steps.isEmpty() && !steps.getLast().position().equals(path.steps().getFirst().position())) return new Preview(null, "Route sections do not connect");
                steps.addAll(path.steps().subList(steps.isEmpty() ? 0 : 1, path.steps().size()));
                if (steps.size() > SettlementRoute.MAX_STEPS) return new Preview(null, "Route is too long");
            } catch (IllegalStateException failure) {
                if (!"route_queue_full".equals(failure.getMessage()) && !"route_data_read_only".equals(failure.getMessage())) throw failure;
                return new Preview(null, failure.getMessage());
            }
        }
        return new Preview(new DiplomaticRoute(steps), "");
    }
    private static Preview shapedPreview(ServerPlayer player, UUID draft, SettlementRouteShape shape, BlockPos from, BlockPos to) {
        var level = player.serverLevel();
        var terrain = com.jvn.villagerretaliation.diplomacy.route.RouteSavedData.get(level);
        if (terrain.readOnly()) return new Preview(null, "Route data is read only");
        List<BlockPos> columns;
        try { columns = shape.columns(); }
        catch (IllegalArgumentException limit) { return new Preview(null, limit.getMessage()); }
        var rules = com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules.get(level.getServer());
        for (int i = 1; i < shape.nodes().size(); i++) {
            if (shape.nodes().get(i - 1).position().distanceToSqr(shape.nodes().get(i).position()) > (long) rules.shipmentRange() * rules.shipmentRange())
                return new Preview(null, "Route section exceeds shipment range");
        }
        var chunks = new java.util.LinkedHashMap<Long, BlockPos>();
        for (var column : columns) {
            if (!level.getWorldBorder().isWithinBounds(column)) return new Preview(null, "Curve crosses the world border", column);
            chunks.putIfAbsent(net.minecraft.world.level.ChunkPos.asLong(column.getX() >> 4, column.getZ() >> 4), column);
        }
        var identity = UUID.nameUUIDFromBytes((draft + ":" + shape.save()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var jobs = chunks.keySet().stream().map(chunk -> UUID.nameUUIDFromBytes((identity + ":" + chunk).getBytes(java.nio.charset.StandardCharsets.UTF_8))).toList();
        var previous = DRAFTS.get(player);
        if (previous != null && (!previous.jobs.equals(jobs) || previous.level != level)) closePreview(player);
        long now = level.getServer().overworld().getGameTime();
        DRAFTS.put(player, new Draft(level, jobs, now + 6000));
        int index = 0, surveying = 0;
        boolean missing = false;
        for (var column : chunks.values()) {
            var id = jobs.get(index++);
            var tile = terrain.observation(level.dimension().location(), column.getX(), column.getZ());
            if (tile != null && tile.observed() <= now && now - tile.observed() <= 24000) {
                DiplomaticRouteService.forget(level, id); continue;
            }
            missing = true;
            if (surveying++ >= 8) continue;
            var existing = terrain.get(id);
            if (existing != null && existing.state() != com.jvn.villagerretaliation.diplomacy.route.RoutePreparation.State.PREPARING) DiplomaticRouteService.forget(level, id);
            var point = new BlockPos(column.getX(), from.getY(), column.getZ());
            try { DiplomaticRouteService.prepareAccess(level, id, point, point, false, 0, 0); }
            catch (IllegalStateException failure) {
                if (!"route_queue_full".equals(failure.getMessage()) && !"route_data_read_only".equals(failure.getMessage())) throw failure;
                return new Preview(null, failure.getMessage());
            }
        }
        if (missing) return new Preview(null, "Surveying route, preview again shortly");
        var steps = new ArrayList<DiplomaticRoute.Step>();
        for (var column : columns) {
            var tile = terrain.observation(level.dimension().location(), column.getX(), column.getZ());
            var position = tile.position(column.getX(), column.getZ());
            boolean walk = tile.kind(column.getX(), column.getZ()) == com.jvn.villagerretaliation.diplomacy.route.RouteTerrain.WALK;
            if (steps.isEmpty() && nearDepot(from, position) && !walk) continue;
            if (!walk || !steps.isEmpty() && Math.abs(steps.getLast().position().getY() - position.getY()) > 1) {
                if (nearDepot(to, position) && !steps.isEmpty() && nearDepot(to, steps.getLast().position())) break;
                return new Preview(null, "Curve crosses blocked or steep ground at " + position.getX() + ", " + position.getZ(), position);
            }
            if (steps.isEmpty() && !nearDepot(from, position)) return new Preview(null, "Curve has no safe access to the origin depot", position);
            steps.add(new DiplomaticRoute.Step(position, DiplomaticRoute.Mode.WALK));
        }
        if (steps.size() < 2 || !nearDepot(to, steps.getLast().position())) return new Preview(null, "Curve has no safe access to the destination depot");
        return new Preview(new DiplomaticRoute(steps), "");
    }
    private static boolean nearDepot(BlockPos depot, BlockPos position) {
        return Math.abs(depot.getX() - position.getX()) <= 2 && Math.abs(depot.getZ() - position.getZ()) <= 2 && Math.abs(depot.getY() - position.getY()) <= 2;
    }
    private static UUID job(UUID draft, List<BlockPos> points, int index) {
        return UUID.nameUUIDFromBytes((draft + ":" + points + ":" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static String save(ServerPlayer player, UUID id, VillageAllegianceId origin, VillageAllegianceId destination, String name, long revision, List<BlockPos> via) {
        return save(player, id, origin, destination, name, revision, via, null);
    }
    static String save(ServerPlayer player, UUID id, VillageAllegianceId origin, VillageAllegianceId destination, String name, long revision, List<BlockPos> via, SettlementRouteShape shape) {
        var data = SettlementSavedData.get(player.serverLevel()); var previous = data.aid().routes.get(id);
        if (data.readOnly() || !data.aid().writable()) return "Settlement data is read only";
        if (previous != null && (!previous.origin.equals(origin) || !previous.destination.equals(destination) || previous.revision != revision)) return "Route changed, refresh first";
        if (previous == null && revision != 0) return "Route was removed, refresh first";
        if (previous == null && data.aid().routes.size() >= 128) return "Route limit reached";
        if (previous != null && building(data, previous)) return "Finish or cancel this route's construction before editing";
        var preview = preview(player, id, origin, destination, via, shape); if (preview.path == null) return preview.problem;
        data.aid().changed();
        var route = new SettlementRoute(id, origin, destination, player.level().dimension().location(), name, preview.path, data.aid().revision());
        route.points = List.copyOf(via); route.shape = shape;
        if (previous != null) {
            var edges = new java.util.HashMap<List<BlockPos>, Integer>();
            for (int i = 0; i < previous.wear.length; i++) edges.put(List.of(previous.path.steps().get(i).position(), previous.path.steps().get(i + 1).position()), i);
            for (int i = 0; i < route.wear.length; i++) {
                var old = edges.get(List.of(route.path.steps().get(i).position(), route.path.steps().get(i + 1).position()));
                if (old != null) { route.wear[i] = previous.wear[old]; route.built[i] = previous.built[old]; route.pendingWear[i] = previous.pendingWear[old]; }
            }
        }
        data.aid().routes.put(id, route); data.setDirty(); closePreview(player); return "Saved. Active journeys keep their departure path and benefits";
    }
    static boolean building(SettlementSavedData data, SettlementRoute route) {
        var town = data.get(route.origin).orElse(null);
        return town != null && java.util.Arrays.stream(route.projects).filter(java.util.Objects::nonNull)
                .anyMatch(id -> town.projects().containsKey(id) && town.projects().get(id).unfinished());
    }
    static String remove(ServerPlayer player, SettlementRoute route, long revision) {
        var data = SettlementSavedData.get(player.serverLevel());
        if (!manages(player, route)) return "Route is unavailable";
        if (data.readOnly() || !data.aid().writable() || route.revision != revision) return "Route changed or data is read only";
        if (building(data, route)) return "Finish or cancel construction before removing the route";
        data.aid().routes.remove(route.id); data.aid().changed(); data.setDirty();
        return "Removed. Active journeys retain their path, participants and cargo";
    }
    static void travelled(ServerLevel level, SettlementCourierJourney journey) {
        if (journey.customRoute == null || journey.detour != null || journey.step == 0
                || journey.leg != SettlementCourierJourney.Leg.OUTBOUND && journey.leg != SettlementCourierJourney.Leg.HOMEBOUND) return;
        var data = SettlementSavedData.get(level); var route = data.aid().routes.get(journey.customRoute);
        if (route == null || route.revision != journey.routeRevision || data.readOnly()) return;
        boolean reverse = journey.reversedRoute ^ journey.leg == SettlementCourierJourney.Leg.HOMEBOUND;
        int edge = reverse ? route.wear.length - journey.step : journey.step - 1;
        route.pendingWear[edge] = (byte) Math.min(SettlementRoute.WEAR_LIMIT, route.pendingWear[edge] + 1);
        data.setDirty(); develop(level, route, edge);
    }
    static void develop(ServerLevel level, SettlementRoute route, int edge) {
        var data = SettlementSavedData.get(level);
        var ground = route.path.steps().get(edge + 1).position().below(); int section = edge / SettlementRoute.SECTION_LENGTH;
        if (!level.hasChunkAt(ground)) { route.blockers[section] = "Trail development waits for loaded terrain"; data.setDirty(); return; }
        if (route.built[edge] == 1 && level.getBlockState(ground).is(Blocks.COBBLESTONE)) { route.pendingWear[edge] = 0; data.setDirty(); return; }
        String problem = RouteGroundService.customGroundProblem(level, ground, route.origin);
        if (!problem.isEmpty()) { route.blockers[section] = problem; route.wear[edge] = 0; route.pendingWear[edge] = 0; route.built[edge] = 0; data.setDirty(); return; }
        int next = Math.min(SettlementRoute.WEAR_LIMIT, route.wear[edge] + route.pendingWear[edge]);
        if (RouteGroundService.wearGround(level, ground, next, route.origin)) { route.wear[edge] = (byte) next; route.pendingWear[edge] = 0; }
        route.blockers[section] = ""; data.setDirty();
    }
    static String construct(ServerPlayer player, SettlementRoute route, int section) {
        if (route == null || section < 0 || section >= route.sections() || !endpoints(player, route.origin, route.destination)) return "Section is unavailable";
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var town = data.get(route.origin).orElseThrow();
        if (data.readOnly()) return "Settlement data is read only";
        if (route.projects[section] != null && town.projects().containsKey(route.projects[section]) && town.projects().get(route.projects[section]).unfinished()) return "Section construction is already queued";
        var cells = new ArrayList<SettlementPathTerrain.Cell>();
        int from = section * SettlementRoute.SECTION_LENGTH, to = Math.min(route.wear.length, from + SettlementRoute.SECTION_LENGTH);
        for (int edge = from; edge < to; edge++) {
            var ground = route.path.steps().get(edge + 1).position().below();
            if (level.hasChunkAt(ground) && level.getBlockState(ground).is(Blocks.COBBLESTONE)) { route.built[edge] = 1; continue; }
            String problem = RouteGroundService.customGroundProblem(level, ground, route.origin);
            if (!problem.isEmpty()) { route.blockers[section] = problem; data.setDirty(); return problem; }
            cells.add(new SettlementPathTerrain.Cell(ground, SettlementPathTerrain.Kind.CONVERT, level.getBlockState(ground),
                    level.getBlockState(ground.above()), level.getBlockState(ground.above(2)), Blocks.COBBLESTONE.defaultBlockState(), level.getBlockState(ground.below())));
        }
        if (cells.isEmpty()) { data.setDirty(); return "Section is already built"; }
        var preview = new SettlementPathTerrain.Preview(cells, "", SettlementPath.Material.COBBLESTONE);
        var order = SettlementPathTerrain.order(preview); order.route(route.id);
        var project = UUID.randomUUID(); String problem = order.commission(player, town, project);
        if (problem.isEmpty()) { route.projects[section] = project; route.blockers[section] = "Construction queued. " + preview.materials(); data.setDirty(); }
        return problem.isEmpty() ? route.blockers[section] : problem;
    }
    static void refresh(ServerLevel level, SettlementRoute route) {
        var data = SettlementSavedData.get(level); var town = data.get(route.origin).orElse(null); if (town == null || data.readOnly() || !data.aid().writable()) return;
        for (int section = 0; section < route.sections(); section++) {
            var project = route.projects[section] == null ? null : town.projects().get(route.projects[section]);
            if (project == null && route.projects[section] != null) project = town.projectHistory().get(route.projects[section]);
            String problem = "";
            for (int edge = section * SettlementRoute.SECTION_LENGTH; edge < Math.min(route.wear.length, (section + 1) * SettlementRoute.SECTION_LENGTH); edge++) {
                var ground = route.path.steps().get(edge + 1).position().below();
                if (!level.hasChunkAt(ground)) { if (problem.isEmpty()) problem = "Unloaded terrain retains its last observed progress"; continue; }
                boolean built = level.getBlockState(ground).is(Blocks.COBBLESTONE);
                String obstruction = built ? "" : RouteGroundService.customGroundProblem(level, ground, route.origin);
                if (!obstruction.isEmpty()) { route.wear[edge] = 0; problem = obstruction; }
                route.built[edge] = (byte) (built ? 1 : 0);
            }
            route.blockers[section] = project == null ? problem : project.status() + (project.blocker().isEmpty() ? "" : ": " + project.blocker())
                    + (problem.isEmpty() ? "" : ". " + problem);
        }
        data.setDirty();
    }
}
