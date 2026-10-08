package com.jvn.villagerretaliation.util;

import com.jvn.villagerretaliation.mixin.ChunkStorageAccessor;
import com.jvn.villagerretaliation.mixin.IOWorkerAccessor;
import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.util.concurrent.CompletionException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;

public final class WorldChunkCheckpoint {
    private WorldChunkCheckpoint() { }

    public static void persist(ServerLevel level, BlockPos position) throws IOException {
        persist(level, position, snapshot -> true);
    }

    public static void persist(ServerLevel level, BlockPos position, java.util.function.Predicate<net.minecraft.nbt.CompoundTag> verify) throws IOException {
        if (!level.getServer().isSameThread()) throw new IOException("World chunk checkpoints require the server thread.");
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) throw new IOException("The world chunk must finish loading before it can be saved.");
        var storage = level.getChunkSource().chunkMap;
        var snapshot = ChunkSerializer.write(level, chunk);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, snapshot));
        if (!verify.test(snapshot)) throw new IOException("Native chunk serialization omitted or changed required campaign block data.");
        try {
            storage.write(chunk.getPos(), snapshot).thenCompose(ignored -> {
                var worker = (IOWorkerAccessor) ((ChunkStorageAccessor) storage).villagerretaliation$worker();
                return worker.<Void>villagerretaliation$submitTask(() -> {
                    try {
                        worker.villagerretaliation$storage().flush();
                        return Either.left(null);
                    } catch (IOException failure) {
                        return Either.right(failure);
                    }
                });
            }).join();
        } catch (CompletionException failure) {
            throw new IOException("The world chunk could not be written and flushed.", failure.getCause());
        }
    }
}
