package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementCourierMapRequest(UUID perspective, UUID village, UUID journey, int page, int routeOffset,
                                          long epoch, long after, int requestId) implements CustomPacketPayload {
    public static final Type<SettlementCourierMapRequest> TYPE = VillagerPayloads.type("settlement_courier_map_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementCourierMapRequest> STREAM_CODEC = VillagerPayloads.codec(
            SettlementCourierMapRequest::encode, SettlementCourierMapRequest::decode);
    public SettlementCourierMapRequest {
        if (page < 0 || page > 128 || routeOffset < 0 || routeOffset > 65536 || epoch < 0 || after < 0 || requestId < 0)
            throw new IllegalArgumentException("Invalid courier map request");
    }
    private static void encode(RegistryFriendlyByteBuf buffer, SettlementCourierMapRequest value) {
        VillageRelationsRequestPayload.writeId(buffer, value.perspective); VillageRelationsRequestPayload.writeId(buffer, value.village);
        VillageRelationsRequestPayload.writeId(buffer, value.journey); buffer.writeVarInt(value.page); buffer.writeVarInt(value.routeOffset);
        buffer.writeVarLong(value.epoch); buffer.writeVarLong(value.after); buffer.writeVarInt(value.requestId);
    }
    private static SettlementCourierMapRequest decode(RegistryFriendlyByteBuf buffer) {
        return new SettlementCourierMapRequest(VillageRelationsRequestPayload.readId(buffer), VillageRelationsRequestPayload.readId(buffer),
                VillageRelationsRequestPayload.readId(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readVarLong(), buffer.readVarInt());
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
