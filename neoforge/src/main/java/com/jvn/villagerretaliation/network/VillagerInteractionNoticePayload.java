package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerInteractionNoticePayload(
        int entityId,
        String text,
        String speakerLabel,
        VillagerInteractionNoticeKind kind
) implements CustomPacketPayload {
    public static final Type<VillagerInteractionNoticePayload> TYPE = new Type<>(
            VillagerRetaliation.id("villager_interaction_notice")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerInteractionNoticePayload> STREAM_CODEC =
            StreamCodec.of(VillagerInteractionNoticePayload::encode, VillagerInteractionNoticePayload::decode);

    public VillagerInteractionNoticePayload(int entityId, String text, String speakerLabel) {
        this(entityId, text, speakerLabel, VillagerInteractionNoticeKind.DEFAULT);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerInteractionNoticePayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.text(), 512);
        buffer.writeUtf(payload.speakerLabel(), 128);
        buffer.writeEnum(payload.kind());
    }

    private static VillagerInteractionNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerInteractionNoticePayload(
                buffer.readVarInt(),
                buffer.readUtf(512),
                buffer.readUtf(128),
                buffer.readEnum(VillagerInteractionNoticeKind.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
