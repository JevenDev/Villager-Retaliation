package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestTrackerSyncPayload(List<Entry> entries, boolean flash) implements CustomPacketPayload {
    public static final int MAX_TRACKER_ENTRIES = 3;
    public static final int MAX_SYNC_ENTRIES = 32;
    public static final Type<QuestTrackerSyncPayload> TYPE = VillagerPayloads.type("quest_tracker_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackerSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(QuestTrackerSyncPayload::encode, QuestTrackerSyncPayload::decode);

    public QuestTrackerSyncPayload {
        entries = entries == null
                ? List.of()
                : List.copyOf(entries.stream().filter(Objects::nonNull).limit(MAX_SYNC_ENTRIES).toList());
    }

    private static void encode(RegistryFriendlyByteBuf buffer, QuestTrackerSyncPayload payload) {
        buffer.writeVarInt(Math.min(MAX_SYNC_ENTRIES, payload.entries().size()));
        for (Entry entry : payload.entries()) {
            buffer.writeUtf(entry.questId(), 128);
            buffer.writeUtf(entry.title(), 128);
            buffer.writeUtf(entry.objective(), 256);
            buffer.writeUtf(entry.metadata(), 256);
            buffer.writeFloat(entry.progress());
            buffer.writeBoolean(entry.showProgress());
        }
        buffer.writeBoolean(payload.flash());
    }

    private static QuestTrackerSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(Math.min(MAX_SYNC_ENTRIES, size));
        for (int i = 0; i < size; i++) {
            Entry entry = new Entry(
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(256),
                    buffer.readUtf(256),
                    buffer.readFloat(),
                    buffer.readBoolean()
            );
            if (entries.size() < MAX_SYNC_ENTRIES) {
                entries.add(entry);
            }
        }
        return new QuestTrackerSyncPayload(entries, buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Entry(
            String questId,
            String title,
            String objective,
            String metadata,
            float progress,
            boolean showProgress) {
        public Entry {
            questId = questId == null ? "" : questId;
            title = title == null ? "" : title;
            objective = objective == null ? "" : objective;
            metadata = metadata == null ? "" : metadata;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
        }
    }
}
