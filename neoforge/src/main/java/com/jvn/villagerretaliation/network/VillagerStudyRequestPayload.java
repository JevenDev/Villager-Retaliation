package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record VillagerStudyRequestPayload(int entityId, String skillId) implements CustomPacketPayload {
    private static final int MAX_SKILL_ID_LENGTH = 64;
    public static final Type<VillagerStudyRequestPayload> TYPE = VillagerPayloads.type("villager_study_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerStudyRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillagerStudyRequestPayload::encode, VillagerStudyRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, VillagerStudyRequestPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.skillId(), MAX_SKILL_ID_LENGTH);
    }

    private static VillagerStudyRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new VillagerStudyRequestPayload(buffer.readVarInt(), buffer.readUtf(MAX_SKILL_ID_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
