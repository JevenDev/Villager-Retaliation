package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementWallUpgrade {
    private final Map<SettlementWallsSavedData.Entry, SettlementWall> pieces = new LinkedHashMap<>();

    SettlementWallUpgrade(SettlementWallsSavedData data, SettlementWallsSavedData.Entry selected, SettlementWall replacement) {
        this.pieces.put(selected, replacement);
        var towers = new LinkedHashMap<BlockPos, SettlementWall.Tower>();
        if (selected.wall().part() == SettlementWall.Part.SPAN) {
            for (var entry : data.sameSettlement(selected)) {
                var wall = entry.wall();
                if (wall.part() != SettlementWall.Part.TOWER || !selected.wall().nodes().contains(wall.nodes().getFirst())) continue;
                var choice = SettlementWall.Tower.DEFAULT.resolved(replacement.type());
                if (wall.nodeTowers().getFirst().resolved(wall.type()) == choice) continue;
                towers.put(wall.nodes().getFirst(), choice);
                this.pieces.put(entry, new SettlementWall(replacement.type(), wall.nodes(), false, 0,
                        List.of(SettlementWall.Tower.DEFAULT, SettlementWall.Tower.NONE), wall.part()));
            }
        } else if (selected.wall().part() == SettlementWall.Part.TOWER) {
            var node = replacement.nodes().getFirst();
            if (!selected.wall().towerAt(node).equals(replacement.towerAt(node)))
                towers.put(node, replacement.nodeTowers().getFirst().resolved(replacement.type()));
        }
        if (towers.isEmpty()) return;
        for (var entry : data.sameSettlement(selected)) {
            var wall = entry.wall();
            if (wall.part() != SettlementWall.Part.SPAN || wall.nodes().stream().noneMatch(towers::containsKey)) continue;
            var type = entry.id().equals(selected.id()) ? replacement.type() : wall.type();
            var choices = java.util.stream.IntStream.range(0, wall.nodes().size())
                    .mapToObj(i -> towers.getOrDefault(wall.nodes().get(i), wall.nodeTowers().get(i).resolved(wall.type()))).toList();
            var changed = new SettlementWall(type, wall.nodes(), false, 0, choices, wall.part());
            if (entry.id().equals(selected.id()) || wall.nodes().stream().anyMatch(node -> wall.towerRadius(node) != changed.towerRadius(node)))
                this.pieces.put(entry, changed);
        }
    }

    boolean current(SettlementWallsSavedData data) {
        return this.pieces.keySet().stream().allMatch(entry -> entry.equals(data.get(entry.id())));
    }

    SettlementWallTerrain.Preview preview(ServerLevel level, java.util.function.Predicate<BlockPos> allowed) {
        var data = SettlementWallsSavedData.get(level);
        if (!current(data)) return new SettlementWallTerrain.Preview(List.of(), "Another upgrade changed this wall. Select it again");
        var replaced = new LinkedHashMap<BlockPos, BlockState>();
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        var foundations = new java.util.HashSet<BlockPos>();
        for (var entry : this.pieces.entrySet()) {
            var issue = SettlementWallsSavedData.modification(level, entry.getKey(), allowed);
            if (!issue.isEmpty()) return new SettlementWallTerrain.Preview(List.of(), issue);
            replaced.putAll(entry.getKey().blocks());
            var plan = SettlementWallGeometry.build(entry.getValue());
            if (!plan.error().isEmpty()) return new SettlementWallTerrain.Preview(List.of(), plan.error());
            plan.cells().forEach(cell -> cells.merge(cell.position(), cell.state(), (first, second) -> second.isAir() ? first : second));
            foundations.addAll(plan.foundations());
        }
        if (cells.size() > SettlementWall.MAX_BLOCKS)
            return new SettlementWallTerrain.Preview(List.of(), "Wall upgrade exceeds 16,384 blocks");
        var plan = new SettlementWallGeometry.Plan(cells.entrySet().stream()
                .map(entry -> new SettlementWallTemplates.Cell(entry.getKey(), entry.getValue())).toList(), foundations, "");
        var preview = SettlementWallTerrain.preview(level, this.pieces.values().iterator().next(), allowed, replaced, plan);
        if (preview.cells().stream().anyMatch(cell -> cell.kind() == SettlementWallTerrain.Kind.PLACE
                && !replaced.containsKey(cell.position()) && data.ownedByOther(level.dimension().location(), cell.position(), null)))
            return new SettlementWallTerrain.Preview(preview.cells(), "The new design overlaps another wall piece");
        return preview;
    }

    SettlementWallMaterials.Cost cost(SettlementWallTerrain.Preview preview) {
        return SettlementWallMaterials.upgradeCost(this.pieces, preview);
    }

    List<SettlementWallsSavedData.Entry> originals() { return List.copyOf(this.pieces.keySet()); }

    void record(ServerLevel level, SettlementWallTerrain.Preview preview) {
        SettlementWallsSavedData.get(level).replace(replacements(preview));
    }

    List<SettlementWallsSavedData.Entry> replacements(SettlementWallTerrain.Preview preview) {
        var replacements = new java.util.ArrayList<SettlementWallsSavedData.Entry>();
        for (var entry : this.pieces.entrySet()) {
            var old = entry.getKey();
            var blocks = new LinkedHashMap<BlockPos, BlockState>();
            pieceCells(entry.getValue(), preview).stream().filter(cell -> !cell.target().isAir()
                    && (cell.kind() == SettlementWallTerrain.Kind.PLACE || cell.kind() == SettlementWallTerrain.Kind.EXISTING))
                    .forEach(cell -> blocks.put(cell.position(), cell.target()));
            replacements.add(new SettlementWallsSavedData.Entry(old.id(), old.group(), old.town(), old.dimension(), entry.getValue(), blocks));
        }
        return List.copyOf(replacements);
    }

    static List<SettlementWallTerrain.Cell> pieceCells(SettlementWall wall, SettlementWallTerrain.Preview preview) {
        var plan = SettlementWallGeometry.build(wall);
        var positions = plan.cells().stream().map(SettlementWallTemplates.Cell::position).collect(java.util.stream.Collectors.toSet());
        var floors = new java.util.HashMap<BlockPos, Integer>();
        plan.foundations().forEach(pos -> floors.merge(pos.atY(0), pos.getY(), Math::min));
        return preview.cells().stream().filter(cell -> positions.contains(cell.position())
                || cell.position().getY() < floors.getOrDefault(cell.position().atY(0), Integer.MIN_VALUE)).toList();
    }
}
