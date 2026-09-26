package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

public record SettlementViewChunkPayload(UUID session, ClientboundLevelChunkWithLightPacket chunk) implements CustomPacketPayload {
    public static final Type<SettlementViewChunkPayload> TYPE = VillagerPayloads.type("settlement_view_chunk");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementViewChunkPayload> STREAM_CODEC =
            VillagerPayloads.codec((buffer, payload) -> {
                buffer.writeUUID(payload.session());
                ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, payload.chunk());
            }, buffer -> new SettlementViewChunkPayload(buffer.readUUID(), ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
