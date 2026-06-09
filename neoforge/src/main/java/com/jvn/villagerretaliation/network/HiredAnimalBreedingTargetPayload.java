package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredAnimalBreedingTargetPayload(int entityId, String targetId) implements CustomPacketPayload {
    public static final Type<HiredAnimalBreedingTargetPayload> TYPE = VillagerPayloads.type("hired_animal_breeding_target");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredAnimalBreedingTargetPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredAnimalBreedingTargetPayload::encode, HiredAnimalBreedingTargetPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredAnimalBreedingTargetPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.targetId(), 128);
    }

    private static HiredAnimalBreedingTargetPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredAnimalBreedingTargetPayload(buffer.readVarInt(), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
