package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public final class SettlementPathTerrain {
    public static final int ROAD_BLOCKS_PER_MATERIAL = 4;
    private SettlementPathTerrain() {}
    public enum Kind { CONVERT, EXISTING, SKIPPED, OUTSIDE, UNAVAILABLE }
    public record Cell(BlockPos position, Kind kind, BlockState soil, BlockState above, BlockState top, BlockState target, BlockState support) {}
    public record Preview(List<Cell> cells, String error, SettlementPath.Material material) {
        public Preview { cells = List.copyOf(cells); }
        public long count(Kind kind) { return this.cells.stream().filter(cell -> cell.kind() == kind).count(); }
        public String materials() {
            if (this.material == SettlementPath.Material.DIRT) return "Shovels required, no construction materials";
            return BuilderStructureScanner.materialSummary(quote(), 5) + ". Pickaxe or shovel required for ground preparation";
        }
        public List<BuilderStructureScanner.MaterialRequirement> quote() {
            if (this.material == SettlementPath.Material.DIRT) return List.of();
            return BuilderStructureScanner.materialRequirements(this.cells.stream().filter(cell -> cell.kind() == Kind.CONVERT).map(Cell::target).toList())
                    .stream().map(row -> new BuilderStructureScanner.MaterialRequirement(row.item(),
                            Math.ceilDiv(row.count(), ROAD_BLOCKS_PER_MATERIAL))).toList();
        }
    }

    public static boolean soil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    public static boolean road(BlockState state) {
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.ANDESITE);
    }

    public static boolean ground(BlockState state, SettlementPath.Material material) {
        return soil(state) || state.is(Blocks.DIRT_PATH) || material != SettlementPath.Material.DIRT && road(state);
    }

    public static boolean grass(BlockState state) {
        return state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN);
    }

    public static BlockPos surface(Level level, int x, int z) {
        var pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        for (int i = 0; i < 2 && grass(level.getBlockState(pos)); i++) pos = pos.below();
        return pos;
    }

    public static Preview preview(Level level, SettlementPath path, Predicate<BlockPos> allowed) {
        return preview(level, path, allowed, SettlementPathGeometry.rasterize(path));
    }

    public static Preview preview(Level level, SettlementPath path, Predicate<BlockPos> allowed, SettlementPathGeometry.Footprint footprint) {
        if (!footprint.error().isEmpty()) return new Preview(List.of(), footprint.error(), path.material());
        var cells = new ArrayList<Cell>();
        for (var column : footprint.columns()) {
            if (!allowed.test(column) || !level.getWorldBorder().isWithinBounds(column)) {
                cells.add(unavailable(column, Kind.OUTSIDE));
                continue;
            }
            if (!level.hasChunkAt(column)) {
                cells.add(unavailable(column, Kind.UNAVAILABLE));
                continue;
            }
            var pos = surface(level, column.getX(), column.getZ());
            if (level.isOutsideBuildHeight(pos) || !allowed.test(pos)) {
                cells.add(unavailable(column, Kind.OUTSIDE));
                continue;
            }
            var soil = level.getBlockState(pos);
            var above = level.getBlockState(pos.above());
            var top = level.getBlockState(pos.above(2));
            boolean clear = above.isAir() || grass(above) && (top.isAir() || grass(top));
            var target = path.material().state(path.seed(), pos);
            var support = level.isOutsideBuildHeight(pos.below()) ? Blocks.AIR.defaultBlockState() : level.getBlockState(pos.below());
            boolean supported = !target.is(Blocks.GRAVEL) || support.isFaceSturdy(level, pos.below(), Direction.UP);
            var kind = soil.equals(target) ? Kind.EXISTING
                    : ground(soil, path.material()) && level.getBlockEntity(pos) == null && clear && supported
                            && target.canSurvive(level, pos) ? Kind.CONVERT : Kind.SKIPPED;
            cells.add(new Cell(pos, kind, soil, above, top, target, support));
        }
        if (cells.stream().anyMatch(cell -> cell.kind() == Kind.OUTSIDE))
            return new Preview(cells, "Red blocks are outside the available settlement area", path.material());
        if (cells.stream().anyMatch(cell -> cell.kind() == Kind.UNAVAILABLE))
            return new Preview(cells, "Load the path terrain before confirming", path.material());
        var start = path.start();
        var first = cells.stream().filter(cell -> cell.position().getX() == start.getX() && cell.position().getZ() == start.getZ())
                .findFirst().orElseThrow();
        if (path.branch() && !road(first.soil())) return new Preview(cells, "Start the branch on an existing path or road", path.material());
        if (!path.branch() && first.kind() == Kind.SKIPPED && !road(first.soil())) return new Preview(cells, "Start on valid soil or an existing path or road", path.material());
        return new Preview(cells, cells.stream().noneMatch(cell -> cell.kind() == Kind.CONVERT) ? "No ground to convert" : "", path.material());
    }

    private static Cell unavailable(BlockPos position, Kind kind) {
        var air = Blocks.AIR.defaultBlockState();
        return new Cell(position, kind, air, air, air, air, air);
    }

    static SettlementConstructionOrder order(Preview preview) {
        if (!preview.error().isEmpty()) throw new IllegalArgumentException("Invalid path preview");
        var cells = new ArrayList<SettlementConstructionOrder.Cell>();
        for (var cell : preview.cells()) {
            if (cell.kind() != Kind.CONVERT) continue;
            if (grass(cell.top()) && grass(cell.above()))
                cells.add(new SettlementConstructionOrder.Cell(cell.position().above(2), cell.top(), Blocks.AIR.defaultBlockState()));
            if (grass(cell.above()))
                cells.add(new SettlementConstructionOrder.Cell(cell.position().above(), cell.above(), Blocks.AIR.defaultBlockState()));
            cells.add(new SettlementConstructionOrder.Cell(cell.position(), cell.soil(), cell.target()));
        }
        var ingredients = preview.quote().stream().map(row -> new BuilderConstructionCost.Ingredient(
                BuiltInRegistries.ITEM.getKey(row.item().getItem()), false, row.count())).toList();
        var cost = ingredients.isEmpty() ? BuilderConstructionCost.LABOR : new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED, ingredients);
        return new SettlementConstructionOrder(preview.material().label(), cells, cost, List.of(), List.of(), null,
                preview.material() != SettlementPath.Material.DIRT);
    }

    public static int apply(Level level, Preview preview) {
        if (!preview.error().isEmpty()) return 0;
        int changed = 0;
        for (var cell : preview.cells()) {
            if (cell.kind() != Kind.CONVERT) continue;
            if (grass(cell.top()) && grass(cell.above())) level.setBlock(cell.position().above(2), Blocks.AIR.defaultBlockState(), 2);
            if (grass(cell.above())) level.setBlock(cell.position().above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(cell.position(), cell.target(), 3);
            changed++;
        }
        return changed;
    }
}
