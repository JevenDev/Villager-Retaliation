package com.jvn.villagerretaliation.exploration;

import java.util.List;

/** Detached, immutable surface shell. No level, entities, model data or block entities escape capture. */
public record MapSnapshot(int x, int z, long revision, List<Column> columns, List<Marker> markers) {
    public static final int SIDE = 8, DEPTH = 48, MAX_BLOCKS = SIDE * SIDE * DEPTH;
    public MapSnapshot {
        columns = List.copyOf(columns);
        markers = List.copyOf(markers);
        if (columns.size() > 64 || markers.size() > 16) throw new IllegalArgumentException("Map tile limits");
        long mask = 0;
        for (Column column : columns) {
            if ((mask & 1L << column.index()) != 0) throw new IllegalArgumentException("Duplicate column");
            mask |= 1L << column.index();
        }
    }
    public long key() { return key(x, z); }
    public static long key(int x, int z) { return (long)x << 32 | z & 0xffffffffL; }
    public static int tile(int block) { return Math.floorDiv(block, SIDE); }
    public boolean observed(int worldX, int worldZ) {
        if (tile(worldX) != x || tile(worldZ) != z) return false;
        int index = Math.floorMod(worldX, SIDE) + SIDE * Math.floorMod(worldZ, SIDE);
        return columns.stream().anyMatch(c -> c.index == index);
    }
    public record Column(int index, int top, List<Block> blocks, boolean exterior) {
        public Column(int index, int top, List<Block> blocks) { this(index, top, blocks, false); }
        public Column {
            blocks = List.copyOf(blocks);
            if (index < 0 || index >= 64 || blocks.size() > DEPTH || top < -4096 || top > 4096)
                throw new IllegalArgumentException("Invalid column");
            int lastY = Integer.MAX_VALUE;
            for (Block b : blocks) {
                if (b.y > top || b.y <= top - DEPTH || b.y >= lastY) throw new IllegalArgumentException("Invalid shell");
                lastY = b.y;
            }
        }
    }
    public record Block(int y, String state, int tint, int waterTint, int faces, int light) {
        public Block(int y, String state, int tint, int waterTint, int faces) { this(y, state, tint, waterTint, faces, 0); }
        public Block { if (state.length() > 1024 || faces < 0 || faces > 63 || light < 0 || light > 15) throw new IllegalArgumentException("Invalid block"); }
    }
    public record Marker(String id, String name, int x, int y, int z) {
        public Marker { if (id.length() > 128 || name.length() > 128) throw new IllegalArgumentException("Invalid marker"); }
    }
    public static boolean inObservation(int x, int z, double px, double pz) {
        double dx = x + .5 - px, dz = z + .5 - pz;
        return dx * dx + dz * dz <= 24 * 24;
    }
    public boolean permits(Marker marker) { return observed(marker.x, marker.z); }
}
