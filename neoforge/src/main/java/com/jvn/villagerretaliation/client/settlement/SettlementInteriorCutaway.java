package com.jvn.villagerretaliation.client.settlement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public final class SettlementInteriorCutaway {
    private static final int MAX_ROOM_COLUMNS = 256;
    private static final int MAX_HIDDEN_BLOCKS = 4096;
    private static final int MAX_OBSERVATIONS = 16384;

    private SettlementInteriorCutaway() {
    }

    public record Wall(BlockPos position, Direction outward) {
    }

    public record Room(SettlementViewBounds bounds, int floor, Set<BlockPos> roof, List<Wall> walls,
                       Map<BlockPos, BlockState> observed, Map<BlockPos, Integer> surfaces) {
        public Room {
            roof = Set.copyOf(roof);
            walls = List.copyOf(walls);
            observed = Map.copyOf(observed);
            surfaces = Map.copyOf(surfaces);
        }

        public boolean unchanged(Level level, Predicate<BlockPos> available) {
            for (var entry : this.observed.entrySet()) {
                if (!available.test(entry.getKey()) || !level.hasChunkAt(entry.getKey())
                        || !level.getBlockState(entry.getKey()).equals(entry.getValue())) return false;
            }
            for (var entry : this.surfaces.entrySet()) {
                var pos = entry.getKey();
                if (!available.test(pos.atY(this.floor)) || !level.hasChunkAt(pos)
                        || level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) != entry.getValue()) return false;
            }
            return true;
        }

        public Set<BlockPos> mask(Vec3 camera) {
            var hidden = new HashSet<>(this.roof);
            for (var wall : this.walls) {
                var pos = wall.position();
                var normal = wall.outward();
                if ((camera.x - pos.getX() - 0.5) * normal.getStepX()
                        + (camera.z - pos.getZ() - 0.5) * normal.getStepZ() > 0) hidden.add(pos);
            }
            return Set.copyOf(hidden);
        }
    }

    public static Room detect(Level level, SettlementViewBounds bounds, BlockPos hit, Predicate<BlockPos> available) {
        if (bounds.volume() > 65536 || bounds.size(Direction.Axis.Y) > 64
                || bounds.size(Direction.Axis.X) > 128 || bounds.size(Direction.Axis.Z) > 128
                || !bounds.box().contains(hit.getCenter()) || !available.test(hit)) return null;
        var scan = new Scan(level, bounds, available);
        BlockPos seed = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                var column = hit.offset(dx, 0, dz);
                if (!bounds.box().contains(column.getCenter())) continue;
                int top = scan.surface(column) - 1;
                if (top > bounds.max().getY() || top < bounds.min().getY() + 3) continue;
                var roof = new BlockPos(column.getX(), top, column.getZ());
                if (!shell(scan.state(roof))) continue;
                for (int y = top - 2; y >= Math.max(bounds.min().getY() + 1, top - 12); y--) {
                    var feet = new BlockPos(column.getX(), y, column.getZ());
                    if (scan.interior(feet) && scan.supports(feet.below())
                            && !scan.openDoorway(feet, Direction.NORTH) && !scan.openDoorway(feet, Direction.EAST)) {
                        if (seed == null || feet.getY() > seed.getY() || feet.getY() == seed.getY()
                                && shell(scan.state(feet.below())) && !shell(scan.state(seed.below()))) seed = feet;
                        break;
                    }
                }
            }
        }
        if (seed == null || scan.invalid) return null;
        var pending = new ArrayDeque<BlockPos>();
        var visited = new HashSet<BlockPos>();
        var roof = new HashSet<BlockPos>();
        var walls = new ArrayList<Wall>();
        pending.add(seed);
        while (!pending.isEmpty()) {
            var feet = pending.removeFirst();
            if (!visited.add(feet)) continue;
            if (visited.size() > MAX_ROOM_COLUMNS || !scan.supports(feet.below())) return null;
            int ceiling = scan.ceiling(feet, roof);
            if (ceiling == Integer.MIN_VALUE) return null;
            for (var direction : Direction.Plane.HORIZONTAL) {
                var next = feet.relative(direction);
                boolean interior = scan.interior(next);
                if (interior && scan.surface(next) <= feet.getY()) continue;
                if (!bounds.box().contains(next.getCenter())) return null;
                if (interior && scan.openDoorway(next, direction)) {
                    if (scan.ceiling(next, roof) == Integer.MIN_VALUE) return null;
                } else if (interior) {
                    pending.add(next);
                } else {
                    if (!shell(scan.state(next)) || !shell(scan.state(next.above()))) return null;
                    // a buried wall must never open a view into terrain
                    if (scan.terrainAboveFloor(next.relative(direction, 2))) return null;
                    for (int y = feet.getY(); y <= ceiling; y++) {
                        var pos = new BlockPos(next.getX(), y, next.getZ());
                        var state = scan.state(pos);
                        if (state.isAir()) continue;
                        if (!shell(state)) return null;
                        walls.add(new Wall(pos, direction));
                    }
                }
            }
            if (scan.invalid || roof.size() + walls.size() > MAX_HIDDEN_BLOCKS) return null;
        }
        return scan.invalid ? null : new Room(bounds, seed.getY() - 1, roof, walls, scan.observed, scan.surfaces);
    }

    private static boolean shell(BlockState state) {
        if (!state.getFluidState().isEmpty()) return false;
        return state.is(BlockTags.PLANKS) || state.is(BlockTags.LOGS) || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS) || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.WOOL) || state.is(BlockTags.TERRACOTTA)
                || state.is(Blocks.BRICKS) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.MOSSY_COBBLESTONE)
                || state.is(Blocks.DEEPSLATE_BRICKS) || state.is(Blocks.DEEPSLATE_TILES)
                || state.is(Blocks.MUD_BRICKS) || state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.RED_NETHER_BRICKS)
                || state.is(Blocks.GLASS) || state.is(Blocks.TINTED_GLASS)
                || state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock;
    }

    private static final class Scan {
        private final Level level;
        private final SettlementViewBounds bounds;
        private final Predicate<BlockPos> available;
        private final Map<BlockPos, BlockState> observed = new HashMap<>();
        private final Map<BlockPos, Integer> surfaces = new HashMap<>();
        private boolean invalid;

        private Scan(Level level, SettlementViewBounds bounds, Predicate<BlockPos> available) {
            this.level = level;
            this.bounds = bounds;
            this.available = available;
        }

        private BlockState state(BlockPos pos) {
            if (!this.available.test(pos) || !this.level.hasChunkAt(pos) || this.observed.size() >= MAX_OBSERVATIONS) {
                this.invalid = true;
                return Blocks.AIR.defaultBlockState();
            }
            return this.observed.computeIfAbsent(pos.immutable(), this.level::getBlockState);
        }

        private int surface(BlockPos pos) {
            if (!this.available.test(pos) || !this.level.hasChunkAt(pos)) {
                this.invalid = true;
                return Integer.MIN_VALUE;
            }
            var column = new BlockPos(pos.getX(), 0, pos.getZ());
            return this.surfaces.computeIfAbsent(column,
                    key -> this.level.getHeight(Heightmap.Types.WORLD_SURFACE, key.getX(), key.getZ()));
        }

        private boolean openDoorway(BlockPos pos, Direction direction) {
            var side = direction.getClockWise();
            return shell(state(pos.relative(side).above())) && shell(state(pos.relative(side.getOpposite()).above()))
                    && (shell(state(pos.above(2))) || shell(state(pos.above(3))));
        }

        private boolean terrainAboveFloor(BlockPos pos) {
            int top = surface(pos) - 1;
            if (this.invalid || (long) top - pos.getY() > 64) return true;
            for (int y = pos.getY(); y <= top; y++) {
                var state = state(pos.atY(y));
                if (!state.isAir() && !shell(state)) return true;
            }
            return false;
        }

        private boolean supports(BlockPos pos) {
            return state(pos).isFaceSturdy(this.level, pos, Direction.UP);
        }

        private boolean interior(BlockPos pos) {
            var feet = state(pos);
            var head = state(pos.above());
            return (!feet.isCollisionShapeFullBlock(this.level, pos) || feet.hasBlockEntity()
                    || feet.is(Blocks.CRAFTING_TABLE) || feet.is(Blocks.SMITHING_TABLE) || feet.is(Blocks.FLETCHING_TABLE)
                    || feet.is(Blocks.CARTOGRAPHY_TABLE) || feet.is(Blocks.LOOM)) && !(feet.getBlock() instanceof DoorBlock)
                    && !(feet.getBlock() instanceof TrapDoorBlock) && feet.getFluidState().isEmpty() && head.getFluidState().isEmpty()
                    && head.getCollisionShape(this.level, pos.above()).isEmpty() && !shell(head);
        }

        private int ceiling(BlockPos feet, Set<BlockPos> roof) {
            int top = surface(feet) - 1;
            if (top > this.bounds.max().getY() || top < feet.getY() + 2 || top > feet.getY() + 12) return Integer.MIN_VALUE;
            int first = Integer.MIN_VALUE;
            for (int y = feet.getY() + 2; y <= top; y++) {
                var pos = new BlockPos(feet.getX(), y, feet.getZ());
                var state = state(pos);
                if (state.isAir()) continue;
                if (!shell(state)) return Integer.MIN_VALUE;
                if (first == Integer.MIN_VALUE) first = y;
                if (top - first > 3) return Integer.MIN_VALUE;
                roof.add(pos);
            }
            return first;
        }
    }
}
