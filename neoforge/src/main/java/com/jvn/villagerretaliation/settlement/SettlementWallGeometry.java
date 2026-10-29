package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementWallGeometry {
    public record Plan(List<SettlementWallTemplates.Cell> cells, Set<BlockPos> foundations, String error) {}
    private SettlementWallGeometry() {}

    public static Plan build(SettlementWall wall) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        var foundations = new HashSet<BlockPos>();
        var span = SettlementWallTemplates.get(wall.type().span());
        var nodes = wall.part() == SettlementWall.Part.COMPLETE ? towers(wall) : wall.nodes();
        if (nodes.size() > 64) return invalid("Wall draft exceeds 64 towers. Increase spacing or shorten the wall");
        var towerNodes = nodes.stream().filter(node -> !wall.towerAt(node).isEmpty()).toList();
        var towerRadii = new java.util.HashMap<BlockPos, Integer>();
        towerNodes.forEach(node -> towerRadii.put(node, wall.towerRadius(node)));
        int segments = wall.part() == SettlementWall.Part.TOWER ? 0 : nodes.size() - (wall.closed() ? 0 : 1);
        for (int i = 0; i < segments; i++) {
            var start = nodes.get(i);
            var end = nodes.get((i + 1) % nodes.size());
            int dx = end.getX() - start.getX(), dz = end.getZ() - start.getZ();
            int length = Math.max(Math.abs(dx), Math.abs(dz));
            if (length < Math.max(1, wall.towerRadius(start) + wall.towerRadius(end) + 2))
                return invalid("Move the nodes farther apart so their towers do not overlap");
            if (length > SettlementWall.MAX_SPAN) return invalid("Each wall span is limited to 128 blocks");
            var rotation = rotation(start, end);
            for (int step = 0; step <= length; step++) {
                var center = new BlockPos(start.getX() + (int) Math.round(dx * (double) step / length),
                        start.getY() + (int) Math.round((end.getY() - start.getY()) * (double) step / length),
                        start.getZ() + (int) Math.round(dz * (double) step / length));
                int column = Math.floorMod(step - 3, span.size().getX());
                for (var cell : span.cells()) {
                    if (cell.position().getX() != column) continue;
                    var offset = new BlockPos(0, cell.position().getY(), cell.position().getZ() - span.size().getZ() / 2).rotate(rotation);
                    var pos = center.offset(offset);
                    if (towerNodes.stream().anyMatch(node -> Math.abs(node.getX() - pos.getX()) <= towerRadii.get(node)
                            && Math.abs(node.getZ() - pos.getZ()) <= towerRadii.get(node))) continue;
                    var state = cell.state().rotate(rotation);
                    if (!state.isAir() || !cells.containsKey(pos)) cells.put(pos, state);
                    if (cell.position().getY() == 0 && !state.isAir()) foundations.add(pos);
                }
                if (cells.size() > SettlementWall.MAX_BLOCKS) return invalid("Wall draft exceeds 16,384 blocks. Confirm a shorter section");
            }
        }
        var placedTowers = new HashSet<BlockPos>();
        for (int i = 0; i < (wall.part() == SettlementWall.Part.SPAN ? 0 : wall.part() == SettlementWall.Part.TOWER ? 1 : nodes.size()); i++) {
            var node = nodes.get(i);
            if (wall.towerAt(node).isEmpty() || !placedTowers.add(node)) continue;
            var tower = SettlementWallTemplates.get(wall.towerAt(node));
            var direction = i + 1 < nodes.size() ? nodes.get(i + 1)
                    : wall.closed() ? nodes.getFirst() : i > 0 ? node.offset(node.subtract(nodes.get(i - 1))) : node.east();
            var rotation = rotation(node, direction);
            for (var cell : tower.cells()) {
                var offset = cell.position().offset(-tower.size().getX() / 2, 0, -tower.size().getZ() / 2).rotate(rotation);
                var pos = node.offset(offset);
                var state = cell.state().rotate(rotation);
                cells.put(pos, state);
                if (offset.getY() == 0 && !state.isAir()) foundations.add(pos);
            }
            if (cells.size() > SettlementWall.MAX_BLOCKS) return invalid("Wall draft exceeds 16,384 blocks. Confirm a shorter section");
        }
        var ordered = new ArrayList<SettlementWallTemplates.Cell>();
        cells.forEach((pos, state) -> ordered.add(new SettlementWallTemplates.Cell(pos, state)));
        ordered.sort(Comparator.comparingInt((SettlementWallTemplates.Cell cell) -> cell.position().getY())
                .thenComparingInt(cell -> cell.position().getX()).thenComparingInt(cell -> cell.position().getZ()));
        return new Plan(List.copyOf(ordered), Set.copyOf(foundations), "");
    }

    public static List<BlockPos> towers(SettlementWall wall) {
        if (wall.part() == SettlementWall.Part.TOWER) return List.of(wall.nodes().getFirst());
        if (wall.part() == SettlementWall.Part.SPAN) return wall.nodes();
        var nodes = new ArrayList<BlockPos>();
        for (int i = 0; i < wall.nodes().size(); i++) {
            var start = wall.nodes().get(i);
            nodes.add(start);
            if (i + 1 == wall.nodes().size() && !wall.closed()) continue;
            var end = wall.nodes().get((i + 1) % wall.nodes().size());
            int length = Math.max(Math.abs(end.getX() - start.getX()), Math.abs(end.getZ() - start.getZ()));
            if (length > SettlementWall.MAX_SPAN) return wall.nodes();
            int intervals = wall.towerSpacing() == 0 ? 1 : Math.max(1, Math.min(length / 6, Math.round(length / (float) wall.towerSpacing())));
            for (int step = 1; step < intervals; step++) {
                double t = step / (double) intervals;
                nodes.add(new BlockPos(start.getX() + (int) Math.round((end.getX() - start.getX()) * t),
                        start.getY() + (int) Math.round((end.getY() - start.getY()) * t),
                        start.getZ() + (int) Math.round((end.getZ() - start.getZ()) * t)));
                if (nodes.size() > 64) return nodes;
            }
        }
        return List.copyOf(nodes);
    }

    public static List<SettlementWall> pieces(SettlementWall wall) {
        var nodes = towers(wall);
        var pieces = new ArrayList<SettlementWall>();
        var seen = new HashSet<BlockPos>();
        for (int i = 0; i < nodes.size(); i++) {
            var node = nodes.get(i);
            if (wall.towerAt(node).isEmpty() || !seen.add(node)) continue;
            var direction = i + 1 < nodes.size() ? nodes.get(i + 1) : wall.closed() ? nodes.getFirst()
                    : i > 0 ? node.offset(node.subtract(nodes.get(i - 1))) : node.east(6);
            pieces.add(new SettlementWall(wall.type(), List.of(node, direction), false, 0,
                    List.of(choice(wall, node), SettlementWall.Tower.NONE), SettlementWall.Part.TOWER));
        }
        for (int i = 0; i < nodes.size() - (wall.closed() ? 0 : 1); i++) {
            var start = nodes.get(i); var end = nodes.get((i + 1) % nodes.size());
            pieces.add(new SettlementWall(wall.type(), List.of(start, end), false, 0,
                    List.of(choice(wall, start).resolved(wall.type()), choice(wall, end).resolved(wall.type())), SettlementWall.Part.SPAN));
        }
        return List.copyOf(pieces);
    }

    private static SettlementWall.Tower choice(SettlementWall wall, BlockPos node) {
        int index = wall.nodes().indexOf(node);
        return index < 0 ? SettlementWall.Tower.DEFAULT : wall.nodeTowers().get(index);
    }

    private static Rotation rotation(BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX(), dz = end.getZ() - start.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
        return dz >= 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
    }
    private static Plan invalid(String message) { return new Plan(List.of(), Set.of(), message); }
}
