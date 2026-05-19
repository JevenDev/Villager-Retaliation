package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerReputationRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerReputationRequestPayload> TYPE = VillagerPayloads.type("villager_reputation_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerReputationRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerReputationRequestPayload::encode, VillagerReputationRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerReputationRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerReputationRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerReputationRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
