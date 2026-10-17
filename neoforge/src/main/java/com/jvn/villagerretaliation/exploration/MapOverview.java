package com.jvn.villagerretaliation.exploration;

import java.util.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

public final class MapOverview {
    public static final int STRIDE = 8;
    private MapOverview() { }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final class Scratch {
        final Sample[] samples = new Sample[64];
        final MapSurfaceColumns.Builder columns = new MapSurfaceColumns.Builder();
        final ArrayList<Sample> pool = new ArrayList<>();
        int used;
        Sample sample(String state, int tint, int water) {
            if (used == pool.size()) pool.add(new Sample());
            var result = pool.get(used++);
            result.state = state; result.tint = tint; result.water = water; result.count = 0; result.height = 0; result.depth = 0; result.next = null;
            return result;
        }
        final long[] coverage = new long[64];
        final int[] light = new int[64], weights = new int[64];
        void clear() {
            for (int i = 0; i < used; i++) pool.get(i).state = null;
            used = 0; columns.clear(); Arrays.fill(samples, null); Arrays.fill(coverage, 0);
            Arrays.fill(light, 0); Arrays.fill(weights, 0);
        }
    }
    public static MapSurfaceSnapshot empty(int x, int z, int stride) { return new MapSurfaceSnapshot(x, z, 0, List.of(), List.of(), stride); }
    public static MapSurfaceSnapshot update(MapSurfaceSnapshot region, MapSurfaceSnapshot tile) {
        int stride = region.stride(), sourceStride = tile.stride(), cells = 8 * sourceStride / stride;
        if (sourceStride > stride || stride % sourceStride != 0) throw new IllegalArgumentException("Overview stride");
        if (tile.columns().isEmpty() && tile.markers().isEmpty() && region.columns().isEmpty() && region.markers().isEmpty()) return region;
        int offsetX = Math.floorMod(tile.x() * sourceStride, stride) * 8 / stride;
        int offsetZ = Math.floorMod(tile.z() * sourceStride, stride) * 8 / stride;
        var scratch = SCRATCH.get(); scratch.clear();
        var columns = scratch.columns;
        for (int i = 0; i < region.columns().size(); i++) {
            int index = region.columns() instanceof MapSurfaceColumns compact ? compact.indices[i] : region.columns().get(i).index();
            if (!(index % 8 >= offsetX && index % 8 < offsetX + cells && index / 8 >= offsetZ && index / 8 < offsetZ + cells)) columns.copy(region.columns(), i);
        }
        sampleTile(scratch, tile, stride);
        appendColumns(scratch, stride);
        var markers = new ArrayList<>(region.markers());
        markers.removeIf(m -> Math.floorDiv(m.x(), 8 * sourceStride) == tile.x() && Math.floorDiv(m.z(), 8 * sourceStride) == tile.z());
        for (int i = 0; i < tile.markers().size(); i++) {
            var marker = tile.markers().get(i); if (tile.permits(marker)) markers.add(marker);
        }
        var result = new MapSurfaceSnapshot(region.x(), region.z(), region.revision() + 1, columns.sorted(), markers, stride);
        return result.sameContent(region) ? region : result;
    }
    // aggregate saved tiles once, avoiding a copied overview after every decoded source
    static MapSurfaceSnapshot combine(int x, int z, int stride, List<MapSurfaceSnapshot> sources) {
        var scratch = SCRATCH.get(); scratch.clear();
        var markers = new ArrayList<MapSnapshot.Marker>();
        for (var source : sources) {
            if (source.stride() != 1 || Math.floorDiv(source.x(), stride) != x || Math.floorDiv(source.z(), stride) != z)
                throw new IllegalArgumentException("Overview source bounds");
            sampleTile(scratch, source, stride);
            for (var marker : source.markers()) if (source.permits(marker)) markers.add(marker);
        }
        appendColumns(scratch, stride);
        return new MapSurfaceSnapshot(x, z, 1, scratch.columns.sorted(), markers, stride);
    }
    private static void sampleTile(Scratch scratch, MapSurfaceSnapshot tile, int stride) {
        int sourceStride = tile.stride();
        int offsetX = Math.floorMod(tile.x() * sourceStride, stride) * 8 / stride;
        int offsetZ = Math.floorMod(tile.z() * sourceStride, stride) * 8 / stride;
        for (int ci = 0; ci < tile.columns().size(); ci++) {
            if (tile.columns() instanceof MapSurfaceColumns compact) {
                for (int layer = compact.starts[ci]; layer < compact.starts[ci + 1]; layer++) {
                    int o = layer * 6; var d = compact.data;
                    if ((d[o + 4] & 2) == 0) continue;
                    sample(scratch, compact.indices[ci], compact.coverage[ci], sourceStride, stride, offsetX, offsetZ,
                            compact.states[layer], d[o], d[o] - d[o + 1] + 1, d[o + 2], d[o + 3], d[o + 5]); break;
                }
            } else {
                var column = tile.columns().get(ci);
                for (int li = 0; li < column.layers().size(); li++) {
                    var block = column.layers().get(li).block();
                    if ((block.faces() & 2) == 0) continue;
                    sample(scratch, column.index(), column.coverage(), sourceStride, stride, offsetX, offsetZ,
                            block.state(), block.y(), block.y() - column.layers().get(li).bottom() + 1, block.tint(), block.waterTint(), block.light()); break;
                }
            }
        }
    }
    private static void appendColumns(Scratch scratch, int stride) {
        var samples = scratch.samples; var columns = scratch.columns; var coverage = scratch.coverage;
        var light = scratch.light; var weights = scratch.weights;
        for (int index = 0; index < samples.length; index++) {
            var dominant = samples[index];
            if (dominant == null) continue;
            for (var sample = dominant.next; sample != null; sample = sample.next) if (sample.count > dominant.count) dominant = sample;
            if (dominant.count == 0) continue;
            int y = (int)Math.round((double)dominant.height / dominant.count);
            long full = stride == 8 ? -1L : (1L << (stride * stride)) - 1;
            columns.column(index, coverage[index] == full ? -1L : coverage[index]);
            columns.layer(y, y - (int)(dominant.depth / dominant.count) + 1, dominant.state, dominant.tint, dominant.water, 2, Math.round((float)light[index] / weights[index]));
        }
    }
    private static void sample(Scratch scratch, int column, long mask, int sourceStride, int stride, int offsetX, int offsetZ,
            String state, int y, int depth, int tint, int water, int light) {
        int cx = column % 8 * sourceStride, cz = column / 8 * sourceStride;
        int index = offsetX + cx / stride + (offsetZ + cz / stride) * 8;
        long covered = 0;
        for (int dz = 0; dz < sourceStride; dz++) for (int dx = 0; dx < sourceStride; dx++)
            if ((mask & 1L << (dx + dz * sourceStride)) != 0) covered |= 1L << (cx % stride + dx + (cz % stride + dz) * stride);
        scratch.coverage[index] |= covered;
        var sample = scratch.samples[index];
        if (sample == null) scratch.samples[index] = sample = scratch.sample(state, tint, water);
        while (!sample.state.equals(state)) {
            if (sample.next == null) sample.next = scratch.sample(state, tint, water);
            sample = sample.next;
        }
        int weight = Long.bitCount(covered);
        scratch.light[index] += light * weight; scratch.weights[index] += weight;
        sample.count += weight; sample.height += (long)y * weight; sample.depth += (long)depth * weight;
    }
    private static final class Sample {
        String state;
        int tint, water;
        int count;
        long height, depth;
        Sample next;
    }
    public static List<MapSurfaceSnapshot> coarsen(List<MapSurfaceSnapshot> sources, int stride) {
        var regions = new Long2ObjectLinkedOpenHashMap<MapSurfaceSnapshot>();
        for (var source : sources) {
            int x = Math.floorDiv(source.x() * source.stride(), stride), z = Math.floorDiv(source.z() * source.stride(), stride);
            long key = MapSnapshot.key(x, z);
            regions.put(key, update(regions.getOrDefault(key, empty(x, z, stride)), source));
        }
        return List.copyOf(regions.values());
    }
}
