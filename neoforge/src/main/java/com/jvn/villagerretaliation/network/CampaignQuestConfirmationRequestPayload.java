package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignQuestConfirmationRequestPayload(UUID requestId, long revision, boolean accept) implements CustomPacketPayload {
    public static final Type<CampaignQuestConfirmationRequestPayload> TYPE = VillagerPayloads.type("campaign_quest_confirmation_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignQuestConfirmationRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignQuestConfirmationRequestPayload::encode, CampaignQuestConfirmationRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignQuestConfirmationRequestPayload payload) {
        buffer.writeUUID(payload.requestId());
        buffer.writeVarLong(payload.revision());
        buffer.writeBoolean(payload.accept());
    }

    private static CampaignQuestConfirmationRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CampaignQuestConfirmationRequestPayload(buffer.readUUID(), buffer.readVarLong(), buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
