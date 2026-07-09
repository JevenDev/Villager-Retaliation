package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredHitboxDebugPreviewPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<HiredHitboxDebugPreviewPayload> TYPE = VillagerPayloads.type("hired_hitbox_debug_preview");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredHitboxDebugPreviewPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredHitboxDebugPreviewPayload::encode, HiredHitboxDebugPreviewPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredHitboxDebugPreviewPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static HiredHitboxDebugPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredHitboxDebugPreviewPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
