package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardPreviewTogglePayload(boolean enabled, String lens, List<String> trackedJobs) implements CustomPacketPayload {
    private static final int MAX_TRACKED_JOBS = 16;
    private static final int NAME_LENGTH = 32;
    public static final Type<ClipboardPreviewTogglePayload> TYPE = VillagerPayloads.type("clipboard_preview_toggle");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPreviewTogglePayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardPreviewTogglePayload::encode, ClipboardPreviewTogglePayload::decode);

    public ClipboardPreviewTogglePayload {
        lens = lens == null ? "none" : lens.trim().toLowerCase(java.util.Locale.ROOT);
        trackedJobs = trackedJobs == null
                ? List.of()
                : List.copyOf(trackedJobs.stream()
                        .filter(job -> job != null && !job.isBlank())
                        .map(job -> job.trim().toLowerCase(java.util.Locale.ROOT))
                        .distinct()
                        .limit(MAX_TRACKED_JOBS)
                        .toList());
    }

    public ClipboardPreviewTogglePayload(boolean enabled) {
        this(enabled, enabled ? "workforce" : "none", List.of());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardPreviewTogglePayload payload) {
        buffer.writeBoolean(payload.enabled());
        buffer.writeUtf(payload.lens(), NAME_LENGTH);
        buffer.writeVarInt(payload.trackedJobs().size());
        for (String job : payload.trackedJobs()) {
            buffer.writeUtf(job, NAME_LENGTH);
        }
    }

    private static ClipboardPreviewTogglePayload decode(RegistryFriendlyByteBuf buffer) {
        boolean enabled = buffer.readBoolean();
        String lens = buffer.readUtf(NAME_LENGTH);
        int count = VillagerPayloads.readCollectionSize(buffer, MAX_TRACKED_JOBS, "clipboard tracked jobs");
        List<String> jobs = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            jobs.add(buffer.readUtf(NAME_LENGTH));
        }
        return new ClipboardPreviewTogglePayload(enabled, lens, jobs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
