package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.party.PartyService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class OrdinaryConversationGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("campaign_fixture:mentor_conversation");

    private OrdinaryConversationGameTests() { }

    @GameTest(template = "empty", batch = "ordinary_conversations")
    public static void survivalConversationsUsePersistentChoicesAndReleaseTheirProvider(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            install(fixture, true);
            fixture.base.mode("legacy");
            var leader = fixture.player();
            var player = fixture.player();
            fixture.party(leader, player);
            PartyService.setAdminPrivileges(leader, player.getUUID(), false);
            helper.assertFalse(CampaignAuthority.canControl(player), "ordinary quest owner has no campaign admin privileges");
            var sceneAccess = new com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.Access(player, player.getUUID(), "", false);
            helper.assertValueEqual(sceneAccess.withController(() -> "continued"), "continued", "ordinary scene continuations use personal authority");
            try {
                new com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.Access(player, player.getUUID(), "")
                        .withController(() -> "continued");
                helper.fail("campaign scene continuations still require campaign authority");
            } catch (IllegalArgumentException expected) { }
            int experience = player.totalExperience;
            var inspected = com.jvn.villagerretaliation.dialogue.conversation.ConversationTopicInspection.inspect(
                    fixture.base.server, ID, "guidance", player.getUUID());
            helper.assertValueEqual(inspected.status(),
                    com.jvn.villagerretaliation.dialogue.conversation.ConversationTopicInspection.Status.AVAILABLE,
                    "ordinary topics can be inspected without campaign state");
            UUID view = UUID.randomUUID();
            var topics = ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(view, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.TOPICS, null, 0, "", "", 0, 0));
            helper.assertTrue(topics.topics().stream().anyMatch(topic -> topic.conversation().equals(ID.toString())),
                    "ordinary topics appear outside Campaign Mode");
            var started = ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(view, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.START, null, 0,
                    ID.toString(), "guidance", 0, 0));
            helper.assertTrue(started.session() != null, "ordinary provider screen starts conversation: " + started.diagnostic());
            helper.assertTrue(started.session().canControl() && started.session().canCancel(),
                    "ordinary quest owners can control and recover conversations without campaign admin privileges");
            var result = ConversationService.view(player, started.session().id());
            helper.assertTrue(result.diagnostic().isBlank(), "ordinary quest provider works outside Campaign Mode");
            helper.assertTrue(result.session().identity().campaign() == null, "ordinary session has no campaign identity");
            helper.assertTrue(result.session().identity().party() == null, "ordinary quest conversations retain individual ownership");
            var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(player.serverLevel())
                    .get(player.getUUID(), ResourceLocation.parse("campaign_fixture:characters"));
            helper.assertTrue(progress != null && progress.questRunId() != null && progress.startCount() == 1,
                    "ordinary conversation starts the current quest through the action pipeline");
            helper.assertValueEqual(player.totalExperience, experience + 7, "personal effect applies once");
            UUID id = result.session().identity().id();
            var first = result.session().transcript().getFirst();
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            helper.assertValueEqual(result.session().transcript().getFirst(), first, "survival presentation survives reload");
            helper.assertValueEqual(player.totalExperience, experience + 7, "reopening cannot replay personal effects");
            helper.assertValueEqual(result.session().receipts().size(), 2, "quest and personal effects have durable receipts");
            while (result.session().phase() == ConversationSession.Phase.EXCHANGES)
                result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "ordinary providers support social checks");
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertValueEqual(result.session().checks().size(), 1, "the server saves one roll");
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            while (result.session().phase() == ConversationSession.Phase.EXCHANGES)
                result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "ordinary conversation completes its branch");
            helper.assertFalse(ConversationService.isCharacterReserved(fixture.mentor), "completion releases the provider");
            fixture.storage.reload();
            var reopened = ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(view, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.TOPICS, null, 0, "", "", 0, 0));
            helper.assertTrue(reopened.topics().stream().anyMatch(topic -> topic.group()
                    == com.jvn.villagerretaliation.network.CampaignConversationViewPayload.TopicGroup.HISTORY && topic.id().equals(id.toString())),
                    "completed conversation history remains reachable after reload");
            var history = ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(view, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.HISTORY, id, 0, "", "", 0, 0));
            helper.assertTrue(history.session() != null && history.session().history() != null, "archived history opens through the provider");
            helper.assertFalse(history.session().canControl() || history.session().canCancel(), "archived history remains read-only");
            helper.assertValueEqual(ConversationService.view(player, id).session().revision(), result.session().revision(),
                    "opening archived history does not change saved state");
            var outsider = fixture.player();
            UUID outsideView = UUID.randomUUID();
            var outsideTopics = ConversationPresentationService.respond(outsider, new CampaignConversationRequestPayload(outsideView, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.TOPICS, null, 0, "", "", 0, 0));
            helper.assertTrue(outsideTopics.topics().stream().noneMatch(topic -> topic.id().equals(id.toString())),
                    "history is private to its owner or saved party");
            var denied = ConversationPresentationService.respond(outsider, new CampaignConversationRequestPayload(outsideView, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.HISTORY, id, 0, "", "", 0, 0));
            helper.assertTrue(denied.session() == null && !denied.diagnostic().isBlank(), "forged archived history requests remain private");
            ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(view, UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.CLOSE, id, 0, "", "", 0, 0));
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ordinary_conversations")
    public static void ordinaryProvidersHaveIndependentLocksAndCannotBorrowCampaignCharacters(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            install(fixture);
            fixture.base.mode("legacy");
            var first = fixture.player();
            var second = fixture.player();
            var other = helper.spawn(EntityType.VILLAGER, 2, 1, 2);
            other.setNoAi(true);
            var a = ConversationService.start(first, ID, "guidance", UUID.randomUUID(), fixture.mentor);
            var b = ConversationService.start(second, ID, "guidance", UUID.randomUUID(), other);
            helper.assertTrue(a.diagnostic().isBlank() && b.diagnostic().isBlank(), "different providers can converse independently");
            fixture.storage.reload();
            helper.assertTrue(ConversationService.isCharacterReserved(fixture.mentor) && ConversationService.isCharacterReserved(other),
                    "both reservations survive reload");
            var denied = ConversationService.start(second, ID, "guidance", UUID.randomUUID(), fixture.mentor);
            helper.assertTrue(denied.session() == null && !denied.diagnostic().isBlank(), "another player's session cannot be borrowed");
        }
        helper.succeed();
    }

    private static void install(CampaignConversationGameTests.Fixture fixture) throws IOException {
        install(fixture, false);
    }

    private static void install(CampaignConversationGameTests.Fixture fixture, boolean effects) throws IOException {
        var quest = CampaignCharacterTestFixture.resource("quests/campaign_fixture/characters/quest.json");
        quest.getAsJsonObject("metadata").remove("campaign");
        quest.getAsJsonObject("provider").remove("campaign_role");
        quest.add("stages", JsonParser.parseString("[{\"id\":\"visit\",\"objectives\":[]}]"));
        var graph = fixture.graph.deepCopy();
        graph.remove("campaign");
        graph.addProperty("character", "provider");
        graph.addProperty("quest", "campaign_fixture:characters");
        if (effects) graph.getAsJsonArray("nodes").get(0).getAsJsonObject().add("effects", JsonParser.parseString(
                "[{\"id\":\"accept\",\"action\":{\"type\":\"quest\",\"action\":\"start\",\"required\":true}},"
                + "{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":7,\"required\":true}}]"));
        for (var raw : graph.getAsJsonArray("nodes")) for (var exchange : raw.getAsJsonObject().getAsJsonArray("exchanges")) {
            var speaker = exchange.getAsJsonObject().getAsJsonObject("speaker");
            if (speaker.get("type").getAsString().equals("role")) speaker.addProperty("role", "provider");
        }
        var locale = CampaignCharacterTestFixture.resource("quests/campaign_fixture/characters/locales/en_us.json");
        String root = "quests/campaign_fixture/characters/";
        var bundles = QuestBundleTransactions.compile(List.of(
                QuestBundleTransactions.RawResource.valid(0, "ordinary", ResourceLocation.parse("campaign_fixture:" + root + "quest.json"), quest),
                QuestBundleTransactions.RawResource.valid(0, "ordinary", ResourceLocation.parse("campaign_fixture:" + root + "locales/en_us.json"), locale),
                QuestBundleTransactions.RawResource.valid(0, "ordinary", ResourceLocation.parse("campaign_fixture:" + root + "conversations/mentor.json"), graph)),
                QuestBundleTransactions.CompatibilityRules.empty());
        fixture.helper.assertTrue(bundles.diagnostics().isEmpty(), "ordinary bundle compiles: " + bundles.diagnostics());
        var quests = VillagerQuestResources.snapshotForCatalog(fixture.base.server, bundles);
        var conversations = ConversationResources.compile(bundles, Set.of(), quests.quests().keySet());
        fixture.helper.assertTrue(conversations.diagnostics().isEmpty(), "ordinary conversation compiles: " + conversations.diagnostics());
        var catalog = new QuestContentCatalog(fixture.base.catalog.generation() + 1, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                quests.triggerEventQuestIds(), Map.of(), Map.of(), List.of(), bundles.bundles(), bundles.localization(),
                QuestRewardCatalog.fromBundles(bundles.bundles()), conversations.conversations());
        QuestContentCatalogs.installForTests(fixture.base.server, catalog, fixture.base.report);
    }
}
