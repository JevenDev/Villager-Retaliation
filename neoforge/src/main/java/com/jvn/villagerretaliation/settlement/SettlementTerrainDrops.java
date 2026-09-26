package com.jvn.villagerretaliation.settlement;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;

final class SettlementTerrainDrops {
    private static final Map<LevelChunk, Boolean> PENDING = new WeakHashMap<>();

    private SettlementTerrainDrops() {
    }

    static void queue(LevelChunk chunk, SettlementTerrainDrop drop) {
        if (!chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(drop.position()))) {
            throw new IllegalArgumentException("Terrain drops require their changed chunk");
        }
        SettlementChunkReceipts.terrainDrops(chunk, true).put(drop.id().toString(), drop.save());
        chunk.setUnsaved(true);
        track(chunk);
    }

    static void track(LevelChunk chunk) {
        if (!SettlementChunkReceipts.terrainDrops(chunk, false).isEmpty()) PENDING.put(chunk, true);
    }

    static void forget(LevelChunk chunk) {
        PENDING.remove(chunk);
    }

    static void tick(MinecraftServer server) {
        for (var chunk : List.copyOf(PENDING.keySet())) {
            if (chunk.getLevel() instanceof ServerLevel level && level.getServer() == server) flush(chunk);
        }
    }

    static void flush(LevelChunk chunk) {
        if (!(chunk.getLevel() instanceof ServerLevel level)
                || level.getChunkSource().getChunkNow(chunk.getPos().x, chunk.getPos().z) != chunk) return;
        var data = SettlementChunkReceipts.terrainDrops(chunk, false);
        int remaining = 64;
        for (String key : List.copyOf(data.getAllKeys())) {
            if (remaining-- <= 0) break;
            var drop = SettlementTerrainDrop.load(data.getCompound(key));
            if (!key.equals(drop.id().toString()) || !chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(drop.position()))) {
                throw new IllegalArgumentException("Saved terrain drops require their identity and chunk");
            }
            if (!level.isPositionEntityTicking(drop.position())) continue;
            var existing = level.getEntity(drop.id());
            if (existing == null) {
                var stack = ItemStack.parseOptional(level.registryAccess(), drop.item());
                if (stack.isEmpty()) throw new IllegalArgumentException("Saved terrain drop item cannot be restored");
                var position = drop.position().getCenter();
                var entity = new ItemEntity(level, position.x, position.y, position.z, stack);
                entity.setUUID(drop.id());
                entity.setDefaultPickUpDelay();
                if (!level.addFreshEntity(entity)) continue;
            }
            data.remove(key);
            chunk.setUnsaved(true);
        }
        if (data.isEmpty()) PENDING.remove(chunk);
    }
}
