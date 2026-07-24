package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by the client when the live clipboard dashboard is dismissed. */
public record ClipboardWorkforceSubscriptionPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<ClipboardWorkforceSubscriptionPayload> TYPE =
            VillagerPayloads.type("clipboard_workforce_subscription");
    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardWorkforceSubscriptionPayload> STREAM_CODEC =
            VillagerPayloads.codec(ClipboardWorkforceSubscriptionPayload::encode, ClipboardWorkforceSubscriptionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, ClipboardWorkforceSubscriptionPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static ClipboardWorkforceSubscriptionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClipboardWorkforceSubscriptionPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
