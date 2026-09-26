package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewRequestPayload(UUID session, int requestId, double x, double z, float height, boolean close)
        implements CustomPacketPayload {
    public static final Type<SettlementViewRequestPayload> TYPE = VillagerPayloads.type("settlement_view_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.session());
                buffer.writeVarInt(payload.requestId());
                buffer.writeDouble(payload.x());
                buffer.writeDouble(payload.z());
                buffer.writeFloat(payload.height());
                buffer.writeBoolean(payload.close());
            }, buffer -> new SettlementViewRequestPayload(buffer.readUUID(), buffer.readVarInt(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readFloat(), buffer.readBoolean()));

    public SettlementViewRequestPayload {
        if (session == null || requestId < 0 || !Double.isFinite(x) || !Double.isFinite(z) || !Float.isFinite(height)) {
            throw new IllegalArgumentException("Invalid settlement view request");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
