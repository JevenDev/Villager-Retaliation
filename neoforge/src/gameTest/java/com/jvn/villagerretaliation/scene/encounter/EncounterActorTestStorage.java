package com.jvn.villagerretaliation.scene.encounter;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class EncounterActorTestStorage implements AutoCloseable {
    private final Map<LevelChunk, EncounterActorBirths.Ledger> originals = new LinkedHashMap<>();

    public EncounterActorTestStorage(ServerLevel level, BlockPos center, int radius) {
        for (int x = (center.getX() - radius) >> 4; x <= (center.getX() + radius) >> 4; x++)
            for (int z = (center.getZ() - radius) >> 4; z <= (center.getZ() + radius) >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) continue;
                originals.put(chunk, chunk.getExistingDataOrNull(EncounterActorBirths.BIRTHS.get()));
                chunk.setData(EncounterActorBirths.BIRTHS, new EncounterActorBirths.Ledger());
            }
    }

    @Override public void close() {
        originals.forEach((chunk, original) -> {
            if (original == null) chunk.removeData(EncounterActorBirths.BIRTHS);
            else chunk.setData(EncounterActorBirths.BIRTHS, original);
        });
    }
}
