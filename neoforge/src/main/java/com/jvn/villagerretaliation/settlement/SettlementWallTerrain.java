package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
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
        var plan = SettlementWallGeometry.build(wall);
        if (!plan.error().isEmpty()) return new Preview(List.of(), plan.error());
        var cells = new ArrayList<Cell>();
        var occupants = occupants(level, plan);
        String error = wall.nodes().size() < 2 ? "Place at least two towers" : "";
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
                        || !before.isAir() && !before.canBeReplaced()
                        || support != null && !support.isFaceSturdy(level, pos.below(), Direction.UP)) {
                    kind = Kind.BLOCKED;
                    if (error.isEmpty()) error = "Wall is obstructed or unsupported. Choose clear, level ground";
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

    private static List<AABB> occupants(Level level, SettlementWallGeometry.Plan plan) {
        if (plan.cells().isEmpty()) return List.of();
        var bounds = new AABB(plan.cells().getFirst().position());
        for (var cell : plan.cells()) bounds = bounds.minmax(new AABB(cell.position()));
        return level.getEntities((net.minecraft.world.entity.Entity) null, bounds, entity -> entity.isAlive() && !entity.isSpectator())
                .stream().map(net.minecraft.world.entity.Entity::getBoundingBox).toList();
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
