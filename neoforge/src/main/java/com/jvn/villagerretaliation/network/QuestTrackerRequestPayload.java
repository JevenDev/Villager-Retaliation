package com.jvn.villagerretaliation.network;

import java.util.Locale;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestTrackerRequestPayload(String questId, Action action) implements CustomPacketPayload {
    public static final Type<QuestTrackerRequestPayload> TYPE = VillagerPayloads.type("quest_tracker_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestTrackerRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(QuestTrackerRequestPayload::encode, QuestTrackerRequestPayload::decode);

    public QuestTrackerRequestPayload {
        questId = questId == null ? "" : questId;
        action = action == null ? Action.TOGGLE : action;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, QuestTrackerRequestPayload payload) {
        buffer.writeUtf(payload.questId(), 128);
        buffer.writeEnum(payload.action());
    }

    private static QuestTrackerRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new QuestTrackerRequestPayload(buffer.readUtf(128), buffer.readEnum(Action.class));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        TRACK,
        UNTRACK,
        TOGGLE,
        REFRESH;

        public static Action byName(String value) {
            if (value == null || value.isBlank()) {
                return TOGGLE;
            }
            try {
                return Action.valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TOGGLE;
            }
        }
    }
}
