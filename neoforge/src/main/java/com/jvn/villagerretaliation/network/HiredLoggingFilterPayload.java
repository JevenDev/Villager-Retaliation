package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredLoggingFilterPayload(int entityId, String filterId) implements CustomPacketPayload {
    public static final Type<HiredLoggingFilterPayload> TYPE = VillagerPayloads.type("hired_logging_filter");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredLoggingFilterPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredLoggingFilterPayload::encode, HiredLoggingFilterPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredLoggingFilterPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.filterId(), 128);
    }

    private static HiredLoggingFilterPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredLoggingFilterPayload(buffer.readVarInt(), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
