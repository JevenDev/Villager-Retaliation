package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredAnimalCullCapPayload(int entityId, int cap) implements CustomPacketPayload {
    public static final Type<HiredAnimalCullCapPayload> TYPE = VillagerPayloads.type("hired_animal_cull_cap");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredAnimalCullCapPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredAnimalCullCapPayload::encode, HiredAnimalCullCapPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredAnimalCullCapPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeVarInt(payload.cap());
    }

    private static HiredAnimalCullCapPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredAnimalCullCapPayload(buffer.readVarInt(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
