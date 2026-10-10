package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignQuestConfirmationPayload(UUID requestId, long revision, int entityId, UUID providerId, boolean pending, String text) implements CustomPacketPayload {
    private static final UUID NO_PROVIDER = new UUID(0L, 0L);
    public static final int MAX_TEXT_LENGTH = 8192;
    public static final Type<CampaignQuestConfirmationPayload> TYPE = VillagerPayloads.type("campaign_quest_confirmation");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignQuestConfirmationPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignQuestConfirmationPayload::encode, CampaignQuestConfirmationPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignQuestConfirmationPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeVarLong(payload.revision());
        buffer.writeVarInt(payload.entityId());
        buffer.writeUUID(payload.providerId() == null ? NO_PROVIDER : payload.providerId());
        buffer.writeBoolean(payload.pending());
        buffer.writeUtf(payload.text(), MAX_TEXT_LENGTH);
    }

    private static CampaignQuestConfirmationPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID requestId = buffer.readUUID();
        long revision = buffer.readVarLong();
        int entityId = buffer.readVarInt();
        UUID providerId = buffer.readUUID();
        return new CampaignQuestConfirmationPayload(requestId, revision, entityId, NO_PROVIDER.equals(providerId) ? null : providerId,
                buffer.readBoolean(), buffer.readUtf(MAX_TEXT_LENGTH));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
