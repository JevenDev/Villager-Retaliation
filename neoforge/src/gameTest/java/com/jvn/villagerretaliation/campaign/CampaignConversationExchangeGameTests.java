package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.party.PartyService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationExchangeGameTests {
    private CampaignConversationExchangeGameTests() { }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void skippedLinesDoNotCastSpeakersOrReappearAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var first = line(fixture, 0);
            first.getAsJsonObject("speaker").addProperty("role", "witness");
            first.add("conditions", JsonParser.parseString("""
                    [{"type":"campaign_character","campaign":"campaign_fixture:characters","role":"witness","state":"available"}]
                    """));
            guard(line(fixture, 2), "promise");
            fixture.install(fixture.graph);
            var player = fixture.player();
            var result = fixture.start(player);
            helper.assertTrue(result.diagnostic().isBlank(), "false optional lines do not block the conversation");
            helper.assertValueEqual(result.session().exchange(), 1, "position refers to the authored exchange");
            helper.assertValueEqual(exchanges(result), List.of("player"), "only the enabled line is presented");
            helper.assertFalse(CampaignCharacterSavedData.get(helper.getLevel()).bindings().containsKey(
                    new CampaignCharacterBinding.Key(fixture.base.campaignId, "witness")), "skipped role is never cast");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "skipped tail reaches responses");
            fact(fixture, player, true);
            fixture.base.reload();
            fixture.storage.reload();
            result = ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "reload retains the completed exchange sequence");
            helper.assertValueEqual(exchanges(result), List.of("player"), "later fact changes cannot insert earlier skipped lines");
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            helper.assertTrue(report.references().stream().anyMatch(reference -> reference.relation() == CampaignReferenceReport.Relation.READS
                    && reference.to().kind() == CampaignReferenceReport.Kind.FACT && reference.to().path().equals(List.of("promise"))
                    && reference.source().pointer().equals("/nodes/0/exchanges/2")),
                    "line conditions retain their source in the dependency report");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void shownLinesStaySavedAcrossHandoverAndPersonalFactChanges(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            guard(line(fixture, 1), "promise");
            fixture.install(fixture.graph);
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            fact(fixture, leader, true);
            var result = fixture.start(leader);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            UUID request = UUID.randomUUID();
            long revision = result.session().revision();
            UUID session = result.session().identity().id();
            result = ConversationService.request(leader, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player"), "leader's personal promise enables the line");
            var transcript = result.session().transcript();
            var replay = ConversationService.request(leader, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(replay.session().transcript(), transcript, "duplicate advance cannot append the line twice");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(leader, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            fact(fixture, leader, false);
            fixture.base.reload();
            fixture.storage.reload();
            result = ConversationService.view(member, session);
            helper.assertValueEqual(result.session().transcript(), transcript, "saved speaker and line survive handover and changed facts");
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), leader.getUUID(), "saved player speech keeps its original speaker");
            result = fixture.intent(member, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player", "pause"), "new controller advances past the saved line");
            var stale = ConversationService.request(leader, session, revision, UUID.randomUUID(), ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(stale.diagnostic().isBlank(), "old revision cannot advance the new position");
            helper.assertValueEqual(stale.session().transcript(), result.session().transcript(), "stale requests preserve presentation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void futureLinesUseTheAuthorizedControllersPersonalConditions(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            guard(line(fixture, 1), "promise");
            fixture.install(fixture.graph);
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            fact(fixture, leader, true);
            var result = fixture.start(leader);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            var denied = fixture.intent(member, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(denied), List.of("mentor"), "listener cannot evaluate or consume the next line");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(leader, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            denied = fixture.intent(member, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(denied), List.of("mentor"), "revoked controller cannot consume the line");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(member, denied, ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(member, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "authorized controller advances");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "pause"), "member does not inherit the leader's personal promise");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void unavailableConditionsPauseWithoutConsumingAnExchange(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            guard(line(fixture, 1), "missing");
            fixture.install(fixture.graph);
            var player = fixture.player();
            var result = fixture.start(player);
            long revision = result.session().revision();
            var blocked = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(blocked.diagnostic().contains("Unknown campaign fact"), "unknown conditions remain recoverable");
            helper.assertValueEqual(blocked.session().revision(), revision, "unavailable advance is not committed");
            helper.assertValueEqual(exchanges(blocked), List.of("mentor"), "unavailable line is not silently skipped");
            fixture.storage.reload();
            var restored = ConversationService.view(player, result.session().identity().id());
            blocked = fixture.intent(player, restored, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(blocked), List.of("mentor"), "reload cannot bypass an unavailable condition");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void failedPresentationSaveRetainsItsChosenLinesUntilRecovery(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            guard(line(fixture, 1), "promise");
            fixture.install(fixture.graph);
            var player = fixture.player();
            fact(fixture, player, true);
            var result = fixture.start(player);
            UUID session = result.session().identity().id();
            fixture.storage.blockWrites();
            var blocked = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(blocked.diagnostic().isBlank(), "failed save returns a diagnostic");
            helper.assertValueEqual(exchanges(blocked), List.of("mentor"), "unsaved line is not published");
            fact(fixture, player, false);
            fixture.storage.restoreWrites();
            ConversationTestStorage.maintain(fixture.base.server);
            fixture.storage.reload();
            result = ConversationService.view(player, session);
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player"), "storage recovery keeps the selected line despite changed facts");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(exchanges(result), List.of("mentor", "player", "pause"), "recovered position advances once");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void allSkippedLinesReachTheAuthoredOutcome(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            for (var raw : fixture.graph.getAsJsonArray("nodes")) {
                var node = raw.getAsJsonObject();
                for (var exchange : node.getAsJsonArray("exchanges")) guard(exchange.getAsJsonObject(), "promise");
            }
            fixture.install(fixture.graph);
            var player = fixture.player();
            var result = fixture.start(player);
            helper.assertTrue(result.session().transcript().isEmpty(), "all hidden lines produce no transcript");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.RESPONSES, "empty presentation still permits choices");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "leave");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "hidden terminal line reaches its outcome");
            helper.assertValueEqual(result.session().outcome(), "guidance_declined", "authored branch remains authoritative");
            helper.assertTrue(result.session().transcript().isEmpty(), "terminal line is not fabricated");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void unavailableChoicesWaitAndRecoverWithoutLosingHistory(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var node = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject();
            for (var response : node.getAsJsonArray("responses")) guard(response.getAsJsonObject(), "promise");
            fixture.install(fixture.graph);
            var player = fixture.player();
            var result = fixture.start(player);
            while (result.session().phase() == ConversationSession.Phase.EXCHANGES)
                result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.responses().stream().noneMatch(ConversationService.Response::available), "all choices wait for their requirements");
            helper.assertTrue(result.diagnostic().contains("Waiting"), "waiting has an explicit recovery explanation");
            var history = result.session().transcript();
            fact(fixture, player, true);
            fixture.storage.reload();
            result = ConversationService.view(player, result.session().identity().id());
            helper.assertTrue(result.responses().stream().allMatch(ConversationService.Response::available), "changed facts enable choices");
            helper.assertValueEqual(result.session().transcript(), history, "refresh does not replay lines or effects");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "leave");
            while (result.session().phase() == ConversationSession.Phase.EXCHANGES)
                result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "recovered choice completes normally");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void cancellationReleasesSpeakersAndRetainsSavedHistory(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            var result = fixture.start(player);
            var history = result.session().transcript();
            result = fixture.intent(player, result, ConversationSession.Intent.CANCEL, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CANCELLED, "cancellation is terminal");
            fixture.storage.reload();
            result = ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(result.session().transcript(), history, "cancellation retains transcript");
            helper.assertFalse(ConversationService.isCharacterReserved(fixture.mentor), "cancelled session releases speaker");
            helper.assertTrue(fixture.start(player).session() != null, "another conversation can begin");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_exchanges")
    public static void explicitMigrationAcceptsPresentationAndRejectsGameplayChanges(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            var result = fixture.start(player);
            var history = result.session().transcript();
            fixture.graph.addProperty("revision", fixture.graph.get("revision").getAsInt() + 1);
            line(fixture, 0).add("camera", JsonParser.parseString("{\"target\":\"none\"}"));
            fixture.install(fixture.graph);
            helper.assertFalse(ConversationService.view(player, result.session().identity().id()).diagnostic().isBlank(), "changed content needs explicit migration");
            result = fixture.intent(player, result, ConversationSession.Intent.MIGRATE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "compatible presentation migration resumes");
            helper.assertValueEqual(result.session().transcript(), history, "migration retains acknowledged history");
            fixture.storage.reload();
            helper.assertTrue(ConversationService.view(player, result.session().identity().id()).diagnostic().isBlank(), "accepted content survives reload");
            guard(line(fixture, 0), "promise");
            fixture.install(fixture.graph);
            var denied = fixture.intent(player, result, ConversationSession.Intent.MIGRATE, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "changed eligibility cannot be migrated as presentation");
            helper.assertValueEqual(denied.session().revision(), result.session().revision(), "rejected migration does not write");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        return new CampaignConversationGameTests.Fixture(helper, false, false, false, campaign -> {
            campaign.add("facts", JsonParser.parseString("[{\"id\":\"promise\",\"scope\":\"player\",\"kind\":\"tag\"}]"));
            var witness = campaign.getAsJsonArray("characters").get(0).getAsJsonObject().deepCopy();
            witness.addProperty("id", "witness");
            campaign.getAsJsonArray("characters").add(witness);
        });
    }

    private static JsonObject line(CampaignConversationGameTests.Fixture fixture, int index) {
        return fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(index).getAsJsonObject();
    }

    private static void guard(JsonObject exchange, String fact) {
        var condition = new JsonObject();
        condition.addProperty("type", "campaign_fact");
        condition.addProperty("campaign", "campaign_fixture:characters");
        condition.addProperty("fact", fact);
        condition.addProperty("value", "true");
        var conditions = new com.google.gson.JsonArray();
        conditions.add(condition);
        exchange.add("conditions", conditions);
    }

    private static void fact(CampaignConversationGameTests.Fixture fixture, ServerPlayer player, boolean value) throws IOException {
        var data = CampaignSavedData.get(player.serverLevel());
        var definition = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId).definition();
        var instance = data.instances().get(definition.id());
        long revision = instance.players().getOrDefault(player.getUUID(), CampaignInstance.PlayerRecord.empty()).revision();
        var result = data.commitFact(definition, player.getUUID(), "promise", CampaignFacts.Mutation.SET,
                Boolean.toString(value), UUID.randomUUID(), revision, player.serverLevel().getGameTime());
        fixture.helper.assertValueEqual(result.status(), CampaignSavedData.Status.APPLIED, "personal fixture fact commits");
    }

    private static List<String> exchanges(ConversationService.Result result) {
        return result.session().transcript().stream().map(ConversationSession.Line::exchange).toList();
    }
}
