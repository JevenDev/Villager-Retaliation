package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillageBoundsSubscriptionPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<VillageBoundsSubscriptionPayload> TYPE = VillagerPayloads.type("village_bounds_subscription");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageBoundsSubscriptionPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageBoundsSubscriptionPayload::encode, VillageBoundsSubscriptionPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillageBoundsSubscriptionPayload payload) {
        buffer.writeBoolean(payload.enabled());
    }

    private static VillageBoundsSubscriptionPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillageBoundsSubscriptionPayload(buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
