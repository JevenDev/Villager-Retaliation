package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerReputationTierNoticePayload(String text) implements CustomPacketPayload {
    public static final Type<VillagerReputationTierNoticePayload> TYPE = new Type<>(
            VillagerRetaliation.id("villager_reputation_tier_notice")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerReputationTierNoticePayload> STREAM_CODEC =
            StreamCodec.of(VillagerReputationTierNoticePayload::encode, VillagerReputationTierNoticePayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerReputationTierNoticePayload payload) {
        buffer.writeUtf(payload.text(), 512);
    }

    private static VillagerReputationTierNoticePayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerReputationTierNoticePayload(buffer.readUtf(512));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
