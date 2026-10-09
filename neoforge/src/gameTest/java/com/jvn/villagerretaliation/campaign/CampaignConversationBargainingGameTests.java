package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestData;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.Map;
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
public final class CampaignConversationBargainingGameTests {
    private CampaignConversationBargainingGameTests() { }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void anotherSessionKeepsTheOriginalPriceRollAndExperience(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            var result = ready(fixture, player);
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "first bargain resolves: " + result.diagnostic());
            var original = result.session().checks().get("convince");
            var operation = result.session().transactionChecks().get("convince");
            var receipt = transactions.data().receipts().get(operation);
            helper.assertTrue(receipt.negotiation().matches(original), "conversation presents the durable transaction check");
            helper.assertValueEqual(receipt.price(), original.success() ? 5 : 8, "authored terms select the price");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "first negotiation ends");
            fixture.storage.reload(); transactions.reload(); social.reload();
            var replay = ready(fixture, player);
            replay = fixture.intent(player, replay, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(replay.diagnostic().isBlank(), "reopened bargain resolves: " + replay.diagnostic());
            helper.assertFalse(replay.session().identity().id().equals(result.session().identity().id()), "replay is a new conversation session");
            helper.assertValueEqual(replay.session().transactionChecks().get("convince"), operation, "new session points to the same personal transaction");
            helper.assertTrue(receipt.negotiation().matches(replay.session().checks().get("convince")), "new session cannot reroll or recalculate modifiers");
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "reopening grants no extra experience");
            helper.assertValueEqual(social.data().receipts().size(), 1, "one original experience receipt remains");
            helper.assertValueEqual(transactions.data().receipts().get(operation), receipt, "reopening leaves the original terms intact");
            fixture.storage.reload();
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "linked session reloads");
            helper.assertValueEqual(ConversationService.view(player, replay.session().identity().id()).session().transactionChecks(), replay.session().transactionChecks(),
                    "transaction links survive another reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void failedTransactionWritesHideTheRollAndRetryItOnce(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var ready = ready(fixture, player);
            transactions.blockWrites();
            player.getRandom().setSeed(29);
            int expected = player.getRandom().nextInt(20) + 1;
            player.getRandom().setSeed(29);
            var failed = fixture.intent(player, ready, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(failed.diagnostic().isBlank(), "failed transaction save is recoverable");
            helper.assertTrue(failed.session().checks().isEmpty() && failed.session().transactionChecks().isEmpty(), "unpublished bargaining roll stays hidden");
            helper.assertTrue(social.data().receipts().isEmpty(), "unpublished bargaining roll grants no experience");
            helper.assertTrue(transactions.data().receipts().isEmpty(), "unpublished transaction does not enter committed state");
            transactions.restoreWrites();
            player.getRandom().setSeed(12345);
            var recovered = fixture.intent(player, failed, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(recovered.diagnostic().isBlank(), "retry persists the first result: " + recovered.diagnostic());
            helper.assertValueEqual(recovered.session().checks().get("convince").die(), expected, "retry does not draw another die");
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "recovered result awards experience once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void durableTransactionRecoversAfterTheConversationWriteIsLost(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var ready = ready(fixture, player);
            fixture.storage.blockWrites();
            var failed = fixture.intent(player, ready, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(failed.session().checks().isEmpty(), "failed conversation save hides its check");
            helper.assertValueEqual(transactions.data().receipts().size(), 1, "transaction result committed before conversation presentation");
            helper.assertTrue(social.data().receipts().isEmpty(), "unpublished presentation does not acknowledge experience");
            var original = transactions.data().receipts().values().iterator().next();
            fixture.storage.restoreWrites();
            fixture.storage.reload(); transactions.reload();
            var recovered = fixture.intent(player, ConversationService.view(player, ready.session().identity().id()), ConversationSession.Intent.ROLL, "");
            helper.assertTrue(recovered.diagnostic().isBlank(), "conversation retry recovers: " + recovered.diagnostic());
            helper.assertTrue(original.negotiation().matches(recovered.session().checks().get("convince")), "lost conversation write cannot reset bargaining");
            helper.assertTrue(social.data().receipts().containsKey(original.negotiation().experienceAward().identity()), "recovery uses the original experience identity");
            fixture.storage.reload(); social.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().receipts().size(), 1, "recovery cannot grant twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void delegatedControlPreservesTheBuyerAndTheirSkillExperience(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var buyer = fixture.player(); var controller = fixture.player();
            fixture.party(buyer, controller); startQuest(fixture, buyer);
            var result = ready(fixture, buyer);
            result = fixture.intent(controller, result, ConversationSession.Intent.JOIN, "");
            var denied = fixture.intent(controller, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "listener cannot bargain");
            PartyService.setAdminPrivileges(buyer, controller.getUUID(), true);
            result = fixture.intent(buyer, denied, ConversationSession.Intent.HAND_OVER, controller.getUUID().toString());
            PartyService.setAdminPrivileges(buyer, controller.getUUID(), false);
            denied = fixture.intent(controller, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "revoked controller cannot bargain");
            helper.assertTrue(transactions.data().receipts().isEmpty(), "rejected requests never negotiate");
            PartyService.setAdminPrivileges(buyer, controller.getUUID(), true);
            result = fixture.intent(controller, denied, ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(controller, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "authorized controller advances the buyer's bargain: " + result.diagnostic());
            helper.assertValueEqual(result.session().checks().get("convince").player(), buyer.getUUID(), "control handover does not change the buyer");
            helper.assertValueEqual(transactions.data().receipts().values().iterator().next().terms().key().player(), buyer.getUUID(), "transaction remains owned by the buyer");
            helper.assertValueEqual(social.data().experience(buyer.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "buyer gains their check experience");
            helper.assertValueEqual(social.data().experience(controller.getUUID(), PlayerSocialSkill.PERSUASION), 0L, "delegated controller receives no duplicate experience");
            fixture.storage.reload();
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "delegated bargaining request reloads with its buyer");
            helper.assertTrue(ConversationService.view(controller, result.session().identity().id()).diagnostic().isBlank(), "delegated session remains playable after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void missingTransactionDataBlocksProgressAndPendingExperience(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var result = ready(fixture, player);
            social.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var saved = transactions.read();
            var missing = saved.copy(); missing.put("Receipts", new net.minecraft.nbt.ListTag());
            transactions.install(missing); social.restoreWrites();
            ConversationTestStorage.maintain(fixture.base.server);
            var blocked = ConversationService.view(player, result.session().identity().id());
            helper.assertFalse(blocked.diagnostic().isBlank(), "missing transaction explains the blocked conversation");
            helper.assertValueEqual(blocked.session().checks(), result.session().checks(), "missing transaction leaves readable check history");
            helper.assertTrue(social.data().receipts().isEmpty(), "missing transaction cannot invent another experience identity");
            var denied = fixture.intent(player, blocked, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(denied.session().phase(), ConversationSession.Phase.ROLLED, "missing transaction cannot advance its branch");
            transactions.install(saved);
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "restored transaction resumes its original experience award");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void completedConversationHistoryPreventsRerollingAMissingTransaction(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var result = ready(fixture, player);
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var original = result.session().checks().get("convince");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "original bargain is complete");
            fixture.storage.reload();
            var saved = transactions.read();
            var missing = saved.copy(); missing.put("Receipts", new net.minecraft.nbt.ListTag());
            transactions.install(missing);
            var retry = ready(fixture, player);
            retry = fixture.intent(player, retry, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(retry.diagnostic().isBlank(), "completed conversation history exposes the missing transaction");
            helper.assertTrue(retry.session().checks().isEmpty() && transactions.data().receipts().isEmpty(), "missing history cannot be replaced with a new roll");
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "missing history grants no additional experience");
            transactions.install(saved);
            retry = fixture.intent(player, retry, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(retry.diagnostic().isBlank(), "restored transaction resumes the new conversation: " + retry.diagnostic());
            var restored = transactions.data().receipts().values().iterator().next();
            helper.assertTrue(restored.negotiation().matches(original) && restored.negotiation().matches(retry.session().checks().get("convince")),
                    "restoring the transaction preserves the original result in both sessions");
            helper.assertValueEqual(social.data().receipts().size(), 1, "restored history reuses the original experience receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void anInactiveQuestCannotCreateANewTransaction(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), fixture.base.campaignId);
            progress.complete(helper.getLevel().getGameTime(), true);
            var result = ready(fixture, player);
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(result.diagnostic().isBlank(), "inactive quest cannot start a new bargain");
            helper.assertTrue(result.session().checks().isEmpty(), "inactive quest does not roll");
            helper.assertTrue(transactions.data().receipts().isEmpty(), "inactive quest does not create a transaction");
            helper.assertTrue(social.data().receipts().isEmpty(), "inactive quest grants no bargaining experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void legacyVersionsCannotClaimNewTransactionLinks(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player(); startQuest(fixture, player);
            var result = ready(fixture, player);
            var data = ConversationSavedData.get(helper.getLevel());
            var legacy = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            legacy.putInt("DataVersion", 10);
            for (var raw : legacy.getList("Sessions", Tag.TAG_COMPOUND)) ((CompoundTag) raw).remove("TransactionChecks");
            fixture.storage.reload(legacy);
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "version ten migrates without invented transaction links");
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "migrated session bargains: " + result.diagnostic());
            var saved = ConversationSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess());
            var corrupted = saved.copy(); corrupted.putInt("DataVersion", 10);
            fixture.storage.reload(corrupted);
            helper.assertFalse(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "legacy data cannot contain transaction links");
            helper.assertValueEqual(ConversationSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()), corrupted,
                    "incompatible data is preserved for recovery");
            fixture.storage.reload(saved);
            var absent = saved.copy();
            absent.getList("Sessions", Tag.TAG_COMPOUND).getCompound(0).remove("TransactionChecks");
            fixture.storage.reload(absent);
            helper.assertFalse(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "current versions require their transaction field");
        }
        helper.succeed();
    }

    static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        var fixture = new CampaignConversationGameTests.Fixture(helper, false, false, false, campaign ->
                campaign.add("transactions", JsonParser.parseString("""
                        [{"id":"supplies","title_key":"campaign_fixture.campaign.characters.mentor","quest":"campaign_fixture:characters",
                          "character":"mentor","payment":{"item":"minecraft:emerald","count":8},"bargained_count":5}]
                        """)), quest -> {
                    var stage = quest.getAsJsonArray("stages").get(0).getAsJsonObject();
                    stage.remove("on_enter");
                    stage.add("objectives", JsonParser.parseString("[{\"id\":\"decision\",\"type\":\"choice\"}]"));
                });
        var graph = SocialProgressionTestData.resource("quests/campaign_fixture/characters/conversations/social_mentor.json");
        graph.addProperty("id", "campaign_fixture:mentor_conversation");
        graph.getAsJsonArray("checks").get(0).getAsJsonObject().addProperty("transaction", "supplies");
        fixture.graph = graph;
        var profile = SocialProgressionTestData.definition();
        fixture.install(graph, Map.of(profile.id(), profile));
        return fixture;
    }

    static void startQuest(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var context = VillagerInteractionService.createDialogueContext(fixture.helper.getLevel(), player, fixture.mentor);
        fixture.base.select(context, "visit", "offer");
        VillagerConversationService.endForPlayer(player, false);
    }

    static ConversationService.Result ready(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var result = fixture.start(player);
        for (int index = 0; index < 3; index++) result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
        result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
        fixture.helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "fixture reaches its bargaining check: " + result.diagnostic());
        return result;
    }
}
