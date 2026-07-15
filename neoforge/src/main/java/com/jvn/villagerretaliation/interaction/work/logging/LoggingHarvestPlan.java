package com.jvn.villagerretaliation.interaction.work.logging;

import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Persistent description of a tree harvest that has already started.
 *
 * <p>The plan is intentionally independent of the current logging options and filters. Those
 * settings may change while a villager is walking, while the villager is unloaded, or between
 * individual blocks of a large tree. Once a tree is selected, finishing that bounded plan is the
 * only way to avoid leaving half-cut trees or switching species midway through a harvest.</p>
 */
final class LoggingHarvestPlan {
    static final int MAX_LOGS = 256;
    static final int MAX_LEAVES = 384;
    static final int MAX_SAPLINGS = 256;

    private static final String ORIGIN_TAG = "PendingLoggingTreeOrigin";
    private static final String LOGS_TAG = "PendingLoggingTreeLogs";
    private static final String LEAVES_TAG = "PendingLoggingTreeLeaves";
    private static final String SAPLINGS_TAG = "PendingLoggingTreeSaplings";
    private static final String SAPLING_ITEM_TAG = "PendingLoggingTreeSaplingItem";
    private static final String STRIP_LOGS_TAG = "PendingLoggingTreeStripLogs";
    private static final String LOG_FAMILY_TAG = "PendingLoggingTreeLogFamily";
    private static final String LOGS_CUT_TAG = "PendingLoggingTreeLogsCut";
    private static final String VERSION_TAG = "PendingLoggingTreePlanVersion";
    private static final String WORK_MIN_TAG = "PendingLoggingTreeWorkMin";
    private static final String WORK_MAX_TAG = "PendingLoggingTreeWorkMax";
    private static final int VERSION = 1;

    private LoggingHarvestPlan() {
    }

    static void begin(
            HiredWorkContext context,
            BlockPos origin,
            List<BlockPos> logs,
            List<BlockPos> leaves,
            List<BlockPos> saplingPositions,
            ItemStack sapling,
            boolean stripLogs,
            String logFamily) {
        clear(context);
        CompoundTag state = context.state();
        state.putLong(ORIGIN_TAG, origin.asLong());
        state.putLongArray(LOGS_TAG, positionsToArray(logs, MAX_LOGS));
        state.putLongArray(LEAVES_TAG, positionsToArray(leaves, MAX_LEAVES));
        state.putLongArray(SAPLINGS_TAG, positionsToArray(saplingPositions, MAX_SAPLINGS));
        state.putBoolean(STRIP_LOGS_TAG, stripLogs);
        state.putInt(LOGS_CUT_TAG, 0);
        if (logFamily != null && !logFamily.isBlank()) {
            state.putString(LOG_FAMILY_TAG, logFamily);
        }
        if (!sapling.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(sapling.getItem());
            state.putString(SAPLING_ITEM_TAG, itemId.toString());
        }
        rememberConfiguration(context);
        read(context);
    }

    /**
     * Reads and normalizes persisted state. Invalid entries, duplicates, and entries outside the
     * current work assignment are removed. An empty or incomplete plan is cleared atomically.
     */
    static Snapshot read(HiredWorkContext context) {
        CompoundTag state = context.state();
        if (!state.contains(ORIGIN_TAG)
                && !state.contains(LOGS_TAG)
                && !state.contains(LEAVES_TAG)
                && !state.contains(SAPLINGS_TAG)
                && !state.contains(LOGS_CUT_TAG)) {
            return null;
        }
        if (!context.hasWorkArea()) {
            clear(context);
            return null;
        }
        boolean normalize = needsNormalization(context, state);
        long[] logs = normalize
                ? normalizedPositions(context, state, LOGS_TAG, MAX_LOGS)
                : state.getLongArray(LOGS_TAG);
        long[] leaves = normalize
                ? normalizedPositions(context, state, LEAVES_TAG, MAX_LEAVES)
                : state.getLongArray(LEAVES_TAG);
        long[] saplings = normalize
                ? normalizedPositions(context, state, SAPLINGS_TAG, MAX_SAPLINGS)
                : state.getLongArray(SAPLINGS_TAG);
        ItemStack sapling = saplings.length == 0 ? ItemStack.EMPTY : readSapling(state);
        if (sapling.isEmpty() && saplings.length > 0) {
            saplings = new long[0];
            state.remove(SAPLINGS_TAG);
        }
        int logsCut = Math.clamp(state.getInt(LOGS_CUT_TAG), 0, MAX_LOGS);
        boolean completedWork = logsCut > 0 && state.contains(ORIGIN_TAG, Tag.TAG_LONG);
        if (logs.length == 0 && leaves.length == 0 && saplings.length == 0 && !completedWork) {
            clear(context);
            return null;
        }

        BlockPos origin = state.contains(ORIGIN_TAG, Tag.TAG_LONG)
                ? BlockPos.of(state.getLong(ORIGIN_TAG))
                : null;
        if (origin == null || !context.isInsideWorkArea(origin)) {
            if (logs.length == 0 && leaves.length == 0 && saplings.length == 0) {
                clear(context);
                return null;
            }
            origin = firstPosition(logs, leaves, saplings);
            state.putLong(ORIGIN_TAG, origin.asLong());
        }
        if (logsCut != state.getInt(LOGS_CUT_TAG)) {
            state.putInt(LOGS_CUT_TAG, logsCut);
        }
        if (normalize) {
            rememberConfiguration(context);
        }
        return new Snapshot(
                origin,
                logs,
                leaves,
                saplings,
                sapling,
                state.getBoolean(STRIP_LOGS_TAG),
                state.getString(LOG_FAMILY_TAG),
                logsCut);
    }

    static boolean has(HiredWorkContext context) {
        return read(context) != null;
    }

    static void rememberLogFamily(HiredWorkContext context, String logFamily) {
        if (logFamily == null || logFamily.isBlank()) {
            return;
        }
        context.state().putString(LOG_FAMILY_TAG, logFamily);
    }

    static void replaceLogs(HiredWorkContext context, long[] positions) {
        putPositions(context.state(), LOGS_TAG, positions);
    }

    static void removeLeaf(HiredWorkContext context, BlockPos pos) {
        removePosition(context.state(), LEAVES_TAG, pos);
    }

    static void removeSapling(HiredWorkContext context, BlockPos pos) {
        removePosition(context.state(), SAPLINGS_TAG, pos);
    }

    static void clearLeaves(HiredWorkContext context) {
        context.state().remove(LEAVES_TAG);
    }

    static void clearSaplings(HiredWorkContext context) {
        context.state().remove(SAPLINGS_TAG);
    }

    static int incrementLogsCut(HiredWorkContext context) {
        int next = Math.min(MAX_LOGS, context.state().getInt(LOGS_CUT_TAG) + 1);
        context.state().putInt(LOGS_CUT_TAG, next);
        return next;
    }

    static boolean contains(long[] packedPositions, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        long packedPos = pos.asLong();
        for (long packed : packedPositions) {
            if (packed == packedPos) {
                return true;
            }
        }
        return false;
    }

    static void clear(HiredWorkContext context) {
        CompoundTag state = context.state();
        state.remove(ORIGIN_TAG);
        state.remove(LOGS_TAG);
        state.remove(LEAVES_TAG);
        state.remove(SAPLINGS_TAG);
        state.remove(SAPLING_ITEM_TAG);
        state.remove(STRIP_LOGS_TAG);
        state.remove(LOG_FAMILY_TAG);
        state.remove(LOGS_CUT_TAG);
        state.remove(VERSION_TAG);
        state.remove(WORK_MIN_TAG);
        state.remove(WORK_MAX_TAG);
    }

    private static boolean needsNormalization(HiredWorkContext context, CompoundTag state) {
        return state.getInt(VERSION_TAG) != VERSION
                || !state.contains(WORK_MIN_TAG, Tag.TAG_LONG)
                || state.getLong(WORK_MIN_TAG) != context.workMin().asLong()
                || !state.contains(WORK_MAX_TAG, Tag.TAG_LONG)
                || state.getLong(WORK_MAX_TAG) != context.workMax().asLong()
                || invalidArray(state, LOGS_TAG, MAX_LOGS)
                || invalidArray(state, LEAVES_TAG, MAX_LEAVES)
                || invalidArray(state, SAPLINGS_TAG, MAX_SAPLINGS);
    }

    private static boolean invalidArray(CompoundTag state, String tag, int maxPositions) {
        return state.contains(tag)
                && (!state.contains(tag, Tag.TAG_LONG_ARRAY) || state.getLongArray(tag).length > maxPositions);
    }

    private static void rememberConfiguration(HiredWorkContext context) {
        CompoundTag state = context.state();
        state.putInt(VERSION_TAG, VERSION);
        state.putLong(WORK_MIN_TAG, context.workMin().asLong());
        state.putLong(WORK_MAX_TAG, context.workMax().asLong());
    }

    private static long[] normalizedPositions(
            HiredWorkContext context,
            CompoundTag state,
            String tag,
            int maxPositions) {
        if (!state.contains(tag, Tag.TAG_LONG_ARRAY)) {
            state.remove(tag);
            return new long[0];
        }
        long[] stored = state.getLongArray(tag);
        long[] normalized = new long[Math.min(stored.length, maxPositions)];
        Set<Long> seen = new HashSet<>();
        int size = 0;
        for (long packed : stored) {
            BlockPos pos = BlockPos.of(packed);
            if (!context.isInsideWorkArea(pos) || !seen.add(packed)) {
                continue;
            }
            normalized[size++] = packed;
            if (size >= maxPositions) {
                break;
            }
        }
        long[] result = java.util.Arrays.copyOf(normalized, size);
        if (!java.util.Arrays.equals(stored, result)) {
            putPositions(state, tag, result);
        }
        return result;
    }

    private static ItemStack readSapling(CompoundTag state) {
        if (!state.contains(SAPLING_ITEM_TAG, Tag.TAG_STRING)) {
            state.remove(SAPLING_ITEM_TAG);
            return ItemStack.EMPTY;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(state.getString(SAPLING_ITEM_TAG));
        if (itemId == null) {
            state.remove(SAPLING_ITEM_TAG);
            return ItemStack.EMPTY;
        }
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(itemId)
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        if (stack.isEmpty()) {
            state.remove(SAPLING_ITEM_TAG);
        }
        return stack;
    }

    private static BlockPos firstPosition(long[] logs, long[] leaves, long[] saplings) {
        if (logs.length > 0) {
            return BlockPos.of(logs[0]);
        }
        if (leaves.length > 0) {
            return BlockPos.of(leaves[0]);
        }
        return BlockPos.of(saplings[0]);
    }

    private static long[] positionsToArray(List<BlockPos> positions, int maxPositions) {
        List<Long> packed = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (BlockPos pos : positions) {
            if (pos != null && seen.add(pos.asLong())) {
                packed.add(pos.asLong());
                if (packed.size() >= maxPositions) {
                    break;
                }
            }
        }
        long[] result = new long[packed.size()];
        for (int i = 0; i < packed.size(); i++) {
            result[i] = packed.get(i);
        }
        return result;
    }

    private static void removePosition(CompoundTag state, String tag, BlockPos pos) {
        if (pos == null || !state.contains(tag, Tag.TAG_LONG_ARRAY)) {
            return;
        }
        long packedPos = pos.asLong();
        long[] stored = state.getLongArray(tag);
        long[] retained = java.util.Arrays.stream(stored)
                .filter(packed -> packed != packedPos)
                .toArray();
        putPositions(state, tag, retained);
    }

    private static void putPositions(CompoundTag state, String tag, long[] positions) {
        if (positions == null || positions.length == 0) {
            state.remove(tag);
        } else {
            state.putLongArray(tag, positions);
        }
    }

    record Snapshot(
            BlockPos origin,
            long[] logs,
            long[] leaves,
            long[] saplings,
            ItemStack sapling,
            boolean stripLogs,
            String logFamily,
            int logsCut) {
        boolean hasLogs() {
            return this.logs.length > 0;
        }

        boolean hasLeaves() {
            return this.leaves.length > 0;
        }
    }
}
