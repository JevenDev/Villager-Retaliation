package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerTradeRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerTradeRequestPayload> TYPE = VillagerPayloads.type("villager_trade_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradeRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerTradeRequestPayload::encode, VillagerTradeRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerTradeRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerTradeRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerTradeRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
