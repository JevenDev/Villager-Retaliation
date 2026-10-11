package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload.Action;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.party.PartyService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationPresentationGameTests {
    private CampaignConversationPresentationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void topicsStartThroughTheOrdinaryScreenAndReconnectToTheSameSession(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player();
            helper.assertTrue(VillagerConversationService.start(player, fixture.mentor), "ordinary interaction starts");
            UUID view = UUID.randomUUID();
            var topics = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
            helper.assertValueEqual(topics.topics().size(), 1, "bound character offers its authored topic");
            var topic = topics.topics().getFirst();
            var started = send(player, fixture, view, Action.START, null, topic.conversation(), topic.id(), 0, 0);
            helper.assertTrue(started.session() != null && started.session().canControl(), "topic starts a controllable conversation: " + started.diagnostic());
            helper.assertFalse(VillagerConversationService.isConversing(fixture.mentor), "campaign presentation does not freeze the actor through an ordinary session");
            var advance = packet(fixture, view, Action.ADVANCE, started, "", "", 0, 0);
            var next = ConversationPresentationService.respond(player, advance);
            var duplicate = ConversationPresentationService.respond(player, advance);
            helper.assertValueEqual(duplicate.session().revision(), next.session().revision(), "duplicate packet cannot advance another exchange");
            helper.assertValueEqual(duplicate.session().line(), next.session().line(), "duplicate packet returns the recorded speaker");
            var stale = send(player, fixture, view, Action.ADVANCE, started, "", "", 0, 0);
            helper.assertValueEqual(stale.session().revision(), next.session().revision(), "old revision cannot advance");
            helper.assertFalse(stale.diagnostic().isBlank(), "stale intent explains its refresh");
            UUID reopened = UUID.randomUUID();
            var resumed = send(player, fixture, reopened, Action.TOPICS, null, "", "", 0, 0);
            helper.assertValueEqual(resumed.session().id(), started.session().id(), "reopening presents the same saved session");
            send(player, fixture, view, Action.CLOSE, next, "", "", 0, 0);
            helper.assertTrue(ConversationService.view(player, started.session().id()).session().controller().equals(player.getUUID()), "old screen cannot close the reopened view");
            send(player, fixture, reopened, Action.CLOSE, resumed, "", "", 0, 0);
            fixture.storage.reload();
            helper.assertTrue(ConversationService.view(player, started.session().id()).session().controller() == null, "closing releases control durably");
            helper.assertTrue(ConversationPresentationService.tryOpen(player, fixture.mentor), "normal right-click can reopen a reserved character");
            var paused = send(player, fixture, UUID.randomUUID(), Action.TOPICS, null, "", "", 0, 0);
            helper.assertTrue(paused.session().canTakeControl(), "owner can resume after closing");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void partyListenersReceiveTheSameExchangeButCannotChoose(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var leader = fixture.player(); var member = fixture.player(); var outsider = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            UUID leaderView = UUID.randomUUID(); UUID memberView = UUID.randomUUID();
            send(leader, fixture, leaderView, Action.TOPICS, null, "", "", 0, 0);
            var current = send(leader, fixture, leaderView, Action.START, null, "campaign_fixture:mentor_conversation", "guidance", 0, 0);
            var listener = send(member, fixture, memberView, Action.TOPICS, null, "", "", 0, 0);
            helper.assertFalse(listener.session().canControl() || listener.session().canTakeControl(), "unchecked member remains a listener");
            current = send(leader, fixture, leaderView, Action.VIEW, current, "", "", 0, 0);
            helper.assertValueEqual(listener.session().line(), current.session().line(), "listener receives the shared speaker, text, and subjects");
            helper.assertTrue(listener.session().line().subjects().stream().anyMatch(value -> value.id().equals(member.getUUID())),
                    "group cameras include the joined participant");
            var denied = send(member, fixture, memberView, Action.ADVANCE, listener, "", "", 0, 0);
            helper.assertValueEqual(denied.session().revision(), listener.session().revision(), "listener cannot advance");
            helper.assertTrue(send(outsider, fixture, UUID.randomUUID(), Action.TOPICS, null, "", "", 0, 0).session() == null, "outside party receives no conversation history");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            current = send(leader, fixture, leaderView, Action.VIEW, current, "", "", 0, 0);
            helper.assertTrue(current.session().handover().stream().anyMatch(value -> value.id().equals(member.getUUID())), "authorized nearby member appears in handover options");
            current = send(leader, fixture, leaderView, Action.HAND_OVER, current, "", member.getUUID().toString(), 0, 0);
            helper.assertFalse(current.session().canControl(), "previous controller becomes a listener");
            listener = send(member, fixture, memberView, Action.VIEW, listener, "", "", 0, 0);
            helper.assertTrue(listener.session().canControl(), "new controller receives control");
            listener = send(member, fixture, memberView, Action.ADVANCE, listener, "", "", 0, 0);
            helper.assertValueEqual(listener.session().line().speaker(), leader.getUUID(), "default player speaker remains the initiator after handover");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void topicAndResponsePagesRetainAuthoredOrder(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var originalTopic = fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject();
            var topics = new com.google.gson.JsonArray();
            for (int index = 0; index < 70; index++) { var topic = originalTopic.deepCopy(); topic.addProperty("id", "topic_" + index); topics.add(topic); }
            fixture.graph.add("topics", topics);
            var welcome = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
            var originalResponse = welcome.getAsJsonArray("responses").get(0).getAsJsonObject();
            var responses = new com.google.gson.JsonArray();
            for (int index = 0; index < 70; index++) { var response = originalResponse.deepCopy(); response.addProperty("id", "reply_" + index); responses.add(response); }
            welcome.add("responses", responses);
            fixture.install(fixture.graph);
            var player = fixture.player(); UUID view = UUID.randomUUID();
            var ids = new ArrayList<String>();
            for (int page = 0; page < 3; page++) {
                var result = send(player, fixture, view, Action.TOPICS, null, "", "", page, 0);
                result.topics().forEach(topic -> ids.add(topic.id()));
                helper.assertValueEqual(result.pageCount(), 3, "all topics have a page");
            }
            helper.assertValueEqual(ids, java.util.stream.IntStream.range(0, 70).mapToObj(index -> "topic_" + index).toList(), "topics retain authored order across pages");
            var current = send(player, fixture, view, Action.START, null, "campaign_fixture:mentor_conversation", "topic_0", 0, 0);
            for (int index = 0; index < 3; index++) current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
            ids.clear();
            for (int page = 0; page < 3; page++) {
                current = send(player, fixture, view, Action.VIEW, current, "", "", page, 0);
                current.session().responses().forEach(response -> ids.add(response.id()));
            }
            helper.assertValueEqual(ids, java.util.stream.IntStream.range(0, 70).mapToObj(index -> "reply_" + index).toList(), "responses retain authored order across pages");
            current = send(player, fixture, view, Action.CHOOSE, current, "", "reply_69", 0, 0);
            helper.assertValueEqual(current.session().phase(), ConversationSession.Phase.CHECK, "response on last page can select a check");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void longExchangesStayReadableAcrossBoundedTextPages(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player(); UUID view = UUID.randomUUID();
            var started = fixture.start(player);
            String text = "a".repeat(8191) + "\uD83D\uDCD6" + "b".repeat(8190) + "\uD83D\uDCD6" + "c".repeat(9000);
            fixture.storage.setLastText(started.session().identity().id(), text);
            var current = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
            var restored = new StringBuilder();
            int parts = current.session().line().parts();
            for (int part = 0; part < parts; part++) {
                current = send(player, fixture, view, Action.VIEW, current, "", "", 0, part);
                String chunk = current.session().line().text();
                helper.assertTrue(chunk.length() <= CampaignConversationViewPayload.MAX_TEXT_LENGTH, "exchange chunk fits the wire");
                helper.assertFalse(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1)), "text pages preserve Unicode boundaries");
                restored.append(chunk);
            }
            helper.assertValueEqual(restored.toString(), text, "every authored character remains readable");
            current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
            helper.assertValueEqual(current.session().line().part(), 0, "new exchanges begin at their first text page");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void closingDuringFailedRollStoragePausesTheSamePendingResult(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player(); UUID view = UUID.randomUUID();
            fixture.start(player);
            var current = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
            for (int index = 0; index < 3; index++) current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
            current = send(player, fixture, view, Action.CHOOSE, current, "", "appeal", 0, 0);
            var id = current.session().id();
            fixture.storage.blockWrites();
            try {
                current = send(player, fixture, view, Action.ROLL, current, "", "", 0, 0);
                helper.assertFalse(current.session().check().resolved(), "unsaved roll stays hidden");
                helper.assertFalse(current.session().canControl(), "failed storage disables choices");
                send(player, fixture, view, Action.CLOSE, current, "", "", 0, 0);
            } finally { fixture.storage.restoreWrites(); }
            ConversationTestStorage.maintain(fixture.base.server);
            fixture.storage.reload();
            var saved = ConversationService.view(player, id).session();
            helper.assertTrue(saved.controller() == null, "storage recovery cannot restore an abandoned controller");
            helper.assertTrue(saved.checks().containsKey("convince"), "the original pending roll is retained");
            var reopened = send(player, fixture, UUID.randomUUID(), Action.TOPICS, null, "", "", 0, 0);
            helper.assertValueEqual(reopened.session().check().die(), saved.checks().get("convince").die(), "reopening displays that saved roll");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void topicsFollowSavedSuccessorsWithoutCastingUnrelatedVillagers(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, true)) {
            var player = fixture.player();
            fixture.mentor.discard();
            var successor = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 1, 1, 1);
            var unrelated = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 2);
            try {
                helper.assertValueEqual(fixture.base.resolve().entity(), successor, "authored successor takes over the role");
                fixture.base.reload();
                var bindings = CampaignCharacterSavedData.get(helper.getLevel()).bindings();
                UUID view = UUID.randomUUID();
                var topics = ConversationPresentationService.respond(player, packet(successor, view, Action.TOPICS, null, "", "", 0, 0));
                helper.assertTrue(topics.topics().stream().anyMatch(topic -> topic.id().equals("guidance")), "successor offers the original role's topic");
                UUID otherView = UUID.randomUUID();
                var otherTopics = ConversationPresentationService.respond(player, packet(unrelated, otherView, Action.TOPICS, null, "", "", 0, 0));
                helper.assertTrue(otherTopics.topics().isEmpty(), "unrelated villager offers no campaign topic");
                var denied = ConversationPresentationService.respond(player, packet(unrelated, otherView, Action.START, null,
                        "campaign_fixture:mentor_conversation", "guidance", 0, 0));
                helper.assertTrue(denied.session() == null && !denied.diagnostic().isBlank(), "a forged topic cannot start on another character");
                helper.assertValueEqual(CampaignCharacterSavedData.get(helper.getLevel()).bindings(), bindings, "topic discovery never casts a character");
                ConversationPresentationService.respond(player, packet(successor, view, Action.TOPICS, null, "", "", 0, 0));
                var started = ConversationPresentationService.respond(player, packet(successor, view, Action.START, null,
                        "campaign_fixture:mentor_conversation", "guidance", 0, 0));
                helper.assertTrue(started.session() != null, "conversation begins on the saved successor: " + started.diagnostic());
                helper.assertValueEqual(started.session().line().speaker(), successor.getUUID(), "successor speaks for the authored role");
                ConversationPresentationService.respond(player, packet(successor, view, Action.CLOSE, started, "", "", 0, 0));
            } finally { successor.discard(); unrelated.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation", timeoutTicks = 200)
    public static void anotherBoundSpeakerCanReopenTheSameConversation(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, true)) {
            var speaker = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(1).getAsJsonObject().getAsJsonObject("speaker");
            speaker.addProperty("type", "role"); speaker.addProperty("role", "successor");
            fixture.install(fixture.graph);
            var secondary = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            try {
                var player = fixture.player(); UUID view = UUID.randomUUID();
                fixture.start(player);
                var current = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
                current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
                helper.assertValueEqual(current.session().line().speaker(), secondary.getUUID(), "second authored role joins the conversation");
                UUID session = current.session().id();
                send(player, fixture, view, Action.CLOSE, current, "", "", 0, 0);
                helper.assertTrue(ConversationPresentationService.tryOpen(player, secondary), "another bound speaker opens the saved conversation");
                view = UUID.randomUUID();
                current = ConversationPresentationService.respond(player, packet(secondary, view, Action.TOPICS, null, "", "", 0, 0));
                helper.assertValueEqual(current.session().id(), session, "reopening keeps the same session");
                current = ConversationPresentationService.respond(player, packet(secondary, view, Action.TAKE_CONTROL, current, "", "", 0, 0));
                helper.assertTrue(current.session().canControl(), "authorized player resumes through the bound speaker");
                current = ConversationPresentationService.respond(player, packet(secondary, view, Action.ADVANCE, current, "", "", 0, 0));
                helper.assertTrue(current.session().line().speaker() == null, "advance reaches the authored narrator exchange");
                var original = fixture.mentor.position();
                fixture.mentor.setPos(original.add(50, 0, 0));
                current = ConversationPresentationService.respond(player, packet(secondary, view, Action.VIEW, current, "", "", 0, 0));
                helper.assertFalse(current.session().canControl() || current.session().canTakeControl(), "being near a secondary speaker does not bypass the primary character range");
                fixture.mentor.setPos(original);
                ConversationPresentationService.respond(player, packet(secondary, view, Action.CLOSE, current, "", "", 0, 0));
            } finally { secondary.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation")
    public static void backgroundPollsCannotConsumeThePlayersRollOrAdvanceBudget(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player(); var view = UUID.randomUUID();
            fixture.start(player);
            var current = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
            for (int index = 0; index < 3; index++) current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
            current = send(player, fixture, view, Action.CHOOSE, current, "", "appeal", 0, 0);
            var id = current.session().id();
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(player.connection.getConnection());
            var level = fixture.base.server.overworld(); long time = level.getGameTime();
            try {
                ConversationPresentationService.handle(player, packet(fixture, view, Action.VIEW, current, "", "", 0, 0));
                ConversationPresentationService.handle(player, packet(fixture, view, Action.ROLL, current, "", "", 0, 0));
                var rolled = ConversationService.view(player, id).session();
                helper.assertValueEqual(rolled.phase(), ConversationSession.Phase.ROLLED, "a poll and roll in the same server tick still resolve the authorized roll");
                helper.assertValueEqual(rolled.checks().size(), 1, "one server-controlled check is saved");
                current = send(player, fixture, view, Action.VIEW, current, "", "", 0, 0);
                ConversationPresentationService.handle(player, packet(fixture, view, Action.ADVANCE, current, "", "", 0, 0));
                helper.assertValueEqual(ConversationService.view(player, id).session().revision(), rolled.revision(), "consecutive player actions still share the two-tick rate limit");
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time + 2);
                ConversationPresentationService.handle(player, packet(fixture, view, Action.VIEW, current, "", "", 0, 0));
                ConversationPresentationService.handle(player, packet(fixture, view, Action.ADVANCE, current, "", "", 0, 0));
                var advanced = ConversationService.view(player, id).session();
                helper.assertValueEqual(advanced.phase(), ConversationSession.Phase.EXCHANGES, "a new poll cannot suppress the next eligible advance");
                helper.assertValueEqual(advanced.checks(), rolled.checks(), "advancing retains the exact saved roll");
                current = send(player, fixture, view, Action.VIEW, current, "", "", 0, 0);
                ConversationPresentationService.handle(player, packet(fixture, view, Action.CLOSE, current, "", "", 0, 0));
                helper.assertTrue(ConversationService.view(player, id).session().controller() == null, "closing remains available while the action budget is busy");
            } finally {
                ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time);
                com.jvn.villagerretaliation.network.ServerboundRequestLimiter.clear(player.getUUID());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation")
    public static void historyReadsRetainSessionRevisionAndIncludeChoices(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player();
            UUID view = UUID.randomUUID();
            var topics = send(player, fixture, view, Action.TOPICS, null, "", "", 0, 0);
            var topic = topics.topics().getFirst();
            var current = send(player, fixture, view, Action.START, null, topic.conversation(), topic.id(), 0, 0);
            while (current.session().phase() == ConversationSession.Phase.EXCHANGES)
                current = send(player, fixture, view, Action.ADVANCE, current, "", "", 0, 0);
            current = send(player, fixture, view, Action.CHOOSE, current, "", "leave", 0, 0);
            long revision = current.session().revision();
            var history = send(player, fixture, view, Action.HISTORY, current, "", "", 0, 0);
            helper.assertValueEqual(history.session().revision(), revision, "history reading cannot execute effects");
            helper.assertTrue(history.session().history().entries().size() >= 4, "history contains exchanges and the chosen response");
            var restored = send(player, fixture, view, Action.VIEW, history, "", "", 0, 0);
            helper.assertTrue(restored.session().history() == null, "returning restores the live presentation");
            helper.assertValueEqual(restored.session().revision(), revision, "history navigation preserves revision");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_presentation")
    public static void listenersResolveTextInTheirOwnLocaleWithCapturedSubstitutions(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var owner = fixture.player();
            var listener = fixture.player();
            fixture.party(owner, listener);
            var prior = listener.clientInformation();
            listener.updateOptions(new net.minecraft.server.level.ClientInformation("fr_fr", prior.viewDistance(), prior.chatVisibility(),
                    prior.chatColors(), prior.modelCustomisation(), prior.mainHand(), prior.textFilteringEnabled(), prior.allowsListing()));
            var started = fixture.start(owner);
            var line = started.session().transcript().getFirst();
            var catalog = com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.current(fixture.base.server);
            var localized = catalog.localization().overlay("fr_fr", java.util.Map.of(line.textKey(),
                    new com.google.gson.JsonPrimitive("Bonjour, {player}.")));
            var translated = new com.jvn.villagerretaliation.quest.content.QuestContentCatalog(catalog.generation() + 1,
                    catalog.compiledQuestCatalog(), catalog.dialogueCatalog(), catalog.quests(), catalog.objectiveEventQuestIds(),
                    catalog.factQuestIds(), catalog.memoryEventQuestIds(), catalog.exclusiveGroupQuestIds(), catalog.triggerEventQuestIds(),
                    catalog.scenes(), catalog.encounters(), catalog.pools(), catalog.bundles(), localized, catalog.rewards(),
                    catalog.conversations(), catalog.socialProgressions());
            var campaigns = com.jvn.villagerretaliation.campaign.content.CampaignResources.current(fixture.base.server).campaigns();
            com.jvn.villagerretaliation.quest.content.QuestContentCatalogs.installForTests(fixture.base.server, translated, fixture.base.report);
            com.jvn.villagerretaliation.campaign.content.CampaignResources.installForTests(fixture.base.server,
                    new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog(translated, campaigns, java.util.List.of()));
            var presented = send(listener, fixture, UUID.randomUUID(), Action.TOPICS, null, "", "", 0, 0);
            helper.assertTrue(presented.session() != null, "listener opens localized session: " + presented.diagnostic());
            helper.assertValueEqual(presented.session().line().text(), "Bonjour, " + owner.getName().getString() + ".",
                    "listener language uses the original speaking player's captured placeholders");
            helper.assertValueEqual(ConversationService.view(owner, started.session().identity().id()).session().transcript().getFirst(), line,
                    "listener translation does not rewrite the historical record");
        }
        helper.succeed();
    }

    private static CampaignConversationViewPayload send(ServerPlayer player, CampaignConversationGameTests.Fixture fixture,
            UUID view, Action action, CampaignConversationViewPayload prior, String conversation, String argument, int page, int textPage) {
        return ConversationPresentationService.respond(player, packet(fixture, view, action, prior, conversation, argument, page, textPage));
    }

    private static CampaignConversationRequestPayload packet(CampaignConversationGameTests.Fixture fixture, UUID view, Action action,
            CampaignConversationViewPayload prior, String conversation, String argument, int page, int textPage) {
        return packet(fixture.mentor, view, action, prior, conversation, argument, page, textPage);
    }

    private static CampaignConversationRequestPayload packet(net.minecraft.world.entity.npc.Villager character, UUID view, Action action,
            CampaignConversationViewPayload prior, String conversation, String argument, int page, int textPage) {
        var session = prior == null ? null : prior.session();
        return new CampaignConversationRequestPayload(view, UUID.randomUUID(), character.getId(), character.getUUID(), action,
                session == null ? null : session.id(), session == null ? 0 : session.revision(), conversation, argument, page, textPage);
    }
}
