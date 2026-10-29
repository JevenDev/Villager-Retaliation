package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementProjectPreviewRequestPayload(UUID session, UUID project, int requestId) implements CustomPacketPayload {
    public static final Type<SettlementProjectPreviewRequestPayload> TYPE = VillagerPayloads.type("settlement_project_preview_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementProjectPreviewRequestPayload> STREAM_CODEC = VillagerPayloads.codec(
            (buffer, payload) -> { buffer.writeUUID(payload.session()); buffer.writeUUID(payload.project()); buffer.writeVarInt(payload.requestId()); },
            buffer -> new SettlementProjectPreviewRequestPayload(buffer.readUUID(), buffer.readUUID(), buffer.readVarInt()));

    public SettlementProjectPreviewRequestPayload {
        if (session == null || project == null || requestId < 1) throw new IllegalArgumentException("Invalid project preview request");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
