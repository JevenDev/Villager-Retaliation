package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerReputationTierNoticePayload(String text, VillagerReputationNoticeKind kind) implements CustomPacketPayload {
    public static final Type<VillagerReputationTierNoticePayload> TYPE = VillagerPayloads.type("villager_reputation_tier_notice");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerReputationTierNoticePayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerReputationTierNoticePayload::encode, VillagerReputationTierNoticePayload::decode);

    public VillagerReputationTierNoticePayload(String text) {
        this(text, VillagerReputationNoticeKind.DEFAULT);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerReputationTierNoticePayload payload) {
        buffer.writeUtf(payload.text(), 512);
        buffer.writeEnum(payload.kind());
    }

    private static VillagerReputationTierNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerReputationTierNoticePayload(
                buffer.readUtf(512),
                buffer.readEnum(VillagerReputationNoticeKind.class)
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
