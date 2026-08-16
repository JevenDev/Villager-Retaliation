package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredFarmingOptionPayload(int entityId, String optionId) implements CustomPacketPayload {
    public static final Type<HiredFarmingOptionPayload> TYPE = VillagerPayloads.type("hired_farming_option");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredFarmingOptionPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredFarmingOptionPayload::encode, HiredFarmingOptionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredFarmingOptionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.optionId(), 64);
    }

    private static HiredFarmingOptionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredFarmingOptionPayload(buffer.readVarInt(), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
