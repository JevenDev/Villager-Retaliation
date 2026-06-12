package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredDebugPreviewSyncPayload(
        boolean enabled,
        List<WorkAreaEntry> workAreas,
        List<StorageEntry> storage,
        int ticks) implements CustomPacketPayload {
    public static final int MAX_WORK_AREAS = 128;
    public static final int MAX_STORAGE = 256;
    public static final Type<HiredDebugPreviewSyncPayload> TYPE = VillagerPayloads.type("hired_debug_preview_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredDebugPreviewSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredDebugPreviewSyncPayload::encode, HiredDebugPreviewSyncPayload::decode);

    public HiredDebugPreviewSyncPayload {
        workAreas = workAreas == null ? List.of() : List.copyOf(workAreas.stream().limit(MAX_WORK_AREAS).toList());
        storage = storage == null ? List.of() : List.copyOf(storage.stream().limit(MAX_STORAGE).toList());
        ticks = Math.max(0, ticks);
    }

    public static HiredDebugPreviewSyncPayload disabled() {
        return new HiredDebugPreviewSyncPayload(false, List.of(), List.of(), 0);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HiredDebugPreviewSyncPayload payload) {
        buffer.writeBoolean(payload.enabled());
        buffer.writeVarInt(Math.min(MAX_WORK_AREAS, payload.workAreas().size()));
        for (WorkAreaEntry entry : payload.workAreas()) {
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.min());
            buffer.writeBlockPos(entry.max());
            buffer.writeBlockPos(entry.center());
            buffer.writeBoolean(entry.showCenter());
            buffer.writeBlockPos(entry.firstCorner());
            buffer.writeBoolean(entry.showFirstCorner());
            buffer.writeBlockPos(entry.secondCorner());
            buffer.writeBoolean(entry.showSecondCorner());
            buffer.writeUtf(entry.ownerName());
            buffer.writeUtf(entry.jobName());
        }
        buffer.writeVarInt(Math.min(MAX_STORAGE, payload.storage().size()));
        for (StorageEntry entry : payload.storage()) {
            buffer.writeResourceLocation(entry.dimension());
            buffer.writeBlockPos(entry.pos());
            buffer.writeBoolean(entry.payment());
            buffer.writeUtf(entry.ownerName());
            buffer.writeUtf(entry.storageType());
        }
        buffer.writeVarInt(payload.ticks());
    }

    private static HiredDebugPreviewSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        int workAreaCount = Math.min(MAX_WORK_AREAS, buffer.readVarInt());
        List<WorkAreaEntry> workAreas = new ArrayList<>(workAreaCount);
        for (int index = 0; index < workAreaCount; index++) {
            workAreas.add(new WorkAreaEntry(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readUtf()
            ));
        }
        int storageCount = Math.min(MAX_STORAGE, buffer.readVarInt());
        List<StorageEntry> storage = new ArrayList<>(storageCount);
        for (int index = 0; index < storageCount; index++) {
            storage.add(new StorageEntry(
                    buffer.readResourceLocation(),
                    buffer.readBlockPos(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readUtf()
            ));
        }
        return new HiredDebugPreviewSyncPayload(enabled, workAreas, storage, buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record WorkAreaEntry(
            net.minecraft.resources.ResourceLocation dimension,
            net.minecraft.core.BlockPos min,
            net.minecraft.core.BlockPos max,
            net.minecraft.core.BlockPos center,
            boolean showCenter,
            net.minecraft.core.BlockPos firstCorner,
            boolean showFirstCorner,
            net.minecraft.core.BlockPos secondCorner,
            boolean showSecondCorner,
            String ownerName,
            String jobName) {
        public WorkAreaEntry {
            ClipboardWorkAreaEntry normalized = new ClipboardWorkAreaEntry(
                    dimension,
                    min,
                    max,
                    center,
                    showCenter,
                    firstCorner,
                    showFirstCorner,
                    secondCorner,
                    showSecondCorner
            );
            min = normalized.min();
            max = normalized.max();
            center = normalized.center();
            firstCorner = normalized.firstCorner();
            secondCorner = normalized.secondCorner();
            ownerName = sanitizeLabel(ownerName);
            jobName = sanitizeLabel(jobName);
        }
    }

    public record StorageEntry(
            net.minecraft.resources.ResourceLocation dimension,
            net.minecraft.core.BlockPos pos,
            boolean payment,
            String ownerName,
            String storageType) {
        public StorageEntry {
            ownerName = sanitizeLabel(ownerName);
            storageType = sanitizeLabel(storageType);
        }
    }

    private static String sanitizeLabel(String label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.trim();
        return trimmed.length() > 64 ? trimmed.substring(0, 64) : trimmed;
    }
}
