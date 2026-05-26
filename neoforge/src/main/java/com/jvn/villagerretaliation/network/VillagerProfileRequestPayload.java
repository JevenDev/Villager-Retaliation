package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerProfileRequestPayload(int entityId) implements CustomPacketPayload {
    public static final Type<VillagerProfileRequestPayload> TYPE = VillagerPayloads.type("villager_profile_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerProfileRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerProfileRequestPayload::encode, VillagerProfileRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerProfileRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
    }

    private static VillagerProfileRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerProfileRequestPayload(buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
