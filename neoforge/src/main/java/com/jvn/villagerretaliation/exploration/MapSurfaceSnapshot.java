package com.jvn.villagerretaliation.exploration;

import java.util.*;

public record MapSurfaceSnapshot(int x, int z, long revision, List<Column> columns, List<MapSnapshot.Marker> markers, int stride) {
    public MapSurfaceSnapshot {
        // compact lists own immutable arrays, materializing them would defeat the surface cache
        if (!(columns instanceof MapSurfaceColumns)) columns = List.copyOf(columns);
        markers = List.copyOf(markers);
    }
    public MapSurfaceSnapshot(int x, int z, long revision, List<Column> columns, List<MapSnapshot.Marker> markers) {
        this(x, z, revision, columns, markers, 1);
    }
    public record Layer(MapSnapshot.Block block, int bottom) { }
    public record Column(int index, List<Layer> layers, long coverage) {
        public Column { if (!(layers instanceof MapSurfaceColumns.Layers)) layers = List.copyOf(layers); }
        public Column(int index, List<Layer> layers) { this(index, layers, -1L); }
    }
    // reuse one reader instead of expanding the compact surface data into temporary objects
    public static final class Reader {
        private List<Column> columns = List.of();
        private MapSurfaceColumns compact;
        private Column column;
        private Layer layer;
        private int ci, li;
        public void reset(List<Column> columns) {
            this.columns = columns; compact = columns instanceof MapSurfaceColumns data ? data : null;
            column = null; layer = null;
        }
        public void column(int index) { ci = index; column = compact == null ? columns.get(index) : null; }
        public int index() { return compact == null ? column.index() : compact.indices[ci]; }
        public long coverage() { return compact == null ? column.coverage() : compact.coverage[ci]; }
        public int layers() { return compact == null ? column.layers().size() : compact.starts[ci + 1] - compact.starts[ci]; }
        public void layer(int index) {
            if (compact == null) layer = column.layers().get(index);
            else li = compact.starts[ci] + index;
        }
        public int y() { return compact == null ? layer.block().y() : compact.data[li * 6]; }
        public int bottom() { return compact == null ? layer.bottom() : compact.data[li * 6 + 1]; }
        public String state() { return compact == null ? layer.block().state() : compact.states[li]; }
        public int tint() { return compact == null ? layer.block().tint() : compact.data[li * 6 + 2]; }
        public int waterTint() { return compact == null ? layer.block().waterTint() : compact.data[li * 6 + 3]; }
        public int faces() { return compact == null ? layer.block().faces() : compact.data[li * 6 + 4]; }
        public int light() { return compact == null ? layer.block().light() : compact.data[li * 6 + 5]; }
        public MapSnapshot.Block block() {
            return compact == null ? layer.block() : new MapSnapshot.Block(y(), state(), tint(), waterTint(), faces(), light());
        }
    }
    public boolean sameContent(MapSurfaceSnapshot other) {
        if (this == other) return true;
        if (other == null || x != other.x || z != other.z || stride != other.stride
                || columns.size() != other.columns.size() || !markers.equals(other.markers)) return false;
        if (columns instanceof MapSurfaceColumns left && other.columns instanceof MapSurfaceColumns right) return left.sameContent(right);
        for (int i = 0; i < columns.size(); i++) {
            var left = columns.get(i); var right = other.columns.get(i);
            if (left == right) continue;
            if (left.index != right.index || left.coverage != right.coverage || left.layers.size() != right.layers.size()) return false;
            for (int j = 0; j < left.layers.size(); j++) {
                var a = left.layers.get(j); var b = right.layers.get(j);
                if (a == b) continue;
                if (a.bottom != b.bottom) return false;
                var first = a.block; var second = b.block;
                if (first != second && (first.y() != second.y() || first.tint() != second.tint() || first.waterTint() != second.waterTint()
                        || first.light() != second.light() || first.faces() != second.faces() || !first.state().equals(second.state()))) return false;
            }
        }
        return true;
    }
    public long key() { return MapSnapshot.key(x, z); }
    public boolean permits(MapSnapshot.Marker marker) {
        return Math.floorDiv(marker.x(), 8 * stride) == x && Math.floorDiv(marker.z(), 8 * stride) == z
                && columns.stream().anyMatch(c -> c.index() == Math.floorMod(Math.floorDiv(marker.x(), stride), 8) + Math.floorMod(Math.floorDiv(marker.z(), stride), 8) * 8
                        && (c.coverage() & 1L << (Math.floorMod(marker.x(), stride) + Math.floorMod(marker.z(), stride) * stride)) != 0);
    }
    private static final ThreadLocal<MapSurfaceColumns.Builder> BUILDER = ThreadLocal.withInitial(MapSurfaceColumns.Builder::new);
    public static MapSurfaceSnapshot from(MapSnapshot source) {
        var columns = BUILDER.get(); columns.clear();
        for (var column : source.columns()) {
            columns.column(column.index(), -1L);
            for (var block : column.blocks()) {
                // old saves retain their shell format, but the flat map needs only the highest known surface
                if (column.exterior() ? (block.faces() & 2) == 0 : block.y() != column.top()) continue;
                int bottom = block.y();
                for (var below : column.blocks())
                    if (column.exterior() && below.y() < block.y() && below.faces() == 0 && below.state().equals(block.state())) bottom = Math.min(bottom, below.y());
                columns.layer(block.y(), bottom, block.state(), block.tint(), block.waterTint(), 2, block.light());
                break;
            }
        }
        return new MapSurfaceSnapshot(source.x(), source.z(), source.revision(), columns.build(), source.markers());
    }
}
