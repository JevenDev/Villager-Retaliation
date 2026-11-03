package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

public record SettlementRouteShape(List<SettlementPath.Node> nodes, boolean smooth) {
    public static final int MAX_POINTS = 12;
    public SettlementRouteShape {
        // NBT canonicalizes signed zero, so snapshots must use the same representation
        nodes = nodes.stream().map(node -> new SettlementPath.Node(zero(node.position()), zero(node.incoming()), zero(node.outgoing()))).toList();
        if (nodes.size() < 2 || nodes.size() > MAX_POINTS + 2) throw new IllegalArgumentException("Invalid route shape");
    }
    private static Vec3 zero(Vec3 point) { return new Vec3(point.x == 0 ? 0 : point.x, point.y == 0 ? 0 : point.y, point.z == 0 ? 0 : point.z); }
    public List<BlockPos> points() { return nodes.subList(1, nodes.size() - 1).stream().map(node -> BlockPos.containing(node.position())).toList(); }
    public SettlementPath path() { return new SettlementPath(nodes, 1, SettlementPath.Style.CLEAN, SettlementPath.Material.DIRT, 0, false); }
    public boolean curved() { return nodes.stream().anyMatch(node -> node.incoming().lengthSqr() > 0 || node.outgoing().lengthSqr() > 0); }
    public SettlementRouteShape endpoints(BlockPos from, BlockPos to) {
        var updated = new ArrayList<>(nodes);
        updated.set(0, updated.getFirst().at(Vec3.atBottomCenterOf(from)));
        updated.set(updated.size() - 1, updated.getLast().at(Vec3.atBottomCenterOf(to)));
        return new SettlementRouteShape(updated, smooth);
    }
    public List<BlockPos> columns() {
        var result = new ArrayList<BlockPos>();
        for (var sample : SettlementPathGeometry.samples(path())) {
            var cell = BlockPos.containing(sample.x, 0, sample.z);
            if (!result.isEmpty()) {
                var previous = result.getLast();
                if (previous.equals(cell)) continue;
                if (previous.getX() != cell.getX() && previous.getZ() != cell.getZ()) {
                    var bridge = previous.getX() < cell.getX() ? new BlockPos(previous.getX(), 0, cell.getZ())
                            : new BlockPos(cell.getX(), 0, previous.getZ());
                    result.add(bridge);
                }
            }
            result.add(cell);
            if (result.size() > SettlementRoute.MAX_STEPS) throw new IllegalArgumentException("Route is too long");
        }
        if (result.size() < 2) throw new IllegalArgumentException("Route needs two different endpoints");
        return List.copyOf(result);
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); var list = new ListTag();
        for (var node : nodes) {
            var row = new CompoundTag(); row.put("Position", vector(node.position()));
            row.put("Incoming", vector(node.incoming())); row.put("Outgoing", vector(node.outgoing())); list.add(row);
        }
        tag.put("Nodes", list); tag.putBoolean("Smooth", smooth); return tag;
    }
    public static SettlementRouteShape load(CompoundTag tag) {
        var list = tag.getList("Nodes", Tag.TAG_COMPOUND);
        if (!tag.contains("Nodes", Tag.TAG_LIST) || !list.equals(tag.get("Nodes")) || list.size() < 2 || list.size() > MAX_POINTS + 2
                || !tag.contains("Smooth", Tag.TAG_BYTE)) throw new IllegalArgumentException("Invalid route shape");
        var nodes = new ArrayList<SettlementPath.Node>();
        for (var value : list) {
            var row = (CompoundTag) value;
            nodes.add(new SettlementPath.Node(vector(row, "Position"), vector(row, "Incoming"), vector(row, "Outgoing")));
        }
        return new SettlementRouteShape(nodes, tag.getBoolean("Smooth"));
    }
    private static ListTag vector(Vec3 point) {
        var tag = new ListTag(); tag.add(DoubleTag.valueOf(point.x)); tag.add(DoubleTag.valueOf(point.y)); tag.add(DoubleTag.valueOf(point.z)); return tag;
    }
    private static Vec3 vector(CompoundTag tag, String key) {
        var list = tag.getList(key, Tag.TAG_DOUBLE);
        if (list.size() != 3 || !list.equals(tag.get(key))) throw new IllegalArgumentException("Invalid route handle");
        return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }
}
