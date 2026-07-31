package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Tree classification and bounded geometry discovery shared by target planning and harvesting. */
final class LoggingTreeGeometry {
    private static final int MAX_HORIZONTAL_DISTANCE = 12;
    private static final int MAX_VERTICAL_DISTANCE = 48;
    private static final int MAX_LEAF_BRIDGE_POSITIONS = 1536;
    private static final int MAX_LEAF_BRIDGE_DISTANCE = 8;
    private static final int LEAF_LOG_ATTACHMENT_RADIUS = 2;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final List<TagKey<Block>> LOG_FAMILY_TAGS = List.of(
            BlockTags.OAK_LOGS,
            BlockTags.SPRUCE_LOGS,
            BlockTags.BIRCH_LOGS,
            BlockTags.JUNGLE_LOGS,
            BlockTags.ACACIA_LOGS,
            BlockTags.DARK_OAK_LOGS,
            BlockTags.MANGROVE_LOGS,
            BlockTags.CHERRY_LOGS);

    private LoggingTreeGeometry() {
    }

    static DiscoveryCache discovery(ServerLevel level, Set<ResourceLocation> filters) {
        return new DiscoveryCache(level, filters == null ? Set.of() : Set.copyOf(filters));
    }

    static List<BlockPos> connectedTreeLogs(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters) {
        List<BlockPos> originComponent = connectedLogs(level, origin, filters);
        BlockPos root = treeRoot(originComponent);
        if (root == null) {
            return List.of();
        }
        List<BlockPos> logs = retainOriginTreeComponents(
                level,
                connectedLogs(level, root, filters, true),
                originComponent);
        if (!isLikelyNaturalTree(level, logs)) {
            return List.of();
        }
        return orderedTreeLogs(logs);
    }

    static List<BlockPos> naturalTreeLeaves(ServerLevel level, List<BlockPos> logs) {
        List<BlockPos> leaves = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Long> selectedLogs = new HashSet<>();
        logs.forEach(log -> selectedLogs.add(log.asLong()));
        for (BlockPos log : logs) {
            for (BlockPos rawPos : BlockPos.betweenClosed(log.offset(-3, -2, -3), log.offset(3, 4, 3))) {
                BlockPos pos = rawPos.immutable();
                if (seen.add(pos.asLong()) && level.hasChunkAt(pos) && isNaturalLeaf(level.getBlockState(pos))
                        && !isAttachedToExternalLog(level, pos, selectedLogs)) {
                    leaves.add(pos);
                    if (leaves.size() >= LoggingHarvestPlan.MAX_LEAVES) {
                        return leaves;
                    }
                }
            }
        }
        return leaves;
    }

    private static boolean isAttachedToExternalLog(
            ServerLevel level,
            BlockPos leaf,
            Set<Long> selectedLogs) {
        for (BlockPos rawPos : BlockPos.betweenClosed(leaf.offset(-3, -4, -3), leaf.offset(3, 2, 3))) {
            BlockPos pos = rawPos.immutable();
            if (selectedLogs.contains(pos.asLong()) || !level.hasChunkAt(pos)) {
                continue;
            }
            if (level.getBlockState(pos).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    static ItemStack saplingForTree(ServerLevel level, List<BlockPos> logs) {
        BlockPos root = treeRoot(logs);
        if (root != null && level.hasChunkAt(root)) {
            ItemStack sapling = saplingForLogState(level.getBlockState(root));
            if (!sapling.isEmpty()) {
                return sapling;
            }
        }
        for (BlockPos log : logs) {
            if (!level.hasChunkAt(log)) {
                continue;
            }
            ItemStack sapling = saplingForLogState(level.getBlockState(log));
            if (!sapling.isEmpty()) {
                return sapling;
            }
        }
        return ItemStack.EMPTY;
    }

    static List<BlockPos> saplingPlantingPositions(
            ServerLevel level,
            HiredWorkContext context,
            List<BlockPos> logs) {
        int minY = logs.stream().mapToInt(BlockPos::getY).min().orElse(Integer.MIN_VALUE);
        List<BlockPos> positions = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos log : logs) {
            if (log.getY() != minY || !context.isInsideWorkArea(log) || !context.isLoaded(level, log)) {
                continue;
            }
            BlockPos below = log.below();
            if (level.hasChunkAt(below)
                    && isNaturalTreeBase(level.getBlockState(below))
                    && seen.add(log.asLong())) {
                positions.add(log.immutable());
            }
        }
        return positions;
    }

    static BlockPos treeRoot(List<BlockPos> logs) {
        BlockPos root = null;
        for (BlockPos log : logs) {
            if (root == null
                    || log.getY() < root.getY()
                    || (log.getY() == root.getY() && log.asLong() < root.asLong())) {
                root = log;
            }
        }
        return root;
    }

    static boolean isMatchingLog(BlockState state, Set<ResourceLocation> filters) {
        return state.is(BlockTags.LOGS) && HiredLoggingFilters.matches(state, filters);
    }

    static String logFamilyKey(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        for (String suffix : List.of("_log", "_wood", "_stem", "_hyphae")) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return id.getNamespace() + ":" + path;
    }

    static boolean isNaturalLeaf(BlockState state) {
        return (state.is(BlockTags.LEAVES)
                && (!state.hasProperty(BlockStateProperties.PERSISTENT)
                || !state.getValue(BlockStateProperties.PERSISTENT)))
                || state.is(Blocks.NETHER_WART_BLOCK)
                || state.is(Blocks.WARPED_WART_BLOCK);
    }

    private static ItemStack saplingForLogState(BlockState state) {
        if (state.is(BlockTags.OAK_LOGS)) {
            return new ItemStack(Items.OAK_SAPLING);
        }
        if (state.is(BlockTags.SPRUCE_LOGS)) {
            return new ItemStack(Items.SPRUCE_SAPLING);
        }
        if (state.is(BlockTags.BIRCH_LOGS)) {
            return new ItemStack(Items.BIRCH_SAPLING);
        }
        if (state.is(BlockTags.JUNGLE_LOGS)) {
            return new ItemStack(Items.JUNGLE_SAPLING);
        }
        if (state.is(BlockTags.ACACIA_LOGS)) {
            return new ItemStack(Items.ACACIA_SAPLING);
        }
        if (state.is(BlockTags.DARK_OAK_LOGS)) {
            return new ItemStack(Items.DARK_OAK_SAPLING);
        }
        if (state.is(BlockTags.MANGROVE_LOGS)) {
            return new ItemStack(Items.MANGROVE_PROPAGULE);
        }
        if (state.is(BlockTags.CHERRY_LOGS)) {
            return new ItemStack(Items.CHERRY_SAPLING);
        }
        if (state.is(Blocks.CRIMSON_STEM) || state.is(Blocks.CRIMSON_HYPHAE)) {
            return new ItemStack(Items.CRIMSON_FUNGUS);
        }
        if (state.is(Blocks.WARPED_STEM) || state.is(Blocks.WARPED_HYPHAE)) {
            return new ItemStack(Items.WARPED_FUNGUS);
        }
        return ItemStack.EMPTY;
    }

    private static List<BlockPos> retainOriginTreeComponents(
            ServerLevel level,
            List<BlockPos> logs,
            List<BlockPos> originComponentLogs) {
        if (logs.isEmpty()) {
            return logs;
        }

        Set<Long> allLogs = new HashSet<>();
        for (BlockPos log : logs) {
            allLogs.add(log.asLong());
        }
        Set<Long> originComponent = new HashSet<>();
        for (BlockPos log : originComponentLogs) {
            originComponent.add(log.asLong());
        }

        List<BlockPos> retained = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (BlockPos seed : logs) {
            if (!visited.add(seed.asLong())) {
                continue;
            }
            List<BlockPos> component = new ArrayList<>();
            Queue<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            boolean belongsToOrigin = false;
            while (!queue.isEmpty()) {
                BlockPos current = queue.remove();
                component.add(current);
                belongsToOrigin |= originComponent.contains(current.asLong());
                for (BlockPos rawNeighbor : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                    BlockPos neighbor = rawNeighbor.immutable();
                    if (!neighbor.equals(current)
                            && allLogs.contains(neighbor.asLong())
                            && visited.add(neighbor.asLong())) {
                        queue.add(neighbor);
                    }
                }
            }
            if (belongsToOrigin || !hasRootedLog(level, component)) {
                retained.addAll(component);
            }
        }
        return retained;
    }

    private static List<BlockPos> connectedLogs(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters) {
        return connectedLogs(level, origin, filters, false);
    }

    private static List<BlockPos> connectedLogs(
            ServerLevel level,
            BlockPos origin,
            Set<ResourceLocation> filters,
            boolean includeLeafAttachedLogs) {
        if (!level.hasChunkAt(origin)) {
            return List.of();
        }
        BlockState originState = level.getBlockState(origin);
        if (!isMatchingLog(originState, filters)) {
            return List.of();
        }
        List<BlockPos> logs = new ArrayList<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Queue<LeafBridgeNode> leafQueue = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> visitedLeaves = new HashSet<>();
        queue.add(origin.immutable());
        visited.add(origin.asLong());

        while (logs.size() < LoggingHarvestPlan.MAX_LOGS
                && (!queue.isEmpty() || (includeLeafAttachedLogs && !leafQueue.isEmpty()))) {
            if (!queue.isEmpty()) {
                BlockPos current = queue.remove();
                if (!level.hasChunkAt(current)) {
                    continue;
                }
                BlockState state = level.getBlockState(current);
                if (!isMatchingTreeLog(state, filters, originState) || !isInsideTreeSearch(origin, current)) {
                    continue;
                }
                logs.add(current);
                enqueueNearbyTreeLogs(level, origin, current, filters, originState, visited, queue, 1);
                if (includeLeafAttachedLogs) {
                    enqueueNearbyTreeLeaves(level, origin, current, 0, visitedLeaves, leafQueue, LEAF_LOG_ATTACHMENT_RADIUS);
                }
                continue;
            }

            LeafBridgeNode node = leafQueue.remove();
            BlockPos leaf = node.pos();
            if (!level.hasChunkAt(leaf) || !isInsideTreeSearch(origin, leaf) || !isNaturalLeaf(level.getBlockState(leaf))) {
                continue;
            }
            enqueueNearbyTreeLogs(level, origin, leaf, filters, originState, visited, queue, LEAF_LOG_ATTACHMENT_RADIUS);
            if (node.distance() < MAX_LEAF_BRIDGE_DISTANCE) {
                enqueueNearbyTreeLeaves(level, origin, leaf, node.distance() + 1, visitedLeaves, leafQueue, 1);
            }
        }
        return logs;
    }

    private static void enqueueNearbyTreeLogs(
            ServerLevel level,
            BlockPos origin,
            BlockPos center,
            Set<ResourceLocation> filters,
            BlockState originState,
            Set<Long> visited,
            Queue<BlockPos> queue,
            int radius) {
        for (BlockPos rawPos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            BlockPos pos = rawPos.immutable();
            if (pos.equals(center)
                    || !isInsideTreeSearch(origin, pos)
                    || !visited.add(pos.asLong())
                    || !level.hasChunkAt(pos)
                    || !isMatchingTreeLog(level.getBlockState(pos), filters, originState)) {
                continue;
            }
            queue.add(pos);
        }
    }

    private static void enqueueNearbyTreeLeaves(
            ServerLevel level,
            BlockPos origin,
            BlockPos center,
            int distance,
            Set<Long> visitedLeaves,
            Queue<LeafBridgeNode> queue,
            int radius) {
        if (visitedLeaves.size() >= MAX_LEAF_BRIDGE_POSITIONS) {
            return;
        }
        for (BlockPos rawPos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (visitedLeaves.size() >= MAX_LEAF_BRIDGE_POSITIONS) {
                return;
            }
            BlockPos pos = rawPos.immutable();
            if (pos.equals(center)
                    || !isInsideTreeSearch(origin, pos)
                    || !level.hasChunkAt(pos)
                    || !isNaturalLeaf(level.getBlockState(pos))) {
                continue;
            }
            if (visitedLeaves.add(pos.asLong())) {
                queue.add(new LeafBridgeNode(pos, distance));
            }
        }
    }

    private static boolean isLikelyNaturalTree(ServerLevel level, List<BlockPos> logs) {
        return !logs.isEmpty()
                && hasRootedLog(level, logs)
                && naturalLeavesNearLogs(level, logs) >= MIN_NATURAL_LEAVES;
    }

    private static boolean isMatchingTreeLog(BlockState state, Set<ResourceLocation> filters, BlockState originState) {
        return isMatchingLog(state, filters) && sameLogFamily(originState, state);
    }

    private static boolean sameLogFamily(BlockState reference, BlockState candidate) {
        if (reference.is(candidate.getBlock())) {
            return true;
        }
        for (TagKey<Block> family : LOG_FAMILY_TAGS) {
            if (reference.is(family)) {
                return candidate.is(family);
            }
        }
        return logFamilyKey(reference).equals(logFamilyKey(candidate));
    }

    private static List<BlockPos> orderedTreeLogs(List<BlockPos> logs) {
        List<BlockPos> ordered = new ArrayList<>(logs);
        ordered.sort((left, right) -> {
            int y = Integer.compare(left.getY(), right.getY());
            return y != 0 ? y : Long.compare(left.asLong(), right.asLong());
        });
        return ordered;
    }

    private static boolean isInsideTreeSearch(BlockPos origin, BlockPos pos) {
        return Math.abs(origin.getX() - pos.getX()) <= MAX_HORIZONTAL_DISTANCE
                && Math.abs(origin.getZ() - pos.getZ()) <= MAX_HORIZONTAL_DISTANCE
                && Math.abs(origin.getY() - pos.getY()) <= MAX_VERTICAL_DISTANCE;
    }

    private static int naturalLeavesNearLogs(ServerLevel level, List<BlockPos> logs) {
        Set<Long> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos rawPos : BlockPos.betweenClosed(log.offset(-2, -1, -2), log.offset(2, 3, 2))) {
                BlockPos pos = rawPos.immutable();
                if (level.hasChunkAt(pos) && isNaturalLeaf(level.getBlockState(pos)) && leaves.add(pos.asLong())) {
                    if (leaves.size() >= MIN_NATURAL_LEAVES) {
                        return leaves.size();
                    }
                }
            }
        }
        return leaves.size();
    }

    private static boolean hasRootedLog(ServerLevel level, List<BlockPos> logs) {
        for (BlockPos log : logs) {
            BlockPos below = log.below();
            if (level.hasChunkAt(below)) {
                BlockState belowState = level.getBlockState(below);
                if (!belowState.is(BlockTags.LOGS) && isNaturalTreeBase(belowState)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNaturalTreeBase(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.CRIMSON_NYLIUM)
                || state.is(Blocks.WARPED_NYLIUM);
    }

    static final class DiscoveryCache {
        private static final Candidate NOT_A_TREE = new Candidate(null, false);

        private final ServerLevel level;
        private final Set<ResourceLocation> filters;
        private final Map<Long, Candidate> byLog = new HashMap<>();
        private final Map<Long, BlockPos> harvestRootByLog = new HashMap<>();
        private int analysisCount;

        private DiscoveryCache(ServerLevel level, Set<ResourceLocation> filters) {
            this.level = level;
            this.filters = filters;
        }

        boolean isNaturalTree(BlockPos pos) {
            return candidate(pos).naturalTree();
        }

        List<BlockPos> distinctRoots(Iterable<BlockPos> candidates) {
            Set<Long> seen = new LinkedHashSet<>();
            List<BlockPos> roots = new ArrayList<>();
            for (BlockPos candidatePos : candidates) {
                Candidate candidate = candidate(candidatePos);
                BlockPos root = candidate.naturalTree() ? harvestRoot(candidate) : null;
                if (root != null && seen.add(root.asLong())) {
                    roots.add(root);
                }
            }
            return roots;
        }

        int analysisCount() {
            return this.analysisCount;
        }

        private Candidate candidate(BlockPos pos) {
            Candidate cached = this.byLog.get(pos.asLong());
            if (cached != null) {
                return cached;
            }
            if (!this.level.hasChunkAt(pos)
                    || !isMatchingLog(this.level.getBlockState(pos), this.filters)) {
                this.byLog.put(pos.asLong(), NOT_A_TREE);
                return NOT_A_TREE;
            }

            List<BlockPos> logs = connectedLogs(this.level, pos, this.filters);
            Candidate discovered = new Candidate(treeRoot(logs), isLikelyNaturalTree(this.level, logs));
            this.analysisCount++;
            for (BlockPos log : logs) {
                this.byLog.putIfAbsent(log.asLong(), discovered);
            }
            this.byLog.putIfAbsent(pos.asLong(), discovered);
            return discovered;
        }

        private BlockPos harvestRoot(Candidate candidate) {
            if (candidate.root() == null) {
                return null;
            }
            BlockPos cached = this.harvestRootByLog.get(candidate.root().asLong());
            if (cached != null) {
                return cached;
            }
            List<BlockPos> harvestLogs = connectedTreeLogs(this.level, candidate.root(), this.filters);
            BlockPos root = treeRoot(harvestLogs);
            if (root == null) {
                return null;
            }
            for (BlockPos log : harvestLogs) {
                this.harvestRootByLog.put(log.asLong(), root);
            }
            return root;
        }
    }

    private record Candidate(BlockPos root, boolean naturalTree) {
    }

    private record LeafBridgeNode(BlockPos pos, int distance) {
    }
}
