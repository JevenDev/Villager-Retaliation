package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConversationHistoryWorldPayload(UUID world) implements CustomPacketPayload {
    public static final Type<ConversationHistoryWorldPayload> TYPE = VillagerPayloads.type("conversation_history_world");
    public static final StreamCodec<RegistryFriendlyByteBuf, ConversationHistoryWorldPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> buffer.writeUUID(payload.world()),
                    buffer -> new ConversationHistoryWorldPayload(buffer.readUUID()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
