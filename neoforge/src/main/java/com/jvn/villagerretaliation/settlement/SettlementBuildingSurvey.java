package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SettlementBuildingSurvey {
    private SettlementBuildingSurvey() {
    }

    public static Survey inspect(ServerLevel level, SettlementArea area) {
        return inspect(level, area, null);
    }

    static Survey inspect(ServerLevel level, SettlementArea area, LevelChunk supplied) {
        return inspect(new ObservedBlocks(level, supplied), area);
    }

    static Survey inspect(ObservedBlocks blocks, SettlementArea area) {
        var level = blocks.level();
        List<BlockPos> beds = new ArrayList<>();
        Map<BlockPos, Boolean> bedAvailability = new LinkedHashMap<>();
        List<BlockPos> workstations = new ArrayList<>();
        List<BlockPos> waterSources = new ArrayList<>();
        Map<BlockPos, ResourceLocation> fixtures = new LinkedHashMap<>();
        boolean fullyLoaded = true;
        for (int chunkX = Math.floorDiv(area.min().getX(), 16); chunkX <= Math.floorDiv(area.max().getX(), 16); chunkX++) {
            for (int chunkZ = Math.floorDiv(area.min().getZ(), 16); chunkZ <= Math.floorDiv(area.max().getZ(), 16); chunkZ++) {
                LevelChunk chunk = blocks.chunk(chunkX, chunkZ);
                var candidates = new java.util.LinkedHashSet<BlockPos>();
                if (chunk != null) {
                    candidates.addAll(points(level, area, chunk));
                } else {
                    if (!blocks.terrain.observedChunk(chunkX, chunkZ)) {
                        fullyLoaded = false;
                        continue;
                    }
                    int minX = Math.max(area.min().getX(), chunkX << 4);
                    int maxX = Math.min(area.max().getX(), (chunkX << 4) + 15);
                    int minZ = Math.max(area.min().getZ(), chunkZ << 4);
                    int maxZ = Math.min(area.max().getZ(), (chunkZ << 4) + 15);
                    for (var pos : BlockPos.betweenClosed(minX, Math.max(area.min().getY(), level.getMinBuildHeight()), minZ,
                            maxX, Math.min(area.max().getY(), level.getMaxBuildHeight() - 1), maxZ)) {
                        if (!blocks.observed(pos)) {
                            fullyLoaded = false;
                        } else if (relevant(area.type(), blocks.getBlockState(pos))) {
                            candidates.add(pos.immutable());
                        }
                    }
                }
                for (var entry : blocks.terrain.constructionChanges().entrySet()) {
                    var pos = entry.getKey();
                    if (pos.getX() >> 4 == chunkX && pos.getZ() >> 4 == chunkZ && area.contains(pos)
                            && relevant(area.type(), entry.getValue())) candidates.add(pos);
                }
                for (BlockPos position : candidates) {
                    if (!relevant(area.type(), blocks.getBlockState(position))) continue;
                    if (area.type() == SettlementAreaType.HOME) {
                        BedState bed = inspectBed(blocks, area, position);
                        if (bed == BedState.AVAILABLE) {
                            beds.add(position);
                            if (blocks.chunk(position.getX() >> 4, position.getZ() >> 4) != null) {
                                bedAvailability.put(position, !level.getPoiManager().existsAtPosition(PoiTypes.HOME, position)
                                        || level.getPoiManager().getFreeTickets(position) > 0);
                            } else if (blocks.pendingConstruction(position, blocks.getBlockState(position))) {
                                bedAvailability.put(position, true);
                            }
                        } else if (bed == BedState.UNOBSERVED) {
                            fullyLoaded = false;
                        }
                    } else if (area.type() == SettlementAreaType.BREWERY && waterSource(blocks.getBlockState(position))) {
                        waterSources.add(position);
                    } else {
                        workstations.add(position);
                        if (requiresFixture(area.type())) {
                            fixtures.put(position, BuiltInRegistries.BLOCK.getKey(blocks.getBlockState(position).getBlock()));
                        }
                    }
                }
            }
        }
        return new Survey(level.getGameTime(), beds, workstations, fullyLoaded, fixtures, waterSources, bedAvailability);
    }

    public static boolean requiresFixture(SettlementAreaType type) {
        return switch (type) {
            case KITCHEN, WORKSHOP, SMELTERY, BREWERY, TRADING_POST -> true;
            default -> false;
        };
    }

    private static boolean relevant(SettlementAreaType type, BlockState state) {
        return switch (type) {
            case HOME -> state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
            case KITCHEN -> state.is(Blocks.SMOKER) || state.is(Blocks.FURNACE) || state.is(Blocks.CRAFTING_TABLE);
            case WORKSHOP -> state.is(Blocks.CRAFTING_TABLE);
            case SMELTERY -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE);
            case BREWERY -> state.is(Blocks.BREWING_STAND) || waterSource(state);
            case TRADING_POST -> state.is(Blocks.LECTERN);
            default -> PoiTypes.forState(state).filter(poi -> poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent();
        };
    }

    static boolean waterSource(BlockState state) {
        var fluid = state.getFluidState();
        return fluid.is(net.minecraft.tags.FluidTags.WATER) && fluid.isSource();
    }

    private static List<BlockPos> points(ServerLevel level, SettlementArea area, LevelChunk chunk) {
        List<BlockPos> result = new ArrayList<>();
        int minX = Math.max(area.min().getX(), chunk.getPos().getMinBlockX());
        int maxX = Math.min(area.max().getX(), chunk.getPos().getMaxBlockX());
        int minZ = Math.max(area.min().getZ(), chunk.getPos().getMinBlockZ());
        int maxZ = Math.min(area.max().getZ(), chunk.getPos().getMaxBlockZ());
        int minY = Math.max(area.min().getY(), level.getMinBuildHeight());
        int maxY = Math.min(area.max().getY(), level.getMaxBuildHeight() - 1);
        for (int sectionY = Math.floorDiv(minY, 16); sectionY <= Math.floorDiv(maxY, 16); sectionY++) {
            var section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
            if (!section.maybeHas(state -> relevant(area.type(), state))) {
                continue;
            }
            for (int y = Math.max(minY, sectionY * 16); y <= Math.min(maxY, sectionY * 16 + 15); y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (relevant(area.type(), section.getBlockState(x & 15, y & 15, z & 15))) {
                            result.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return result;
    }

    static boolean observed(ServerLevel level, BlockPos position, LevelChunk supplied) {
        return new ObservedBlocks(level, supplied).observed(position);
    }

    static boolean fixtureAvailable(ObservedBlocks blocks, BlockPos position, ResourceLocation expected) {
        return blocks.observed(position) ? BuiltInRegistries.BLOCK.getKey(blocks.getBlockState(position).getBlock()).equals(expected)
                : !blocks.blocked(position);
    }

    public static boolean usableBed(ServerLevel level, BlockPos head) {
        return inspectBed(new ObservedBlocks(level, null), null, head) == BedState.AVAILABLE;
    }

    static BedState inspectBed(ServerLevel level, SettlementArea area, BlockPos head) {
        return inspectBed(level, area, head, null);
    }

    static BedState inspectBed(ServerLevel level, SettlementArea area, BlockPos head, LevelChunk supplied) {
        return inspectBed(new ObservedBlocks(level, supplied), area, head);
    }

    static BedState inspectBed(ObservedBlocks blocks, SettlementArea area, BlockPos head) {
        if (!blocks.observed(head)) {
            return blocks.blocked(head) ? BedState.BLOCKED : BedState.UNOBSERVED;
        }
        BlockState state = blocks.getBlockState(head);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
            return BedState.BLOCKED;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos foot = head.relative(facing.getOpposite());
        if (area != null && !area.contains(foot)) {
            return BedState.BLOCKED;
        }
        if (!blocks.observed(foot)) {
            return blocks.blocked(foot) ? BedState.BLOCKED : BedState.UNOBSERVED;
        }
        BlockState other = blocks.getBlockState(foot);
        if (!other.is(state.getBlock()) || other.getValue(BedBlock.PART) != BedPart.FOOT
                || other.getValue(BedBlock.FACING) != facing) {
            return BedState.BLOCKED;
        }
        for (var pos : List.of(head, foot)) {
            if (blocks.blocked(pos.above()) || blocks.observed(pos.above()) && !clearAbove(blocks, pos)) return BedState.BLOCKED;
        }
        if (!blocks.observed(head.above()) || !blocks.observed(foot.above())) return BedState.UNOBSERVED;
        boolean unobservedExit = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos headExit = head.relative(direction);
            BlockPos footExit = foot.relative(direction);
            var headState = exit(blocks, headExit);
            var footState = exit(blocks, footExit);
            if (headState == BedState.AVAILABLE || footState == BedState.AVAILABLE) return BedState.AVAILABLE;
            unobservedExit |= headState == BedState.UNOBSERVED || footState == BedState.UNOBSERVED;
        }
        return unobservedExit ? BedState.UNOBSERVED : BedState.BLOCKED;
    }

    enum BedState {
        AVAILABLE,
        BLOCKED,
        UNOBSERVED
    }

    private static boolean clearAbove(ObservedBlocks blocks, BlockPos pos) {
        return blocks.getBlockState(pos.above()).getCollisionShape(blocks, pos.above()).isEmpty();
    }

    private static BedState exit(ObservedBlocks blocks, BlockPos pos) {
        if (blocks.blocked(pos) || blocks.blocked(pos.above()) || blocks.blocked(pos.below())) return BedState.BLOCKED;
        if (!blocks.observed(pos) || !blocks.observed(pos.above()) || !blocks.observed(pos.below())) return BedState.UNOBSERVED;
        return blocks.getBlockState(pos).getCollisionShape(blocks, pos).isEmpty() && clearAbove(blocks, pos)
                && !blocks.getBlockState(pos.below()).getCollisionShape(blocks, pos.below()).isEmpty()
                && blocks.getFluidState(pos).isEmpty() ? BedState.AVAILABLE : BedState.BLOCKED;
    }

    static final class ObservedBlocks implements BlockGetter {
        private final ServerLevel level;
        private final LevelChunk supplied;
        private final SettlementTerrainObservations terrain;

        ObservedBlocks(ServerLevel level, LevelChunk supplied) {
            this(level, supplied, null);
        }

        ObservedBlocks(ServerLevel level, LevelChunk supplied, SettlementRecord town) {
            this.level = level;
            this.supplied = supplied;
            this.terrain = new SettlementTerrainObservations(town, this);
        }

        ServerLevel level() {
            return this.level;
        }

        LevelChunk chunk(int x, int z) {
            return this.supplied != null && this.supplied.getPos().x == x && this.supplied.getPos().z == z
                    ? this.supplied : this.level.getChunkSource().getChunkNow(x, z);
        }

        boolean observed(BlockPos position) {
            return this.terrain.state(position) != null;
        }

        boolean blocked(BlockPos position) {
            return this.terrain.blocked(position);
        }

        boolean pendingConstruction(BlockPos position, BlockState expected) {
            return this.terrain.constructionChanges().get(position) == expected && this.terrain.state(position) == expected;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos position) {
            var chunk = chunk(Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
            return chunk == null ? null : chunk.getBlockEntity(position, LevelChunk.EntityCreationType.CHECK);
        }

        @Override
        public BlockState getBlockState(BlockPos position) {
            var observed = this.terrain.state(position);
            return observed == null ? Blocks.AIR.defaultBlockState() : observed;
        }

        @Override
        public FluidState getFluidState(BlockPos position) {
            return getBlockState(position).getFluidState();
        }

        @Override
        public int getHeight() {
            return this.level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.level.getMinBuildHeight();
        }
    }

    public record Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded,
                         Map<BlockPos, ResourceLocation> fixtures, List<BlockPos> waterSources, Map<BlockPos, Boolean> bedAvailability) {
        public Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded) {
            this(checkedAt, beds, workstations, fullyLoaded, Map.of());
        }

        public Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded,
                      Map<BlockPos, ResourceLocation> fixtures) {
            this(checkedAt, beds, workstations, fullyLoaded, fixtures, List.of());
        }

        public Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded,
                      Map<BlockPos, ResourceLocation> fixtures, List<BlockPos> waterSources) {
            this(checkedAt, beds, workstations, fullyLoaded, fixtures, waterSources, Map.of());
        }

        public Survey {
            bedAvailability = Map.copyOf(bedAvailability);
            if (!new java.util.HashSet<>(beds).containsAll(bedAvailability.keySet())) {
                throw new IllegalArgumentException("Bed availability must belong to surveyed beds");
            }
            beds = beds.stream().distinct().sorted().toList();
            workstations = workstations.stream().distinct().sorted().toList();
            fixtures = Map.copyOf(fixtures);
            waterSources = waterSources.stream().distinct().sorted().toList();
            if (!new java.util.HashSet<>(workstations).containsAll(fixtures.keySet())) {
                throw new IllegalArgumentException("Observed fixtures must belong to surveyed workstations");
            }
            checkedAt = Math.max(0L, checkedAt);
            if (beds.size() > SettlementArea.MAX_VOLUME || workstations.size() > SettlementArea.MAX_VOLUME
                    || waterSources.size() > SettlementArea.MAX_VOLUME) {
                throw new IllegalArgumentException("Building survey exceeds its maximum volume");
            }
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("CheckedAt", this.checkedAt);
            tag.putLongArray("Beds", this.beds.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("Workstations", this.workstations.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putBoolean("FullyLoaded", this.fullyLoaded);
            tag.putLongArray("WaterSources", this.waterSources.stream().mapToLong(BlockPos::asLong).toArray());
            ListTag fixtures = new ListTag();
            this.fixtures.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                CompoundTag fixture = new CompoundTag();
                fixture.putLong("Position", entry.getKey().asLong());
                fixture.putString("Block", entry.getValue().toString());
                fixtures.add(fixture);
            });
            tag.put("Fixtures", fixtures);
            var availability = new ListTag();
            this.bedAvailability.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                var bed = new CompoundTag();
                bed.putLong("Position", entry.getKey().asLong());
                bed.putBoolean("Available", entry.getValue());
                availability.add(bed);
            });
            tag.put("BedAvailability", availability);
            return tag;
        }

        static Survey load(CompoundTag tag) {
            Map<BlockPos, ResourceLocation> fixtures = new LinkedHashMap<>();
            for (Tag raw : tag.getList("Fixtures", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) raw;
                ResourceLocation block = ResourceLocation.tryParse(entry.getString("Block"));
                if (block == null || fixtures.putIfAbsent(BlockPos.of(entry.getLong("Position")), block) != null) {
                    throw new IllegalArgumentException("Observed fixture has an invalid block or duplicate position");
                }
            }
            Map<BlockPos, Boolean> availability = new LinkedHashMap<>();
            for (Tag raw : tag.getList("BedAvailability", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) raw;
                if (!entry.contains("Position", Tag.TAG_LONG) || !entry.contains("Available", Tag.TAG_BYTE)
                        || availability.putIfAbsent(BlockPos.of(entry.getLong("Position")), entry.getBoolean("Available")) != null) {
                    throw new IllegalArgumentException("Bed availability has a missing or duplicate position");
                }
            }
            return new Survey(tag.getLong("CheckedAt"),
                    Arrays.stream(tag.getLongArray("Beds")).mapToObj(BlockPos::of).toList(),
                    Arrays.stream(tag.getLongArray("Workstations")).mapToObj(BlockPos::of).toList(),
                    tag.getBoolean("FullyLoaded"), fixtures,
                    Arrays.stream(tag.getLongArray("WaterSources")).mapToObj(BlockPos::of).toList(), availability);
        }
    }
}
