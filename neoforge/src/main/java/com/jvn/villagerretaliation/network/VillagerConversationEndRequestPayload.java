package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerConversationEndRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerConversationEndRequestPayload> TYPE = VillagerPayloads.type("villager_conversation_end_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerConversationEndRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerConversationEndRequestPayload::encode, VillagerConversationEndRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerConversationEndRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerConversationEndRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerConversationEndRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
