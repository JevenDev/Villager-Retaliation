package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HiredBuilderOrderPayload(int entityId, Action action, ResourceLocation structureId) implements CustomPacketPayload {
    public static final Type<HiredBuilderOrderPayload> TYPE = VillagerPayloads.type("hired_builder_order");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredBuilderOrderPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredBuilderOrderPayload::encode, HiredBuilderOrderPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredBuilderOrderPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeEnum(payload.action());
        buffer.writeResourceLocation(payload.structureId());
    }

    private static HiredBuilderOrderPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredBuilderOrderPayload(
                buffer.readVarInt(),
                buffer.readEnum(Action.class),
                buffer.readResourceLocation());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Action {
        PREVIEW,
        CONFIRM,
        CANCEL
    }
}
