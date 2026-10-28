package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewOpenPayload(int requestId) implements CustomPacketPayload {
    public static final Type<SettlementViewOpenPayload> TYPE = VillagerPayloads.type("settlement_view_open");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewOpenPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> buffer.writeVarInt(payload.requestId()),
                    buffer -> new SettlementViewOpenPayload(buffer.readVarInt()));

    public SettlementViewOpenPayload {
        if (requestId < 1 || requestId == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid world view request");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
