package com.jvn.villagerretaliation.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Resolves framed transfer rules for one physical container, including connected chest halves. */
public final class ContainerFilterResolver {
    private static final Map<CacheKey, Resolution> CACHE = new HashMap<>();

    private ContainerFilterResolver() {
    }

    static Resolution resolve(
            ServerLevel level,
            VillagerInventoryOverflowService.ContainerCandidate candidate) {
        if (candidate == null) {
            return Resolution.unavailable(List.of());
        }
        return resolve(level, candidate.pos(), candidate.positions());
    }

    static Resolution resolve(ServerLevel level, BlockPos canonicalPos, List<BlockPos> logicalPositions) {
        List<BlockPos> positions = normalizePositions(canonicalPos, logicalPositions);
        if (level == null || positions.isEmpty() || !canResolveLive(level, positions)) {
            return Resolution.unavailable(positions);
        }

        BlockPos keyPos = canonicalPos == null ? positions.getFirst() : canonicalPos.immutable();
        CacheKey key = new CacheKey(level.dimension(), keyPos);
        Resolution cached = CACHE.get(key);
        if (cached != null && cached.logicalPositions().equals(positions)) {
            return cached;
        }

        Resolution resolved = new Resolution(scanAttachedRules(level, positions), positions, true);
        CACHE.put(key, resolved);
        return resolved;
    }

    public static void invalidateFrame(ServerLevel level, ItemFrame frame) {
        if (level == null || frame == null) {
            return;
        }
        invalidateAround(level, attachedBlock(frame));
    }

    public static void invalidateContainer(ServerLevel level, BlockPos pos) {
        if (level != null && pos != null) {
            invalidateAround(level, pos);
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunk = event.getChunk().getPos();
        CACHE.entrySet().removeIf(entry -> entry.getKey().dimension().equals(level.dimension())
                && touchesChunk(entry.getValue().logicalPositions(), chunk));
    }

    public static void clearRuntimeState() {
        CACHE.clear();
    }

    static int cachedContainerCount() {
        return CACHE.size();
    }

    private static List<ItemStack> scanAttachedRules(ServerLevel level, List<BlockPos> logicalPositions) {
        Map<UUID, ItemFrame> attached = new LinkedHashMap<>();
        for (BlockPos containerPos : logicalPositions) {
            for (ItemFrame frame : level.getEntitiesOfClass(
                    ItemFrame.class,
                    new AABB(containerPos).inflate(1.0D),
                    candidate -> candidate.isAlive()
                            && logicalPositions.contains(attachedBlock(candidate))
                            && !candidate.getItem().isEmpty())) {
                attached.putIfAbsent(frame.getUUID(), frame);
            }
        }

        List<ItemFrame> ordered = new ArrayList<>(attached.values());
        ordered.sort(Comparator
                .comparingLong((ItemFrame frame) -> attachedBlock(frame).asLong())
                .thenComparingInt(frame -> frame.getDirection().get3DDataValue())
                .thenComparing(frame -> frame.getUUID().toString()));

        List<ItemStack> rules = new ArrayList<>(ordered.size());
        for (ItemFrame frame : ordered) {
            // Ordinary framed items were exact-item routes before configured filter items existed.
            // Retaining them here preserves those worlds; malformed configured filters fail in matching.
            rules.add(frame.getItem().copyWithCount(1));
        }
        return List.copyOf(rules);
    }

    private static boolean canResolveLive(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos pos : positions) {
            if (!level.isPositionEntityTicking(pos)) {
                return false;
            }
            for (Direction direction : Direction.values()) {
                if (!level.isPositionEntityTicking(pos.relative(direction))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void invalidateAround(ServerLevel level, BlockPos changedPos) {
        CACHE.entrySet().removeIf(entry -> entry.getKey().dimension().equals(level.dimension())
                && entry.getValue().logicalPositions().stream()
                        .anyMatch(pos -> pos.distManhattan(changedPos) <= 1));
    }

    private static boolean touchesChunk(List<BlockPos> positions, ChunkPos chunk) {
        for (BlockPos pos : positions) {
            if (new ChunkPos(pos).equals(chunk)) {
                return true;
            }
            for (Direction direction : Direction.values()) {
                if (new ChunkPos(pos.relative(direction)).equals(chunk)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BlockPos attachedBlock(ItemFrame frame) {
        return frame.getPos().relative(frame.getDirection().getOpposite());
    }

    private static List<BlockPos> normalizePositions(BlockPos canonicalPos, List<BlockPos> positions) {
        List<BlockPos> normalized = new ArrayList<>();
        if (positions != null) {
            for (BlockPos position : positions) {
                if (position != null && !normalized.contains(position)) {
                    normalized.add(position.immutable());
                }
            }
        }
        if (normalized.isEmpty() && canonicalPos != null) {
            normalized.add(canonicalPos.immutable());
        }
        normalized.sort(Comparator.comparingLong(BlockPos::asLong));
        return List.copyOf(normalized);
    }

    public record Resolution(List<ItemStack> rules, List<BlockPos> logicalPositions, boolean live) {
        public Resolution {
            rules = rules == null ? List.of() : rules.stream()
                    .filter(stack -> stack != null && !stack.isEmpty())
                    .map(stack -> stack.copyWithCount(1))
                    .toList();
            logicalPositions = logicalPositions == null ? List.of() : List.copyOf(logicalPositions);
        }

        private static Resolution unavailable(List<BlockPos> logicalPositions) {
            return new Resolution(List.of(), logicalPositions, false);
        }
    }

    private record CacheKey(ResourceKey<Level> dimension, BlockPos pos) {
        private CacheKey {
            pos = pos.immutable();
        }
    }
}
