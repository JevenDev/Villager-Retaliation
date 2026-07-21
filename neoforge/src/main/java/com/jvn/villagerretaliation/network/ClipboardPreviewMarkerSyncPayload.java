package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.ClipboardWorkforceSnapshot.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClipboardPreviewMarkerSyncPayload(List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 256;
    private static final int LABEL_LENGTH = 64;
    public static final Type<ClipboardPreviewMarkerSyncPayload> TYPE = VillagerPayloads.type("clipboard_preview_marker_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPreviewMarkerSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardPreviewMarkerSyncPayload::encode, ClipboardPreviewMarkerSyncPayload::decode);

    public ClipboardPreviewMarkerSyncPayload {
        entries = entries == null ? List.of() : List.copyOf(entries.stream().limit(MAX_ENTRIES).toList());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardPreviewMarkerSyncPayload payload) {
        buffer.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries()) {
            buffer.writeUUID(entry.villagerId());
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.position());
            buffer.writeBoolean(entry.target() != null);
            if (entry.target() != null) {
                buffer.writeBlockPos(entry.target());
            }
            buffer.writeUtf(entry.ownerName(), LABEL_LENGTH);
            buffer.writeUtf(entry.jobName(), LABEL_LENGTH);
            buffer.writeEnum(entry.status());
        }
    }

    private static ClipboardPreviewMarkerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = VillagerPayloads.readCollectionSize(buffer, MAX_ENTRIES, "clipboard preview markers");
        List<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID villagerId = buffer.readUUID();
            ResourceLocation dimension = buffer.readResourceLocation();
            BlockPos position = buffer.readBlockPos();
            BlockPos target = buffer.readBoolean() ? buffer.readBlockPos() : null;
            entries.add(new Entry(
                    villagerId,
                    dimension,
                    position,
                    target,
                    buffer.readUtf(LABEL_LENGTH),
                    buffer.readUtf(LABEL_LENGTH),
                    buffer.readEnum(WorkerStatus.class)));
        }
        return new ClipboardPreviewMarkerSyncPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            UUID villagerId,
            ResourceLocation dimension,
            BlockPos position,
            BlockPos target,
            String ownerName,
            String jobName,
            WorkerStatus status) {
        public Entry {
            ownerName = sanitize(ownerName);
            jobName = sanitize(jobName);
            status = status == null ? WorkerStatus.UNKNOWN : status;
        }

        private static String sanitize(String value) {
            if (value == null) {
                return "";
            }
            String trimmed = value.trim();
            return trimmed.length() > LABEL_LENGTH ? trimmed.substring(0, LABEL_LENGTH) : trimmed;
        }
    }
}
