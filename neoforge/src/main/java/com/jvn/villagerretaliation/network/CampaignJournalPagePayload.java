package com.jvn.villagerretaliation.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignJournalPagePayload(UUID requestId, String campaignId, int page, int pageCount,
        List<Summary> campaigns, List<String> paragraphs) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 7;
    public static final int MAX_PARAGRAPH_LENGTH = 4096;
    public static final Type<CampaignJournalPagePayload> TYPE = VillagerPayloads.type("campaign_journal_page");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignJournalPagePayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignJournalPagePayload::encode, CampaignJournalPagePayload::decode);

    public CampaignJournalPagePayload {
        campaigns = List.copyOf(campaigns);
        paragraphs = List.copyOf(paragraphs);
        if (page < 0 || pageCount < 1 || page >= pageCount || campaigns.size() > PAGE_SIZE || paragraphs.size() > PAGE_SIZE) {
            throw new IllegalArgumentException("Invalid campaign journal page");
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignJournalPagePayload value) {
        buffer.writeUUID(value.requestId());
        buffer.writeUtf(value.campaignId(), 256);
        buffer.writeVarInt(value.page());
        buffer.writeVarInt(value.pageCount());
        buffer.writeVarInt(value.campaigns().size());
        for (var campaign : value.campaigns()) {
            buffer.writeUtf(campaign.id(), 256);
            buffer.writeUtf(campaign.title(), 512);
        }
        buffer.writeVarInt(value.paragraphs().size());
        for (String paragraph : value.paragraphs()) buffer.writeUtf(paragraph, MAX_PARAGRAPH_LENGTH);
    }

    private static CampaignJournalPagePayload decode(RegistryFriendlyByteBuf buffer) {
        UUID request = buffer.readUUID();
        String campaign = buffer.readUtf(256);
        int page = buffer.readVarInt();
        int pages = buffer.readVarInt();
        var summaries = new ArrayList<Summary>();
        int summaryCount = readCount(buffer);
        for (int i = 0; i < summaryCount; i++) summaries.add(new Summary(buffer.readUtf(256), buffer.readUtf(512)));
        var paragraphs = new ArrayList<String>();
        int paragraphCount = readCount(buffer);
        for (int i = 0; i < paragraphCount; i++) paragraphs.add(buffer.readUtf(MAX_PARAGRAPH_LENGTH));
        return new CampaignJournalPagePayload(request, campaign, page, pages, summaries, paragraphs);
    }

    private static int readCount(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid campaign journal page size");
        return count;
    }

    public record Summary(String id, String title) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
