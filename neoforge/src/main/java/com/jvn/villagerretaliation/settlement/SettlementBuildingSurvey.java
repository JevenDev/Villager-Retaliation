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
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

public final class SettlementBuildingSurvey {
    private SettlementBuildingSurvey() {
    }

    public static Survey inspect(ServerLevel level, SettlementArea area) {
        List<BlockPos> beds = new ArrayList<>();
        List<BlockPos> workstations = new ArrayList<>();
        Map<BlockPos, ResourceLocation> fixtures = new LinkedHashMap<>();
        boolean fullyLoaded = true;
        int minChunkX = Math.floorDiv(area.min().getX(), 16);
        int maxChunkX = Math.floorDiv(area.max().getX(), 16);
        int minChunkZ = Math.floorDiv(area.min().getZ(), 16);
        int maxChunkZ = Math.floorDiv(area.max().getZ(), 16);
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.hasChunk(x, z)) {
                    fullyLoaded = false;
                    continue;
                }
                var points = level.getPoiManager().getInChunk(type -> type.is(PoiTypes.HOME)
                                || type.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), new ChunkPos(x, z), PoiManager.Occupancy.ANY)
                        .filter(poi -> area.contains(poi.getPos())).toList();
                for (var poi : points) {
                    BlockPos pos = poi.getPos();
                    if (poi.getPoiType().is(PoiTypes.HOME)) {
                        if (area.type() == SettlementAreaType.HOME) {
                            BedState bed = inspectBed(level, area, pos);
                            if (bed == BedState.AVAILABLE) {
                                beds.add(pos.immutable());
                            } else if (bed == BedState.UNOBSERVED) {
                                fullyLoaded = false;
                            }
                        }
                    } else if (!requiresFixture(area.type())) {
                        workstations.add(pos.immutable());
                    }
                }
                if (requiresFixture(area.type())) {
                    collectFixtures(level, area, x, z, workstations, fixtures);
                }
            }
        }
        return new Survey(level.getGameTime(), beds, workstations, fullyLoaded, fixtures);
    }

    public static boolean requiresFixture(SettlementAreaType type) {
        return switch (type) {
            case KITCHEN, WORKSHOP, SMELTERY, BREWERY, TRADING_POST -> true;
            default -> false;
        };
    }

    private static boolean fixture(SettlementAreaType type, BlockState state) {
        return switch (type) {
            case KITCHEN -> state.is(Blocks.SMOKER) || state.is(Blocks.FURNACE) || state.is(Blocks.CRAFTING_TABLE);
            case WORKSHOP -> state.is(Blocks.CRAFTING_TABLE);
            case SMELTERY -> state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE);
            case BREWERY -> state.is(Blocks.BREWING_STAND);
            case TRADING_POST -> state.is(Blocks.LECTERN);
            default -> false;
        };
    }

    private static void collectFixtures(ServerLevel level, SettlementArea area, int chunkX, int chunkZ,
                                        List<BlockPos> fixtures, Map<BlockPos, ResourceLocation> types) {
        var chunk = level.getChunk(chunkX, chunkZ);
        int minX = Math.max(area.min().getX(), chunkX * 16);
        int maxX = Math.min(area.max().getX(), chunkX * 16 + 15);
        int minZ = Math.max(area.min().getZ(), chunkZ * 16);
        int maxZ = Math.min(area.max().getZ(), chunkZ * 16 + 15);
        int minY = Math.max(area.min().getY(), level.getMinBuildHeight());
        int maxY = Math.min(area.max().getY(), level.getMaxBuildHeight() - 1);
        for (int sectionY = Math.floorDiv(minY, 16); sectionY <= Math.floorDiv(maxY, 16); sectionY++) {
            var section = chunk.getSection(level.getSectionIndexFromSectionY(sectionY));
            if (!section.maybeHas(state -> fixture(area.type(), state))) {
                continue;
            }
            for (int y = Math.max(minY, sectionY * 16); y <= Math.min(maxY, sectionY * 16 + 15); y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (fixture(area.type(), section.getBlockState(x & 15, y & 15, z & 15))) {
                            BlockPos position = new BlockPos(x, y, z);
                            fixtures.add(position);
                            types.put(position, BuiltInRegistries.BLOCK.getKey(section.getBlockState(x & 15, y & 15, z & 15).getBlock()));
                        }
                    }
                }
            }
        }
    }

    public static boolean usableBed(ServerLevel level, BlockPos head) {
        return inspectBed(level, head) == BedState.AVAILABLE;
    }

    static BedState inspectBed(ServerLevel level, SettlementArea area, BlockPos head) {
        if (level.hasChunkAt(head)) {
            BlockState state = level.getBlockState(head);
            if (state.getBlock() instanceof BedBlock
                    && !area.contains(head.relative(state.getValue(BedBlock.FACING).getOpposite()))) {
                return BedState.BLOCKED;
            }
        }
        return inspectBed(level, head);
    }

    private static BedState inspectBed(ServerLevel level, BlockPos head) {
        if (!level.hasChunkAt(head)) {
            return BedState.UNOBSERVED;
        }
        BlockState state = level.getBlockState(head);
        if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
            return BedState.BLOCKED;
        }
        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos foot = head.relative(facing.getOpposite());
        if (!level.hasChunkAt(foot)) {
            return BedState.UNOBSERVED;
        }
        BlockState other = level.getBlockState(foot);
        if (!other.is(state.getBlock()) || other.getValue(BedBlock.PART) != BedPart.FOOT
                || other.getValue(BedBlock.FACING) != facing
                || !clearAbove(level, head) || !clearAbove(level, foot)) {
            return BedState.BLOCKED;
        }
        boolean unobservedExit = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos headExit = head.relative(direction);
            BlockPos footExit = foot.relative(direction);
            if (safeExit(level, headExit) || safeExit(level, footExit)) {
                return BedState.AVAILABLE;
            }
            unobservedExit |= !level.hasChunkAt(headExit) || !level.hasChunkAt(footExit);
        }
        return unobservedExit ? BedState.UNOBSERVED : BedState.BLOCKED;
    }

    enum BedState {
        AVAILABLE,
        BLOCKED,
        UNOBSERVED
    }

    private static boolean clearAbove(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private static boolean safeExit(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && clearAbove(level, pos)
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.getFluidState(pos).isEmpty();
    }

    public record Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded,
                         Map<BlockPos, ResourceLocation> fixtures) {
        public Survey(long checkedAt, List<BlockPos> beds, List<BlockPos> workstations, boolean fullyLoaded) {
            this(checkedAt, beds, workstations, fullyLoaded, Map.of());
        }

        public Survey {
            beds = beds.stream().distinct().sorted().toList();
            workstations = workstations.stream().distinct().sorted().toList();
            fixtures = Map.copyOf(fixtures);
            if (!new java.util.HashSet<>(workstations).containsAll(fixtures.keySet())) {
                throw new IllegalArgumentException("Observed fixtures must belong to surveyed workstations");
            }
            checkedAt = Math.max(0L, checkedAt);
            if (beds.size() > SettlementArea.MAX_VOLUME || workstations.size() > SettlementArea.MAX_VOLUME) {
                throw new IllegalArgumentException("Building survey exceeds its maximum volume");
            }
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("CheckedAt", this.checkedAt);
            tag.putLongArray("Beds", this.beds.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putLongArray("Workstations", this.workstations.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putBoolean("FullyLoaded", this.fullyLoaded);
            ListTag fixtures = new ListTag();
            this.fixtures.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                CompoundTag fixture = new CompoundTag();
                fixture.putLong("Position", entry.getKey().asLong());
                fixture.putString("Block", entry.getValue().toString());
                fixtures.add(fixture);
            });
            tag.put("Fixtures", fixtures);
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
            return new Survey(tag.getLong("CheckedAt"),
                    Arrays.stream(tag.getLongArray("Beds")).mapToObj(BlockPos::of).toList(),
                    Arrays.stream(tag.getLongArray("Workstations")).mapToObj(BlockPos::of).toList(),
                    tag.getBoolean("FullyLoaded"), fixtures);
        }
    }
}
