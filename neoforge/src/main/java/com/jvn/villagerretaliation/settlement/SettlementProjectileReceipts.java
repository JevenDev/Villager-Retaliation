package com.jvn.villagerretaliation.settlement;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementProjectileReceipts {
    private static final Map<ServerLevel, Index> LEVELS = new WeakHashMap<>();

    private SettlementProjectileReceipts() {
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var index = LEVELS.computeIfAbsent(level, unused -> new Index());
        index.remove(chunk.getPos().toLong());
        for (String key : SettlementChunkReceipts.projectiles(chunk, false).getAllKeys()) index.record(chunk, UUID.fromString(key));
        if (index.chunks.isEmpty()) LEVELS.remove(level);
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var index = LEVELS.get(level);
        if (index == null) return;
        var entry = index.chunks.get(chunk.getPos().toLong());
        if (entry != null && entry.chunk().get() == chunk) index.remove(chunk.getPos().toLong());
        if (index.chunks.isEmpty()) LEVELS.remove(level);
    }

    static void record(LevelChunk chunk, UUID id) {
        if (chunk.getLevel() instanceof ServerLevel level) LEVELS.computeIfAbsent(level, unused -> new Index()).record(chunk, id);
    }

    static Set<Long> locations(ServerLevel level, UUID id) {
        var index = LEVELS.get(level);
        if (index == null) return Set.of();
        var locations = index.projectiles.get(id);
        return locations == null ? Set.of() : Set.copyOf(locations);
    }

    private static final class Index {
        private final Map<Long, ChunkReceipts> chunks = new HashMap<>();
        private final Map<UUID, Set<Long>> projectiles = new HashMap<>();

        private void record(LevelChunk chunk, UUID id) {
            long position = chunk.getPos().toLong();
            var entry = this.chunks.get(position);
            if (entry == null || entry.chunk().get() != chunk) {
                remove(position);
                entry = new ChunkReceipts(new WeakReference<>(chunk), new LinkedHashSet<>());
                this.chunks.put(position, entry);
            }
            entry.ids().add(id);
            this.projectiles.computeIfAbsent(id, unused -> new LinkedHashSet<>()).add(position);
        }

        private void remove(long position) {
            var entry = this.chunks.remove(position);
            if (entry == null) return;
            for (var id : entry.ids()) {
                var locations = this.projectiles.get(id);
                locations.remove(position);
                if (locations.isEmpty()) this.projectiles.remove(id);
            }
        }
    }

    private record ChunkReceipts(WeakReference<LevelChunk> chunk, Set<UUID> ids) {
    }
}
