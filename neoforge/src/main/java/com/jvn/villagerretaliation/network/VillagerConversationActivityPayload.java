package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client activity that keeps an already-open villager conversation from expiring. */
public record VillagerConversationActivityPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerConversationActivityPayload> TYPE =
            VillagerPayloads.type("villager_conversation_activity");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerConversationActivityPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerConversationActivityPayload::encode, VillagerConversationActivityPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerConversationActivityPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerConversationActivityPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerConversationActivityPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
