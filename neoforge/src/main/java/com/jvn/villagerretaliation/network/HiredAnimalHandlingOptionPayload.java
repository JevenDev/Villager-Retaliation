package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredAnimalHandlingOptionPayload(int entityId, String optionId) implements CustomPacketPayload {
    public static final Type<HiredAnimalHandlingOptionPayload> TYPE =
            VillagerPayloads.type("hired_animal_handling_option");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredAnimalHandlingOptionPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredAnimalHandlingOptionPayload::encode, HiredAnimalHandlingOptionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredAnimalHandlingOptionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.optionId(), 64);
    }

    private static HiredAnimalHandlingOptionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredAnimalHandlingOptionPayload(buffer.readVarInt(), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
