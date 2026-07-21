package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClipboardAssignedStorageSyncPayload(List<Entry> entries, int ticks) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 32;
    public static final Type<ClipboardAssignedStorageSyncPayload> TYPE = VillagerPayloads.type("clipboard_assigned_storage_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardAssignedStorageSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardAssignedStorageSyncPayload::encode, ClipboardAssignedStorageSyncPayload::decode);

    public ClipboardAssignedStorageSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_ENTRIES).toList());
        ticks = Math.max(0, ticks);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardAssignedStorageSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (Entry entry : payload.entries()) {
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.pos());
            buffer.writeBoolean(entry.payment());
            buffer.writeUtf(entry.ownerName(), 64);
            buffer.writeUtf(entry.storageType(), 64);
        }
        buffer.writeVarInt(payload.ticks());
    }

    private static ClipboardAssignedStorageSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "clipboard assigned storage entries");
        List<Entry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new Entry(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(64),
                    buffer.readUtf(64)));
        }
        return new ClipboardAssignedStorageSyncPayload(entries, buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(ResourceLocation dimension, BlockPos pos, boolean payment, String ownerName, String storageType) {
        public Entry {
            ownerName = ownerName == null ? "" : ownerName;
            storageType = storageType == null ? "" : storageType;
        }

        public Entry(ResourceLocation dimension, BlockPos pos, boolean payment) {
            this(dimension, pos, payment, "", "");
        }
    }
}
