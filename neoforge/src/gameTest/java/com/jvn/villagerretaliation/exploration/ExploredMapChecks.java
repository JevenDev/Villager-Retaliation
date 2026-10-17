package com.jvn.villagerretaliation.exploration;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Disposable fixtures and deterministic images; no Minecraft client, world save or graphics context. */
public final class ExploredMapChecks {
    private static int passed;
    private static Path output;
    public static void main(String[] args) throws Exception {
        output = Path.of(args.length > 0 ? args[0] : "build/explored-map-checks"); Files.createDirectories(output);
        Path fixture = Files.createTempDirectory(output, "fixture-");
        var store = new MapStore(fixture, "server", "world", "player", "minecraft:overworld");
        var base = tile(0,0,1,"minecraft:grass_block[snowy=false]"); store.write(base);
        check(store.read(0,0).equals(base), "round trip blockstates, elevation, masks and revision");
        check(new MapStore(fixture,"server","other-world","player","minecraft:overworld").read(0,0).columns().isEmpty(), "world replacement isolation");
        check(new MapStore(fixture,"other-server","world","player","minecraft:overworld").read(0,0).columns().isEmpty(), "server isolation");
        check(new MapStore(fixture,"server","world","other-player","minecraft:overworld").read(0,0).columns().isEmpty(), "player isolation");
        check(new MapStore(fixture,"server","world","player","minecraft:the_nether").read(0,0).columns().isEmpty(), "dimension isolation");
        check(!new MapStore(fixture,"../escape","world","player","dimension").root().toString().contains("escape"), "scope paths cannot traverse");
        var changed = tile(0,0,2,"minecraft:dirt_path"); store.write(changed);
        check(store.read(0,0).equals(changed) && !changed.columns().equals(base.columns()), "same air count surface replacement persists");
        check(MapSnapshot.tile(-1)==-1 && MapSnapshot.tile(-8)==-1 && MapSnapshot.tile(-9)==-2, "negative coordinate tile boundaries");
        check(base.observed(0,0) && !base.observed(1,0) && !base.observed(8,0), "partial mask does not expose neighboring columns or tiles");
        check(MapSnapshot.inObservation(0,0,.5,.5) && MapSnapshot.inObservation(24,0,.5,.5) && !MapSnapshot.inObservation(25,0,.5,.5), "observation radius independent of loaded chunks");
        var marker = new MapSnapshot.Marker("actual-id","Actual village",0,65,0);
        var marked = new MapSnapshot(0,0,3,base.columns(),List.of(marker)); store.write(marked);
        check(store.read(0,0).markers().equals(List.of(marker)), "observed village marker persistence");
        expectFailure(() -> store.write(new MapSnapshot(0,0,4,base.columns(),List.of(new MapSnapshot.Marker("hidden","",1,65,0)))), "unexplored marker rejected");
        expectFailure(() -> new MapSnapshot(0,0,1,List.of(base.columns().getFirst(),base.columns().getFirst()),List.of()), "duplicate column rejected");
        expectFailure(() -> new MapSnapshot.Column(64,64,List.of()), "out of bounds mask rejected");
        expectFailure(() -> new MapSnapshot.Column(0,64,List.of(new MapSnapshot.Block(0,"stone",0,0,1))), "capture depth bound enforced");
        byte[] bytes = MapStore.encode(base); bytes[bytes.length-1] ^= 1;
        expectFailure(() -> MapStore.decode(bytes, 0, 0), "corrupt checksum rejected");
        expectFailure(() -> MapStore.decode(new byte[]{1,2,3}, 0, 0), "truncated tile rejected");
        expectFailure(() -> MapStore.decode(MapStore.encode(base), 1, 0), "coordinate substitution rejected");
        Files.write(store.root().resolve("r.0.0.vrregion.pending"),new byte[]{1,2,3});
        check(store.read(0,0).equals(marked), "interrupted pending write preserves committed tile");
        store.close();
        MapRegionChecks.run(fixture);
        legacy(fixture);
        captureQueue();
        cache(fixture);
        batchLoading(fixture);
        surfaceCache(fixture);
        overview(fixture);
        bulkOverviews();
        surfaceReader();
        markerAnchors(fixture);
        lighting();
        FlatMapChecks.run();
        MapCloudChecks.run();
        compactSurfaceDecoding();
        System.out.println("Passed " + passed + " explored-map checks. Fixtures/images: " + output.toAbsolutePath());
    }
    private static void markerAnchors(Path root) throws Exception {
        var store = new MapStore(root, "markers", "world", "player", "dim");
        var anchor = new MapSnapshot.Marker("village", "Shared name", 0, 65, 0);
        var duplicate = new MapSnapshot.Marker("village", "Shared name", 8, 90, 0);
        var other = new MapSnapshot.Marker("other", "Shared name", 16, 65, 0);
        for (var marker : List.of(anchor, duplicate, other)) {
            int x = MapSnapshot.tile(marker.x());
            var terrain = tile(x, 0, 1, "minecraft:stone");
            store.write(new MapSnapshot(x, 0, 1, terrain.columns(), List.of(marker)));
        }
        var repository = new MapRepository(store);
        await(repository, () -> repository.get(0, 0) != null);
        await(repository, () -> repository.getSurface(1, 0) != null && repository.getSurface(2, 0) != null);
        check(repository.resolveMarkers(List.of(duplicate, anchor, duplicate, other)).equals(List.of(anchor, other)),
                "both map views resolve duplicate village IDs to one anchor without merging equal names");
        check(repository.resolveMarkers(List.of(duplicate)).equals(List.of(anchor)),
                "a viewport containing only a duplicate retains the original village position and elevation");
        for (int stride : new int[]{2, 4, 8, 4, 2}) {
            await(repository, () -> repository.getOverview(0, 0, stride) != null);
            var visible = repository.getOverview(0, 0, stride).markers();
            check(repository.resolveMarkers(visible).stream().filter(m -> m.id().equals("village")).toList().equals(List.of(anchor)),
                    "village anchor remains fixed through LOD stride " + stride);
        }
        for (int x = 3; x < MapRepository.MAX_TILES + 4; x++) {
            final int tileX = x;
            await(repository, () -> repository.get(tileX, 0) != null);
        }
        check(repository.loaded().stream().noneMatch(t -> t.key() == MapSnapshot.key(0, 0)) && repository.hasMarker("village"),
                "capture remembers discovered villages after their detailed terrain is evicted");
        check(repository.resolveMarkers(List.of(duplicate)).equals(List.of(anchor)),
                "terrain eviction cannot move a village icon");
        check(repository.resolveMarkers(List.of(new MapSnapshot.Marker("unknown", "", 32, 65, 0))).isEmpty(),
                "unobserved village IDs are not introduced by marker resolution");
        repository.close(); check(repository.awaitClosed(5000), "marker repository closes cleanly");

        var reopened = new MapRepository(new MapStore(root, "markers", "world", "player", "dim"));
        await(reopened, () -> reopened.getOverview(0, 0, 8) != null);
        var wide = reopened.resolveMarkers(reopened.getOverview(0, 0, 8).markers());
        check(wide.size() == 2, "existing duplicate records load as one icon per village from a cold coarse LOD");
        var selected = wide.stream().filter(m -> m.id().equals("village")).findFirst().orElseThrow();
        await(reopened, () -> reopened.getSurface(0, 0) != null && reopened.getSurface(1, 0) != null);
        check(reopened.resolveMarkers(List.of(duplicate, anchor)).equals(List.of(selected)),
                "loading fine terrain after a coarse LOD cannot switch its selected village anchor");
        reopened.close(); check(reopened.awaitClosed(5000), "reopened marker repository closes cleanly");
    }
    private static void lighting() {
        var day = MapLighting.at(0, true);
        var dusk = MapLighting.at(.25, true);
        var night = MapLighting.at(.5, true);
        var dawn = MapLighting.at(.75, true);
        check(day.equals(MapLighting.DAY) && day.background() == 0xff212328, "daylight preserves original map colors");
        check(night.red() >= .35 && night.blue() > night.green() && night.green() > night.red(),
                "night light stays readable with a cool tint");
        check(dusk.red() > dusk.green() && dusk.green() > dusk.blue() && dusk.equals(dawn),
                "dawn and dusk pass through warm light");
        check(dusk.green() > night.green() && dusk.green() < day.green(), "twilight brightness lies between day and night");
        check(MapLighting.at(1, true).equals(day) && MapLighting.at(-.5, true).equals(night),
                "lighting wraps continuously across day boundaries");
        check(MapLighting.at(.5, false).equals(day), "dimensions without skylight keep their existing lighting");
        boolean continuous = true;
        var previous = day;
        for (int tick = 1; tick <= 24000; tick++) {
            var next = MapLighting.at(tick / 24000.0, true);
            continuous &= Math.abs(next.red() - previous.red()) < .002
                    && Math.abs(next.green() - previous.green()) < .002 && Math.abs(next.blue() - previous.blue()) < .002;
            previous = next;
        }
        check(continuous, "the full day cycle has no abrupt lighting steps");
    }
    private static void compactSurfaceDecoding() throws Exception {
        var random = new Random(73194); boolean equivalent = true, rejection = true;
        for (int sample = 0; sample < 80; sample++) {
            var columns = new ArrayList<MapSnapshot.Column>();
            for (int c = 0; c < 16; c++) {
                int top = random.nextInt(380) - 64, y = top;
                var blocks = new ArrayList<MapSnapshot.Block>();
                while (y > top - 32) {
                    blocks.add(new MapSnapshot.Block(y, "minecraft:" + (random.nextBoolean() ? "stone" : "grass_block"),
                            random.nextInt(0x1000000), random.nextInt(0x1000000), random.nextInt(64), random.nextInt(16)));
                    y -= random.nextInt(3) + 1;
                }
                columns.add(new MapSnapshot.Column(c, top, blocks, random.nextBoolean()));
            }
            var source = new MapSnapshot(-2, 3, sample, columns, List.of(new MapSnapshot.Marker("anchor", "", -16, 64, 24)));
            byte[] bytes = MapStore.encode(source);
            var expected = MapSurfaceSnapshot.from(source); var actual = MapStore.decodeSurface(bytes, -2, 3);
            equivalent &= actual.sameContent(expected) && expected.sameContent(actual) && actual.columns().equals(expected.columns());
            for (int mutation = 0; mutation < 8; mutation++) {
                byte[] broken = bytes.clone(); int offset = 8 + random.nextInt(broken.length - 8); broken[offset] ^= (byte)(1 << random.nextInt(8));
                var crc = new java.util.zip.CRC32(); crc.update(broken, 8, broken.length - 8); java.nio.ByteBuffer.wrap(broken).putLong(crc.getValue());
                MapSurfaceSnapshot full = null, compact = null; boolean fullRejected = false, compactRejected = false;
                try { full = MapSurfaceSnapshot.from(MapStore.decode(broken, -2, 3)); } catch (IOException e) { fullRejected = true; }
                try { compact = MapStore.decodeSurface(broken, -2, 3); } catch (IOException e) { compactRejected = true; }
                rejection &= fullRejected == compactRejected && (fullRejected || full.sameContent(compact));
            }
        }
        check(equivalent, "compact saved surfaces preserve exterior masks, flat surfaces, legacy tops, light, and markers");
        check(rejection, "compact decoder preserves validation and content across 640 checksum-valid mutations");
    }
    private static void surfaceReader() {
        var columns = new ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) columns.add(new MapSnapshot.Column(i, 80, List.of(
                new MapSnapshot.Block(80, "minecraft:stone", i, i + 1, 42, i % 16),
                new MapSnapshot.Block(70, "minecraft:dirt", i + 2, i + 3, 40, (i + 1) % 16)), true));
        var source = MapSurfaceSnapshot.from(new MapSnapshot(-2, 1, 3, columns, List.of()));
        var materialized = source.columns().stream().map(c -> new MapSurfaceSnapshot.Column(c.index(),
                c.layers().stream().toList(), c.coverage())).toList();
        var reader = new MapSurfaceSnapshot.Reader();
        for (var input : List.of(source.columns(), materialized, source.columns())) {
            reader.reset(input);
            for (int ci = 0; ci < input.size(); ci++) {
                reader.column(ci); var expected = input.get(ci);
                check(reader.index() == expected.index() && reader.coverage() == expected.coverage() && reader.layers() == expected.layers().size(),
                        "surface reader preserves column metadata after reuse");
                for (int li = 0; li < reader.layers(); li++) {
                    reader.layer(li); var layer = expected.layers().get(li);
                    check(reader.block().equals(layer.block()) && reader.bottom() == layer.bottom(), "surface reader preserves every primitive material and layer field");
                }
            }
        }
        reader.reset(List.of());
    }
    private static void bulkOverviews() {
        var random = new Random(81235);
        for (int stride : new int[]{2, 4, 8}) for (int origin : new int[]{0, -8, 2999992}) {
            var sources = new ArrayList<MapSurfaceSnapshot>();
            var expected = MapOverview.empty(origin, origin, stride);
            for (int z = 0; z < stride; z++) for (int x = 0; x < stride; x++) {
                var columns = new ArrayList<MapSnapshot.Column>();
                for (int i = 0; i < 64; i++) if (random.nextInt(3) != 0) {
                    int top = random.nextInt(300) - 60;
                    columns.add(new MapSnapshot.Column(i, top, List.of(new MapSnapshot.Block(top,
                            i % 3 == 0 ? "minecraft:stone" : "minecraft:dirt", 0xffaabb, 0xffccdd, i % 7 == 0 ? 40 : 42, i % 16)), true));
                }
                var source = MapSurfaceSnapshot.from(new MapSnapshot(origin * stride + x, origin * stride + z, 1, columns, List.of()));
                sources.add(source); expected = MapOverview.update(expected, source);
            }
            var actual = MapOverview.combine(origin, origin, stride, sources);
            check(actual.sameContent(expected), "bulk overview preserves materials, heights, light and discovery at stride " + stride + " / " + origin);
            long before = ((com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int repeat = 0; repeat < 20; repeat++) MapOverview.combine(origin, origin, stride, sources);
            long bulkBytes = ((com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
            before = ((com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId());
            for (int repeat = 0; repeat < 20; repeat++) {
                var region = MapOverview.empty(origin, origin, stride);
                for (var source : sources) region = MapOverview.update(region, source);
            }
            long incrementalBytes = ((com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean()).getThreadAllocatedBytes(Thread.currentThread().threadId()) - before;
            check(bulkBytes < incrementalBytes / 2, "bulk overview cuts transient allocation by at least half at stride " + stride);
        }
    }
    private static void overview(Path root) throws Exception {
        var mixed = new ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) mixed.add(new MapSnapshot.Column(i, i, List.of(new MapSnapshot.Block(i,
                i < 16 ? "minecraft:dirt" : "minecraft:stone", 0xffffff, 0xffffff, 2)), true));
        var mixedSource = MapSurfaceSnapshot.from(new MapSnapshot(0, 0, 1, mixed, List.of()));
        var dominant = MapOverview.update(MapOverview.empty(0, 0, 8), mixedSource);
        var dominantMaterial = dominant.columns().getFirst().layers().getFirst().block();
        check(dominantMaterial.state().equals("minecraft:stone") && dominantMaterial.y() == 40 && dominant.columns().getFirst().coverage() == -1L,
                "overview aggregation preserves dominant material, weighted height, and full coverage");
        check(MapOverview.update(dominant, mixedSource) == dominant, "unchanged overview content retains its cached identity");
        var store = new MapStore(root, "overview", "world", "player", "dim");
        store.write(tile(150, 0, 1, "minecraft:stone"));
        store.write(tile(-151, -1, 1, "minecraft:sand"));
        var region = store.readOverview(18, 0);
        check(region.stride() == 8 && region.columns().size() == 1 && region.columns().getFirst().index() == 6 && region.columns().getFirst().coverage() == 1,
                "large overview reads only recorded tiles and keeps their world position");
        check(store.readOverview(-19, -1).columns().getFirst().index() == 57, "overview handles negative region boundaries");
        store.write(tile(151, 0, 2, "minecraft:grass_block"));
        check(store.readOverview(18, 0).columns().size() == 2, "overview index includes exploration recorded after its initial scan");
        var full = new ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) full.add(new MapSnapshot.Column(i, 64, List.of(new MapSnapshot.Block(64, "minecraft:stone", 0xffffff, 0xffffff, 2)), true));
        store.write(new MapSnapshot(0, 0, 1, full, List.of()));
        for (int stride : new int[]{2, 4, 8}) {
            var level = store.readOverview(0, 0, stride);
            check(store.readOverview(0, 0, stride) == level, "repeated LOD reads reuse the derived surface at stride " + stride);
            check(level.columns().size() == 64 / (stride * stride) && level.columns().stream().allMatch(c -> c.coverage() == -1L),
                    "progressive overview preserves complete cells at stride " + stride);
        }
        check(store.readOverview(0, 0, 1).columns().size() == 64, "single-stride overview reads remain compatible");
        var cachedSurface = store.readSurface(0, 0);
        check(store.readSurface(0, 0) == cachedSurface, "saved surface reads reuse detached summaries without decoding shells");
        var updatedColumns = new ArrayList<>(full);
        for (int i = 0; i < 64; i++) updatedColumns.set(i, new MapSnapshot.Column(i, 90, List.of(new MapSnapshot.Block(90, "minecraft:gold_block", 0xffffff, 0xffffff, 2)), true));
        store.write(new MapSnapshot(0, 0, 2, updatedColumns, List.of()));
        check(store.readSurface(0, 0) != cachedSurface && store.readSurface(0, 0).columns().getFirst().layers().getFirst().block().y() == 90,
                "saved changes invalidate the decoded summary cache");
        check(store.readOverview(0, 0, 2).columns().getFirst().layers().getFirst().block().y() != 64,
                "saved changes invalidate already derived overview heights");

        while (store.migrateStep().more()) { }
        var emptyRegion = store.readOverview(40, 40, 8);
        check(emptyRegion.columns().isEmpty() && store.readOverview(40, 40, 8) == emptyRegion, "missing regions reuse an empty overview");
        store.write(tile(320, 320, 1, "minecraft:stone"));
        check(store.readOverview(40, 40, 8).columns().size() == 1, "new exploration replaces a previously empty region cache");
        var retainedOverview = store.readOverview(0, 0, 8);
        for (int i = 0; i <= MapRepository.MAX_SURFACES * 2; i++) store.readOverview(1000 + i, 1000, 8);
        check(store.readOverview(0, 0, 8) == retainedOverview, "panning through unknown terrain cannot evict derived explored terrain");

        try (var repo = new MapRepository(store)) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (repo.getOverview(18, 0) == null && System.nanoTime() < deadline) { repo.tick(); Thread.sleep(2); }
            check(repo.getOverview(18, 0) != null && repo.loaded().isEmpty(), "regional overview loads asynchronously without detailed residency");
            var cachedOverview = repo.getOverview(18, 0);
            while (repo.get(150, 0) == null && System.nanoTime() < deadline) { repo.tick(); Thread.sleep(2); }
            check(repo.get(150, 0) != null && repo.getOverview(18, 0) == cachedOverview,
                    "loading saved detail retains the existing overview");
            check(repo.put(tile(150, 0, 9, "minecraft:dirt_path")) && repo.getOverview(18, 0) != cachedOverview,
                    "live observations still update a cached overview immediately");
            synchronized (store) {
                repo.getOverview(75, 0, 2); repo.tick();
                check(repo.put(tile(150, 0, 10, "minecraft:gold_block")), "observations can advance during an in-flight overview read");
            }
            await(repo, () -> repo.getOverview(75, 0, 2) != null);
            check(repo.getOverview(75, 0, 2).columns().stream().flatMap(c -> c.layers().stream())
                    .anyMatch(layer -> layer.block().state().equals("minecraft:gold_block")),
                    "overview completion preserves observations newer than the worker inputs");
        }
    }
    private static void legacy(Path root) throws Exception {
        var store = new MapStore(root, "legacy", "world", "player", "dim");
        var bytes = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.DataOutputStream(bytes)) {
            out.writeInt(0x56524d32); out.writeInt(0); out.writeInt(0); out.writeLong(7);
            out.writeInt(1); out.writeUTF("minecraft:stone"); out.writeInt(1);
            out.writeInt(0); out.writeInt(70); out.writeInt(2);
            for (int y : new int[]{70, 40}) { out.writeInt(y); out.writeInt(0); out.writeInt(0xffffff); out.writeInt(0xffffff); out.writeInt(42); }
            out.writeInt(1); out.writeUTF("village"); out.writeUTF("Saved village"); out.writeInt(0); out.writeInt(70); out.writeInt(0);
        }
        var crc = new java.util.zip.CRC32(); crc.update(bytes.toByteArray());
        Files.createDirectories(store.root());
        try (var out = new java.io.DataOutputStream(Files.newOutputStream(store.root().resolve("0_0.vrmap")))) { out.writeLong(crc.getValue()); out.write(bytes.toByteArray()); }
        var old = store.read(0, 0);
        check(!old.columns().getFirst().exterior() && old.columns().getFirst().blocks().size() == 2 && old.markers().size() == 1,
                "previous map format retains exploration, layers, and village markers");
        var visible = MapSurfaceSnapshot.from(old).columns().getFirst().layers();
        check(visible.size() == 1 && visible.getFirst().block().y() == 70 && visible.getFirst().block().faces() == 2,
                "unverified legacy cave geometry is hidden before revisiting");
        store.write(old);
        check(store.read(0, 0).equals(old), "legacy safety flag survives saving in the new format");
        while (store.migrateStep().more()) { }
        check(store.read(0, 0).equals(old) && !Files.exists(store.root().resolve("0_0.vrmap")), "version two migration preserves explored terrain and markers");
        store.close();
    }
    private static void captureQueue() {
        var small = MapCaptureQueue.chunkOffsets(2); var large = MapCaptureQueue.chunkOffsets(6);
        check(small.size() == 25 && large.size() == 169 && large.stream().anyMatch(p -> p[0] == 6 && p[1] == 0),
                "capture sweep expands to the configured chunk render distance");
        check(small.stream().allMatch(p -> Math.abs(p[0]) <= 2 && Math.abs(p[1]) <= 2) && small.getFirst()[0] == 0 && small.getFirst()[1] == 0,
                "capture sweep prioritizes nearby chunks and respects a reduced range");
        var queue = new MapCaptureQueue();
        var expected = new HashSet<Long>(); var captured = new HashSet<Long>();
        for (int tick = 0; tick < 16; tick++) {
            for (int z = -24; z <= 24; z++) for (int x = tick * 8 - 24; x <= tick * 8 + 24; x++) if (MapSnapshot.inObservation(x, z, tick * 8 + .5, .5)) {
                queue.offer(x, z); expected.add(MapSnapshot.key(x, z));
            }
            drain(queue, captured, tick < 3 ? 0 : 512);
        }
        drain(queue, captured, Integer.MAX_VALUE);
        check(captured.equals(expected), "fast movement and delayed tile reads preserve every queued world column");
        queue.offer(120, 0);
        check(queue.tiles().isEmpty(), "completed columns do not crowd out new terrain");
        queue.refresh(); queue.offer(120, 0);
        check(!queue.tiles().isEmpty(), "refresh permits updates to previously captured terrain");
        queue.clear(); queue.offerTile(-1, -1);
        check(queue.mask(MapSnapshot.key(-1, -1)) == -1L, "loaded chunks enqueue complete tiles including negative coordinates");
        queue.complete(MapSnapshot.key(-1, -1), 1L); queue.offerTile(-1, -1);
        check(queue.mask(MapSnapshot.key(-1, -1)) == ~1L, "distant scheduling preserves completed columns in partial tiles");
        queue.clear(); for (int x = 0; x < 10000; x += 8) queue.offer(x, 0);
        check(queue.tiles().size() == MapCaptureQueue.MAX_TILES, "capture backlog is bounded");
    }
    private static void drain(MapCaptureQueue queue, Set<Long> captured, int budget) {
        for (long key : queue.tiles()) {
            long mask = queue.mask(key), done = 0;
            while (mask != 0 && budget-- > 0) {
                int index = Long.numberOfTrailingZeros(mask);
                captured.add(MapSnapshot.key((int)(key >> 32) * 8 + index % 8, (int)key * 8 + index / 8));
                done |= 1L << index; mask &= ~(1L << index);
            }
            queue.complete(key, done);
        }
    }
    private static void cache(Path root) throws Exception {
        var store = new MapStore(root,"cache","world","player","dim");
        var cache = new MapRepository(store);
        for(int i=0;i<1000;i++) cache.get(i,0);
        check(cache.requestCount() == MapRepository.MAX_REQUESTS,"pending disk requests bounded");
        await(cache, () -> cache.get(0,0) != null);
        check(cache.put(tile(0,0,1,"minecraft:stone")),"loaded tile can become dirty");
        cache.tick();
        check(cache.put(tile(0,0,2,"minecraft:dirt_path")),"revisit while previous write is in flight accepted");
        await(cache, () -> cache.dirtyCount()==0);
        check(store.read(0,0).revision()==2,"stale write completion cannot clear a newer dirty revision");
        for(int i=1;i<260;i++) { final int x=i; await(cache, () -> cache.get(x,0)!=null); }
        check(cache.loaded().size() <= MapRepository.MAX_TILES,"LRU resident tile bound");
        await(cache, () -> cache.get(0,0)!=null);
        check(cache.get(0,0).revision()==2,"evicted region reloads last seen snapshot");
        int count=0;
        for(var t:cache.loaded()) if(cache.put(tile(t.x(),t.z(),3,"minecraft:stone"))) count++;
        check(count==MapRepository.MAX_DIRTY && cache.dirtyCount()==MapRepository.MAX_DIRTY,"dirty backlog bound applies backpressure without eviction");
        cache.close(); check(cache.awaitClosed(5000),"disconnect flush drains bounded immutable batch");
        check(store.read(0,0).revision()>=2,"disconnect preserves recorded exploration");
    }
    private static void batchLoading(Path root) throws Exception {
        var store = new MapStore(root, "batch", "world", "player", "dim");
        for (int i = 0; i < 64; i++) store.write(tile(i, 0, 1, "minecraft:stone"));
        try (var cache = new MapRepository(store)) {
            for (int i = 0; i < 32; i++) cache.get(i, 0);
            cache.tick();
            for (int i = 0; i < 32; i++) cache.get(i, 0);
            check(cache.requestCount() == 32, "in-flight reads are not queued twice");
            await(cache, () -> !cache.loaded().isEmpty());
            check(cache.loaded().size() == 32, "one completion publishes a full tile batch");
            check(cache.requestCount() == 0, "completed batch releases all request slots");
            check(cache.put(tile(0, 0, 2, "minecraft:dirt_path")), "batch fixture dirties a loaded tile");
            for (int i = 32; i < 64; i++) cache.get(i, 0);
            cache.tick();
            check(cache.put(tile(0, 0, 3, "minecraft:grass_block")), "new observation arrives during a mixed read/write batch");
            await(cache, () -> cache.loaded().size() == 64);
            check(cache.get(0, 0).revision() == 3 && cache.dirtyCount() == 1, "stale batch write preserves newer dirty observation");
            await(cache, () -> cache.dirtyCount() == 0);
            check(store.read(0, 0).revision() == 3, "latest observation persists after batched I/O");
        }
        var blocked = new MapStore(root, "blocked-write", "world", "player", "dim");
        var cache = new MapRepository(blocked);
        await(cache, () -> cache.get(0, 0) != null);
        await(cache, () -> cache.get(16, 0) != null);
        Files.createDirectories(blocked.root().resolve("r.0.0.vrregion"));
        cache.put(tile(0, 0, 1, "minecraft:stone"));
        cache.put(tile(16, 0, 1, "minecraft:stone"));
        await(cache, () -> cache.get(1, 0) != null);
        check(cache.dirtyCount() == 1 && !cache.error().isEmpty(), "failed saves cannot starve requested terrain reads");
        check(blocked.read(16, 0).revision() == 1, "healthy regions save and release dirty slots while another region fails");
        Files.delete(blocked.root().resolve("r.0.0.vrregion"));
        await(cache, () -> cache.dirtyCount() == 0);
        cache.close();
        check(cache.awaitClosed(5000), "mixed I/O worker closes cleanly");
    }
    private static void surfaceCache(Path root) throws Exception {
        var store = new MapStore(root, "surfaces", "world", "player", "dim");
        var shell = new MapSnapshot(0, 0, 1, List.of(new MapSnapshot.Column(0, 70, List.of(
                new MapSnapshot.Block(70, "minecraft:oak_leaves", 0x88aa44, 0xffffff, 42),
                new MapSnapshot.Block(65, "minecraft:oak_log", 0xffffff, 0xffffff, 42),
                new MapSnapshot.Block(64, "minecraft:grass_block", 0x88aa44, 0xffffff, 42)), true)), List.of());
        store.write(shell);
        var cache = new MapRepository(store);
        await(cache, () -> cache.getSurface(0, 0) != null);
        check(cache.loaded().isEmpty(), "overview reads do not populate or evict the detailed terrain cache");
        check(cache.getSurface(0, 0).columns().getFirst().layers().size() == 1, "flat overview retains the highest surface without expanding buried layers");
        await(cache, () -> cache.get(0, 0) != null);
        check(cache.get(0, 0).equals(shell), "requesting detail after overview reloads the complete saved shell");
        store.write(new MapSnapshot(1, 0, 1, shell.columns(), List.of()));
        cache.getSurface(1, 0); cache.tick();
        cache.get(1, 0);
        await(cache, () -> cache.get(1, 0) != null);
        check(cache.get(1, 0).columns().equals(shell.columns()), "detail requests arriving during summary reads still receive complete shells");

        for (int i = 1; i <= MapRepository.MAX_SURFACES; i++) {
            final int x = i;
            await(cache, () -> cache.getSurface(x, 0) != null);
        }
        check(cache.surfaceCount() == MapRepository.MAX_SURFACES && cache.loaded().size() == 2,
                "far zoom keeps both overview and detail residency bounded independently");
        check(cache.getSurface(0, 0) != null && cache.requestCount() == 0, "evicted summaries rebuild immediately from resident detail without stale disk reads");
        cache.close(); check(cache.awaitClosed(5000), "overview worker closes cleanly");
    }
    private static MapSnapshot tile(int x,int z,long revision,String state) {
        return new MapSnapshot(x,z,revision,List.of(new MapSnapshot.Column(0,64,List.of(new MapSnapshot.Block(64,state,0xffffff,0xabcdef,63)),true)),List.of());
    }
    private interface Checked { void run() throws Exception; }
    private static void expectFailure(Checked runnable,String name)throws Exception {try{runnable.run();}catch(Exception expected){check(true,name);return;}throw new AssertionError(name);}
    private static void check(boolean value,String name){if(!value)throw new AssertionError(name);passed++;System.out.println("PASS "+name);}
    private static void await(MapRepository repository,java.util.function.BooleanSupplier done)throws Exception{
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!done.getAsBoolean()){repository.tick();Thread.sleep(1);if(System.nanoTime()>deadline)throw new AssertionError("Repository timed out");}
    }
}
