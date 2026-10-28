package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public record SettlementPath(List<Node> nodes, int width, Style style, Material material, long seed, boolean branch) {
    public static final int MAX_NODES = 64;
    public static final int MAX_COLUMNS = 4096;
    public enum Style { CLEAN, NATURAL }
    public enum Material { DIRT }
    public record Node(Vec3 position, Vec3 incoming, Vec3 outgoing) {
        public Node {
            if (!valid(position, 30_000_000) || !valid(incoming, 256) || !valid(outgoing, 256)
                    || incoming.y != 0 || outgoing.y != 0) throw new IllegalArgumentException("Invalid path node");
        }
        public Node(Vec3 position) { this(position, Vec3.ZERO, Vec3.ZERO); }
        public Node at(Vec3 point) { return new Node(point, this.incoming, this.outgoing); }
        private static boolean valid(Vec3 value, double limit) {
            return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z)
                    && Math.abs(value.x) <= limit && Math.abs(value.y) <= limit && Math.abs(value.z) <= limit;
        }
    }
    public SettlementPath {
        nodes = List.copyOf(nodes);
        if (nodes.size() > MAX_NODES || width < 1 || width > 7 || style == null || material == null)
            throw new IllegalArgumentException("Invalid path settings");
    }
    public BlockPos start() { return BlockPos.containing(this.nodes.getFirst().position()); }
}
