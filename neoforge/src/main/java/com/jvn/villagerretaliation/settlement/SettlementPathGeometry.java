package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class SettlementPathGeometry {
    private SettlementPathGeometry() {}
    public record Footprint(List<BlockPos> columns, String error) {}
    private record Cell(BlockPos position, double distance, boolean core) {}

    public static Vec3 point(SettlementPath.Node a, SettlementPath.Node b, double t) {
        double s = 1 - t;
        return a.position().scale(s * s * s).add(a.position().add(a.outgoing()).scale(3 * s * s * t))
                .add(b.position().add(b.incoming()).scale(3 * s * t * t)).add(b.position().scale(t * t * t));
    }

    public static List<Vec3> samples(SettlementPath path) {
        var points = new ArrayList<Vec3>();
        for (int i = 1; i < path.nodes().size() + (path.closed() ? 1 : 0); i++) {
            var a = path.nodes().get(i - 1);
            var b = path.nodes().get(i % path.nodes().size());
            double length = a.outgoing().length() + a.position().add(a.outgoing()).distanceTo(b.position().add(b.incoming()))
                    + b.incoming().length();
            if (length > 4096 || points.size() + Math.ceil(length * 4) > 32768)
                throw new IllegalArgumentException("Path is too long. Confirm a shorter section");
            int steps = Math.max(1, (int) Math.ceil(length * 4));
            for (int step = 0; step <= steps; step++) points.add(point(a, b, step / (double) steps));
        }
        return points;
    }

    public static Footprint rasterize(SettlementPath path) {
        if (path.nodes().size() < 2) return new Footprint(List.of(), "Place at least two nodes");
        List<Vec3> samples;
        try { samples = samples(path); }
        catch (IllegalArgumentException error) { return new Footprint(List.of(), error.getMessage()); }
        Map<Long, Cell> cells = new LinkedHashMap<>();
        double radius = path.width() / 2.0;
        double shift = path.width() % 2 == 0 ? 0.5 : 0;
        BlockPos previous = path.start();
        add(cells, previous, 0, true);
        for (var sample : samples) {
            double x = sample.x + shift, z = sample.z + shift;
            var core = BlockPos.containing(x, sample.y, z);
            add(cells, core, 0, true);
            if (previous != null && previous.getX() != core.getX() && previous.getZ() != core.getZ()) {
                // choose the same bridge cell when traversing the curve in either direction
                var bridge = previous.getX() < core.getX() ? new BlockPos(previous.getX(), core.getY(), core.getZ())
                        : new BlockPos(core.getX(), core.getY(), previous.getZ());
                add(cells, bridge, 0, true);
            }
            previous = core;
            if (path.width() > 1) {
                for (int dx = -path.width(); dx <= path.width(); dx++) {
                    for (int dz = -path.width(); dz <= path.width(); dz++) {
                        var candidate = core.offset(dx, 0, dz);
                        double distance = Math.hypot(candidate.getX() + 0.5 - x, candidate.getZ() + 0.5 - z);
                        if (distance < radius) add(cells, candidate, distance, false);
                    }
                }
            }
            if (cells.size() > SettlementPath.MAX_COLUMNS)
                return new Footprint(List.of(), "Path exceeds 4,096 columns. Confirm a shorter section");
        }
        for (var node : path.nodes()) {
            var anchor = BlockPos.containing(node.position());
            var shifted = BlockPos.containing(node.position().add(shift, 0, shift));
            add(cells, anchor, 0, true);
            add(cells, new BlockPos(anchor.getX(), anchor.getY(), shifted.getZ()), 0, true);
        }
        if (cells.size() > SettlementPath.MAX_COLUMNS) return new Footprint(List.of(), "Path exceeds 4,096 columns");
        var columns = new ArrayList<BlockPos>();
        for (var cell : cells.values()) {
            double edge = radius - 0.75 * noise(path.seed(), cell.position());
            if (path.style() == SettlementPath.Style.CLEAN || cell.core() || cell.distance() < edge) columns.add(cell.position());
        }
        return new Footprint(List.copyOf(columns), "");
    }

    private static void add(Map<Long, Cell> cells, BlockPos pos, double distance, boolean core) {
        long key = BlockPos.asLong(pos.getX(), 0, pos.getZ());
        var old = cells.get(key);
        if (old == null) cells.put(key, new Cell(pos, distance, core));
        else if (distance < old.distance() || core && !old.core())
            cells.put(key, new Cell(pos, Math.min(distance, old.distance()), core || old.core()));
    }

    private static double noise(long seed, BlockPos pos) {
        long value = seed ^ (long) Math.floorDiv(pos.getX(), 2) * 341873128712L
                ^ (long) Math.floorDiv(pos.getZ(), 2) * 132897987541L;
        value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
        value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
        return ((value ^ value >>> 31) & 0xffff) / 65535.0;
    }
}
