package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardPreviewTogglePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<ClipboardPreviewTogglePayload> TYPE = VillagerPayloads.type("clipboard_preview_toggle");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardPreviewTogglePayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardPreviewTogglePayload::encode, ClipboardPreviewTogglePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardPreviewTogglePayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static ClipboardPreviewTogglePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardPreviewTogglePayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
