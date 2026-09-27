package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.compat.sodium.SodiumSettlementCompat;
import com.jvn.villagerretaliation.network.SettlementViewChunkPayload;
import com.jvn.villagerretaliation.settlement.SettlementViewWindow;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

public final class SettlementViewChunks {
    private static final Map<ChunkPos, LevelChunk> CHUNKS = new ConcurrentHashMap<>();
    private static ClientLevel level;
    private static UUID session;
    private static Set<ChunkPos> allowed = Set.of();
    private static boolean reading;

    private SettlementViewChunks() {
    }

    static void begin(ClientLevel currentLevel, UUID id, SettlementViewWindow window) {
        clear();
        level = currentLevel;
        session = id;
        update(window);
    }

    static void update(SettlementViewWindow window) {
        allowed = Set.copyOf(window.chunks());
        for (ChunkPos position : List.copyOf(CHUNKS.keySet())) {
            if (!allowed.contains(position)) {
                remove(position);
            }
        }
    }

    public static void accept(SettlementViewChunkPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ChunkPos position = new ChunkPos(payload.chunk().getX(), payload.chunk().getZ());
        if (!payload.session().equals(session) || level != minecraft.level || !allowed.contains(position)
                || minecraft.getConnection() == null) {
            return;
        }
        reading = true;
        try {
            payload.chunk().handle(minecraft.getConnection());
        } finally {
            reading = false;
        }
    }

    public static LevelChunk get(ClientLevel currentLevel, int x, int z) {
        return currentLevel == level ? CHUNKS.get(new ChunkPos(x, z)) : null;
    }

    public static LevelChunk replace(ClientLevel currentLevel, int x, int z, FriendlyByteBuf buffer,
                                     CompoundTag heightmaps, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities) {
        ChunkPos position = new ChunkPos(x, z);
        if (!reading || currentLevel != level || !allowed.contains(position)) {
            return null;
        }
        LevelChunk existing = currentLevel.getChunkSource().getChunk(x, z, false);
        LevelChunk remote = CHUNKS.get(position);
        if (existing != null && existing != remote) {
            return null;
        }
        if (remote == null) {
            remote = new LevelChunk(currentLevel, position);
            CHUNKS.put(position, remote);
        }
        remote.replaceWithPacketData(buffer, heightmaps, blockEntities);
        currentLevel.onChunkLoaded(position);
        // remote chunks bypass sodium's hook inside the vanilla chunk cache
        SodiumSettlementCompat.onChunkLoaded(currentLevel, position);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(remote, false));
        return remote;
    }

    public static void replacedNormally(ClientLevel currentLevel, LevelChunk chunk) {
        if (currentLevel != level || chunk == null || CHUNKS.get(chunk.getPos()) == chunk) {
            return;
        }
        LevelChunk previous = CHUNKS.remove(chunk.getPos());
        if (previous != null) {
            previous.clearAllBlockEntities();
        }
    }

    static void clear() {
        for (ChunkPos position : List.copyOf(CHUNKS.keySet())) {
            remove(position);
        }
        session = null;
        level = null;
        allowed = Set.of();
    }

    private static void remove(ChunkPos position) {
        LevelChunk chunk = CHUNKS.remove(position);
        ClientLevel currentLevel = level;
        if (chunk == null || currentLevel == null) {
            return;
        }
        if (currentLevel.getChunkSource().getChunk(position.x, position.z, false) != null) {
            chunk.clearAllBlockEntities();
            return;
        }
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
        currentLevel.unload(chunk);
        currentLevel.queueLightUpdate(() -> {
            if (currentLevel.getChunkSource().getChunk(position.x, position.z, false) != null) {
                return;
            }
            var light = currentLevel.getLightEngine();
            light.setLightEnabled(position, false);
            for (int section = light.getMinLightSection(); section < light.getMaxLightSection(); section++) {
                var sectionPosition = SectionPos.of(position, section);
                light.queueSectionData(LightLayer.BLOCK, sectionPosition, null);
                light.queueSectionData(LightLayer.SKY, sectionPosition, null);
            }
            for (int section = currentLevel.getMinSection(); section < currentLevel.getMaxSection(); section++) {
                light.updateSectionStatus(SectionPos.of(position, section), true);
                currentLevel.setSectionDirtyWithNeighbors(position.x, section, position.z);
            }
        });
    }
}
