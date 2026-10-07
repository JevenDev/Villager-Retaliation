package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.network.CampaignJournalPagePayload;
import com.jvn.villagerretaliation.network.CampaignJournalRequestPayload;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignJournalGameTests {
    private CampaignJournalGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_journal")
    public static void journalPagesKeepEveryCampaignInOrder(GameTestHelper helper) {
        var entries = new ArrayList<CampaignJournalPresenter.Entry>();
        for (int i = 0; i < 19; i++) entries.add(entry("test:story_" + i, "Story " + i));
        var received = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
            UUID request = UUID.randomUUID();
            var response = CampaignJournalService.page(entries, new CampaignJournalRequestPayload(request, "", page));
            helper.assertValueEqual(response.requestId(), request, "page acknowledges the requested snapshot");
            helper.assertValueEqual(response.pageCount(), 3, "all campaigns have a reachable page");
            helper.assertTrue(response.paragraphs().isEmpty(), "index does not mix in story details");
            response.campaigns().forEach(campaign -> received.add(campaign.id()));
        }
        helper.assertValueEqual(received, entries.stream().map(CampaignJournalPresenter.Entry::campaignId).toList(),
                "pagination neither drops nor duplicates campaigns");
        helper.assertValueEqual(CampaignJournalService.page(entries, new CampaignJournalRequestPayload(UUID.randomUUID(), "", -1)).page(),
                0, "negative page requests resolve to the first page");
        helper.assertValueEqual(CampaignJournalService.page(entries, new CampaignJournalRequestPayload(UUID.randomUUID(), "", Integer.MAX_VALUE)).page(),
                2, "stale page requests resolve to the current last page");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_journal")
    public static void journalKeepsLongAuthoredTextAcrossPagesAndMessages(GameTestHelper helper) {
        String title = "a".repeat(510) + "\uD83D\uDCD6" + "b".repeat(3583) + "\uD83D\uDCD6" + "c".repeat(30000);
        var entries = List.of(entry("test:long_story", title));
        var index = CampaignJournalService.page(entries, new CampaignJournalRequestPayload(UUID.randomUUID(), "", 0));
        helper.assertTrue(index.campaigns().getFirst().title().length() <= 512, "index titles fit the wire limit");
        helper.assertFalse(Character.isHighSurrogate(index.campaigns().getFirst().title().charAt(509)), "short title preserves Unicode boundaries");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        var text = new StringBuilder();
        try {
            int pageCount = 1;
            for (int page = 0; page < pageCount; page++) {
                var response = CampaignJournalService.page(entries, new CampaignJournalRequestPayload(UUID.randomUUID(), "test:long_story", page));
                pageCount = response.pageCount();
                buffer.clear();
                CampaignJournalPagePayload.STREAM_CODEC.encode(buffer, response);
                var decoded = CampaignJournalPagePayload.STREAM_CODEC.decode(buffer);
                helper.assertValueEqual(decoded, response, "page identity and authored text survive the network");
                for (String paragraph : decoded.paragraphs()) {
                    helper.assertFalse(Character.isHighSurrogate(paragraph.charAt(paragraph.length() - 1)), "chunks do not split Unicode characters");
                    text.append(paragraph);
                }
            }
            helper.assertTrue(pageCount > 1, "long content exercises more than one network page");
            buffer.clear();
            var request = new CampaignJournalRequestPayload(UUID.randomUUID(), "test:long_story", 1);
            CampaignJournalRequestPayload.STREAM_CODEC.encode(buffer, request);
            helper.assertValueEqual(CampaignJournalRequestPayload.STREAM_CODEC.decode(buffer), request, "request identity and selected page survive the network");
        } finally {
            buffer.release();
        }
        helper.assertValueEqual(text.toString(), title, "full story text is preserved despite the abbreviated index title");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_journal")
    public static void journalHandlesEmptyAndRemovedCampaignPages(GameTestHelper helper) {
        var empty = CampaignJournalService.page(List.of(), new CampaignJournalRequestPayload(UUID.randomUUID(), "", 100));
        helper.assertTrue(empty.page() == 0 && empty.pageCount() == 1 && empty.campaigns().isEmpty(), "empty index remains a valid page");
        UUID requestId = UUID.randomUUID();
        var missing = CampaignJournalService.page(List.of(), new CampaignJournalRequestPayload(requestId, "test:removed", 100));
        helper.assertTrue(missing.requestId().equals(requestId) && missing.campaignId().equals("test:removed") && missing.page() == 0,
                "removed campaign replies still identify the matching selection");
        helper.assertTrue(missing.campaigns().isEmpty() && !missing.paragraphs().isEmpty(), "removed content provides readable feedback");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_journal")
    public static void journalRejectsOversizedAndNegativeWireCollections(GameTestHelper helper) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            for (boolean summaries : List.of(true, false)) {
                for (int count : new int[] {-1, CampaignJournalPagePayload.PAGE_SIZE + 1, Integer.MAX_VALUE}) {
                    buffer.clear();
                    buffer.writeUUID(UUID.randomUUID());
                    buffer.writeUtf("test:story", 256);
                    buffer.writeVarInt(0);
                    buffer.writeVarInt(1);
                    if (!summaries) buffer.writeVarInt(0);
                    buffer.writeVarInt(count);
                    boolean rejected = false;
                    try {
                        CampaignJournalPagePayload.STREAM_CODEC.decode(buffer);
                    } catch (IllegalArgumentException exception) {
                        rejected = true;
                    }
                    helper.assertTrue(rejected, "invalid collection length is rejected before reading or allocating entries");
                }
            }
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    private static CampaignJournalPresenter.Entry entry(String id, String title) {
        return new CampaignJournalPresenter.Entry(id, title, "", 0L, 0L, List.of(), List.of());
    }
}
