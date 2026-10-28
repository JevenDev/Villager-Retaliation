package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementViewOpenResultPayload(int requestId, SettlementManagementPayload snapshot) implements CustomPacketPayload {
    public static final Type<SettlementViewOpenResultPayload> TYPE = VillagerPayloads.type("settlement_view_open_result");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewOpenResultPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeVarInt(payload.requestId());
                buffer.writeBoolean(payload.snapshot() != null);
                if (payload.snapshot() != null) SettlementManagementPayload.STREAM_CODEC.encode(buffer, payload.snapshot());
            }, buffer -> new SettlementViewOpenResultPayload(buffer.readVarInt(),
                    buffer.readBoolean() ? SettlementManagementPayload.STREAM_CODEC.decode(buffer) : null));

    public SettlementViewOpenResultPayload {
        if (requestId < 1 || requestId == Integer.MAX_VALUE || snapshot != null && (snapshot.header() == null
                || snapshot.requestId() != requestId || snapshot.openScreen()
                || snapshot.page() != SettlementManagementRequestPayload.Page.SUMMARY)) {
            throw new IllegalArgumentException("Invalid world view opening result");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
