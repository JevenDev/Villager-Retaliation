package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredLoggingOptionPayload(int entityId, String optionId) implements CustomPacketPayload {
    public static final Type<HiredLoggingOptionPayload> TYPE = VillagerPayloads.type("hired_logging_option");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredLoggingOptionPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredLoggingOptionPayload::encode, HiredLoggingOptionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredLoggingOptionPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.optionId(), 64);
    }

    private static HiredLoggingOptionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredLoggingOptionPayload(buffer.readVarInt(), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
