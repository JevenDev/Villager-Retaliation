package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerWorldTextIndicatorPayload(
        int entityId,
        String text,
        VillagerWorldTextIndicatorKind kind) implements CustomPacketPayload {
    public static final Type<VillagerWorldTextIndicatorPayload> TYPE = VillagerPayloads.type("villager_world_text_indicator");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerWorldTextIndicatorPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerWorldTextIndicatorPayload::encode, VillagerWorldTextIndicatorPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerWorldTextIndicatorPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.text(), 64);
        buffer.writeEnum(payload.kind());
    }

    private static VillagerWorldTextIndicatorPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerWorldTextIndicatorPayload(
                buffer.readVarInt(),
                buffer.readUtf(64),
                buffer.readEnum(VillagerWorldTextIndicatorKind.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
