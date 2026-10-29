package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementProjectPreviewPayload(UUID session, UUID project, int requestId, SettlementBuildingPreviewPayload preview) implements CustomPacketPayload {
    public static final Type<SettlementProjectPreviewPayload> TYPE = VillagerPayloads.type("settlement_project_preview");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementProjectPreviewPayload> STREAM_CODEC = VillagerPayloads.codec((buffer, payload) -> {
        buffer.writeUUID(payload.session());
        buffer.writeUUID(payload.project());
        buffer.writeVarInt(payload.requestId());
        buffer.writeBoolean(payload.preview() != null);
        if (payload.preview() != null) SettlementBuildingPreviewPayload.STREAM_CODEC.encode(buffer, payload.preview());
    }, buffer -> new SettlementProjectPreviewPayload(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(),
            buffer.readBoolean() ? SettlementBuildingPreviewPayload.STREAM_CODEC.decode(buffer) : null));

    public SettlementProjectPreviewPayload {
        if (session == null || project == null || requestId < 1 || preview != null && preview.requestId() != requestId)
            throw new IllegalArgumentException("Invalid project preview");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
