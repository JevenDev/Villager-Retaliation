package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementInfrastructureTerrain {
    public enum Kind { PLACE, REMOVE, EXISTING, BLOCKED }
    public record Cell(BlockPos position, BlockState before, BlockState target, Kind kind) {}
    public record Preview(List<Cell> cells, String error) {
        public Preview { cells = List.copyOf(cells); }
        public String materials() {
            var materials = BuilderStructureScanner.materialRequirements(cells.stream().filter(c -> c.kind() == Kind.PLACE).map(Cell::target).toList());
            return materials.isEmpty() ? "Builder labor. Removed blocks yield no items."
                    : BuilderStructureScanner.materialSummary(materials, 5);
        }
    }

    private SettlementInfrastructureTerrain() {}

    public static Preview preview(Level level, SettlementInfrastructure draft, Predicate<BlockPos> allowed) {
        var output = new LinkedHashMap<BlockPos, BlockState>();
        String error = switch (draft.type()) {
            case BRIDGE, STAIRS -> route(level, draft, allowed, output);
            case PEN -> pen(level, draft, allowed, output);
            case CLEAR, LEVEL, FILL -> earthwork(level, draft, allowed, output);
        };
        if (!error.isEmpty()) return new Preview(List.of(), error);
        if (output.size() > SettlementInfrastructure.MAX_CELLS) return new Preview(List.of(), "Select a smaller area, at most 4096 blocks can change");
        for (var entry : output.entrySet()) {
            if (!entry.getValue().is(Blocks.OAK_FENCE)) continue;
            var state = entry.getValue();
            for (var direction : Direction.Plane.HORIZONTAL) {
                var neighbor = output.getOrDefault(entry.getKey().relative(direction), level.getBlockState(entry.getKey().relative(direction)));
                var property = switch (direction) {
                    case NORTH -> net.minecraft.world.level.block.FenceBlock.NORTH;
                    case SOUTH -> net.minecraft.world.level.block.FenceBlock.SOUTH;
                    case EAST -> net.minecraft.world.level.block.FenceBlock.EAST;
                    default -> net.minecraft.world.level.block.FenceBlock.WEST;
                };
                state = state.setValue(property, neighbor.is(Blocks.OAK_FENCE) || neighbor.is(Blocks.OAK_FENCE_GATE)
                        || neighbor.isFaceSturdy(level, entry.getKey().relative(direction), direction.getOpposite()));
            }
            entry.setValue(state);
        }
        var cells = new ArrayList<Cell>();
        for (var entry : output.entrySet()) {
            var pos = entry.getKey();
            if (!available(level, pos, allowed)) return new Preview(cells, "Keep the entire plan on loaded land inside the settlement");
            var before = level.getBlockState(pos);
            var after = entry.getValue();
            boolean valid = level.getBlockEntity(pos) == null && (before.equals(after) || natural(before)
                    || before.is(Blocks.WATER) && draft.type() == SettlementInfrastructure.Type.BRIDGE);
            Kind kind = !valid ? Kind.BLOCKED : before.equals(after) ? Kind.EXISTING : after.isAir() ? Kind.REMOVE : Kind.PLACE;
            cells.add(new Cell(pos, before, after, kind));
            if (!valid) error = "The plan would replace a building, container or unsafe block";
        }
        if (error.isEmpty() && cells.stream().allMatch(cell -> cell.kind() == Kind.EXISTING)) error = "No construction work is needed";
        return new Preview(cells, error);
    }

    static SettlementConstructionOrder order(SettlementInfrastructure draft, Preview preview, UUID id) {
        if (!preview.error().isEmpty()) throw new IllegalArgumentException(preview.error());
        var cells = preview.cells().stream().filter(cell -> cell.kind() == Kind.PLACE || cell.kind() == Kind.REMOVE)
                .map(cell -> new SettlementConstructionOrder.Cell(cell.position(), cell.before(), cell.target())).toList();
        SettlementArea area = draft.type() == SettlementInfrastructure.Type.PEN
                ? new SettlementArea(id, SettlementAreaType.ANIMAL_PEN, "Animal pen", min(draft).above(),
                        max(draft).atY(draft.first().getY() + 4), 5, true) : null;
        return new SettlementConstructionOrder(draft.type().label(), cells, BuilderConstructionCost.EXACT, List.of(), List.of(), area);
    }

    private static String route(Level level, SettlementInfrastructure draft, Predicate<BlockPos> allowed, Map<BlockPos, BlockState> output) {
        var a = draft.first();
        var b = draft.second();
        if (a.getX() != b.getX() && a.getZ() != b.getZ()) return "Align the two endpoints north/south or east/west";
        int length = Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ());
        int rise = b.getY() - a.getY();
        if (length < 2) return "Place the endpoints at least two blocks apart";
        if (draft.type() == SettlementInfrastructure.Type.BRIDGE && rise != 0) return "A bridge needs endpoints at the same height";
        if (draft.type() == SettlementInfrastructure.Type.STAIRS && (rise == 0 || Math.abs(rise) > length))
            return "Stairs need different heights and at most one block of rise per step";
        var direction = Direction.fromDelta(Integer.signum(b.getX() - a.getX()), 0, Integer.signum(b.getZ() - a.getZ()));
        for (var endpoint : List.of(a, b)) {
            if (!available(level, endpoint, allowed) || !level.getBlockState(endpoint).isFaceSturdy(level, endpoint, Direction.UP))
                return "Both endpoints need solid ground";
        }
        for (var approach : List.of(a.relative(direction.getOpposite()), b.relative(direction))) {
            if (!available(level, approach, allowed) || !level.getBlockState(approach).isFaceSturdy(level, approach, Direction.UP)
                    || !clearHeadroom(level, approach, allowed)) return "Both ends need a clear, walkable connection";
        }
        var sideways = direction.getClockWise();
        for (int step = 0; step <= length; step++) {
            int y = a.getY() + Math.floorDiv(rise * step, length);
            var center = a.relative(direction, step).atY(y);
            for (int width = -1; width <= 1; width++) {
                var pos = center.relative(sideways, width);
                if (!clearHeadroom(level, pos, allowed)) return "Keep two blocks of headroom above the whole route";
                var state = Blocks.OAK_PLANKS.defaultBlockState();
                int nextY = a.getY() + Math.floorDiv(rise * Math.min(step + 1, length), length);
                int previousY = a.getY() + Math.floorDiv(rise * Math.max(step - 1, 0), length);
                if (draft.type() == SettlementInfrastructure.Type.STAIRS && (nextY > y || previousY > y))
                    state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, rise > 0 ? direction : direction.getOpposite());
                output.put(pos, state);
            }
        }
        if (draft.type() == SettlementInfrastructure.Type.BRIDGE) {
            for (int step = 1; step < length; step++) for (int width : new int[]{-1, 1})
                output.put(a.relative(direction, step).relative(sideways, width).above(), Blocks.OAK_FENCE.defaultBlockState());
        }
        return "";
    }

    private static String pen(Level level, SettlementInfrastructure draft, Predicate<BlockPos> allowed, Map<BlockPos, BlockState> output) {
        var min = min(draft).atY(draft.first().getY());
        var max = max(draft).atY(draft.first().getY());
        int width = max.getX() - min.getX() + 1, depth = max.getZ() - min.getZ() + 1;
        if (width < 7 || depth < 7 || width > 32 || depth > 32) return "Animal pens need a level area between 7 x 7 and 32 x 32 blocks";
        for (var raw : BlockPos.betweenClosed(min, max)) {
            var pos = raw.immutable();
            if (!available(level, pos, allowed) || !level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP)
                    || !clearHeadroom(level, pos, allowed)) return "Level the pen floor and clear its headroom first";
            if (pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getZ() == min.getZ() || pos.getZ() == max.getZ())
                output.put(pos.above(), Blocks.OAK_FENCE.defaultBlockState());
        }
        var gate = new BlockPos((min.getX() + max.getX()) / 2, min.getY() + 1, min.getZ());
        output.put(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        var entrance = gate.north().below();
        if (!available(level, entrance, allowed) || !level.getBlockState(entrance).isFaceSturdy(level, entrance, Direction.UP)
                || !clearHeadroom(level, entrance, allowed)) return "Leave a walkable entrance outside the pen gate";
        for (int y = 1; y <= 3; y++) for (int x : new int[]{1, 3}) for (int z : new int[]{1, 3})
            output.put(min.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState());
        for (int x = 0; x <= 4; x++) for (int z = 0; z <= 4; z++) output.put(min.offset(x, 4, z), Blocks.OAK_PLANKS.defaultBlockState());
        return "";
    }

    private static String earthwork(Level level, SettlementInfrastructure draft, Predicate<BlockPos> allowed, Map<BlockPos, BlockState> output) {
        var min = min(draft);
        var max = max(draft);
        int targetY = draft.first().getY();
        var removals = new ArrayList<BlockPos>();
        var fills = new ArrayList<BlockPos>();
        for (int x = min.getX(); x <= max.getX(); x++) for (int z = min.getZ(); z <= max.getZ(); z++) {
            var probe = new BlockPos(x, targetY, z);
            if (!available(level, probe, allowed)) return "Load and claim the entire selection";
            var surface = ground(level, x, z);
            if (!natural(level.getBlockState(surface)) || level.getBlockEntity(surface) != null)
                return "Remove buildings and containers from the selected area first";
            if (Math.abs(surface.getY() - targetY) > 8) return "Earthworks can change at most eight blocks of ground height";
            if (draft.type() == SettlementInfrastructure.Type.CLEAR) {
                for (int y = surface.getY() + 1; y <= Math.min(level.getMaxBuildHeight() - 1, surface.getY() + 16); y++) {
                    var pos = new BlockPos(x, y, z);
                    if (!available(level, pos, allowed)) return "Load and claim the full clearing area";
                    if (!level.getBlockState(pos).isAir()) removals.add(pos);
                }
            } else {
                if (draft.type() == SettlementInfrastructure.Type.LEVEL)
                    for (int y = surface.getY(); y > targetY; y--) removals.add(new BlockPos(x, y, z));
                for (int y = surface.getY() + 1; y <= targetY; y++) fills.add(new BlockPos(x, y, z));
            }
            if (removals.size() + fills.size() > SettlementInfrastructure.MAX_CELLS) return "Select a smaller earthwork area";
        }
        removals.sort(java.util.Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        removals.forEach(pos -> output.put(pos, Blocks.AIR.defaultBlockState()));
        fills.sort(java.util.Comparator.comparingInt(BlockPos::getY));
        fills.forEach(pos -> output.put(pos, Blocks.DIRT.defaultBlockState()));
        return "";
    }

    public static BlockPos ground(Level level, int x, int z) {
        var pos = SettlementPathTerrain.surface(level, x, z);
        for (int depth = 0; depth < 32 && pos.getY() > level.getMinBuildHeight(); depth++) {
            var state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !state.canBeReplaced()) break;
            pos = pos.below();
        }
        return pos;
    }

    private static boolean natural(BlockState state) {
        return state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()
                || SettlementFarmTerrain.soil(state) || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    private static boolean available(Level level, BlockPos pos, Predicate<BlockPos> allowed) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos) && allowed.test(pos) && level.hasChunkAt(pos);
    }

    private static boolean clearHeadroom(Level level, BlockPos floor, Predicate<BlockPos> allowed) {
        return available(level, floor.above(), allowed) && available(level, floor.above(2), allowed)
                && level.getBlockState(floor.above()).getCollisionShape(level, floor.above()).isEmpty()
                && level.getBlockState(floor.above(2)).getCollisionShape(level, floor.above(2)).isEmpty()
                && level.getFluidState(floor.above()).isEmpty() && level.getFluidState(floor.above(2)).isEmpty();
    }

    private static BlockPos min(SettlementInfrastructure draft) {
        return new BlockPos(Math.min(draft.first().getX(), draft.second().getX()), Math.min(draft.first().getY(), draft.second().getY()),
                Math.min(draft.first().getZ(), draft.second().getZ()));
    }

    private static BlockPos max(SettlementInfrastructure draft) {
        return new BlockPos(Math.max(draft.first().getX(), draft.second().getX()), Math.max(draft.first().getY(), draft.second().getY()),
                Math.max(draft.first().getZ(), draft.second().getZ()));
    }
}
