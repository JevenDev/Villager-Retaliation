package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HiredDebugHudPreviewPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<HiredDebugHudPreviewPayload> TYPE = VillagerPayloads.type("hired_debug_hud_preview");
    public static final StreamCodec<RegistryFriendlyByteBuf, HiredDebugHudPreviewPayload> STREAM_CODEC =
            VillagerPayloads.codec(HiredDebugHudPreviewPayload::encode, HiredDebugHudPreviewPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, HiredDebugHudPreviewPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static HiredDebugHudPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        return new HiredDebugHudPreviewPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
