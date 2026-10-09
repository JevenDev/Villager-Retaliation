package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignConversationBargainingGameTests.startQuest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionSequenceRunner;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestData;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignPaymentGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");

    private CampaignPaymentGameTests() { }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void directPaymentsStayPersonalAndSurviveReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel())) {
            var first = fixture.player(); var second = fixture.player();
            startQuest(fixture, first); startQuest(fixture, second);
            for (var player : new ServerPlayer[] {first, second}) player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var action = actions().getFirst();
            helper.assertTrue(action.required(), "payment is always required");
            helper.assertValueEqual(SceneContinuation.loadAction(SceneContinuation.saveAction(action)), action, "suspended actions preserve typed payment fields");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), first, fixture.mentor);
            helper.assertTrue(condition(CampaignTransactionPredicate.State.UNARRANGED).evaluate(context).matches(), "started quest has no transaction yet");
            var result = VillagerActionRegistry.execute(context, action, Map.of());
            helper.assertValueEqual(result.status(), ActionStatus.SUCCESS, "first payment applies: " + result.message());
            helper.assertValueEqual(first.getInventory().countItem(Items.EMERALD), 8, "direct payment uses the base price");
            helper.assertValueEqual(second.getInventory().countItem(Items.EMERALD), 16, "another player keeps their currency");
            transactions.reload();
            helper.assertTrue(condition(CampaignTransactionPredicate.State.PAID).evaluate(context).matches(), "paid branch survives reload");
            first.setPos(fixture.mentor.getX() + 100, fixture.mentor.getY(), fixture.mentor.getZ());
            helper.assertTrue(condition(CampaignTransactionPredicate.State.PAID).evaluate(context).matches(), "personal story condition does not require the seller to be nearby");
            first.setPos(fixture.mentor.getX() + 1, fixture.mentor.getY(), fixture.mentor.getZ());
            result = VillagerActionRegistry.execute(context, action, Map.of());
            helper.assertValueEqual(result.status(), ActionStatus.ALREADY_SATISFIED, "repeated payment uses the existing receipt");
            helper.assertValueEqual(first.getInventory().countItem(Items.EMERALD), 8, "repeat payment costs nothing");
            var otherContext = VillagerInteractionService.createDialogueContext(helper.getLevel(), second, fixture.mentor);
            helper.assertFalse(condition(CampaignTransactionPredicate.State.PAID).evaluate(otherContext).matches(), "payment never grants another player's progress");
            helper.assertTrue(VillagerActionRegistry.execute(otherContext, action, Map.of()).success(), "another buyer can arrange their own transaction");
            helper.assertValueEqual(transactions.data().receipts().size(), 2, "each buyer has one receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void missingCurrencyStopsLaterEffectsUntilTheBuyerRetries(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var ready = paymentChoice(fixture, player);
            int experience = player.totalExperience;
            var failed = fixture.intent(player, ready, ConversationSession.Intent.CHOOSE, "pay");
            helper.assertValueEqual(failed.session().phase(), ConversationSession.Phase.RESPONSE_EFFECTS, "failed payment keeps the chosen branch waiting");
            helper.assertFalse(failed.diagnostic().isBlank(), "missing currency explains the blocked payment");
            helper.assertValueEqual(player.totalExperience, experience, "required payment failure stops subsequent effects");
            var receipt = transactions.data().receipts().values().iterator().next();
            helper.assertValueEqual(receipt.phase(), CampaignTransaction.Phase.PAYMENT_PENDING, "accepted payment keeps its price");
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 16, "maintenance does not charge a previously unavailable payment");
            fixture.storage.reload(); transactions.reload();
            var recovered = fixture.intent(player, ConversationService.view(player, failed.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertTrue(recovered.diagnostic().isBlank(), "explicit retry finishes payment: " + recovered.diagnostic());
            helper.assertValueEqual(recovered.session().phase(), ConversationSession.Phase.COMPLETE, "paid branch reaches its outcome");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 16 - receipt.price(), "retry charges only the agreed price");
            helper.assertValueEqual(player.totalExperience, experience + 7, "later effect runs after payment");
            fixture.storage.reload(); transactions.reload();
            fixture.intent(player, recovered, ConversationSession.Intent.RESUME, "");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 16 - receipt.price(), "completed session cannot charge again");
            helper.assertValueEqual(player.totalExperience, experience + 7, "completed session cannot repeat later effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void handoverCannotChoosePaymentForAnotherBuyersBargain(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var buyer = fixture.player(); var member = fixture.player();
            fixture.party(buyer, member); PartyService.setAdminPrivileges(buyer, member.getUUID(), true);
            startQuest(fixture, buyer); startQuest(fixture, member);
            buyer.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            member.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var result = paymentChoice(fixture, buyer);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(buyer, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            var denied = fixture.intent(member, result, ConversationSession.Intent.CHOOSE, "pay");
            helper.assertFalse(denied.diagnostic().isBlank(), "new controller cannot take another buyer's bargain");
            helper.assertValueEqual(denied.session().phase(), ConversationSession.Phase.RESPONSES, "rejected choice does not trap the session in a foreign payment");
            helper.assertValueEqual(buyer.getInventory().countItem(Items.EMERALD), 16, "buyer has not accepted payment");
            helper.assertValueEqual(member.getInventory().countItem(Items.EMERALD), 16, "controller does not silently pay the base price");
            result = fixture.intent(member, denied, ConversationSession.Intent.HAND_OVER, buyer.getUUID().toString());
            result = fixture.intent(buyer, result, ConversationSession.Intent.CHOOSE, "pay");
            helper.assertTrue(result.diagnostic().isBlank(), "original buyer can choose payment after handover: " + result.diagnostic());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "original buyer completes the purchase");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void acceptedPaymentResumesForItsOriginalBuyerAfterHandover(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var buyer = fixture.player(); var member = fixture.player();
            fixture.party(buyer, member); PartyService.setAdminPrivileges(buyer, member.getUUID(), true);
            startQuest(fixture, buyer);
            var result = paymentChoice(fixture, buyer);
            result = fixture.intent(buyer, result, ConversationSession.Intent.CHOOSE, "pay");
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(buyer, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            buyer.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            member.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var price = transactions.data().receipts().values().iterator().next().price();
            fixture.storage.reload(); transactions.reload();
            result = fixture.intent(member, ConversationService.view(member, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertTrue(result.diagnostic().isBlank(), "authorized controller resumes the accepted payment: " + result.diagnostic());
            helper.assertValueEqual(buyer.getInventory().countItem(Items.EMERALD), 16 - price, "accepted payment keeps its original buyer");
            helper.assertValueEqual(member.getInventory().countItem(Items.EMERALD), 16, "controller is not substituted as the buyer");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void failedWorldSaveAndUnpaidActionsNeverRunLaterEffects(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            int experience = player.totalExperience;
            transactions.blockWrites();
            var failed = ActionSequenceRunner.run(context, actions(), 0, Map.of(), null, null).outcome();
            helper.assertValueEqual(failed.status(), ActionStatus.UNAVAILABLE, "unpersisted payment is unavailable");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 16, "world write failure cannot debit inventory");
            helper.assertValueEqual(player.totalExperience, experience, "world write failure stops later effects");
            transactions.restoreWrites();
            var recovered = ActionSequenceRunner.run(context, actions(), 0, Map.of(), null, null).outcome();
            helper.assertTrue(recovered.success(), "retry uses the saved intent: " + recovered.message());
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 8, "recovery charges once");
            helper.assertValueEqual(player.totalExperience, experience + 7, "later effects follow a saved payment");
            helper.assertTrue(condition(CampaignTransactionPredicate.State.PAID).evaluate(context).matches(), "recovery exposes the paid branch");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void aSceneRecoversItsInterruptedPaymentWithoutChargingAgain(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            for (int index : new int[] {1, 2})
                fixture.graph.getAsJsonArray("nodes").get(index).getAsJsonObject().getAsJsonArray("responses").get(0).getAsJsonObject()
                        .getAsJsonArray("effects").get(0).getAsJsonObject().add("action", JsonParser.parseString("""
                                {"type":"start_scene","scene_id":"campaign_fixture:character_greeting","operation_id":"pay","wait_for_result":true,"required":true}
                                """));
            var profile = SocialProgressionTestData.definition();
            fixture.install(fixture.graph, Map.of(profile.id(), profile));
            var definition = CampaignConversationSceneHandoverGameTests.scene(fixture, """
                    {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:character_greeting","definition_version":1,
                     "ownership":"player","entry_step":"purchase","actors":[],"steps":[
                      {"id":"purchase","type":"villagerretaliation:action_batch","data":{"actions":[
                        {"id":"payment","type":"campaign_payment","quest":"campaign_fixture:characters","transaction":"supplies"},
                        {"id":"later","type":"experience","amount":3,"required":true}]},"next":"done"},
                      {"id":"done","type":"villagerretaliation:scene_complete"}]}
                    """);
            var player = fixture.player(); startQuest(fixture, player);
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var result = paymentChoice(fixture, player);
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "pay");
            var waiting = result.session().receipts().values().stream().filter(receipt -> receipt.result().suspended()).findFirst().orElseThrow();
            var data = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.get(helper.getLevel());
            var scene = data.get(waiting.result().suspensionId()).orElseThrow();
            long time = helper.getLevel().getGameTime();
            com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(fixture.base.server, data, scene, definition, time++);
            var step = definition.steps().get("purchase");
            var execution = new com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext(fixture.base.server, data, scene, definition,
                    step, scene.currentRecord(step.type()), time++, true);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            int experience = player.totalExperience;
            try {
                com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard.applyActionOnce(execution, "action/payment",
                        com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt.Kind.ITEM_GRANT, () -> {
                            helper.assertTrue(VillagerActionRegistry.execute(context, actions().getFirst(), Map.of()).success(), "payment commits before interruption");
                            throw new InterruptedPayment();
                        });
                helper.fail("fixture must interrupt before the scene records payment completion");
            } catch (InterruptedPayment expected) { }
            int paidBalance = player.getInventory().countItem(Items.EMERALD);
            var saved = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
            data = com.jvn.villagerretaliation.scene.persistence.SceneSavedData.load(saved, helper.getLevel().registryAccess());
            fixture.base.server.overworld().getDataStorage().set("villagerretaliation_scenes", data);
            com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.clearRuntimeState();
            transactions.reload(); fixture.storage.reload();
            scene = data.get(scene.id()).orElseThrow();
            for (int tick = 0; tick < 8 && !scene.state().terminal(); tick++)
                com.jvn.villagerretaliation.scene.runtime.SceneStepEngine.process(fixture.base.server, data, scene, definition, time++);
            helper.assertValueEqual(scene.completionResult(), com.jvn.villagerretaliation.scene.runtime.SceneInstance.CompletionResult.SUCCESS,
                    "durable payment recovers an interrupted scene");
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), paidBalance, "scene recovery cannot charge again");
            helper.assertValueEqual(player.totalExperience, experience + 3, "scene suffix runs once after payment recovery");
            result = fixture.intent(player, ConversationService.view(player, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
            helper.assertTrue(result.diagnostic().isBlank(), "conversation resumes after its payment scene: " + result.diagnostic());
            helper.assertValueEqual(player.totalExperience, experience + 10, "conversation suffix follows the completed scene once");
        }
        helper.succeed();
    }

    private static final class InterruptedPayment extends RuntimeException { }

    private static CampaignTransactionPredicate condition(CampaignTransactionPredicate.State state) {
        return new CampaignTransactionPredicate(QUEST, QUEST, "supplies", state);
    }

    private static java.util.List<VillagerActionDefinition> actions() {
        return VillagerActionDefinition.readList(QUEST, "payment test", JsonParser.parseString("""
                {"actions":[{"type":"campaign_payment","transaction":"supplies"},{"type":"experience","amount":7}]}
                """).getAsJsonObject(), QUEST);
    }

    static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        var fixture = CampaignConversationBargainingGameTests.fixture(helper);
        var graph = fixture.graph;
        for (int index : new int[] {1, 2}) {
            var node = graph.getAsJsonArray("nodes").get(index).getAsJsonObject();
            node.remove("outcome"); node.remove("effects");
            node.add("responses", JsonParser.parseString("""
                    [{"id":"pay","label":{"key":"#conversation.appeal"},"next":"paid","effects":[
                      {"id":"payment","action":{"type":"campaign_payment","transaction":"supplies"}},
                      {"id":"after_payment","action":{"type":"experience","amount":7,"required":true}}]},
                     {"id":"leave","label":{"key":"#conversation.leave"},"next":"leave"}]
                    """));
        }
        graph.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"paid\",\"outcome\":\"guidance_given\"}"));
        graph.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"leave\",\"outcome\":\"guidance_declined\"}"));
        var profile = SocialProgressionTestData.definition();
        fixture.install(graph, Map.of(profile.id(), profile));
        return fixture;
    }

    static ConversationService.Result paymentChoice(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var result = CampaignConversationBargainingGameTests.ready(fixture, player);
        result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
        result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
        result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
        fixture.helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "fixture offers payment after the bargain: " + result.diagnostic());
        return result;
    }
}
