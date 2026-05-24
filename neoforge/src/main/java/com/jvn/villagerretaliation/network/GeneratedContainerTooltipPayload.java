package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GeneratedContainerTooltipPayload(int containerId, boolean generated) implements CustomPacketPayload {
    public static final Type<GeneratedContainerTooltipPayload> TYPE = VillagerPayloads.type("generated_container_tooltip");
    public static final StreamCodec<RegistryFriendlyByteBuf, GeneratedContainerTooltipPayload> STREAM_CODEC =
            VillagerPayloads.codec(GeneratedContainerTooltipPayload::encode, GeneratedContainerTooltipPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, GeneratedContainerTooltipPayload payload) {
        buffer.writeVarInt(payload.containerId());
        buffer.writeBoolean(payload.generated());
    }

    private static GeneratedContainerTooltipPayload decode(RegistryFriendlyByteBuf buffer) {
        return new GeneratedContainerTooltipPayload(buffer.readVarInt(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
