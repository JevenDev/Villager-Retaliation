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
        var entry = chunk.getData(RECEIPTS).getCompound(Long.toString(position.asLong()));
        if (entry.hasUUID("Id")) {
            return entry.getUUID("Id");
        }
        UUID id = UUID.randomUUID();
        applied(chunk, position, id, 0L);
        return id;
    }

    static long version(LevelChunk chunk, BlockPos position, UUID id) {
        var entry = chunk.getData(RECEIPTS).getCompound(Long.toString(position.asLong()));
        return entry.hasUUID("Id") && entry.getUUID("Id").equals(id) ? entry.getLong("Version") : -1L;
    }

    static void applied(LevelChunk chunk, BlockPos position, UUID id, long version) {
        var entry = new CompoundTag();
        entry.putUUID("Id", id);
        entry.putLong("Version", version);
        chunk.getData(RECEIPTS).put(Long.toString(position.asLong()), entry);
        chunk.setUnsaved(true);
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
