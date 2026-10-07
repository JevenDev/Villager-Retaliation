package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignQuestConfirmationPayload(UUID requestId, long revision, int entityId, UUID providerId, boolean pending, String text) implements CustomPacketPayload {
    public static final int MAX_TEXT_LENGTH = 8192;
    public static final Type<CampaignQuestConfirmationPayload> TYPE = VillagerPayloads.type("campaign_quest_confirmation");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignQuestConfirmationPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignQuestConfirmationPayload::encode, CampaignQuestConfirmationPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignQuestConfirmationPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeVarLong(payload.revision());
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.providerId());
        buffer.writeBoolean(payload.pending());
        buffer.writeUtf(payload.text(), MAX_TEXT_LENGTH);
    }

    private static CampaignQuestConfirmationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CampaignQuestConfirmationPayload(buffer.readUUID(), buffer.readVarLong(), buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean(), buffer.readUtf(MAX_TEXT_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
