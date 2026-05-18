package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerConversationEndedPayload(int entityId, String goodbyeText) implements CustomPacketPayload {
    public static final Type<VillagerConversationEndedPayload> TYPE = new Type<>(
            VillagerRetaliation.id("villager_conversation_ended")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerConversationEndedPayload> STREAM_CODEC =
            StreamCodec.of(VillagerConversationEndedPayload::encode, VillagerConversationEndedPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerConversationEndedPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.goodbyeText(), 512);
    }

    private static VillagerConversationEndedPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerConversationEndedPayload(buffer.readVarInt(), buffer.readUtf(512));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
