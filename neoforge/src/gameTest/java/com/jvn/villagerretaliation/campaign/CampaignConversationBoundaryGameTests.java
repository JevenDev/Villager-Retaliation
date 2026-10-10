package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationQueries;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class CampaignConversationBoundaryGameTests {
    private CampaignConversationBoundaryGameTests() { }

    @GameTest(template = "empty", batch = "conversation_boundaries", timeoutTicks = 200)
    public static void topicQueriesExplainHiddenRequirementsAndKeepPresentationOrder(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var declared = fixture.graph.getAsJsonArray("topics");
            var hidden = declared.get(0).getAsJsonObject().deepCopy();
            hidden.addProperty("id", "trusted");
            hidden.add("conditions", JsonParser.parseString("[{\"type\":\"reputation\",\"min_reputation\":1000000}]"));
            declared.add(hidden);
            fixture.install(fixture.graph);
            var player = fixture.player();
            var topics = ConversationQueries.topics(player, fixture.mentor);
            helper.assertValueEqual(topics.stream().map(ConversationQueries.Topic::id).toList(), List.of("guidance", "trusted"), "query retains authored order");
            helper.assertFalse(topics.getLast().eligible() || topics.getLast().available(), "unmet topic remains unavailable");
            helper.assertTrue(topics.getLast().reason().contains("1000000"), "query explains the unmet requirement");
            var payload = ConversationPresentationService.respond(player, new CampaignConversationRequestPayload(UUID.randomUUID(), UUID.randomUUID(),
                    fixture.mentor.getId(), fixture.mentor.getUUID(), CampaignConversationRequestPayload.Action.TOPICS, null, 0, "", "", 0, 0));
            helper.assertValueEqual(payload.topics().stream().map(topic -> topic.id()).toList(), List.of("guidance"), "existing screen keeps unavailable topics hidden");
            try { topics.clear(); helper.fail("topic results must be immutable"); }
            catch (UnsupportedOperationException expected) { }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_boundaries", timeoutTicks = 200)
    public static void queryViewsCaptureParticipationAndControlAcrossHandover(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var leader = fixture.player(); var member = fixture.player(); var outsider = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            var started = fixture.start(leader);
            UUID id = started.session().identity().id();
            var before = ConversationQueries.view(leader, id);
            helper.assertTrue(before.control().canControl() && before.control().handover().stream().anyMatch(value -> value.id().equals(member.getUUID())), "query offers the authorized member");
            var handed = fixture.intent(leader, started, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            var after = ConversationQueries.view(member, id);
            helper.assertTrue(after.control().canControl() && after.snapshot().participants().contains(member.getUUID()), "new controller appears in the current query");
            helper.assertValueEqual(before.snapshot().controller(), leader.getUUID(), "previous query keeps its controller");
            helper.assertValueEqual(after.actingPlayer(), leader.getUUID(), "handover preserves the original effect actor");
            var stale = ConversationService.request(member, id, before.snapshot().revision(), UUID.randomUUID(), ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(stale.session().revision(), handed.session().revision(), "immutable expected revision still rejects stale transitions");
            helper.assertFalse(stale.diagnostic().isBlank(), "stale transition retains its diagnostic");
            helper.assertTrue(ConversationQueries.view(outsider, id).snapshot() == null && ConversationQueries.saved(outsider, id) == null, "queries enforce session membership");
            fixture.storage.reload();
            helper.assertValueEqual(ConversationQueries.view(member, id).snapshot(), after.snapshot(), "query state survives the storage adapter");
            try { after.snapshot().participants().clear(); helper.fail("query participants must be immutable"); }
            catch (UnsupportedOperationException expected) { }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_boundaries", timeoutTicks = 200)
    public static void injectedEffectsPreserveDurableOrderingAndOriginalRecipients(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.effects("[{\"id\":\"missing\",\"action\":{\"type\":\"start_scene\",\"quest_id\":\"campaign_fixture:characters\",\"scene_id\":\"campaign_fixture:missing\",\"operation_id\":\"missing\",\"required\":true}},"
                    + "{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":9}}]");
            var leader = fixture.player(); var member = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(leader.getUUID(), fixture.base.campaignId);
            progress.start(fixture.mentor.getUUID(), helper.getLevel().dimension(), fixture.mentor.blockPosition(), helper.getLevel().getGameTime());
            UUID originalRun = progress.beginRun(leader.getUUID(), fixture.base.campaignId, null);
            var started = fixture.start(leader);
            UUID id = started.session().identity().id();
            fixture.intent(leader, started, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            var calls = new ArrayList<ConversationTestStorage.EffectCall>();
            String issue = ConversationTestStorage.driveWithEffects(member, id, call -> {
                calls.add(call);
                try {
                    var receipt = fixture.storage.durable(id).receipts().get(call.effect());
                    helper.assertValueEqual(receipt.state(), ConversationSession.ReceiptState.PREPARED, "effect intent is durable before the executor runs");
                    helper.assertValueEqual(receipt.actor(), leader.getUUID(), "prepared receipt keeps the original actor");
                    helper.assertValueEqual(receipt.questRun(), originalRun, "prepared receipt keeps the original quest run");
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
                return ActionResult.unavailable("fixture waiting", VillagerActionRegistry.capabilities(call.action()), VillagerActionResult.EMPTY);
            });
            helper.assertValueEqual(issue, "fixture waiting", "required typed unavailability blocks advancement");
            helper.assertValueEqual(calls.size(), 1, "later effects do not run after the required failure");
            fixture.storage.reload();
            calls.clear();
            issue = ConversationTestStorage.driveWithEffects(member, id, call -> {
                calls.add(call);
                helper.assertValueEqual(call.actor(), leader.getUUID(), "replacement executor keeps the deciding player");
                helper.assertValueEqual(call.controller(), member.getUUID(), "replacement executor sees the current controller separately");
                if (call.action().amount() == 9) helper.assertValueEqual(call.replacements().get("boundary"), "captured", "effects receive the prior saved replacements");
                return ActionResult.success(new VillagerActionResult(true, "", "", Map.of("boundary", "captured"), false), VillagerActionRegistry.capabilities(call.action()));
            });
            helper.assertTrue(issue.isBlank(), "replacement executor completes the same runtime path: " + issue);
            helper.assertValueEqual(calls.stream().map(call -> call.effect().substring(call.effect().lastIndexOf('/') + 1)).toList(), List.of("missing", "gift"), "existing effect cursor preserves authored ordering");
            helper.assertValueEqual(calls.getFirst().questRun(), originalRun, "scene operation keeps the original quest run");
            helper.assertValueEqual(ConversationQueries.view(member, id).snapshot().phase(), ConversationSession.Phase.EXCHANGES, "successful effects reach the existing conversation exchanges");
            fixture.storage.reload();
            calls.clear();
            ConversationTestStorage.driveWithEffects(member, id, call -> { calls.add(call); return ActionResult.success(VillagerActionResult.success(), Set.of()); });
            helper.assertTrue(calls.isEmpty(), "resolved effects never replay after reload");
        }
        helper.succeed();
    }
}
