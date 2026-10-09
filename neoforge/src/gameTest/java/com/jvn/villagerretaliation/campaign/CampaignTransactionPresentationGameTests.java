package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignConversationBargainingGameTests.*;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignTransactionPresentationGameTests {
    private CampaignTransactionPresentationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void viewingPricesDoesNotArrangeOrRollTransactions(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignPaymentGameTests.fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            var result = ready(fixture, player);
            var before = transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var view = view(fixture, player);
            helper.assertTrue(view.session().canControl(), "a quoted bargain can be rolled");
            var quote = view.session().transactions().getFirst();
            helper.assertValueEqual(quote.buyer(), player.getUUID(), "quote names the buyer");
            helper.assertValueEqual(quote.basePrice(), 8, "quote includes the original price");
            helper.assertValueEqual(quote.bargainPrice(), 5, "quote includes the possible discounted price");
            helper.assertValueEqual(quote.state(), CampaignConversationViewPayload.TransactionState.QUOTED, "viewing does not negotiate");
            helper.assertValueEqual(transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess()), before, "viewing preserves transaction storage");
            helper.assertTrue(result.session().checks().isEmpty() && social.data().receipts().isEmpty(), "viewing draws no roll and awards no experience");
            player.getRandom().setSeed(3L);
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            int price = transactions.data().receipts().values().iterator().next().price();
            var saved = ConversationSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            var replacements = saved.getList("Sessions", Tag.TAG_COMPOUND).getCompound(0).getCompound("Replacements");
            helper.assertValueEqual(replacements.getString("campaign_transaction_price"), Integer.toString(price), "result text receives the saved price before any payment");
            helper.assertValueEqual(replacements.getString("campaign_transaction_buyer"), player.getName().getString(), "result text retains its buyer");
            fixture.storage.reload(); transactions.reload();
            var restored = view(fixture, player).session().transactions().getFirst();
            helper.assertValueEqual(restored.price(), price, "reload preserves the negotiated price");
            helper.assertValueEqual(restored.state(), CampaignConversationViewPayload.TransactionState.NEGOTIATED, "reload preserves its state");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void delegatedPaymentOptionsKeepTheOriginalBuyerAndLeaveOtherChoicesAvailable(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignPaymentGameTests.fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var buyer = fixture.player(); var successor = fixture.player();
            startQuest(fixture, buyer); startQuest(fixture, successor);
            fixture.party(buyer, successor);
            PartyService.setAdminPrivileges(buyer, successor.getUUID(), true);
            var result = CampaignPaymentGameTests.paymentChoice(fixture, buyer);
            var original = view(fixture, buyer).session().transactions().getFirst();
            fixture.intent(buyer, result, ConversationSession.Intent.HAND_OVER, successor.getUUID().toString());
            var delegated = view(fixture, successor);
            var price = delegated.session().transactions().getFirst();
            helper.assertValueEqual(price.buyer(), buyer.getUUID(), "handover cannot substitute the new controller's wallet");
            helper.assertValueEqual(price.price(), original.price(), "handover preserves the saved price");
            helper.assertTrue(price.diagnostic().contains("Hand control back"), "the buyer mismatch explains recovery");
            helper.assertFalse(delegated.session().responses().stream().filter(response -> response.id().equals("pay")).findFirst().orElseThrow().available(),
                    "another controller cannot choose this buyer's payment");
            helper.assertTrue(delegated.session().responses().stream().filter(response -> response.id().equals("leave")).findFirst().orElseThrow().available(),
                    "unrelated choices remain available");
            helper.assertValueEqual(transactions.data().receipts().size(), 1, "viewing does not create a successor transaction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void unpublishedRollsRemainHiddenAndStorageRecoveryKeepsRetryAvailable(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignPaymentGameTests.fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var result = ready(fixture, player);
            transactions.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var pending = view(fixture, player);
            helper.assertFalse(pending.session().check().resolved(), "unsaved results are not presented");
            helper.assertValueEqual(pending.session().transactions().getFirst().state(), CampaignConversationViewPayload.TransactionState.QUOTED,
                    "unsaved negotiations cannot reveal a discounted result");
            helper.assertTrue(pending.session().canControl(), "failed storage leaves an explicit retry available");
            transactions.restoreWrites();
            helper.assertTrue(view(fixture, player).session().canControl(), "storage recovery cannot strand the roll behind a disabled button");
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var restored = view(fixture, player);
            helper.assertTrue(restored.session().check().resolved() && restored.session().transactions().getFirst().diagnostic().isBlank(),
                    "retry presents the durable result and clears its storage notice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void choicesDisclosePossibleScenePaymentsWithoutStartingTheirEffects(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignPaymentGameTests.fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            for (int index : new int[] {1, 2}) fixture.graph.getAsJsonArray("nodes").get(index).getAsJsonObject()
                    .getAsJsonArray("responses").get(0).getAsJsonObject().add("effects", com.google.gson.JsonParser.parseString("""
                            [{"id":"scene","action":{"type":"start_scene","scene":"campaign_fixture:character_greeting",
                              "operation_id":"purchase","wait_for_result":true,"required":true}}]
                            """));
            var profile = com.jvn.villagerretaliation.skill.player.SocialProgressionTestData.definition();
            fixture.install(fixture.graph, java.util.Map.of(profile.id(), profile));
            CampaignConversationSceneHandoverGameTests.scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","ownership":"player",
                     "entry_step":"purchase","actors":[],"steps":[
                      {"id":"purchase","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"payment","type":"campaign_payment","quest":"campaign_fixture:characters","transaction":"supplies"}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var player = fixture.player(); startQuest(fixture, player);
            CampaignPaymentGameTests.paymentChoice(fixture, player);
            var before = transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var price = view(fixture, player).session().transactions().getFirst();
            helper.assertTrue(price.possible(), "a scene payment is presented as a possible consequence");
            helper.assertValueEqual(price.response(), "pay", "scene terms identify their response");
            helper.assertValueEqual(transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess()), before, "previewing a scene applies no payment");
            helper.assertTrue(com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel()).all().isEmpty(),
                    "previewing never starts a scene");
        }
        helper.succeed();
    }

    private static CampaignConversationViewPayload view(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        return ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(
                UUID.randomUUID(), UUID.randomUUID(), fixture.mentor.getId(), fixture.mentor.getUUID(),
                CampaignConversationRequestPayload.Action.TOPICS, null, 0, "", "", 0, 0));
    }
}
