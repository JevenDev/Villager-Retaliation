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

    public static ClipboardWorkAreaSyncPayload assigned(ResourceLocation dimension, BlockPos min, BlockPos max, BlockPos center, int ticks) {
        return new ClipboardWorkAreaSyncPayload(List.of(new ClipboardWorkAreaEntry(dimension, min, max, center, true, min, false, max, false)), ticks);
    }

    public static ClipboardWorkAreaSyncPayload assigned(
            ResourceLocation dimension,
            BlockPos min,
            BlockPos max,
            BlockPos center,
            String ownerName,
            String jobName,
            int ticks) {
        return new ClipboardWorkAreaSyncPayload(List.of(new ClipboardWorkAreaEntry(
                dimension, min, max, center, true, min, false, max, false, ownerName, jobName)), ticks);
    }

    public static ClipboardWorkAreaSyncPayload selection(ResourceLocation dimension, BlockPos first, BlockPos second, int ticks) {
        return new ClipboardWorkAreaSyncPayload(List.of(new ClipboardWorkAreaEntry(dimension, first, second, null, false, first, true, second, true)), ticks);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkAreaSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_ENTRIES, payload.entries().size()));
        for (ClipboardWorkAreaEntry entry : payload.entries()) {
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.min());
            buffer.writeBlockPos(entry.max());
            buffer.writeBlockPos(entry.center());
            buffer.writeBoolean(entry.showCenter());
            buffer.writeBlockPos(entry.firstCorner());
            buffer.writeBoolean(entry.showFirstCorner());
            buffer.writeBlockPos(entry.secondCorner());
            buffer.writeBoolean(entry.showSecondCorner());
            buffer.writeUtf(entry.ownerName(), 64);
            buffer.writeUtf(entry.jobName(), 64);
        }
        buffer.writeVarInt(payload.ticks());
    }

    private static ClipboardWorkAreaSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "clipboard work area entries");
        List<ClipboardWorkAreaEntry> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new ClipboardWorkAreaEntry(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(64),
                    buffer.readUtf(64)
            ));
        }
        return new ClipboardWorkAreaSyncPayload(entries, buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
