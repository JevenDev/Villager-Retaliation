package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerTradeRefreshRequestPayload(int entityId, int offerIndex) implements CustomPacketPayload {
    public static final Type<VillagerTradeRefreshRequestPayload> TYPE = VillagerPayloads.type("villager_trade_refresh_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerTradeRefreshRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerTradeRefreshRequestPayload::encode, VillagerTradeRefreshRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerTradeRefreshRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.offerIndex());
    }

    private static VillagerTradeRefreshRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerTradeRefreshRequestPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
