package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class SettlementChunkReceipts {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    private static final Supplier<AttachmentType<CompoundTag>> RECEIPTS = TYPES.register("settlement_terrain",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());

    private SettlementChunkReceipts() {
    }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }

    static UUID identity(LevelChunk chunk, BlockPos position) {
        return identity(chunk, Long.toString(position.asLong()));
    }

    static UUID identity(LevelChunk chunk, String key) {
        var entry = chunk.getData(RECEIPTS).getCompound(key);
        if (entry.hasUUID("Id")) {
            return entry.getUUID("Id");
        }
        UUID id = UUID.randomUUID();
        applied(chunk, key, id, 0L);
        return id;
    }

    static long version(LevelChunk chunk, BlockPos position, UUID id) {
        return version(chunk, Long.toString(position.asLong()), id);
    }

    static long version(LevelChunk chunk, String key, UUID id) {
        var entry = chunk.getData(RECEIPTS).getCompound(key);
        return entry.hasUUID("Id") && entry.getUUID("Id").equals(id) ? entry.getLong("Version") : -1L;
    }

    static void applied(LevelChunk chunk, BlockPos position, UUID id, long version) {
        applied(chunk, Long.toString(position.asLong()), id, version);
    }

    static void applied(LevelChunk chunk, String key, UUID id, long version) {
        var entry = new CompoundTag();
        entry.putUUID("Id", id);
        entry.putLong("Version", version);
        chunk.getData(RECEIPTS).put(key, entry);
        chunk.setUnsaved(true);
    }

    static CompoundTag mineUpdates(LevelChunk chunk, boolean create) {
        if (!create && !chunk.hasData(RECEIPTS)) return new CompoundTag();
        var data = chunk.getData(RECEIPTS);
        if (create && !data.contains("MineUpdates", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            data.put("MineUpdates", new CompoundTag());
        }
        return data.getCompound("MineUpdates");
    }

    static CompoundTag terrainDrops(LevelChunk chunk, boolean create) {
        if (!create && !chunk.hasData(RECEIPTS)) return new CompoundTag();
        var data = chunk.getData(RECEIPTS);
        if (create && !data.contains("TerrainDrops", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            data.put("TerrainDrops", new CompoundTag());
        }
        return data.getCompound("TerrainDrops");
    }

    static long lastTick(LevelChunk chunk) {
        return chunk.hasData(RECEIPTS) ? chunk.getData(RECEIPTS).getLong("LastTick") : 0L;
    }

    public static void onVanillaTick(LevelChunk chunk, long now) {
        if (chunk.hasData(RECEIPTS)) {
            chunk.getData(RECEIPTS).putLong("LastTick", now);
        }
    }
}
