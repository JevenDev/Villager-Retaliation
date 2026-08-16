package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredHuntingTargetPayload(int entityId, String targetId) implements CustomPacketPayload {
    public static final Type<HiredHuntingTargetPayload> TYPE = VillagerPayloads.type("hired_hunting_target");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredHuntingTargetPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredHuntingTargetPayload::encode, HiredHuntingTargetPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredHuntingTargetPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.targetId(), 64);
    }

    private static HiredHuntingTargetPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredHuntingTargetPayload(buffer.readVarInt(), buffer.readUtf(64));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
