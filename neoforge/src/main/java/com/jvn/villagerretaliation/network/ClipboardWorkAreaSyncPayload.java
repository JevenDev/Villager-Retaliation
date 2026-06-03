package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClipboardWorkAreaSyncPayload(List<ClipboardWorkAreaEntry> entries, int ticks) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 16;
    public static final Type<ClipboardWorkAreaSyncPayload> TYPE = VillagerPayloads.type("clipboard_work_area_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardWorkAreaSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardWorkAreaSyncPayload::encode, ClipboardWorkAreaSyncPayload::decode);

    public ClipboardWorkAreaSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_ENTRIES).toList());
        ticks = Math.max(0, ticks);
    }

    public static ClipboardWorkAreaSyncPayload single(ResourceLocation dimension, BlockPos min, BlockPos max, int ticks) {
        return new ClipboardWorkAreaSyncPayload(List.of(new ClipboardWorkAreaEntry(dimension, min, max)), ticks);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkAreaSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (ClipboardWorkAreaEntry entry : payload.entries()) {
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.min());
            buffer.writeBlockPos(entry.max());
        }
        buffer.writeVarInt(payload.ticks());
    }

    private static ClipboardWorkAreaSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = Math.min(MAX_ENTRIES, buffer.readVarInt());
        List<ClipboardWorkAreaEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new ClipboardWorkAreaEntry(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos()
            ));
        }
        return new ClipboardWorkAreaSyncPayload(entries, buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
