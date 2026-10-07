package com.jvn.villagerretaliation.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignJournalRequestPayload(UUID requestId, String campaignId, int page) implements CustomPacketPayload {
    public static final Type<CampaignJournalRequestPayload> TYPE = VillagerPayloads.type("campaign_journal_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignJournalRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignJournalRequestPayload::encode, CampaignJournalRequestPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignJournalRequestPayload value) {
        buffer.writeUUID(value.requestId());
        buffer.writeUtf(value.campaignId(), 256);
        buffer.writeVarInt(value.page());
    }

    private static CampaignJournalRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        return new CampaignJournalRequestPayload(buffer.readUUID(), buffer.readUtf(256), buffer.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
