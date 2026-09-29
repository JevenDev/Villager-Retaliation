package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.mixin.ChunkStorageAccessor;
import com.jvn.villagerretaliation.mixin.IOWorkerAccessor;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

final class SettlementChunkCheckpoint {
    private static final Map<LevelChunk, State> CHUNKS = new WeakHashMap<>();
    private static final Map<ServerLevel, Queue> QUEUES = new WeakHashMap<>();

    private SettlementChunkCheckpoint() { }

    static long revision(LevelChunk chunk) {
        return CHUNKS.computeIfAbsent(chunk, ignored -> new State()).revision;
    }

    static void changed(LevelChunk chunk) {
        var state = CHUNKS.computeIfAbsent(chunk, ignored -> new State());
        state.revision = Math.incrementExact(state.revision);
    }

    static boolean durable(ServerLevel level, LevelChunk chunk, long revision) {
        var state = CHUNKS.computeIfAbsent(chunk, ignored -> new State());
        if (state.durable >= revision) return true;
        var queue = QUEUES.computeIfAbsent(level, ignored -> new Queue());
        long now = Integer.toUnsignedLong(level.getServer().getTickCount());
        if (now >= state.retryAt) queue.waiting.putIfAbsent(chunk.getPos(), new WeakReference<>(chunk));
        pump(level, queue, now);
        return false;
    }

    private static void pump(ServerLevel level, Queue queue, long now) {
        if (queue.pending || now < queue.nextAt) return;
        LevelChunk chunk = null;
        var iterator = queue.waiting.entrySet().iterator();
        for (int visited = 0; iterator.hasNext() && visited < 8; visited++) {
            var entry = iterator.next();
            iterator.remove();
            var candidate = entry.getValue().get();
            if (candidate == null || level.getChunkSource().getChunkNow(entry.getKey().x, entry.getKey().z) != candidate) continue;
            var candidateState = CHUNKS.get(candidate);
            if (candidateState == null || candidateState.durable >= candidateState.revision || now < candidateState.retryAt) continue;
            chunk = candidate;
            break;
        }
        if (chunk == null) return;
        var state = CHUNKS.get(chunk);
        var position = chunk.getPos();
        queue.pending = true;
        queue.nextAt = now + 1;
        long captured = state.revision;
        try {
            var storage = level.getChunkSource().chunkMap;
            var snapshot = ChunkSerializer.write(level, chunk);
            if (!SettlementChunkReceipts.includedIn(chunk, snapshot)) throw new IllegalStateException("Chunk serialization omitted settlement receipts");
            // retirement waits for region storage to flush, never just serialization or a clean dirty flag
            storage.write(chunk.getPos(), snapshot)
                    .thenCompose(ignored -> {
                        var worker = (IOWorkerAccessor) ((ChunkStorageAccessor) storage).villagerretaliation$worker();
                        return worker.<Void>villagerretaliation$submitTask(() -> {
                            try {
                                worker.villagerretaliation$storage().flush();
                                return Either.left(null);
                            } catch (java.io.IOException failure) {
                                return Either.right(failure);
                            }
                        });
                    })
                    .whenComplete((ignored, failure) -> level.getServer().execute(() -> {
                        queue.pending = false;
                        if (failure == null) state.durable = Math.max(state.durable, captured);
                        else {
                            state.retryAt = Integer.toUnsignedLong(level.getServer().getTickCount()) + 200;
                            LogUtils.getLogger().error("Settlement construction checkpoint failed at {}. Pending world work is retained.", position, failure);
                        }
                    }));
        } catch (RuntimeException failure) {
            queue.pending = false;
            state.retryAt = now + 200;
            LogUtils.getLogger().error("Settlement construction checkpoint could not be queued at {}. Pending world work is retained.", position, failure);
        }
    }

    private static final class State {
        private long revision;
        private long durable;
        private long retryAt;
    }

    private static final class Queue {
        private final Map<ChunkPos, WeakReference<LevelChunk>> waiting = new LinkedHashMap<>();
        private boolean pending;
        private long nextAt;
    }
}
