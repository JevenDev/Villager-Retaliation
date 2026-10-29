package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementFarmTerrain {
    public enum Kind { TILL, WATER, EXISTING, SKIPPED, OUTSIDE, UNAVAILABLE }
    public record Cell(BlockPos position, Kind kind, BlockState soil, BlockState above, BlockState top) {
        Cell withKind(Kind value) { return new Cell(position, value, soil, above, top); }
    }
    public record Preview(List<Cell> cells, String error) {
        public Preview { cells = List.copyOf(cells); }
        public long count(Kind kind) { return cells.stream().filter(cell -> cell.kind() == kind).count(); }
        public String materials() { return "Hoe and " + count(Kind.WATER) + " water buckets in farmer supplies or assigned storage. Empty buckets are returned."; }
    }
    private SettlementFarmTerrain() {}

    public static boolean soil(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT);
    }

    public static boolean clear(Level level, BlockPos pos) {
        var above = level.getBlockState(pos.above());
        return above.isAir() || SettlementPathTerrain.grass(above)
                && (level.getBlockState(pos.above(2)).isAir() || SettlementPathTerrain.grass(level.getBlockState(pos.above(2))));
    }

    public static boolean contained(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos.below()) || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        for (var direction : Direction.Plane.HORIZONTAL) {
            var neighbor = pos.relative(direction);
            if (!level.hasChunkAt(neighbor)) return false;
            var state = level.getBlockState(neighbor);
            if (!state.is(Blocks.FARMLAND) && !state.isFaceSturdy(level, neighbor, direction.getOpposite())) return false;
        }
        return true;
    }

    public static boolean hydrated(Level level, BlockPos soil) {
        for (var pos : BlockPos.betweenClosed(soil.offset(-4, 0, -4), soil.offset(4, 1, 4)))
            if (level.hasChunkAt(pos) && level.getFluidState(pos).is(FluidTags.WATER)) return true;
        return false;
    }

    public static Preview preview(Level level, SettlementFarm farm, Predicate<BlockPos> allowed) {
        var cells = new ArrayList<Cell>();
        var dry = new ArrayList<BlockPos>();
        var candidates = new ArrayList<BlockPos>();
        for (int x = farm.minX(); x <= farm.maxX(); x++) {
            for (int z = farm.minZ(); z <= farm.maxZ(); z++) {
                var pos = new BlockPos(x, farm.first().getY(), z);
                Kind kind;
                if (!allowed.test(pos) || !level.getWorldBorder().isWithinBounds(pos)) kind = Kind.OUTSIDE;
                else if (!level.hasChunkAt(pos)) kind = Kind.UNAVAILABLE;
                else {
                    pos = SettlementPathTerrain.surface(level, x, z);
                    if (level.isOutsideBuildHeight(pos) || !allowed.test(pos)) kind = Kind.OUTSIDE;
                    else if (level.getBlockState(pos).is(Blocks.FARMLAND)) kind = Kind.EXISTING;
                    else kind = soil(level.getBlockState(pos)) && clear(level, pos) ? Kind.TILL : Kind.SKIPPED;
                }
                if (kind == Kind.OUTSIDE || kind == Kind.UNAVAILABLE) {
                    var air = Blocks.AIR.defaultBlockState();
                    cells.add(new Cell(pos, kind, air, air, air));
                    continue;
                }
                cells.add(new Cell(pos, kind, level.getBlockState(pos), level.getBlockState(pos.above()), level.getBlockState(pos.above(2))));
                if (kind == Kind.TILL || kind == Kind.EXISTING) {
                    if (!hydrated(level, pos)) dry.add(pos);
                    if (kind == Kind.TILL && contained(level, pos)) candidates.add(pos);
                }
            }
        }
        if (cells.stream().anyMatch(cell -> cell.kind() == Kind.OUTSIDE)) return new Preview(cells, "Farmland must stay inside the available settlement area");
        if (cells.stream().anyMatch(cell -> cell.kind() == Kind.UNAVAILABLE)) return new Preview(cells, "Load the farmland terrain before confirming");
        if (level.dimensionType().ultraWarm()) return new Preview(cells, "Water cannot irrigate farmland in this dimension");
        var water = SettlementFarmIrrigation.plan(dry, candidates);
        if (water == null) return new Preview(cells, "Some soil cannot be irrigated safely. Expand the area or provide contained water nearby");
        var sources = new HashSet<>(water);
        var planned = cells.stream().map(cell -> sources.contains(cell.position()) ? cell.withKind(Kind.WATER) : cell).toList();
        if (planned.stream().noneMatch(cell -> cell.kind() == Kind.TILL || cell.kind() == Kind.EXISTING))
            return new Preview(planned, "No tillable farmland remains after irrigation");
        if (planned.stream().noneMatch(cell -> cell.kind() == Kind.TILL || cell.kind() == Kind.WATER))
            return new Preview(planned, "No farmland work is needed");
        var work = planned.stream().filter(cell -> cell.kind() == Kind.TILL || cell.kind() == Kind.WATER).toList();
        int low = work.stream().mapToInt(cell -> cell.position().getY()).min().orElse(0);
        int high = work.stream().mapToInt(cell -> cell.position().getY()).max().orElse(0);
        return new Preview(planned, high - low > 60 ? "Farmland spans too many height levels" : "");
    }
}
