package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerInteractionNoticePayload(int entityId, String text, String speakerLabel) implements CustomPacketPayload {
    public static final Type<VillagerInteractionNoticePayload> TYPE = VillagerPayloads.type("villager_interaction_notice");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerInteractionNoticePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerInteractionNoticePayload::encode, VillagerInteractionNoticePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerInteractionNoticePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.text(), 512);
        buffer.writeUtf(payload.speakerLabel(), 128);
    }

    private static VillagerInteractionNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerInteractionNoticePayload(buffer.readVarInt(), buffer.readUtf(512), buffer.readUtf(128));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
