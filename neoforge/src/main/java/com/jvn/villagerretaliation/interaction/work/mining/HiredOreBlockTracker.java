package com.jvn.villagerretaliation.interaction.work.mining;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class HiredOreBlockTracker {
    private static final Map<ServerLevel, OreIndex> INDEXES = new HashMap<>();
    private static final long RECENT_EXPOSURE_TICKS = 20L * 30L;
    private static final long RECENT_EXPOSURE_PRUNE_TICKS = 100L;

    private HiredOreBlockTracker() {
    }

    public static void clearRuntimeState() {
        INDEXES.clear();
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            OreIndex index = INDEXES.get(level);
            if (index != null) {
                index.forgetChunk(event.getChunk().getPos().toLong());
            }
        }
    }

    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        updatePlacedBlock(event.getLevel(), event.getPos(), event.getPlacedBlock());
    }

    public static void onFluidPlaceBlock(BlockEvent.FluidPlaceBlockEvent event) {
        updatePlacedBlock(event.getLevel(), event.getPos(), event.getNewState());
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            index(level).rememberPotentialExposureAround(level, event.getPos());
        }
    }

    public static void onBlockBroken(ServerLevel level, BlockPos pos) {
        OreIndex index = index(level);
        index.remove(pos);
        index.rememberPotentialExposureAround(level, pos);
    }

    public static List<BlockPos> nearbyOreBlocks(ServerLevel level, BlockPos center, int radius, int verticalRadius) {
        return index(level).nearbyOreBlocks(level, center, Math.max(1, radius), Math.max(1, verticalRadius));
    }

    public static List<BlockPos> recentlyExposedOreBlocks(ServerLevel level, BlockPos center, int radius, int verticalRadius) {
        return index(level).recentlyExposedOreBlocks(level, center, Math.max(1, radius), Math.max(1, verticalRadius));
    }

    static boolean isTrackedOre(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(Tags.Blocks.ORES)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private static OreIndex index(ServerLevel level) {
        return INDEXES.computeIfAbsent(level, ignored -> new OreIndex());
    }

    private static void updatePlacedBlock(LevelAccessor levelAccessor, BlockPos pos, BlockState state) {
        if (levelAccessor instanceof ServerLevel level) {
            if (isTrackedOre(state)) {
                index(level).add(pos);
            } else {
                index(level).remove(pos);
            }
        }
    }

    private static final class OreIndex {
        private final Map<Long, Set<Long>> oreBlocksByChunk = new HashMap<>();
        private final Set<Long> fullyIndexedChunks = new HashSet<>();
        private final Map<Long, Map<Long, Long>> recentlyExposedOreBlocksByChunk = new HashMap<>();
        private long nextRecentExposurePruneGameTime;

        void indexChunk(LevelChunk chunk) {
            Set<Long> oreBlocks = new HashSet<>();
            long chunkKey = chunk.getPos().toLong();
            chunk.findBlocks(HiredOreBlockTracker::isTrackedOre, (pos, state) -> oreBlocks.add(pos.asLong()));
            this.oreBlocksByChunk.put(chunkKey, oreBlocks);
            this.fullyIndexedChunks.add(chunkKey);
        }

        void forgetChunk(long chunkKey) {
            this.oreBlocksByChunk.remove(chunkKey);
            this.fullyIndexedChunks.remove(chunkKey);
            this.recentlyExposedOreBlocksByChunk.remove(chunkKey);
        }

        void add(BlockPos pos) {
            this.oreBlocksByChunk
                    .computeIfAbsent(ChunkPos.asLong(pos), ignored -> new HashSet<>())
                    .add(pos.asLong());
        }

        void remove(BlockPos pos) {
            Set<Long> oreBlocks = this.oreBlocksByChunk.get(ChunkPos.asLong(pos));
            if (oreBlocks == null) {
                removeRecentExposure(pos);
                return;
            }
            oreBlocks.remove(pos.asLong());
            removeRecentExposure(pos);
        }

        void rememberPotentialExposureAround(ServerLevel level, BlockPos origin) {
            long expiresGameTime = level.getGameTime() + RECENT_EXPOSURE_TICKS;
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                BlockPos candidate = origin.relative(direction).immutable();
                if (level.hasChunkAt(candidate) && isTrackedOre(level.getBlockState(candidate))) {
                    add(candidate);
                    this.recentlyExposedOreBlocksByChunk
                            .computeIfAbsent(ChunkPos.asLong(candidate), ignored -> new HashMap<>())
                            .put(candidate.asLong(), expiresGameTime);
                }
            }
        }

        List<BlockPos> nearbyOreBlocks(ServerLevel level, BlockPos center, int radius, int verticalRadius) {
            int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
            int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
            int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
            int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
            int minY = Math.max(level.getMinBuildHeight(), center.getY() - verticalRadius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + verticalRadius);
            List<BlockPos> matches = new ArrayList<>();

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    ensureChunkIndexed(level, chunkX, chunkZ, chunkKey);
                    Set<Long> oreBlocks = this.oreBlocksByChunk.get(chunkKey);
                    if (oreBlocks == null || oreBlocks.isEmpty()) {
                        continue;
                    }
                    oreBlocks.removeIf(packedPos -> isStale(level, BlockPos.of(packedPos)));
                    for (long packedPos : oreBlocks) {
                        BlockPos pos = BlockPos.of(packedPos);
                        if (pos.getY() >= minY
                                && pos.getY() <= maxY
                                && Math.abs(pos.getX() - center.getX()) <= radius
                                && Math.abs(pos.getZ() - center.getZ()) <= radius) {
                            matches.add(pos);
                        }
                    }
                }
            }

            matches.sort(Comparator.comparingDouble(pos -> center.distSqr(pos)));
            return matches;
        }

        List<BlockPos> recentlyExposedOreBlocks(ServerLevel level, BlockPos center, int radius, int verticalRadius) {
            long now = level.getGameTime();
            pruneExpiredRecentExposures(now);
            int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
            int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
            int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
            int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
            int minY = Math.max(level.getMinBuildHeight(), center.getY() - verticalRadius);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + verticalRadius);
            List<BlockPos> matches = new ArrayList<>();

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    Map<Long, Long> recent = this.recentlyExposedOreBlocksByChunk.get(chunkKey);
                    if (recent == null) {
                        continue;
                    }
                    recent.entrySet().removeIf(entry ->
                            entry.getValue() <= now || isStale(level, BlockPos.of(entry.getKey())));
                    if (recent.isEmpty()) {
                        this.recentlyExposedOreBlocksByChunk.remove(chunkKey);
                        continue;
                    }
                    for (long packedPos : recent.keySet()) {
                        BlockPos pos = BlockPos.of(packedPos);
                        if (pos.getY() >= minY
                                && pos.getY() <= maxY
                                && Math.abs(pos.getX() - center.getX()) <= radius
                                && Math.abs(pos.getZ() - center.getZ()) <= radius) {
                            matches.add(pos);
                        }
                    }
                }
            }

            matches.sort(Comparator.comparingDouble(pos -> center.distSqr(pos)));
            return matches;
        }

        private void removeRecentExposure(BlockPos pos) {
            long chunkKey = ChunkPos.asLong(pos);
            Map<Long, Long> recent = this.recentlyExposedOreBlocksByChunk.get(chunkKey);
            if (recent == null) {
                return;
            }
            recent.remove(pos.asLong());
            if (recent.isEmpty()) {
                this.recentlyExposedOreBlocksByChunk.remove(chunkKey);
            }
        }

        private void pruneExpiredRecentExposures(long gameTime) {
            if (gameTime < this.nextRecentExposurePruneGameTime) {
                return;
            }
            this.nextRecentExposurePruneGameTime = gameTime + RECENT_EXPOSURE_PRUNE_TICKS;
            this.recentlyExposedOreBlocksByChunk.entrySet().removeIf(entry -> {
                entry.getValue().entrySet().removeIf(exposure -> exposure.getValue() <= gameTime);
                return entry.getValue().isEmpty();
            });
        }

        private void ensureChunkIndexed(ServerLevel level, int chunkX, int chunkZ, long chunkKey) {
            if (this.fullyIndexedChunks.contains(chunkKey)) {
                return;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk != null) {
                indexChunk(chunk);
            }
        }

        private boolean isStale(ServerLevel level, BlockPos pos) {
            return !level.hasChunkAt(pos) || !isTrackedOre(level.getBlockState(pos));
        }
    }
}
