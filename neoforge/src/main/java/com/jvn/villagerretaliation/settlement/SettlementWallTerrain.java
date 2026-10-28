package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class SettlementWallTerrain {
    public enum Kind { PLACE, EXISTING, BLOCKED, OUTSIDE, UNAVAILABLE }
    public record Cell(BlockPos position, BlockState target, BlockState before, BlockState support, Kind kind) {}
    public record Preview(List<Cell> cells, String error) {}
    private SettlementWallTerrain() {}

    public static Preview preview(Level level, SettlementWall wall, Predicate<BlockPos> allowed) {
        return preview(level, wall, allowed, Map.of());
    }

    public static Preview preview(Level level, SettlementWall wall, Predicate<BlockPos> allowed, Map<BlockPos, BlockState> replaced) {
        for (var entry : replaced.entrySet()) {
            var pos = entry.getKey();
            if (!allowed.test(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
                return new Preview(List.of(), "Keep the entire upgraded wall inside the settlement");
            if (!level.hasChunkAt(pos)) return new Preview(List.of(), "Load the whole wall before upgrading");
            if (!level.getBlockState(pos).equals(entry.getValue()) && !level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null)
                return new Preview(List.of(), "This wall has been edited. Restore its blocks before upgrading");
        }
        var plan = SettlementWallGeometry.build(wall);
        if (!plan.error().isEmpty()) return new Preview(List.of(), plan.error());
        plan = extendFoundations(level, plan, allowed, replaced);
        if (!replaced.isEmpty()) {
            var merged = new LinkedHashMap<BlockPos, BlockState>();
            plan.cells().forEach(cell -> merged.put(cell.position(), cell.state()));
            replaced.keySet().forEach(pos -> merged.putIfAbsent(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
            if (merged.size() > SettlementWall.MAX_BLOCKS) return new Preview(List.of(), "Wall upgrade exceeds 16,384 blocks");
            var replacement = new ArrayList<SettlementWallTemplates.Cell>();
            merged.forEach((pos, state) -> replacement.add(new SettlementWallTemplates.Cell(pos, state)));
            replacement.sort(Comparator.comparingInt((SettlementWallTemplates.Cell cell) -> cell.position().getY())
                    .thenComparingInt(cell -> cell.position().getX()).thenComparingInt(cell -> cell.position().getZ()));
            plan = new SettlementWallGeometry.Plan(List.copyOf(replacement), plan.foundations(), plan.error());
        }
        var cells = new ArrayList<Cell>();
        var occupants = occupants(level, plan);
        String error = wall.nodes().size() < 2 ? "Place at least two towers" : plan.error();
        for (var planned : plan.cells()) {
            var pos = planned.position();
            BlockState before = null, support = null;
            Kind kind;
            if (!allowed.test(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                kind = Kind.OUTSIDE;
                error = "Keep the whole wall inside the settlement";
            } else if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.below())) {
                kind = Kind.UNAVAILABLE;
                if (error.isEmpty()) error = "Load the terrain beneath the wall";
            } else {
                before = level.getBlockState(pos);
                support = plan.foundations().contains(pos) ? level.getBlockState(pos.below()) : null;
                if (before.equals(planned.state())) kind = Kind.EXISTING;
                else if (level.getBlockEntity(pos) != null || !before.getFluidState().isEmpty()
                        || !before.isAir() && !before.canBeReplaced() && !before.equals(replaced.get(pos))
                        || support != null && !support.isFaceSturdy(level, pos.below(), Direction.UP)) {
                    kind = Kind.BLOCKED;
                    if (error.isEmpty()) error = "Wall is obstructed or cannot reach solid support";
                } else if (!planned.state().getCollisionShape(level, pos).isEmpty()
                        && occupants.stream().anyMatch(bounds -> bounds.intersects(new AABB(pos)))) {
                    kind = Kind.BLOCKED;
                    if (error.isEmpty()) error = "Move entities out of the wall before confirming";
                } else kind = Kind.PLACE;
            }
            cells.add(new Cell(pos, planned.state(), before, support, kind));
        }
        return new Preview(List.copyOf(cells), error);
    }

    private static SettlementWallGeometry.Plan extendFoundations(Level level, SettlementWallGeometry.Plan plan,
                                                                  Predicate<BlockPos> allowed, Map<BlockPos, BlockState> replaced) {
        var cells = new LinkedHashMap<BlockPos, BlockState>();
        plan.cells().forEach(cell -> cells.put(cell.position(), cell.state()));
        var foundations = new HashSet<BlockPos>();
        var columns = new HashSet<BlockPos>();
        String error = "";
        for (var cell : plan.cells()) {
            var bottom = cell.position();
            if (!plan.foundations().contains(bottom) || !columns.add(bottom.atY(0))) continue;
            if (allowed.test(bottom) && !level.isOutsideBuildHeight(bottom)
                    && level.getWorldBorder().isWithinBounds(bottom) && level.hasChunkAt(bottom)) {
                while (true) {
                    var below = bottom.below();
                    if (!allowed.test(below) || level.isOutsideBuildHeight(below)
                            || !level.getWorldBorder().isWithinBounds(below)) {
                        error = "Wall foundations cannot reach support inside the settlement and world bounds";
                        break;
                    }
                    if (!level.hasChunkAt(below)) {
                        error = "Load the terrain beneath the wall";
                        break;
                    }
                    var support = replaced.containsKey(below) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                            : level.getBlockState(below);
                    if (support.isFaceSturdy(level, below, Direction.UP)) break;
                    if (level.getBlockEntity(below) != null || !support.getFluidState().isEmpty()
                            || !support.isAir() && !support.canBeReplaced()) {
                        error = "Wall foundations are obstructed before reaching solid support";
                        break;
                    }
                    if (!cells.containsKey(below) && cells.size() >= SettlementWall.MAX_BLOCKS) {
                        error = "Wall foundations exceed 16,384 blocks. Confirm a shorter section";
                        break;
                    }
                    cells.put(below, cell.state());
                    bottom = below;
                }
            }
            foundations.add(bottom);
        }
        var extended = new ArrayList<SettlementWallTemplates.Cell>();
        cells.forEach((pos, state) -> extended.add(new SettlementWallTemplates.Cell(pos, state)));
        extended.sort(Comparator.comparingInt((SettlementWallTemplates.Cell cell) -> cell.position().getY())
                .thenComparingInt(cell -> cell.position().getX()).thenComparingInt(cell -> cell.position().getZ()));
        return new SettlementWallGeometry.Plan(List.copyOf(extended), java.util.Set.copyOf(foundations), error);
    }

    private static List<AABB> occupants(Level level, SettlementWallGeometry.Plan plan) {
        if (plan.cells().isEmpty()) return List.of();
        var bounds = new AABB(plan.cells().getFirst().position());
        for (var cell : plan.cells()) bounds = bounds.minmax(new AABB(cell.position()));
        return level.getEntities((net.minecraft.world.entity.Entity) null, bounds, entity -> entity.isAlive() && !entity.isSpectator())
                .stream().map(net.minecraft.world.entity.Entity::getBoundingBox).toList();
    }

    public static Preview demolition(Level level, Map<BlockPos, BlockState> blocks, Predicate<BlockPos> allowed) {
        var cells = new ArrayList<Cell>();
        for (var entry : blocks.entrySet()) {
            var pos = entry.getKey();
            if (!allowed.test(pos) || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos))
                return new Preview(List.of(), "Keep the entire wall piece inside the settlement");
            if (!level.hasChunkAt(pos)) return new Preview(List.of(), "Load the whole wall piece before destroying");
            var before = level.getBlockState(pos);
            if (before.equals(entry.getValue()) && level.getBlockEntity(pos) == null)
                cells.add(new Cell(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), before, null, Kind.PLACE));
        }
        cells.sort(Comparator.comparingLong(cell -> cell.position().asLong()));
        return new Preview(List.copyOf(cells), "");
    }

    public static int apply(Level level, Preview preview) {
        if (!preview.error().isEmpty()) return 0;
        int placed = 0;
        for (var cell : preview.cells()) if (cell.kind() == Kind.PLACE) {
            level.setBlock(cell.position(), cell.target(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (!cell.target().isAir()) placed++;
        }
        return placed;
    }
}
