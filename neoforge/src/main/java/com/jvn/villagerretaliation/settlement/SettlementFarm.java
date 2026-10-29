package com.jvn.villagerretaliation.settlement;

import java.util.List;
import net.minecraft.core.BlockPos;

public record SettlementFarm(Shape shape, List<BlockPos> nodes) {
    public enum Shape {
        RECTANGLE("Rectangle"), POLYGON("Polygon"), CIRCLE("Circle");
        private final String label;
        Shape(String label) { this.label = label; }
        public String label() { return this.label; }
    }
    public static final int MAX_SPAN = 64;
    public static final int MAX_CELLS = MAX_SPAN * MAX_SPAN;
    public static final int MAX_NODES = 32;

    public SettlementFarm(BlockPos first, BlockPos second) { this(Shape.RECTANGLE, java.util.Arrays.asList(first, second)); }

    public SettlementFarm {
        if (shape == null || nodes == null || nodes.stream().anyMatch(java.util.Objects::isNull)
                || (shape == Shape.POLYGON ? nodes.size() < 3 || nodes.size() > MAX_NODES : nodes.size() != 2))
            throw new IllegalArgumentException("Invalid farmland shape");
        nodes = nodes.stream().map(BlockPos::immutable).toList();
        long width = (long) nodes.stream().mapToInt(BlockPos::getX).max().orElseThrow()
                - nodes.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 1;
        long depth = (long) nodes.stream().mapToInt(BlockPos::getZ).max().orElseThrow()
                - nodes.stream().mapToInt(BlockPos::getZ).min().orElseThrow() + 1;
        if (shape == Shape.CIRCLE) width = depth = 2 * (long) Math.floor(radius(nodes)) + 1;
        if (width > MAX_SPAN || depth > MAX_SPAN)
            throw new IllegalArgumentException("Farmland must fit within 64 x 64 blocks");
        if (shape == Shape.POLYGON) {
            long area = 0;
            var origin = nodes.getFirst();
            for (int i = 0; i < nodes.size(); i++) {
                var a = nodes.get(i);
                var b = nodes.get((i + 1) % nodes.size());
                if (a.getX() == b.getX() && a.getZ() == b.getZ())
                    throw new IllegalArgumentException("Polygon nodes must be distinct");
                area += cross(origin, a, b);
                for (int j = i + 1; j < nodes.size(); j++) {
                    if (j == i + 1 || i == 0 && j == nodes.size() - 1) continue;
                    if (intersects(a, b, nodes.get(j), nodes.get((j + 1) % nodes.size())))
                        throw new IllegalArgumentException("Polygon edges must not cross");
                }
            }
            if (area == 0) throw new IllegalArgumentException("Polygon needs at least three corners enclosing an area");
        }
    }

    public net.minecraft.nbt.CompoundTag save() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Shape", shape.name());
        tag.putLongArray("Nodes", nodes.stream().mapToLong(BlockPos::asLong).toArray());
        return tag;
    }

    public static SettlementFarm load(net.minecraft.nbt.CompoundTag tag) {
        return new SettlementFarm(Shape.valueOf(tag.getString("Shape")),
                java.util.Arrays.stream(tag.getLongArray("Nodes")).mapToObj(BlockPos::of).toList());
    }

    public BlockPos first() { return this.nodes.getFirst(); }
    public BlockPos second() { return this.nodes.get(1); }
    private static double radius(List<BlockPos> nodes) {
        return Math.hypot((double) nodes.get(1).getX() - nodes.getFirst().getX(),
                (double) nodes.get(1).getZ() - nodes.getFirst().getZ());
    }
    private int radius() { return (int) Math.floor(radius(this.nodes)); }
    public int minX() { return this.shape == Shape.CIRCLE ? first().getX() - radius() : this.nodes.stream().mapToInt(BlockPos::getX).min().orElseThrow(); }
    public int maxX() { return this.shape == Shape.CIRCLE ? first().getX() + radius() : this.nodes.stream().mapToInt(BlockPos::getX).max().orElseThrow(); }
    public int minZ() { return this.shape == Shape.CIRCLE ? first().getZ() - radius() : this.nodes.stream().mapToInt(BlockPos::getZ).min().orElseThrow(); }
    public int maxZ() { return this.shape == Shape.CIRCLE ? first().getZ() + radius() : this.nodes.stream().mapToInt(BlockPos::getZ).max().orElseThrow(); }

    public boolean contains(int x, int z) {
        if (this.shape == Shape.RECTANGLE) return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
        if (this.shape == Shape.CIRCLE) {
            double dx = (double) x - first().getX(), dz = (double) z - first().getZ();
            double r = radius(this.nodes);
            return dx * dx + dz * dz <= r * r + 1.0E-7;
        }
        var point = new BlockPos(x, first().getY(), z);
        boolean inside = false;
        for (int i = 0; i < this.nodes.size(); i++) {
            var a = this.nodes.get(i);
            var b = this.nodes.get((i + 1) % this.nodes.size());
            if (onSegment(a, b, point)) return true;
            if ((a.getZ() > z) != (b.getZ() > z)
                    && x < a.getX() + (double) (z - a.getZ()) * (b.getX() - a.getX()) / (b.getZ() - a.getZ()))
                inside = !inside;
        }
        return inside;
    }

    private static long cross(BlockPos a, BlockPos b, BlockPos c) {
        return ((long) b.getX() - a.getX()) * ((long) c.getZ() - a.getZ())
                - ((long) b.getZ() - a.getZ()) * ((long) c.getX() - a.getX());
    }
    private static boolean onSegment(BlockPos a, BlockPos b, BlockPos p) {
        return cross(a, b, p) == 0 && p.getX() >= Math.min(a.getX(), b.getX()) && p.getX() <= Math.max(a.getX(), b.getX())
                && p.getZ() >= Math.min(a.getZ(), b.getZ()) && p.getZ() <= Math.max(a.getZ(), b.getZ());
    }
    private static boolean intersects(BlockPos a, BlockPos b, BlockPos c, BlockPos d) {
        return onSegment(a, b, c) || onSegment(a, b, d) || onSegment(c, d, a) || onSegment(c, d, b)
                || Long.signum(cross(a, b, c)) * Long.signum(cross(a, b, d)) < 0
                && Long.signum(cross(c, d, a)) * Long.signum(cross(c, d, b)) < 0;
    }
}
