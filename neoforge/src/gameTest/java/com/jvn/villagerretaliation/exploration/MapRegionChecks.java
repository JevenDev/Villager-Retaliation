package com.jvn.villagerretaliation.exploration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

final class MapRegionChecks {
    private static int passed;
    static void run(Path root) throws Exception {
        roundTrip(root);
        reusableReads(root);
        recovery(root);
        migration(root);
        scaling(root);
        System.out.println("Passed " + passed + " compressed-region checks");
    }
    private static MapSnapshot tile(int x, int z, long revision) {
        var columns = new ArrayList<MapSnapshot.Column>();
        for (int i = 0; i < 64; i++) {
            var blocks = new ArrayList<MapSnapshot.Block>();
            for (int y = 70; y >= 64; y--) blocks.add(new MapSnapshot.Block(y,
                    y == 70 ? "minecraft:grass_block[snowy=false]" : "minecraft:stone", 0x88aa44, 0x4488ff, 42));
            columns.add(new MapSnapshot.Column(i, 70, blocks, true));
        }
        return new MapSnapshot(x, z, revision, columns,
                List.of(new MapSnapshot.Marker("village", "Saved village", x * 8, 70, z * 8)));
    }
    private static void roundTrip(Path root) throws Exception {
        Path scope = root.resolve("regions");
        var tiles = new ArrayList<MapSnapshot>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) tiles.add(tile(x, z, 1));
        try (var store = new MapStore(scope)) { store.writeBatch(tiles); }
        try (var store = new MapStore(scope)) {
            for (var tile : tiles) check(store.read(tile.x(), tile.z()).equals(tile), "region tile round trip");
            check(store.read(16, 0).columns().isEmpty(), "unwritten region is empty");
            for (int x : new int[]{-17, -16, -1, 16, 31, 32, 3000000}) {
                var tile = tile(x, -x, 9); store.write(tile);
                check(store.read(x, -x).equals(tile), "region boundaries and negative coordinates");
            }
            for (int i = 0; i < 32; i++) store.write(tile(i * 16, -32, 1));
            check(store.read(0, 0).equals(tiles.getFirst()), "evicted file handles reopen correctly");
            check(store.readOverview(0, 0, 8).columns().size() == 64, "overview uses indexed compressed tiles");
        }
        check(Files.size(scope.resolve("r.0.0.vrregion")) < 256L * MapStore.encode(tiles.getFirst()).length / 4,
                "representative terrain compresses to less than a quarter of its original bytes");
    }
    private static void reusableReads(Path root) throws Exception {
        Path scope = root.resolve("reusable-reads"), file = scope.resolve("r.0.0.vrregion");
        var large = tile(0, 0, 1);
        var small = new MapSnapshot(1, 0, 2, List.of(large.columns().getFirst()), List.of());
        try (var store = new MapStore(scope)) { store.writeBatch(List.of(large, small)); }
        try (var region = new MapRegionFile(file, 0, 0)) {
            var buffer = new MapRegionFile.ReadBuffer();
            int length = region.read(0, buffer);
            check(MapStore.decodeSurface(buffer.raw, length, 0, 0).equals(MapSurfaceSnapshot.from(large)), "reused read decodes large terrain");
            byte[] raw = buffer.raw, compressed = buffer.compressed;
            length = region.read(1, buffer);
            check(length < raw.length && buffer.raw == raw && buffer.compressed == compressed,
                    "smaller records reuse both buffers with a distinct logical length");
            check(MapStore.decodeSurface(buffer.raw, length, 1, 0).equals(MapSurfaceSnapshot.from(small)), "stale buffer tails are excluded from decoding and checksums");
            check(region.read(2, buffer) == 0, "missing record cannot reuse stale terrain");
            buffer.raw[8] ^= 1;
            int smallLength = length;
            fails(() -> MapStore.decodeSurface(buffer.raw, smallLength, 1, 0), "reused buffer still validates tile checksum");
            length = region.read(0, buffer);
            check(MapStore.decodeSurface(buffer.raw, length, 0, 0).equals(MapSurfaceSnapshot.from(large)), "large record survives reuse after shorter reads");
        }
    }
    private static int newest(byte[] bytes, int index) {
        int a = (int)MapRegionFile.indexOffset(index, 0), b = (int)MapRegionFile.indexOffset(index, 1);
        return ByteBuffer.wrap(bytes).getLong(a) >= ByteBuffer.wrap(bytes).getLong(b) ? a : b;
    }
    private static void recovery(Path root) throws Exception {
        Path scope = root.resolve("recovery"), file = scope.resolve("r.0.0.vrregion");
        var first = tile(0, 0, 1); var second = tile(0, 0, 2);
        try (var store = new MapStore(scope)) { store.write(first); store.write(second); }
        byte[] valid = Files.readAllBytes(file), torn = valid.clone();
        int entry = newest(valid, 0);
        torn[entry + 31] ^= 1; Files.write(file, torn);
        try (var store = new MapStore(scope)) {
            check(store.read(0, 0).equals(first), "torn index preserves previous committed tile");
            store.write(second);
        }
        try (var store = new MapStore(scope)) { check(store.read(0, 0).equals(second), "replacement repairs torn index without overwriting retained data"); }
        Files.write(file, valid);
        Files.write(file, new byte[8192], StandardOpenOption.APPEND);
        try (var store = new MapStore(scope)) {
            check(store.read(0, 0).equals(second), "unpublished appended data does not change committed tile");
            store.write(tile(0, 0, 3));
            check(Files.size(file) <= valid.length + 8192, "unpublished space can be reused");
        }
        byte[] damaged = valid.clone();
        int offset = (int)ByteBuffer.wrap(valid).getLong(entry + 8);
        damaged[offset] ^= 1; Files.write(file, damaged);
        try (var store = new MapStore(scope)) { fails(() -> store.read(0, 0), "committed payload corruption is reported, not silently rolled back"); }
        Files.write(file, Arrays.copyOf(valid, MapRegionFile.DATA_START - 1));
        try (var store = new MapStore(scope)) { fails(() -> store.read(0, 0), "truncated region index rejected"); }
        byte[] invalidLength = valid.clone();
        ByteBuffer.wrap(invalidLength).putInt(entry + 20, MapStore.encode(second).length - 1);
        var crc = new java.util.zip.CRC32(); crc.update(new byte[12]); crc.update(invalidLength, entry, 28);
        ByteBuffer.wrap(invalidLength).putInt(entry + 28, (int)crc.getValue()); Files.write(file, invalidLength);
        try (var store = new MapStore(scope)) { fails(() -> store.read(0, 0), "decompression cannot exceed declared record size"); }
        Files.write(file, valid);
        Files.copy(file, scope.resolve("r.1.0.vrregion"));
        try (var store = new MapStore(scope)) { fails(() -> store.read(16, 0), "region coordinate substitution rejected"); }
        Path failedBatch = root.resolve("failed-batch"); Files.createDirectories(failedBatch.resolve("r.0.0.vrregion"));
        try (var store = new MapStore(failedBatch)) {
            var errors = store.saveBatch(List.of(first, tile(16, 0, 1)));
            check(errors.size() == 1 && errors.containsKey(first.key()) && store.read(16, 0).equals(tile(16, 0, 1)),
                    "one failed region does not prevent other regions from saving");
        }
        Files.write(file, valid);
        try (var store = new MapStore(scope)) {
            for (int i = 3; i <= 8; i++) store.write(tile(0, 0, i));
            long stableSize = Files.size(file);
            for (int i = 9; i <= 160; i++) store.write(tile(0, 0, i));
            check((Files.size(file) + MapRegionFile.SECTOR - 1) / MapRegionFile.SECTOR == (stableSize + MapRegionFile.SECTOR - 1) / MapRegionFile.SECTOR, "repeated observations recycle sectors without unbounded growth");
            check(store.read(0, 0).revision() == 160, "recycled records retain newest snapshot");
        }
    }
    private static void migration(Path root) throws Exception {
        Path scope = root.resolve("migration"); Files.createDirectories(scope);
        var originals = new ArrayList<MapSnapshot>();
        for (int x = -16; x < 17; x++) {
            var tile = tile(x, 0, 1); originals.add(tile);
            Files.write(scope.resolve(x + "_0.vrmap"), MapStore.encode(tile));
        }
        try (var store = new MapStore(scope)) {
            check(store.read(-1, 0).equals(originals.get(15)), "legacy terrain available before migration");
            var step = store.migrateStep();
            check(step.more() && step.migrated() > 0 && step.migrated() <= 8, "migration work is bounded per I/O batch");
        }
        try (var store = new MapStore(scope)) {
            while (store.migrateStep().more()) { }
            for (var tile : originals) check(store.read(tile.x(), tile.z()).equals(tile), "migration restart preserves every column and marker");
        }
        try (var files = Files.newDirectoryStream(scope, "*.vrmap")) { check(!files.iterator().hasNext(), "verified legacy files removed after migration"); }
        var old = tile(0, 0, 1);
        var updated = new MapSnapshot(0, 0, 2, List.of(new MapSnapshot.Column(0, 71,
                List.of(new MapSnapshot.Block(71, "minecraft:dirt_path", 0xffffff, 0xffffff, 42)), true)), List.of());
        Path overlap = root.resolve("migration-overlap"); Files.createDirectories(overlap);
        Files.write(overlap.resolve("0_0.vrmap"), MapStore.encode(old));
        try (var store = new MapStore(overlap)) {
            store.write(updated);
            var merged = store.read(0, 0);
            check(merged.columns().size() == 64 && merged.columns().getFirst().equals(updated.columns().getFirst()) && merged.markers().equals(old.markers()),
                    "interrupted migration merges missing exploration and keeps newer observations");
            while (store.migrateStep().more()) { }
            check(store.read(0, 0).equals(merged), "verified merge persists without loss");
        }
        Files.write(overlap.resolve("0_0.vrmap"), MapStore.encode(old));
        try (var store = new MapStore(overlap)) {
            while (store.migrateStep().more()) { }
            check(store.read(0, 0).columns().getFirst().equals(updated.columns().getFirst()), "migration replay cannot restore stale overlapping terrain");
        }
        Path corrupt = root.resolve("migration-corrupt"); Files.createDirectories(corrupt);
        byte[] damaged = new byte[]{1, 2, 3}; Files.write(corrupt.resolve("0_0.vrmap"), damaged);
        Files.write(corrupt.resolve("1_0.vrmap"), MapStore.encode(tile(1, 0, 1)));
        try (var store = new MapStore(corrupt)) {
            boolean reported = false;
            MapStore.Migration step;
            do { step = store.migrateStep(); reported |= !step.error().isEmpty(); } while (step.more());
            check(reported && Arrays.equals(damaged, Files.readAllBytes(corrupt.resolve("0_0.vrmap"))), "corrupt legacy file preserved and reported");
            check(store.read(1, 0).equals(tile(1, 0, 1)), "corrupt legacy tile does not block healthy migration");
            fails(() -> store.read(0, 0), "unmigrated corrupt legacy tile remains visible as a read error");
        }
        Path writeFailure = root.resolve("migration-write-failure"); Files.createDirectories(writeFailure.resolve("r.0.0.vrregion.pending"));
        Files.write(writeFailure.resolve("0_0.vrmap"), MapStore.encode(old));
        try (var store = new MapStore(writeFailure)) {
            fails(store::migrateStep, "failed region publication reports migration failure");
            check(Arrays.equals(MapStore.encode(old), Files.readAllBytes(writeFailure.resolve("0_0.vrmap"))), "failed region publication leaves the original bytes intact");
        }
        Files.delete(writeFailure.resolve("r.0.0.vrregion.pending"));
        try (var store = new MapStore(writeFailure)) {
            while (store.migrateStep().more()) { }
            check(store.read(0, 0).equals(old), "migration resumes after destination becomes writable");
        }
        Path background = root.resolve("migration-background"); Files.createDirectories(background);
        for (int i = 0; i < 96; i++) Files.write(background.resolve(i + "_0.vrmap"), MapStore.encode(tile(i, 0, 1)));
        var repository = new MapRepository(new MapStore(background));
        try {
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (repository.get(95, 0) == null && System.nanoTime() < deadline) { repository.tick(); Thread.sleep(1); }
            check(tile(95, 0, 1).equals(repository.get(95, 0)), "visible terrain loads while migration runs in the background");
            try (var files = Files.list(background)) {
                check(files.filter(path -> path.toString().endsWith(".vrmap")).count() >= 80, "loading visible terrain does not wait for the full migration");
            }
        } finally { repository.close(); check(repository.awaitClosed(5000), "migration worker releases storage on disconnect"); }
        try (var store = new MapStore(background)) {
            while (store.migrateStep().more()) { }
            check(store.read(0, 0).equals(tile(0, 0, 1)) && store.read(95, 0).equals(tile(95, 0, 1)), "disconnected migration resumes without losing either end of the explored area");
        }
        Path blocked = root.resolve("migration-blocked"); Files.createDirectories(blocked.resolve("r.0.0.vrregion"));
        byte[] bytes = MapStore.encode(old); Files.write(blocked.resolve("0_0.vrmap"), bytes);
        try (var store = new MapStore(blocked)) {
            var result = store.migrateStep();
            check(!result.error().isEmpty() && Arrays.equals(bytes, Files.readAllBytes(blocked.resolve("0_0.vrmap"))), "failed destination preserves migration source");
        }
    }
    private static void scaling(Path root) throws Exception {
        Path scope = root.resolve("large-regions");
        var tiles = new ArrayList<MapSnapshot>();
        for (int z = -32; z < 32; z++) for (int x = -32; x < 32; x++) tiles.add(tile(x, z, 1));
        long raw = 0;
        for (var tile : tiles) raw += MapStore.encode(tile).length;
        try (var store = new MapStore(scope)) { store.writeBatch(tiles); }
        long disk = 0; int files = 0;
        try (var paths = Files.newDirectoryStream(scope)) { for (var path : paths) { files++; disk += Files.size(path); } }
        check(files == 16 && disk < raw / 4, "large world groups 4096 tiles into 16 compressed files");
        long start = System.nanoTime();
        try (var store = new MapStore(scope)) {
            for (var tile : tiles) if (!store.read(tile.x(), tile.z()).equals(tile)) throw new AssertionError("large world snapshot mismatch");
            check(store.readOverview(-4, -4, 8).columns().size() == 64, "large world overview reads indexed region");
        }
        System.out.printf(Locale.ROOT, "4096-tile compressed storage: %,d -> %,d bytes, %d files, cold sequential decode %.1f ms%n",
                raw, disk, files, (System.nanoTime() - start) / 1_000_000.0);
    }
    private interface Checked { void run() throws Exception; }
    private static void fails(Checked action, String message) throws Exception {
        try { action.run(); } catch (IOException e) { check(true, message); return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); passed++; }
}
