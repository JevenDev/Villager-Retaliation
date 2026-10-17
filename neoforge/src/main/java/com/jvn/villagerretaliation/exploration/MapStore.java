package com.jvn.villagerretaliation.exploration;

import java.io.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.CRC32;

// one owner per scope, with all disk work on the map I/O worker
public final class MapStore implements AutoCloseable {
    public static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private final Path root;
    private final MapRegionFile.ReadBuffer readBuffer = new MapRegionFile.ReadBuffer();
    private final LinkedHashMap<Long, MapRegionFile> regions = new LinkedHashMap<>(16, .75f, true);
    private final LinkedHashSet<Long> missingRegions = new LinkedHashSet<>();
    private DirectoryStream<Path> legacyFiles;
    private Iterator<Path> legacyIterator;
    private boolean migrationFinished, legacyRemaining;
    public record Migration(boolean more, int migrated, String error) { }
    private record Legacy(Path path, byte[] bytes, MapSnapshot tile, boolean write) { }
    private static final java.util.regex.Pattern LEGACY_NAME = java.util.regex.Pattern.compile("(-?\\d+)_(-?\\d+)\\.vrmap");
    private final Long2ObjectLinkedOpenHashMap<MapSurfaceSnapshot> overviewSources = new Long2ObjectLinkedOpenHashMap<>(256);
    private record OverviewKey(int x, int z, int stride) {
        @Override public int hashCode() { return (int)it.unimi.dsi.fastutil.HashCommon.mix(MapSnapshot.key(x, z)) ^ stride; }
    }
    private final LinkedHashMap<OverviewKey, MapSurfaceSnapshot> overviews = new LinkedHashMap<>(256, .75f, true);
    private final LinkedHashMap<OverviewKey, MapSurfaceSnapshot> emptyOverviews = new LinkedHashMap<>(256, .75f, true);
    synchronized void rememberSurface(MapSurfaceSnapshot surface) {
        boolean empty = surface.columns().isEmpty() && surface.markers().isEmpty();
        if (empty) overviewSources.remove(surface.key());
        else overviewSources.putAndMoveToLast(surface.key(), surface);
        while (overviewSources.size() > MapRepository.MAX_SURFACES) overviewSources.removeFirst();
    }
    public MapStore(Path base, String server, String world, String player, String dimension) {
        this(base.resolve(hash(server + "\n" + world + "\n" + player + "\n" + dimension)));
    }
    MapStore(Path scope) { root = scope; }
    public Path root() { return root; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private Path path(int x, int z) { return root.resolve(x + "_" + z + ".vrmap"); }
    private static MapSnapshot empty(int x, int z) { return new MapSnapshot(x, z, 0, List.of(), List.of()); }
    public synchronized MapSnapshot read(int x, int z) throws IOException {
        var tile = readRegion(x, z);
        Path legacy = path(x, z);
        if ((!migrationFinished || legacyRemaining) && Files.exists(legacy)) {
            var old = decode(readLegacy(legacy), x, z);
            return tile == null ? old : merge(old, tile);
        }
        return tile == null ? empty(x, z) : tile;
    }
    private MapSnapshot readRegion(int x, int z) throws IOException {
        var region = region(x, z, false);
        if (region == null) return null;
        byte[] bytes = region.read(MapRegionFile.index(x, z));
        return bytes == null ? null : decode(bytes, x, z);
    }
    private static byte[] readLegacy(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Oversize map tile");
        return Files.readAllBytes(file);
    }
    static MapSnapshot decode(byte[] bytes, int x, int z) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("Oversize map tile");
        if (bytes.length < 16) throw new IOException("Truncated map tile");
        CRC32 crc = new CRC32(); crc.update(bytes, 8, bytes.length - 8);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readLong() != crc.getValue()) throw new IOException("Map checksum");
            int version = in.readInt();
            if (version != 0x56524d32 && version != 0x56524d33 && version != 0x56524d34) throw new IOException("Map version");
            if (in.readInt() != x || in.readInt() != z) throw new IOException("Map coordinate mismatch");
            long revision = in.readLong();
            int paletteSize = bounded(in.readInt(), MapSnapshot.MAX_BLOCKS);
            List<String> palette = new ArrayList<>();
            for (int i = 0; i < paletteSize; i++) { String state = in.readUTF(); if (state.length() > 1024) throw new IOException("State length"); palette.add(state); }
            int count = bounded(in.readInt(), 64);
            List<MapSnapshot.Column> columns = new ArrayList<>();
            for (int c = 0; c < count; c++) {
                int index = in.readInt(), top = in.readInt(), blocks = bounded(in.readInt(), MapSnapshot.DEPTH);
                List<MapSnapshot.Block> shell = new ArrayList<>();
                for (int b = 0; b < blocks; b++) {
                    int y = in.readInt(), state = in.readInt();
                    if (state < 0 || state >= palette.size()) throw new IOException("State palette index");
                    shell.add(new MapSnapshot.Block(y, palette.get(state), in.readInt(), in.readInt(), in.readInt(), version >= 0x56524d34 ? in.readUnsignedByte() : 0));
                }
                columns.add(new MapSnapshot.Column(index, top, shell, version >= 0x56524d33 && in.readBoolean()));
            }
            int markers = bounded(in.readInt(), 16);
            List<MapSnapshot.Marker> locations = new ArrayList<>();
            for (int m = 0; m < markers; m++) locations.add(new MapSnapshot.Marker(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt()));
            if (in.available() != 0) throw new IOException("Trailing map data");
            MapSnapshot result = new MapSnapshot(x, z, revision, columns, locations);
            if (locations.stream().anyMatch(m -> !result.permits(m))) throw new IOException("Unobserved marker");
            return result;
        } catch (IllegalArgumentException e) { throw new IOException("Invalid map data", e); }
    }
    private MapSurfaceSnapshot readSurfaceData(int x, int z) throws IOException {
        if ((!migrationFinished || legacyRemaining) && Files.exists(path(x, z))) return MapSurfaceSnapshot.from(read(x, z));
        var region = region(x, z, false);
        int length = region == null ? 0 : region.read(MapRegionFile.index(x, z), readBuffer);
        return length == 0 ? new MapSurfaceSnapshot(x, z, 0, List.of(), List.of()) : decodeSurface(readBuffer.raw, length, x, z);
    }
    private static final ThreadLocal<MapSurfaceColumns.Builder> SURFACE_DECODER = ThreadLocal.withInitial(MapSurfaceColumns.Builder::new);
    static MapSurfaceSnapshot decodeSurface(byte[] bytes, int x, int z) throws IOException {
        return decodeSurface(bytes, bytes.length, x, z);
    }
    static MapSurfaceSnapshot decodeSurface(byte[] bytes, int length, int x, int z) throws IOException {
        if (length > MAX_FILE_BYTES) throw new IOException("Oversize map tile");
        if (length < 16 || length > bytes.length) throw new IOException("Truncated map tile");
        CRC32 crc = new CRC32(); crc.update(bytes, 8, length - 8);
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes, 0, length))) {
            if (in.readLong() != crc.getValue()) throw new IOException("Map checksum");
            int version = in.readInt();
            if (version != 0x56524d32 && version != 0x56524d33 && version != 0x56524d34) throw new IOException("Map version");
            if (in.readInt() != x || in.readInt() != z) throw new IOException("Map coordinate mismatch");
            long revision = in.readLong();
            int paletteSize = bounded(in.readInt(), MapSnapshot.MAX_BLOCKS);
            var palette = new String[paletteSize];
            for (int i = 0; i < paletteSize; i++) { palette[i] = in.readUTF(); if (palette[i].length() > 1024) throw new IOException("State length"); }
            int count = bounded(in.readInt(), 64); long columns = 0;
            var builder = SURFACE_DECODER.get(); builder.clear();
            for (int c = 0; c < count; c++) {
                int index = in.readInt(), top = in.readInt(), blocks = bounded(in.readInt(), MapSnapshot.DEPTH);
                if (index < 0 || index >= 64 || top < -4096 || top > 4096 || (columns & 1L << index) != 0) throw new IOException("Invalid column");
                columns |= 1L << index; builder.column(index, -1L);
                int lastY = Integer.MAX_VALUE, topTint = 0, topWater = 0, topLight = 0; String topState = null, surfaceState = null;
                int surfaceY = 0, surfaceBottom = 0, surfaceTint = 0, surfaceWater = 0, surfaceLight = 0;
                for (int i = 0; i < blocks; i++) {
                    int y = in.readInt(), state = in.readInt(), tint = in.readInt(), water = in.readInt(), faces = in.readInt();
                    int light = version >= 0x56524d34 ? in.readUnsignedByte() : 0;
                    if (state < 0 || state >= paletteSize || faces < 0 || faces > 63 || light > 15 || y > top || y <= top - MapSnapshot.DEPTH || y >= lastY)
                        throw new IOException("Invalid shell");
                    lastY = y;
                    if (y == top) { topState = palette[state]; topTint = tint; topWater = water; topLight = light; }
                    if (surfaceState == null && (faces & 2) != 0) {
                        surfaceState = palette[state]; surfaceY = y; surfaceBottom = y;
                        surfaceTint = tint; surfaceWater = water; surfaceLight = light;
                    } else if (surfaceState != null && faces == 0 && surfaceState.equals(palette[state])) surfaceBottom = y;
                }
                boolean exterior = version >= 0x56524d33 && in.readBoolean();
                if (exterior && surfaceState != null) builder.layer(surfaceY, surfaceBottom, surfaceState, surfaceTint, surfaceWater, 2, surfaceLight);
                if (!exterior) {
                    if (topState != null) builder.layer(top, top, topState, topTint, topWater, 2, topLight);
                }
            }
            int countMarkers = bounded(in.readInt(), 16);
            var markers = new ArrayList<MapSnapshot.Marker>(countMarkers);
            for (int i = 0; i < countMarkers; i++) {
                var marker = new MapSnapshot.Marker(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt());
                int index = Math.floorMod(marker.x(), 8) + Math.floorMod(marker.z(), 8) * 8;
                if (MapSnapshot.tile(marker.x()) != x || MapSnapshot.tile(marker.z()) != z || (columns & 1L << index) == 0) throw new IOException("Unobserved marker");
                markers.add(marker);
            }
            if (in.available() != 0) throw new IOException("Trailing map data");
            return new MapSurfaceSnapshot(x, z, revision, builder.build(), markers);
        } catch (IllegalArgumentException e) { throw new IOException("Invalid map data", e); }
    }
    synchronized MapSurfaceSnapshot readSurface(int x, int z) throws IOException {
        var cached = overviewSources.getAndMoveToLast(MapSnapshot.key(x, z));
        if (cached != null) return cached;
        var surface = readSurfaceData(x, z);
        rememberSurface(surface);
        return surface;
    }
    public MapSurfaceSnapshot readOverview(int x, int z) throws IOException { return readOverview(x, z, MapOverview.STRIDE); }
    public synchronized MapSurfaceSnapshot readOverview(int x, int z, int stride) throws IOException {
        if (stride != 2 && stride != 4 && stride != 8) {
            var result = MapOverview.empty(x, z, stride);
            for (int dz = 0; dz < stride; dz++) for (int dx = 0; dx < stride; dx++)
                result = MapOverview.update(result, MapSurfaceSnapshot.from(read(x * stride + dx, z * stride + dz)));
            return result;
        }
        var key = new OverviewKey(x, z, stride);
        var empty = emptyOverviews.get(key);
        if (empty != null) return empty;
        var cached = overviews.get(key);
        if (cached != null) return cached;
        if (migrationFinished && !legacyRemaining && region(x * stride, z * stride, false) == null)
            return cacheOverview(key, MapOverview.empty(x, z, stride));
        var sources = new ArrayList<MapSurfaceSnapshot>(stride * stride);
        for (int dz = 0; dz < stride; dz++) for (int dx = 0; dx < stride; dx++)
            sources.add(readSurface(x * stride + dx, z * stride + dz));
        return cacheOverview(key, MapOverview.combine(x, z, stride, sources));
    }
    private MapSurfaceSnapshot cacheOverview(OverviewKey key, MapSurfaceSnapshot overview) {
        var cache = overview.columns().isEmpty() && overview.markers().isEmpty() ? emptyOverviews : overviews;
        cache.put(key, overview);
        while (cache.size() > MapRepository.MAX_SURFACES * 2) cache.remove(cache.keySet().iterator().next());
        return overview;
    }
    private MapRegionFile region(int x, int z, boolean create) throws IOException {
        int rx = Math.floorDiv(x, MapRegionFile.SIDE), rz = Math.floorDiv(z, MapRegionFile.SIDE);
        long key = MapSnapshot.key(rx, rz);
        var region = regions.get(key);
        if (region != null) return region;
        Path file = root.resolve("r." + rx + "." + rz + ".vrregion");
        if (!create && (missingRegions.contains(key) || !Files.exists(file))) {
            missingRegions.add(key);
            while (missingRegions.size() > MapRepository.MAX_SURFACES) missingRegions.remove(missingRegions.iterator().next());
            return null;
        }
        if (regions.size() >= 16) {
            var oldest = regions.entrySet().iterator().next();
            oldest.getValue().close(); regions.remove(oldest.getKey());
        }
        region = new MapRegionFile(file, rx, rz);
        regions.put(key, region); missingRegions.remove(key);
        return region;
    }
    private static int bounded(int count, int max) throws IOException {
        if (count < 0 || count > max) throw new IOException("Map collection limit");
        return count;
    }
    static byte[] encode(MapSnapshot tile) throws IOException {
        if (tile.markers().stream().anyMatch(m -> !tile.permits(m))) throw new IOException("Unobserved marker");
        var bytes = new ByteArrayOutputStream();
        Map<String, Integer> palette = new LinkedHashMap<>();
        for (var column : tile.columns()) for (var block : column.blocks()) palette.computeIfAbsent(block.state(), key -> palette.size());
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(0x56524d34); out.writeInt(tile.x()); out.writeInt(tile.z()); out.writeLong(tile.revision());
            out.writeInt(palette.size()); for (String state : palette.keySet()) out.writeUTF(state);
            out.writeInt(tile.columns().size());
            for (var c : tile.columns()) {
                out.writeInt(c.index()); out.writeInt(c.top()); out.writeInt(c.blocks().size());
                for (var b : c.blocks()) { out.writeInt(b.y()); out.writeInt(palette.get(b.state())); out.writeInt(b.tint()); out.writeInt(b.waterTint()); out.writeInt(b.faces()); out.writeByte(b.light()); }
                out.writeBoolean(c.exterior());
            }
            out.writeInt(tile.markers().size());
            for (var m : tile.markers()) { out.writeUTF(m.id()); out.writeUTF(m.name()); out.writeInt(m.x()); out.writeInt(m.y()); out.writeInt(m.z()); }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length + 8 > MAX_FILE_BYTES) throw new IOException("Oversize map tile");
        CRC32 crc = new CRC32(); crc.update(payload);
        var result = new ByteArrayOutputStream(payload.length + 8);
        try (var out = new DataOutputStream(result)) { out.writeLong(crc.getValue()); out.write(payload); }
        return result.toByteArray();
    }
    public void write(MapSnapshot tile) throws IOException { writeBatch(List.of(tile)); }
    public void writeBatch(List<MapSnapshot> tiles) throws IOException {
        IOException failure = null;
        for (var error : new LinkedHashSet<>(saveBatch(tiles).values())) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure != null) throw failure;
    }
    synchronized Map<Long, IOException> saveBatch(List<MapSnapshot> tiles) {
        var errors = new LinkedHashMap<Long, IOException>();
        var grouped = new LinkedHashMap<Long, Map<Integer, byte[]>>();
        for (var tile : tiles) {
            long key = MapSnapshot.key(Math.floorDiv(tile.x(), MapRegionFile.SIDE), Math.floorDiv(tile.z(), MapRegionFile.SIDE));
            try {
                byte[] bytes = encode(tile);
                grouped.computeIfAbsent(key, unused -> new LinkedHashMap<>()).put(MapRegionFile.index(tile.x(), tile.z()), bytes);
                overviewSources.remove(tile.key());
                for (int stride = 2; stride <= MapOverview.STRIDE; stride *= 2) {
                    var overviewKey = new OverviewKey(Math.floorDiv(tile.x(), stride), Math.floorDiv(tile.z(), stride), stride);
                    overviews.remove(overviewKey); emptyOverviews.remove(overviewKey);
                }
            } catch (IOException e) { errors.put(tile.key(), e); }
        }
        for (var group : grouped.entrySet()) {
            long key = group.getKey();
            try { region((int)(key >> 32) * MapRegionFile.SIDE, (int)key * MapRegionFile.SIDE, true).write(group.getValue()); }
            catch (IOException e) {
                var failed = regions.remove(key);
                if (failed != null) try { failed.close(); } catch (IOException close) { e.addSuppressed(close); }
                for (int index : group.getValue().keySet()) errors.put(MapSnapshot.key(
                        (int)(key >> 32) * MapRegionFile.SIDE + index % MapRegionFile.SIDE,
                        (int)key * MapRegionFile.SIDE + index / MapRegionFile.SIDE), e);
            }
        }
        return errors;
    }
    public synchronized Migration migrateStep() throws IOException {
        if (migrationFinished) return new Migration(false, 0, "");
        if (legacyFiles == null) {
            if (!Files.isDirectory(root)) { migrationFinished = true; return new Migration(false, 0, ""); }
            legacyFiles = Files.newDirectoryStream(root, "*.vrmap"); legacyIterator = legacyFiles.iterator();
        }
        var batch = new ArrayList<Legacy>();
        String error = "";
        long deadline = System.nanoTime() + 4_000_000;
        try {
            for (int count = 0; count < 8 && legacyIterator.hasNext(); count++) {
                Path file = legacyIterator.next();
                var match = LEGACY_NAME.matcher(file.getFileName().toString());
                if (!match.matches()) continue;
                try {
                    int x = Integer.parseInt(match.group(1)), z = Integer.parseInt(match.group(2));
                    byte[] bytes = readLegacy(file);
                    var old = decode(bytes, x, z); var current = readRegion(x, z);
                    var merged = current == null ? old : merge(old, current);
                    batch.add(new Legacy(file, bytes, merged, !merged.equals(current)));
                } catch (IOException | IllegalArgumentException e) {
                    legacyRemaining = true; error = "Map migration preserved " + file.getFileName() + ": " + e.getMessage();
                    System.getLogger("VR map").log(System.Logger.Level.WARNING, error, e);
                }
                if (System.nanoTime() >= deadline) break;
            }
            if (!batch.isEmpty()) {
                writeBatch(batch.stream().filter(Legacy::write).map(Legacy::tile).toList());
                for (var legacy : batch) {
                    // old files remain recoverable until the durable replacement passes a full decode
                    if (!legacy.tile.equals(readRegion(legacy.tile.x(), legacy.tile.z()))) throw new IOException("Map migration verification failed");
                    if (!Arrays.equals(legacy.bytes, readLegacy(legacy.path))) throw new IOException("Legacy map changed during migration");
                    Files.delete(legacy.path);
                }
            }
            if (!legacyIterator.hasNext()) { legacyFiles.close(); legacyFiles = null; legacyIterator = null; migrationFinished = true; }
            return new Migration(!migrationFinished, batch.size(), error);
        } catch (DirectoryIteratorException e) { throw e.getCause(); }
        catch (IOException e) { legacyRemaining = true; throw e; }
    }
    private static MapSnapshot merge(MapSnapshot old, MapSnapshot current) throws IOException {
        var columns = new TreeMap<Integer, MapSnapshot.Column>();
        for (var column : old.columns()) columns.put(column.index(), column);
        for (var column : current.columns()) columns.put(column.index(), column);
        var markers = new LinkedHashMap<String, MapSnapshot.Marker>();
        for (var marker : old.markers()) markers.put(marker.id(), marker);
        for (var marker : current.markers()) markers.put(marker.id(), marker);
        if (markers.size() > 16) throw new IOException("Too many markers to merge legacy map safely");
        return new MapSnapshot(current.x(), current.z(), Math.max(old.revision(), current.revision()),
                List.copyOf(columns.values()), List.copyOf(markers.values()));
    }
    @Override public synchronized void close() throws IOException {
        IOException failure = null;
        for (var region : regions.values()) try { region.close(); }
        catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        regions.clear(); missingRegions.clear(); overviewSources.clear(); overviews.clear(); emptyOverviews.clear();
        if (legacyFiles != null) try { legacyFiles.close(); }
        catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        legacyFiles = null; legacyIterator = null;
        if (failure != null) throw failure;
    }
}
