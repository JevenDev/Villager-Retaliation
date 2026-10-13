package com.jvn.villagerretaliation.interaction.work.mining;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
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
    private static volatile Set<BlockState> trackedStates;
    private static net.minecraft.server.MinecraftServer budgetServer;
    private static int budgetTick;
    private static int captures;
    private static final long RECENT_EXPOSURE_TICKS = 20L * 30L;
    private static final long RECENT_EXPOSURE_PRUNE_TICKS = 100L;

    private HiredOreBlockTracker() {
    }

    public static void clearRuntimeState() {
        INDEXES.values().forEach(OreIndex::cancel);
        INDEXES.clear();
        OreIndexTasks.clear();
        trackedStates = null;
        budgetServer = null;
        captures = 0;
    }

    public static void tick() {
        INDEXES.forEach((level, index) -> index.finish(level));
    }

    public static void onBlockChanged(LevelChunk chunk, BlockPos position, BlockState previous, BlockState replacement) {
        if (!(chunk.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread() || previous == null || previous == replacement) return;
        var index = INDEXES.get(level);
        if (index == null || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
        long key = chunk.getPos().toLong();
        var pending = index.pending.get(key);
        if (pending != null && pending.chunk == chunk) pending.changed.add(position.asLong());
        if (!index.fullyIndexedChunks.contains(key) && pending == null) return;
        if (isTrackedOre(replacement)) index.add(position);
        else index.remove(position);
    }

    public static boolean ready(ServerLevel level, BlockPos center, int radius) {
        var index = INDEXES.get(level);
        if (index == null) return false;
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++) {
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) != null && !index.fullyIndexedChunks.contains(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
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
        return trackedStates().contains(state);
    }

    private static Set<BlockState> trackedStates() {
        var current = trackedStates;
        if (current != null) return current;
        var states = new HashSet<BlockState>();
        for (var block : BuiltInRegistries.BLOCK) {
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (!state.isAir() && (state.is(Tags.Blocks.ORES) || OreNodeDefinitions.extractions(state) > 0
                        || path.endsWith("_ore") || path.equals("ancient_debris"))) states.add(state);
            }
        }
        trackedStates = Set.copyOf(states);
        return trackedStates;
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
        private final Map<Long, LongSet> oreBlocksByChunk = new HashMap<>();
        private final Set<Long> fullyIndexedChunks = new HashSet<>();
        private final Map<Long, Pending> pending = new HashMap<>();
        private final Map<Long, Map<Long, Long>> recentlyExposedOreBlocksByChunk = new HashMap<>();
        private long nextRecentExposurePruneGameTime;

        void finish(ServerLevel level) {
            var iterator = this.pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                var job = entry.getValue();
                if (!job.result.isDone()) continue;
                iterator.remove();
                if (level.getChunkSource().getChunkNow(job.chunk.getPos().x, job.chunk.getPos().z) != job.chunk) continue;
                LongSet ores;
                try {
                    ores = job.result.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while receiving ore index", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    throw new IllegalStateException("Failed to index ore chunk " + job.chunk.getPos(), exception.getCause());
                }
                // edits since capture override the worker's snapshot before it becomes visible
                for (long packed : job.changed) {
                    if (isTrackedOre(job.chunk.getBlockState(BlockPos.of(packed)))) ores.add(packed);
                    else ores.remove(packed);
                }
                this.oreBlocksByChunk.put(entry.getKey(), ores);
                this.fullyIndexedChunks.add(entry.getKey());
            }
        }

        void cancel() {
            this.pending.values().forEach(job -> job.result.cancel(false));
            this.pending.clear();
        }

        void forgetChunk(long chunkKey) {
            var job = this.pending.remove(chunkKey);
            if (job != null) job.result.cancel(false);
            this.oreBlocksByChunk.remove(chunkKey);
            this.fullyIndexedChunks.remove(chunkKey);
            this.recentlyExposedOreBlocksByChunk.remove(chunkKey);
        }

        void add(BlockPos pos) {
            this.oreBlocksByChunk
                    .computeIfAbsent(ChunkPos.asLong(pos), ignored -> new LongOpenHashSet())
                    .add(pos.asLong());
        }

        void remove(BlockPos pos) {
            LongSet oreBlocks = this.oreBlocksByChunk.get(ChunkPos.asLong(pos));
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
            finish(level);
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
                    LongSet oreBlocks = this.oreBlocksByChunk.get(chunkKey);
                    if (oreBlocks == null || oreBlocks.isEmpty()) {
                        continue;
                    }
                    var iterator = oreBlocks.iterator();
                    while (iterator.hasNext()) {
                        long packed = iterator.nextLong();
                        if (BlockPos.getY(packed) < minY || BlockPos.getY(packed) > maxY || Math.abs(BlockPos.getX(packed) - center.getX()) > radius
                                || Math.abs(BlockPos.getZ(packed) - center.getZ()) > radius) continue;
                        BlockPos pos = BlockPos.of(packed);
                        if (isStale(level, pos)) iterator.remove();
                        else matches.add(pos);
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
            if (this.fullyIndexedChunks.contains(chunkKey) || this.pending.containsKey(chunkKey)) return;
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) return;
            var server = level.getServer();
            if (budgetServer != server || budgetTick != server.getTickCount()) {
                budgetServer = server;
                budgetTick = server.getTickCount();
                captures = 0;
            }
            var ores = trackedStates();
            int sections = 0;
            for (var section : chunk.getSections()) {
                if (section.getStates().maybeHas(ores::contains) && ++sections > 1) break;
            }
            if (sections > 1) {
                if (captures >= 2 || !OreIndexTasks.hasCapacity()) return;
                captures++;
            }
            var snapshot = OreIndexSnapshot.capture(chunk, ores);
            if (snapshot.sections().size() <= 1) {
                this.oreBlocksByChunk.put(chunkKey, snapshot.scan());
                this.fullyIndexedChunks.add(chunkKey);
                return;
            }
            var result = OreIndexTasks.submit(snapshot::scan);
            if (result != null) this.pending.put(chunkKey, new Pending(chunk, result));
        }

        private record Pending(LevelChunk chunk, java.util.concurrent.Future<LongSet> result,
                               LongSet changed) {
            Pending(LevelChunk chunk, java.util.concurrent.Future<LongSet> result) {
                this(chunk, result, new LongOpenHashSet());
            }
        }

        private boolean isStale(ServerLevel level, BlockPos pos) {
            return !level.hasChunkAt(pos) || !isTrackedOre(level.getBlockState(pos));
        }
    }
}
