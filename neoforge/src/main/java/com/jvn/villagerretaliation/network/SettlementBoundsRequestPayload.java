package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementBoundsRequestPayload(UUID town, int requestId) implements CustomPacketPayload {
    public static final Type<SettlementBoundsRequestPayload> TYPE = VillagerPayloads.type("settlement_bounds_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBoundsRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.town());
                buffer.writeVarInt(payload.requestId());
            }, buffer -> new SettlementBoundsRequestPayload(buffer.readUUID(), buffer.readVarInt()));

    public SettlementBoundsRequestPayload {
        if (town == null || requestId < 0) throw new IllegalArgumentException("Invalid settlement bounds request");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
