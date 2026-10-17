package com.jvn.villagerretaliation.exploration;

import java.io.IOException;
import it.unimi.dsi.fastutil.longs.*;
import java.util.*;
import java.util.concurrent.*;

/** Client-owned LRU. One bounded disk batch in flight. Dirty data is pinned until its exact revision is saved. */
public final class MapRepository implements AutoCloseable {
    public static final int MAX_TILES = 192, MAX_DIRTY = 64, MAX_REQUESTS = 256, MAX_SURFACES = 4096;
    private static final int READ_BATCH = 128, WRITE_BATCH = 8;
    private final MapStore store;
    private final Map<String, MapSnapshot.Marker> markers = new HashMap<>();
    private final LinkedHashMap<Long, MapSnapshot> tiles = new LinkedHashMap<>(256, .75f, true);
    private final LinkedHashSet<Long> dirty = new LinkedHashSet<>(), requested = new LinkedHashSet<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "VR map storage"); t.setDaemon(true); return t; });
    private final Set<Long> reading = new HashSet<>(), detailRequested = new HashSet<>();
    private final Long2ObjectLinkedOpenHashMap<MapSurfaceSnapshot> surfaces = new Long2ObjectLinkedOpenHashMap<>(256);
    private CompletableFuture<Batch> pending;
    private boolean migrationPending = true;
    private String migrationError = "";
    private record Batch(List<Result> results, MapStore.Migration migration) { }
    private final LinkedHashMap<OverviewKey, MapSurfaceSnapshot> overviews = new LinkedHashMap<>(256, .75f, true);
    private final Map<Integer, Long2ObjectMap<OverviewKey>> overviewKeys = new HashMap<>();
    private final LinkedHashSet<OverviewKey> overviewRequests = new LinkedHashSet<>();
    private CompletableFuture<List<OverviewResult>> overviewPending;
    private record OverviewKey(int x, int z, int stride) {
        @Override public int hashCode() { return (int)it.unimi.dsi.fastutil.HashCommon.mix(MapSnapshot.key(x, z)) ^ stride; }
    }
    private record OverviewRead(OverviewKey key, MapSurfaceSnapshot[] sources) { }
    private record OverviewResult(OverviewKey key, MapSurfaceSnapshot tile, MapSurfaceSnapshot[] sources, String error) { }
    private String error = "";
    private long generation;
    public long generation() { return generation; }
    private record Result(long key, MapSnapshot tile, MapSurfaceSnapshot surface, boolean write, String error) {
        static Result read(long key, MapSnapshot tile, String error) { return new Result(key, tile, MapSurfaceSnapshot.from(tile), false, error); }
    }
    public MapRepository(MapStore store) { this.store = store; }
    public MapSnapshot get(int x, int z) {
        long key = MapSnapshot.key(x, z);
        var tile = tiles.get(key);
        if (tile == null && request(key)) detailRequested.add(key);
        return tile;
    }
    public MapSurfaceSnapshot getSurface(int x, int z) {
        long key = MapSnapshot.key(x, z);
        var surface = surfaces.getAndMoveToLast(key);
        if (surface == null) {
            var tile = tiles.get(key);
            if (tile != null) { rememberSurface(tile); return surfaces.getAndMoveToLast(key); }
            request(key);
        }
        return surface;
    }
    public MapSurfaceSnapshot getOverview(int x, int z) { return getOverview(x, z, MapOverview.STRIDE); }
    public MapSurfaceSnapshot getOverview(int x, int z, int stride) {
        var index = overviewKeys.computeIfAbsent(stride, ignored -> new Long2ObjectOpenHashMap<>());
        long position = MapSnapshot.key(x, z);
        var key = index.get(position);
        if (key != null) return overviews.get(key);
        if (overviewRequests.size() >= MAX_REQUESTS) return null;
        key = new OverviewKey(x, z, stride); index.put(position, key); overviewRequests.add(key);
        return null;
    }
    private void tickOverviews() {
        if (overviewPending != null) {
            if (!overviewPending.isDone()) return;
            generation++;
            for (var result : overviewPending.join()) {
                overviewRequests.remove(result.key);
                if (!result.error.isEmpty()) { error = result.error; overviewKeys.get(result.key.stride).remove(MapSnapshot.key(result.key.x, result.key.z)); continue; }
                var region = result.tile;
                // merge only observations that changed after the worker captured its inputs
                int stride = region.stride();
                for (int i = 0; i < result.sources.length; i++) {
                    var tile = unsavedSurface(MapSnapshot.key(region.x() * stride + i % stride, region.z() * stride + i / stride));
                    if (tile != null && tile != result.sources[i]) region = MapOverview.update(region, tile);
                }
                rememberMarkers(region);
                overviews.put(result.key, region); generation++;
                while (overviews.size() > MAX_SURFACES) {
                    var retired = overviews.keySet().iterator().next(); overviews.remove(retired);
                    overviewKeys.get(retired.stride).remove(MapSnapshot.key(retired.x, retired.z));
                }
            }
            overviewPending = null;
        }
        if (overviewRequests.isEmpty()) return;
        var reads = new ArrayList<OverviewRead>();
        for (var key : overviewRequests.stream().limit(32).toList()) {
            var sources = new MapSurfaceSnapshot[key.stride * key.stride];
            for (int i = 0; i < sources.length; i++) sources[i] = unsavedSurface(MapSnapshot.key(key.x * key.stride + i % key.stride, key.z * key.stride + i / key.stride));
            reads.add(new OverviewRead(key, sources));
        }
        overviewPending = CompletableFuture.supplyAsync(() -> {
            var results = new ArrayList<OverviewResult>();
            long deadline = System.nanoTime() + 8_000_000;
            for (var read : reads) {
                var key = read.key;
                try {
                    var region = store.readOverview(key.x, key.z, key.stride);
                    for (var tile : read.sources) if (tile != null) region = MapOverview.update(region, tile);
                    results.add(new OverviewResult(key, region, read.sources, ""));
                } catch (IOException e) { results.add(new OverviewResult(key, null, read.sources, "Map overview unavailable: " + e.getMessage())); }
                if (System.nanoTime() >= deadline) break;
            }
            return results;
        }, io);
    }
    private MapSurfaceSnapshot unsavedSurface(long key) {
        if (dirty.isEmpty() || !dirty.contains(key)) return null;
        var surface = surfaces.get(key);
        return surface != null ? surface : MapSurfaceSnapshot.from(tiles.get(key));
    }
    private boolean request(long key) {
        if (reading.contains(key) || requested.contains(key)) return true;
        return requested.size() + reading.size() < MAX_REQUESTS && requested.add(key);
    }
    public int surfaceCount() { return surfaces.size(); }
    private void rememberSurface(MapSnapshot tile) {
        rememberSurface(MapSurfaceSnapshot.from(tile), true);
    }
    private void rememberSurface(MapSurfaceSnapshot surface, boolean observed) {
        rememberMarkers(surface);
        surfaces.putAndMoveToLast(surface.key(), surface); generation++;
        // disk overviews already include saved tiles, only live observations need merging
        if (observed) for (int stride = 2; stride <= MapOverview.STRIDE; stride *= 2) {
            var index = overviewKeys.get(stride);
            var regionKey = index == null ? null : index.get(MapSnapshot.key(Math.floorDiv(surface.x(), stride), Math.floorDiv(surface.z(), stride)));
            var region = overviews.get(regionKey);
            if (region != null) overviews.put(regionKey, MapOverview.update(region, surface));
        }
        while (surfaces.size() > MAX_SURFACES) surfaces.removeFirst();
    }
    private void rememberMarkers(MapSurfaceSnapshot surface) {
        // keep each observed anchor independent of terrain eviction and LOD load order
        for (var marker : surface.markers()) if (surface.permits(marker)) markers.putIfAbsent(marker.id(), marker);
    }
    public boolean hasMarker(String id) { return markers.containsKey(id); }
    public List<MapSnapshot.Marker> resolveMarkers(Collection<MapSnapshot.Marker> visible) {
        var result = new LinkedHashMap<String, MapSnapshot.Marker>();
        for (var marker : visible) {
            var anchor = markers.get(marker.id());
            if (anchor != null) result.putIfAbsent(anchor.id(), anchor);
        }
        return List.copyOf(result.values());
    }
    public boolean put(MapSnapshot tile) {
        if (!tiles.containsKey(tile.key()) || !dirty.contains(tile.key()) && dirty.size() >= MAX_DIRTY) return false;
        tiles.put(tile.key(), tile); rememberSurface(tile); dirty.add(tile.key()); return true;
    }
    public Collection<MapSnapshot> loaded() { return List.copyOf(tiles.values()); }
    public String error() { return error.isEmpty() ? migrationError : error; }
    public int dirtyCount() { return dirty.size(); }
    public int requestCount() { return requested.size() + reading.size(); }
    public void tick() {
        tickOverviews();
        if (pending != null) {
            if (!pending.isDone()) return;
            var batch = pending.join(); pending = null;
            migrationPending = batch.migration.more();
            if (!batch.migration.error().isEmpty()) migrationError = batch.migration.error();
            error = "";
            for (var result : batch.results) {
                if (!result.error.isEmpty()) error = result.error;
                if (result.write) {
                    if (result.error.isEmpty() && tiles.get(result.key) == result.tile) dirty.remove(result.key);
                } else {
                    reading.remove(result.key);
                    // corrupt files stay untouched until a fresh local observation replaces them
                    var observed = tiles.get(result.key);
                    if (observed == null) rememberSurface(result.surface, false);
                    else rememberSurface(observed);
                    if (detailRequested.remove(result.key)) {
                        if (result.tile == null) {
                            request(result.key); detailRequested.add(result.key);
                        } else {
                            makeSpace();
                            if (tiles.size() < MAX_TILES) tiles.putIfAbsent(result.key, result.tile);
                        }
                    }
                }
            }
        }
        if (dirty.isEmpty() && requested.isEmpty() && !migrationPending) return;
        var reads = requested.stream().limit(READ_BATCH).toList();
        var writes = dirty.stream().limit(WRITE_BATCH).map(tiles::get).toList();
        requested.removeAll(reads); reading.addAll(reads);
        boolean migrate = migrationPending;
        var details = Set.copyOf(detailRequested);
        // every batch services reads and writes so observation cannot starve the visible map
        pending = CompletableFuture.supplyAsync(() -> {
            var results = new ArrayList<Result>(reads.size() + writes.size());
            for (long key : reads) {
                int x = (int)(key >> 32), z = (int)key;
                try {
                    var result = details.contains(key) ? Result.read(key, store.read(x, z), "")
                            : new Result(key, null, store.readSurface(x, z), false, "");
                    if (result.tile != null) store.rememberSurface(result.surface);
                    results.add(result);
                }
                catch (IOException e) { results.add(Result.read(key, new MapSnapshot(x, z, 0, List.of(), List.of()), "Map tile unavailable: " + e.getMessage())); }
            }
            var failures = store.saveBatch(writes);
            for (var tile : writes) {
                var failure = failures.get(tile.key());
                results.add(new Result(tile.key(), tile, null, true, failure == null ? "" : "Map save failed: " + failure.getMessage()));
            }
            MapStore.Migration migration;
            try { migration = migrate ? store.migrateStep() : new MapStore.Migration(false, 0, ""); }
            catch (IOException e) {
                migration = new MapStore.Migration(false, 0, "Map migration paused, old files preserved: " + e.getMessage());
                System.getLogger("VR map").log(System.Logger.Level.WARNING, migration.error(), e);
            }
            return new Batch(results, migration);
        }, io);
    }
    private void makeSpace() {
        var iterator = tiles.entrySet().iterator();
        while (tiles.size() >= MAX_TILES && iterator.hasNext()) if (!dirty.contains(iterator.next().getKey())) iterator.remove();
    }
    @Override public void close() {
        // The bounded final immutable batch follows the in-flight write, with no client-thread disk wait.
        List<MapSnapshot> finalWrites = dirty.stream().map(tiles::get).filter(Objects::nonNull).toList();
        io.execute(() -> {
            try { store.writeBatch(finalWrites); }
            catch (IOException e) { System.getLogger("VR map").log(System.Logger.Level.WARNING, "Could not save explored map tiles", e); }
            finally {
                try { store.close(); }
                catch (IOException e) { System.getLogger("VR map").log(System.Logger.Level.WARNING, "Could not close explored map storage", e); }
            }
        });
        io.shutdown();
    }
    public boolean awaitClosed(long millis) throws InterruptedException { return io.awaitTermination(millis, TimeUnit.MILLISECONDS); }
}
