package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public record SettlementPath(List<Node> nodes, int width, Style style, Material material, long seed, boolean branch, boolean closed) {
    public static final int MAX_NODES = 64;
    public static final int MAX_COLUMNS = 4096;
    public enum Style { CLEAN, NATURAL }
    public enum Material {
        DIRT("Dirt path"), GRAVEL("Gravel road"), COBBLESTONE("Cobblestone road"), MIXED("Mixed road");
        private final String label;
        Material(String label) { this.label = label; }
        public String label() { return this.label; }
        public BlockState state(long seed, BlockPos pos) {
            return switch (this) {
                case DIRT -> Blocks.DIRT_PATH.defaultBlockState();
                case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
                case COBBLESTONE -> Blocks.COBBLESTONE.defaultBlockState();
                case MIXED -> {
                    long value = seed ^ (long) pos.getX() * 341873128712L ^ (long) pos.getZ() * 132897987541L;
                    value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
                    value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
                    yield switch ((int) Math.floorMod(value ^ value >>> 31, 5)) {
                        case 0, 1 -> Blocks.GRAVEL.defaultBlockState();
                        case 2, 3 -> Blocks.COBBLESTONE.defaultBlockState();
                        default -> Blocks.ANDESITE.defaultBlockState();
                    };
                }
            };
        }
    }
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
    public SettlementPath(List<Node> nodes, int width, Style style, Material material, long seed, boolean branch) {
        this(nodes, width, style, material, seed, branch, false);
    }
    public SettlementPath {
        nodes = List.copyOf(nodes);
        if (nodes.size() > MAX_NODES || closed && nodes.size() < 3 || width < 1 || width > 7 || style == null || material == null)
            throw new IllegalArgumentException("Invalid path settings");
    }
    public BlockPos start() { return BlockPos.containing(this.nodes.getFirst().position()); }
}
