package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClipboardModeChangePayload(int delta, int menuSlotIndex) implements CustomPacketPayload {
    public static final Type<ClipboardModeChangePayload> TYPE = VillagerPayloads.type("clipboard_mode_change");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardModeChangePayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardModeChangePayload::encode, ClipboardModeChangePayload::decode);

    public ClipboardModeChangePayload {
        delta = Integer.compare(delta, 0);
    }

    public ClipboardModeChangePayload(int delta) {
        this(delta, -1);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardModeChangePayload payload) {
        buffer.writeByte(payload.delta());
        buffer.writeVarInt(payload.menuSlotIndex());
    }

    private static ClipboardModeChangePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardModeChangePayload(buffer.readByte(), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
