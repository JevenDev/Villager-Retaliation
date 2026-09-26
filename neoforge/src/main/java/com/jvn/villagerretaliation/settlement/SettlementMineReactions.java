package com.jvn.villagerretaliation.settlement;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementMineReactions {
    private static final int UPDATES_PER_TICK = 64;
    private static final Map<LevelChunk, Pending> PENDING = new WeakHashMap<>();

    private SettlementMineReactions() {
    }

    static void queue(LevelChunk chunk, BlockPos position, BlockState before, BlockState after) {
        if (before == after) return;
        if (!inside(chunk, position)) throw new IllegalArgumentException("Mine reactions require their changed chunk");
        var data = SettlementChunkReceipts.mineUpdates(chunk, true);
        var pending = pending(chunk, data);
        var existing = pending.changes.get(position);
        var original = existing == null ? before : existing.before();
        var key = Long.toString(position.asLong());
        if (original == after) {
            pending.changes.remove(position);
            data.remove(key);
        } else {
            pending.changes.put(position.immutable(), new Change(original, after));
            var tag = new CompoundTag();
            tag.put("Before", NbtUtils.writeBlockState(original));
            tag.put("After", NbtUtils.writeBlockState(after));
            data.put(key, tag);
        }
        chunk.setUnsaved(true);
    }

    static void track(LevelChunk chunk) {
        var data = SettlementChunkReceipts.mineUpdates(chunk, false);
        if (!data.isEmpty()) pending(chunk, data);
    }

    static void forget(LevelChunk chunk) {
        PENDING.remove(chunk);
    }

    static void tick(net.minecraft.server.MinecraftServer server) {
        for (var chunk : java.util.List.copyOf(PENDING.keySet())) {
            if (chunk.getLevel() instanceof ServerLevel level && level.getServer() == server) flush(chunk);
        }
    }

    static void flush(LevelChunk chunk) {
        var data = SettlementChunkReceipts.mineUpdates(chunk, false);
        if (data.isEmpty()) {
            PENDING.remove(chunk);
            return;
        }
        if (!(chunk.getLevel() instanceof ServerLevel level)
                || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
        var pending = pending(chunk, data);
        int remaining = Math.min(UPDATES_PER_TICK, pending.changes.size());
        while (remaining-- > 0 && !pending.changes.isEmpty()) {
            var iterator = pending.changes.entrySet().iterator();
            var entry = iterator.next();
            var position = entry.getKey();
            var change = entry.getValue();
            iterator.remove();
            if (!neighborsLoaded(level, position)) {
                pending.changes.put(position, change);
                continue;
            }
            data.remove(Long.toString(position.asLong()));
            chunk.setUnsaved(true);
            if (chunk.getBlockState(position) != change.after()) continue;
            level.markAndNotifyBlock(position, chunk, change.before(), change.after(), Block.UPDATE_ALL, 512);
            com.jvn.villagerretaliation.interaction.work.HiredPathMemory.onBlockChanged(level, position);
            com.jvn.villagerretaliation.interaction.work.mining.HiredOreBlockTracker.onBlockBroken(level, position);
        }
        if (pending.changes.isEmpty()) PENDING.remove(chunk);
    }

    private static boolean neighborsLoaded(ServerLevel level, BlockPos position) {
        for (int x = position.getX() - 2 >> 4; x <= position.getX() + 2 >> 4; x++) {
            for (int z = position.getZ() - 2 >> 4; z <= position.getZ() + 2 >> 4; z++) {
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
            }
        }
        return true;
    }

    private static boolean inside(LevelChunk chunk, BlockPos position) {
        return position.getX() >> 4 == chunk.getPos().x && position.getZ() >> 4 == chunk.getPos().z
                && position.getY() >= chunk.getMinBuildHeight() && position.getY() < chunk.getMaxBuildHeight();
    }

    private static Pending pending(LevelChunk chunk, CompoundTag data) {
        var pending = PENDING.get(chunk);
        if (pending != null && pending.data == data) return pending;
        pending = new Pending(data);
        for (String key : data.getAllKeys()) {
            var position = BlockPos.of(Long.parseLong(key));
            if (!Long.toString(position.asLong()).equals(key) || !inside(chunk, position)) {
                throw new IllegalArgumentException("Saved mine reactions require a unique position inside their chunk");
            }
            var tag = data.getCompound(key);
            if (!tag.contains("Before", net.minecraft.nbt.Tag.TAG_COMPOUND) || !tag.contains("After", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                throw new IllegalArgumentException("Saved mine reactions require both block states");
            }
            var before = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Before"));
            var after = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("After"));
            pending.changes.put(position, new Change(before, after));
        }
        PENDING.put(chunk, pending);
        return pending;
    }

    private static final class Pending {
        private final CompoundTag data;
        private final Map<BlockPos, Change> changes = new LinkedHashMap<>();

        private Pending(CompoundTag data) {
            this.data = data;
        }
    }

    private record Change(BlockState before, BlockState after) {
    }
}
