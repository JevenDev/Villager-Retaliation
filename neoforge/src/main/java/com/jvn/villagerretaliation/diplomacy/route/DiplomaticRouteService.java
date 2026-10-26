package com.jvn.villagerretaliation.diplomacy.route;

import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules;
import com.mojang.logging.LogUtils;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class DiplomaticRouteService {
    private static final Map<MinecraftServer, Scheduler> SCHEDULERS = new ConcurrentHashMap<>();
    private DiplomaticRouteService() { }
    public record Metrics(int queued, int tickets, long tickNanos, long maximumStepNanos, long dependencyBudget,
                          long generationSubmissions, long searchSubmissions, long observedTiles, long generatedDependencies) { }

    public static RoutePreparation prepare(ServerLevel level, UUID id, BlockPos start, BlockPos end, boolean boatAllowed) {
        return prepareAccess(level, id, start, end, boatAllowed, 0, 0);
    }
    public static RoutePreparation prepareAccess(ServerLevel level, UUID id, BlockPos start, BlockPos end, boolean boatAllowed,
                                                 int startTolerance, int endTolerance) {
        requireServerThread(level);
        var data = RouteSavedData.get(level);
        var existing = data.get(id);
        if (existing != null) {
            if (!existing.dimension().equals(level.dimension().location()) || !existing.start().equals(start)
                    || !existing.end().equals(end) || existing.boatAllowed() != boatAllowed
                    || existing.startTolerance() != startTolerance || existing.endTolerance() != endTolerance)
                throw new IllegalArgumentException("Route identity already assigned to different endpoints");
            return existing;
        }
        if (data.readOnly()) throw new IllegalStateException("route_data_read_only");
        if (data.jobs.size() >= RouteSavedData.JOB_LIMIT) throw new IllegalStateException("route_queue_full");
        var rules = VillageDiplomacyRules.get(level.getServer());
        var result = new RoutePreparation(id, level.dimension().location(), start, end, boatAllowed, startTolerance, endTolerance);
        if (!level.getWorldBorder().isWithinBounds(start) || !level.getWorldBorder().isWithinBounds(end)) result.block("world_border");
        else if (start.distSqr(end) > (long) rules.shipmentRange() * rules.shipmentRange()) result.block("shipment_range");
        else {
            var terrain = data.snapshot(result.dimension(), bounds(level, result, rules), level.getServer().overworld().getGameTime());
            for (var cached : data.jobs.values()) {
                if (cached.state != RoutePreparation.State.READY || !cached.dimension().equals(result.dimension())) continue;
                DiplomaticRoute route = null;
                if (cached.start().equals(start) && cached.end().equals(end) && cached.startTolerance() == startTolerance && cached.endTolerance() == endTolerance) route = cached.route;
                else if (cached.start().equals(end) && cached.end().equals(start) && cached.startTolerance() == endTolerance && cached.endTolerance() == startTolerance) route = cached.route.reversed();
                if (route != null && (boatAllowed || !route.needsBoat()) && RouteSearch.valid(route, terrain)) {
                    result.route = route;
                    result.state = RoutePreparation.State.READY;
                    break;
                }
            }
        }
        data.jobs.put(id, result);
        data.changed();
        scheduler(level.getServer());
        return result;
    }

    public static DiplomaticRoute ready(ServerLevel level, UUID id) {
        requireServerThread(level);
        var data = RouteSavedData.get(level);
        var job = data.get(id);
        if (data.readOnly() || job == null || job.state != RoutePreparation.State.READY
                || !job.dimension().equals(level.dimension().location())) return null;
        if (RouteSearch.valid(job.route, data.snapshot(job.dimension(), bounds(level, job, VillageDiplomacyRules.get(level.getServer())),
                level.getServer().overworld().getGameTime()))) return job.route;
        job.state = RoutePreparation.State.PREPARING;
        job.route = null;
        job.blocker = "terrain_changed";
        job.width = 1;
        data.changed();
        return null;
    }

    public static boolean retry(ServerLevel level, UUID id) {
        requireServerThread(level);
        var data = RouteSavedData.get(level);
        var job = data.get(id);
        if (data.readOnly() || job == null || job.state != RoutePreparation.State.BLOCKED
                || job.attempts >= VillageDiplomacyRules.get(level.getServer()).routeRetries()) return false;
        job.attempts++;
        job.state = RoutePreparation.State.PREPARING;
        job.blocker = "";
        job.width = 1;
        data.changed();
        return true;
    }
    public static void cancel(ServerLevel level, UUID id) {
        requireServerThread(level);
        var data = RouteSavedData.get(level);
        var job = data.get(id);
        if (data.readOnly() || job == null) return;
        job.state = RoutePreparation.State.CANCELLED;
        job.blocker = "cancelled";
        data.changed();
        var scheduler = SCHEDULERS.get(level.getServer());
        if (scheduler != null) scheduler.cancel(id);
    }
    public static void forget(ServerLevel level, UUID id) {
        cancel(level, id);
        var data = RouteSavedData.get(level);
        if (!data.readOnly() && data.jobs.remove(id) != null) data.changed();
    }
    public static void terrainChanged(ServerLevel level, BlockPos position) {
        var scheduler = SCHEDULERS.get(level.getServer());
        if (scheduler == null) return;
        var key = new RouteSavedData.Key(level.dimension().location(), ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
        if (!level.getServer().isSameThread()) {
            if (!scheduler.invalidations.offer(key)) scheduler.invalidateAll.set(true);
            return;
        }
        scheduler.invalidate(key);
    }
    private static void requireServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Diplomatic routes require the server thread");
    }
    private static Scheduler scheduler(MinecraftServer server) { return SCHEDULERS.computeIfAbsent(server, Scheduler::new); }
    public static Metrics metrics(MinecraftServer server) {
        var scheduler = SCHEDULERS.get(server);
        return scheduler == null ? new Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0) : scheduler.metrics();
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) { scheduler(event.getServer()).tick(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var scheduler = SCHEDULERS.remove(event.getServer());
        if (scheduler != null) scheduler.close();
    }
    private static RouteSearch.Bounds bounds(ServerLevel level, RoutePreparation job, VillageDiplomacyRules.Rules rules) {
        int extra = rules.routeDetourChunks();
        var border = level.getWorldBorder();
        return new RouteSearch.Bounds(Math.max((Math.min(job.start().getX(), job.end().getX()) >> 4) - extra, (int) Math.ceil(border.getMinX() / 16)),
                Math.max((Math.min(job.start().getZ(), job.end().getZ()) >> 4) - extra, (int) Math.ceil(border.getMinZ() / 16)),
                Math.min((Math.max(job.start().getX(), job.end().getX()) >> 4) + extra, (int) Math.floor((border.getMaxX() - 16) / 16)),
                Math.min((Math.max(job.start().getZ(), job.end().getZ()) >> 4) + extra, (int) Math.floor((border.getMaxZ() - 16) / 16)));
    }

    private static final class Work {
        final RoutePreparation job;
        final ArrayDeque<Long> wanted = new ArrayDeque<>();
        Work(RoutePreparation job) { this.job = job; }
    }
    private static final class Observation {
        final Work work;
        final ServerLevel level;
        final ChunkPos position;
        final long started;
        CompletableFuture<Optional<CompoundTag>> disk;
        CompletableFuture<BlockGetter> decoded;
        RouteSurfaceReader reader;
        ChunkAccess live;
        boolean generationNeeded;
        boolean invalidated;
        Observation(Work work, ServerLevel level, ChunkPos position, long now) {
            this.work = work; this.level = level; this.position = position; this.started = now;
        }
    }
    private static final class Scheduler {
        final MinecraftServer server;
        final RouteSavedData data;
        final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1), task -> {
            var thread = new Thread(task, "villager-diplomatic-routes");
            thread.setDaemon(true);
            return thread;
        });
        final Map<UUID, Work> work = new HashMap<>();
        final ArrayDeque<UUID> queue = new ArrayDeque<>();
        final ArrayBlockingQueue<RouteSavedData.Key> invalidations = new ArrayBlockingQueue<>(4096);
        final AtomicBoolean invalidateAll = new AtomicBoolean();
        Observation observation;
        RouteGeneration generation;
        CompletableFuture<RouteSearch.Result> search;
        CompletableFuture<?> drainingDisk;
        Work searching;
        Map<Long, RouteTerrain> input;
        long nextGeneration, tickNanos, maximumStepNanos, dependencyBudget, generationSubmissions, searchSubmissions, observedTiles, generatedDependencies;
        int maintenance;

        Scheduler(MinecraftServer server) {
            this.server = server;
            this.data = RouteSavedData.get(server.overworld());
            this.nextGeneration = server.overworld().getGameTime() + VillageDiplomacyRules.get(server).generationInterval();
        }
        void tick() {
            long start = System.nanoTime();
            try { step(); }
            finally { this.tickNanos = System.nanoTime() - start; this.maximumStepNanos = Math.max(this.maximumStepNanos, this.tickNanos); }
        }
        void invalidate(RouteSavedData.Key key) {
            if (this.data.readOnly()) return;
            if (this.data.terrain.remove(key) != null) this.data.changed();
            if (this.observation != null && this.observation.work.job.dimension().equals(key.dimension())
                    && this.observation.position.toLong() == key.chunk()) this.observation.invalidated = true;
        }
        void step() {
            if (this.invalidateAll.getAndSet(false) && !this.data.readOnly()) {
                this.data.terrain.clear(); this.data.changed();
                if (this.observation != null) this.observation.invalidated = true;
            }
            for (int index = 0; index < 64; index++) {
                var key = this.invalidations.poll();
                if (key == null) break;
                invalidate(key);
            }
            var rules = VillageDiplomacyRules.get(this.server);
            long now = this.server.overworld().getGameTime();
            if (this.data.readOnly() || !rules.enabled() || !VillageDiplomacyRules.problem().isEmpty()) {
                if (this.observation != null) cancelObservation();
                if (this.search != null) { this.search.cancel(true); this.search = null; this.searching = null; this.input = null; }
                return;
            }
            if (++this.maintenance >= 20) {
                this.maintenance = 0;
                for (var job : this.data.jobs.values()) if (job.state == RoutePreparation.State.PREPARING && !this.work.containsKey(job.id())) {
                    this.work.put(job.id(), new Work(job)); this.queue.add(job.id());
                }
                this.work.entrySet().removeIf(entry -> entry.getValue().job.state != RoutePreparation.State.PREPARING);
                this.queue.removeIf(id -> !this.work.containsKey(id));
            }
            if (this.search != null && this.search.isDone()) finishSearch(rules);
            if (this.drainingDisk != null && this.drainingDisk.isDone()) this.drainingDisk = null;
            if (this.generation != null && this.observation == null && this.generation.drained()) {
                this.generatedDependencies += this.generation.generatedDependencies(); this.generation = null;
            }
            if (this.observation != null) {
                try { observe(rules, now); }
                catch (CompletionException | IllegalArgumentException | IllegalStateException failure) {
                    LogUtils.getLogger().error("Diplomatic terrain observation failed at {}", this.observation.position, failure);
                    block(this.observation.work, "terrain_unavailable"); cancelObservation();
                }
            }
            boolean loaded = this.server.getAverageTickTimeNanos() >= rules.maximumTickMillis() * 1_000_000L;
            if (loaded || this.queue.isEmpty()) return;
            int candidates = this.queue.size();
            while (candidates-- > 0) {
                var id = this.queue.removeFirst(); this.queue.addLast(id);
                var work = this.work.get(id);
                if (work == null || work == this.searching || this.observation != null && this.observation.work == work
                        || work.job.state != RoutePreparation.State.PREPARING) continue;
                var level = this.server.getLevel(ResourceKey.create(Registries.DIMENSION, work.job.dimension()));
                if (level == null) { block(work, "dimension_unavailable"); continue; }
                if (work.job.start().distSqr(work.job.end()) > (long) rules.shipmentRange() * rules.shipmentRange()) {
                    block(work, "shipment_range"); continue;
                }
                if (!level.getWorldBorder().isWithinBounds(work.job.start()) || !level.getWorldBorder().isWithinBounds(work.job.end())) {
                    block(work, "world_border"); continue;
                }
                while (!work.wanted.isEmpty()) {
                    var tile = this.data.terrain.get(new RouteSavedData.Key(work.job.dimension(), work.wanted.peekFirst()));
                    if (tile == null || now - tile.observed() > 24000 || tile.observed() > now) break;
                    work.wanted.removeFirst();
                }
                if (!work.wanted.isEmpty()) {
                    if (this.observation == null && this.generation == null && this.drainingDisk == null) {
                        this.observation = new Observation(work, level, new ChunkPos(work.wanted.peekFirst()), now);
                        return;
                    }
                } else if (this.search == null && workerIdle() && (this.observation == null || this.observation.decoded == null)) {
                    submit(work, level, rules, now); return;
                }
            }
        }
        void submit(Work work, ServerLevel level, VillageDiplomacyRules.Rules rules, long now) {
            var bounds = bounds(level, work.job, rules);
            this.input = this.data.snapshot(work.job.dimension(), bounds, now);
            var snapshot = this.input;
            int width = work.job.width;
            this.searching = work;
            this.search = CompletableFuture.supplyAsync(() -> RouteSearch.search(work.job.start(), work.job.end(), snapshot, bounds,
                    width, rules.routeChunkLimit(), rules.routeNodeLimit(), rules.shipmentRange() * rules.routeDetourMultiplier(), work.job.boatAllowed(),
                    work.job.startTolerance(), work.job.endTolerance()), this.worker);
            this.searchSubmissions++;
        }
        void finishSearch(VillageDiplomacyRules.Rules rules) {
            var work = this.searching;
            try {
                var result = this.search.join();
                if (work.job.state != RoutePreparation.State.PREPARING) return;
                if (this.input.entrySet().stream().anyMatch(entry -> this.data.terrain.get(new RouteSavedData.Key(work.job.dimension(), entry.getKey())) != entry.getValue())) return;
                if (result.route() != null) { work.job.route = result.route(); work.job.state = RoutePreparation.State.READY; work.job.blocker = ""; this.data.changed(); }
                else if (!result.missing().isEmpty()) work.wanted.addAll(result.missing());
                else if ((result.blocker().equals("unreachable") || result.blocker().equals("no_walking_route")) && work.job.width < rules.routeDetourChunks()) {
                    work.job.width = Math.min(rules.routeDetourChunks(), work.job.width * 2); this.data.changed();
                } else block(work, result.blocker());
            } catch (CompletionException failure) {
                LogUtils.getLogger().error("Diplomatic route search failed", failure); block(work, "route_search_failed");
            } finally { this.search = null; this.searching = null; this.input = null; }
        }
        void observe(VillageDiplomacyRules.Rules rules, long now) {
            var observation = this.observation;
            if (observation.work.job.state != RoutePreparation.State.PREPARING) { cancelObservation(); return; }
            if (observation.invalidated) { cancelObservation(); return; }
            if (now - observation.started > 2400) { block(observation.work, "terrain_timeout"); cancelObservation(); return; }
            if (observation.reader != null) {
                if (observation.live != null) {
                    var holder = observation.level.getChunkSource().chunkMap.getVisibleChunkIfPresent(observation.position.toLong());
                    if (holder == null) { cancelObservation(); return; }
                    if (holder.getGenerationRefCount() != 0) { observation.reader = null; observation.live = null; return; }
                }
                long started = System.nanoTime();
                for (int columns = 0; columns < 16 && System.nanoTime() - started < 400_000; columns++) {
                    if (observation.reader.step()) {
                        this.data.observe(observation.level.dimension().location(), observation.reader.result()); this.observedTiles++;
                        observation.work.wanted.removeFirst(); cancelObservation(); return;
                    }
                }
                return;
            }
            if (this.generation != null) {
                var chunk = this.generation.poll(now);
                if (chunk != null) { observation.live = chunk; observation.reader = new RouteSurfaceReader(observation.position, chunk, now); }
                return;
            }
            if (observation.generationNeeded) {
                if (now < this.nextGeneration || this.server.getAverageTickTimeNanos() >= rules.maximumTickMillis() * 1_000_000L) return;
                var dependencies = RouteGeneration.footprint(observation.position);
                long additions = dependencies.stream().filter(key -> !observation.work.job.dependencies.contains(key)).count();
                if (observation.work.job.dependencies.size() + additions > rules.generationLimit()) {
                    block(observation.work, "generation_limit"); cancelObservation(); return;
                }
                observation.work.job.dependencies.addAll(dependencies); this.data.changed(); this.dependencyBudget += additions;
                this.generation = new RouteGeneration(observation.level, observation.position, now);
                this.generationSubmissions++; this.nextGeneration = now + rules.generationInterval();
                return;
            }
            if (observation.decoded != null) {
                if (observation.decoded.isDone()) observation.reader = new RouteSurfaceReader(observation.position, observation.decoded.join(), now);
                return;
            }
            if (this.server.getAverageTickTimeNanos() >= rules.maximumTickMillis() * 1_000_000L) return;
            var holder = observation.level.getChunkSource().chunkMap.getVisibleChunkIfPresent(observation.position.toLong());
            if (holder != null && holder.getGenerationRefCount() != 0) return;
            var chunk = holder == null ? null : holder.getChunkIfPresentUnchecked(ChunkStatus.FEATURES);
            if (chunk != null && holder.getGenerationRefCount() == 0) {
                observation.live = chunk; observation.reader = new RouteSurfaceReader(observation.position, chunk, now); return;
            }
            if (observation.disk == null) { observation.disk = observation.level.getChunkSource().chunkMap.read(observation.position); return; }
            if (!observation.disk.isDone() || this.search != null || !workerIdle()) return;
            var tag = observation.disk.join().orElse(null);
            if (tag == null) { observation.generationNeeded = true; return; }
            int version = tag.getInt("DataVersion");
            int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
            if (version > current) { block(observation.work, "unsupported_terrain"); cancelObservation(); return; }
            var status = ChunkStatus.byName(tag.getString("Status"));
            if (version < current || status != null && status.isBefore(ChunkStatus.FEATURES)) { observation.generationNeeded = true; return; }
            if (status == null) { block(observation.work, "malformed_terrain"); cancelObservation(); return; }
            int minimum = observation.level.getMinBuildHeight(), height = observation.level.getHeight();
            observation.decoded = CompletableFuture.supplyAsync(() -> RouteSurfaceReader.decode(tag, observation.position, minimum, height), this.worker);
        }
        boolean workerIdle() { return this.worker.getActiveCount() == 0 && this.worker.getQueue().isEmpty(); }
        void block(Work work, String reason) { work.job.block(reason); this.data.changed(); }
        void cancel(UUID id) {
            if (this.observation != null && this.observation.work.job.id().equals(id)) cancelObservation();
            if (this.searching != null && this.searching.job.id().equals(id)) { this.search.cancel(true); this.search = null; this.searching = null; this.input = null; }
            this.work.remove(id); this.queue.remove(id);
        }
        void cancelObservation() {
            if (this.generation != null) this.generation.release();
            if (this.observation != null) {
                if (this.observation.decoded != null) this.observation.decoded.cancel(true);
                if (this.observation.disk != null && !this.observation.disk.isDone()) this.drainingDisk = this.observation.disk;
            }
            this.observation = null;
        }
        Metrics metrics() {
            return new Metrics(this.work.size(), this.generation != null && this.generation.ticketHeld() ? 1 : 0,
                    this.tickNanos, this.maximumStepNanos, this.dependencyBudget, this.generationSubmissions, this.searchSubmissions, this.observedTiles,
                    this.generatedDependencies + (this.generation == null ? 0 : this.generation.generatedDependencies()));
        }
        void close() {
            cancelObservation();
            if (this.search != null) this.search.cancel(true);
            this.worker.shutdownNow();
        }
    }
}
