package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignStoryObjectiveGameTests {
    private static final String SPEAK = """
            {"id":"speak","type":"campaign_speak",
             "story":{"role":"mentor","conversation":"campaign_fixture:mentor_conversation","node":"welcome"}}
            """;

    private CampaignStoryObjectiveGameTests() { }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void speakingRequiresAnAcknowledgedRoleExchangeAndSurvivesReload(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var player = fixture.player();
            accept(fixture, player);
            var result = fixture.start(player);
            helper.assertFalse(complete(fixture, player), "opening a conversation does not finish speaking");
            var request = UUID.randomUUID();
            long revision = result.session().revision();
            UUID session = result.session().identity().id();
            result = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "role exchange advances: " + result.diagnostic());
            helper.assertTrue(complete(fixture, player), "saved role exchange fulfills the authored objective");
            var duplicate = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(duplicate.session(), result.session(), "duplicate request cannot acknowledge another exchange");
            var stale = ConversationService.request(player, session, revision, UUID.randomUUID(), ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(stale.session(), result.session(), "old revision cannot advance presentation");
            fixture.storage.reload();
            helper.assertTrue(complete(fixture, player), "speaking evidence survives reload");
            var completed = new java.util.ArrayList<String>();
            helper.assertTrue(QuestObjectiveDispatchService.poll(context(fixture, player), objective -> completed.add(objective.id())),
                    "existing objective dispatch applies saved speaking evidence");
            helper.assertValueEqual(completed, java.util.List.of("visit.speak"), "exact objective completes once");
            helper.assertFalse(QuestObjectiveDispatchService.poll(context(fixture, player), objective -> completed.add(objective.id())),
                    "repeat polling does not duplicate completion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void onlyJoinedPresentPlayersWithAnActiveQuestHearTheExchange(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var leader = fixture.player();
            var listener = fixture.player();
            accept(fixture, leader);
            accept(fixture, listener);
            fixture.party(leader, listener);
            var result = fixture.start(leader);
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            helper.assertTrue(result.diagnostic().isBlank(), "party listener joins: " + result.diagnostic());
            result = fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(complete(fixture, leader) && complete(fixture, listener), "present controller and listener receive personal evidence");
            fixture.storage.reload();
            helper.assertTrue(complete(fixture, listener), "listener evidence persists independently");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void lateListenersDoNotReceiveEarlierRoleExchanges(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var leader = fixture.player();
            var listener = fixture.player();
            accept(fixture, leader);
            accept(fixture, listener);
            fixture.party(leader, listener);
            var result = fixture.intent(leader, fixture.start(leader), ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(complete(fixture, listener), "party membership alone gives no speaking credit");
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(complete(fixture, listener), "player and narrator exchanges do not backfill the mentor's line");
            fixture.storage.reload();
            helper.assertFalse(complete(fixture, listener), "reload cannot infer evidence from lifetime participants");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void absentListenersAndLaterQuestRunsCannotReuseSpeakingCredit(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var leader = fixture.player();
            var listener = fixture.player();
            accept(fixture, leader);
            accept(fixture, listener);
            fixture.party(leader, listener);
            var result = fixture.intent(listener, fixture.start(leader), ConversationSession.Intent.JOIN, "");
            listener.setPos(fixture.mentor.getX() + 80, fixture.mentor.getY(), fixture.mentor.getZ());
            fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(complete(fixture, listener), "joined listener outside the conversation has no evidence");
            var progress = context(fixture, leader).progress();
            UUID previous = progress.questRunId();
            progress.start(fixture.mentor.getUUID(), leader.serverLevel().dimension(), fixture.mentor.blockPosition(), leader.serverLevel().getGameTime());
            progress.beginRun(leader.getUUID(), fixture.base.campaignId, null);
            progress.setCurrentStage("visit");
            helper.assertFalse(previous.equals(progress.questRunId()), "fixture starts a different quest run");
            helper.assertFalse(complete(fixture, leader), "earlier run cannot fulfill a restarted quest");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void speakingWithoutAnActiveQuestDoesNotAwardFutureCredit(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var player = fixture.player();
            var result = fixture.intent(player, fixture.start(player), ConversationSession.Intent.ADVANCE, "");
            accept(fixture, player);
            helper.assertFalse(complete(fixture, player), "quest accepted after the role line has no earlier evidence");
            fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(complete(fixture, player), "advancing another speaker does not backfill credit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void failedSavesDoNotExposePendingSpeakingEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var player = fixture.player();
            accept(fixture, player);
            var result = fixture.start(player);
            fixture.storage.blockWrites();
            try {
                var failed = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
                helper.assertFalse(failed.diagnostic().isBlank(), "failed save is reported");
                helper.assertFalse(complete(fixture, player), "unsaved candidate cannot complete the objective");
            } finally {
                fixture.storage.restoreWrites();
            }
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertTrue(complete(fixture, player), "successful persistence retry exposes the saved evidence");
            fixture.storage.reload();
            helper.assertTrue(complete(fixture, player), "retry survives disk reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void oldSavesDoNotInventCreditAndMalformedEvidenceRemainsRepairable(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var player = fixture.player();
            accept(fixture, player);
            fixture.intent(player, fixture.start(player), ConversationSession.Intent.ADVANCE, "");
            var saved = ConversationSavedData.get(helper.getLevel()).save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
            var old = saved.copy();
            old.putInt("DataVersion", 5);
            old.getList("Sessions", Tag.TAG_COMPOUND).getCompound(0).remove("ObjectiveEvidence");
            fixture.storage.reload(old);
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "version five migrates");
            helper.assertFalse(complete(fixture, player), "old transcript does not fabricate objective evidence");
            var malformed = saved.copy();
            malformed.getList("Sessions", Tag.TAG_COMPOUND).getCompound(0).getList("ObjectiveEvidence", Tag.TAG_COMPOUND)
                    .getCompound(0).putUUID("Speaker", UUID.randomUUID());
            fixture.storage.reload(malformed);
            var data = ConversationSavedData.get(helper.getLevel());
            helper.assertFalse(data.diagnostic().isBlank(), "wrong bound speaker blocks malformed save");
            helper.assertFalse(complete(fixture, player), "invalid evidence cannot complete the objective");
            helper.assertValueEqual(data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess()), malformed,
                    "malformed save is preserved for repair");
            fixture.storage.reload(saved);
            helper.assertTrue(complete(fixture, player), "restoring original evidence recovers progression");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void changedOrMissingConversationContentCannotReuseEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, SPEAK)) {
            var player = fixture.player();
            accept(fixture, player);
            fixture.intent(player, fixture.start(player), ConversationSession.Intent.ADVANCE, "");
            fixture.install(null);
            helper.assertFalse(complete(fixture, player), "missing conversation leaves objective dormant");
            var changed = fixture.graph.deepCopy();
            changed.addProperty("revision", 2);
            fixture.install(changed);
            helper.assertFalse(complete(fixture, player), "incompatible graph cannot reuse saved evidence");
            fixture.install(fixture.graph);
            helper.assertTrue(complete(fixture, player), "compatible restored content recovers original evidence");
        }
        helper.succeed();
    }


    @GameTest(template = "empty", batch = "campaign_story_objectives")
    public static void outcomeScopesKeepWorldDiscoveryParticipationAndCompletionSeparate(GameTestHelper helper) throws IOException {
        String objective = """
                {"id":"resolve","type":"campaign_outcome","story":{"milestone":"decision","outcome":"help","scope":"world"}}
                """;
        try (var fixture = fixture(helper, objective)) {
            var actor = fixture.player();
            var late = fixture.player();
            accept(fixture, actor);
            var data = CampaignSavedData.get(helper.getLevel());
            var content = fixture.base.campaigns.campaigns().get(fixture.base.campaignId);
            var committed = data.commitWorldOutcome(content, "decision", "help", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, java.util.Set.of(actor.getUUID()), helper.getLevel().getGameTime());
            helper.assertValueEqual(committed.status(), CampaignSavedData.Status.APPLIED, "shared outcome commits");
            for (var scope : CampaignOutcomePredicate.Scope.values()) {
                boolean expectedActor = scope != CampaignOutcomePredicate.Scope.COMPLETED;
                helper.assertValueEqual(outcome(fixture, actor, scope), expectedActor, "actor's " + scope + " scope");
                helper.assertValueEqual(outcome(fixture, late, scope), scope == CampaignOutcomePredicate.Scope.WORLD,
                        "late player's " + scope + " scope before discovery");
            }
            data.discover(content.definition(), late.getUUID(), "decision");
            helper.assertTrue(outcome(fixture, late, CampaignOutcomePredicate.Scope.DISCOVERED), "late discovery is available");
            helper.assertFalse(outcome(fixture, late, CampaignOutcomePredicate.Scope.PARTICIPATED), "discovery grants no participation");
            data.enterMilestone(content.definition(), late.getUUID(), "decision", helper.getLevel().getGameTime());
            var completed = data.completeMilestone(content.definition(), late.getUUID(), "decision", CampaignInstance.Route.CATCH_UP,
                    "help", UUID.randomUUID(), helper.getLevel().getGameTime());
            helper.assertValueEqual(completed.status(), CampaignSavedData.Status.APPLIED, "personal catch-up completes");
            helper.assertTrue(outcome(fixture, late, CampaignOutcomePredicate.Scope.COMPLETED), "catch-up personal completion is available");
            helper.assertFalse(outcome(fixture, late, CampaignOutcomePredicate.Scope.PARTICIPATED), "catch-up never invents participation");
            fixture.base.reload();
            helper.assertTrue(outcome(fixture, late, CampaignOutcomePredicate.Scope.COMPLETED), "personal completion survives reload");
            helper.assertFalse(outcome(fixture, late, CampaignOutcomePredicate.Scope.PARTICIPATED), "reload retains scope separation");
        }
        helper.succeed();
    }

    private static boolean outcome(CampaignConversationGameTests.Fixture fixture, ServerPlayer player, CampaignOutcomePredicate.Scope scope) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"campaign_fixture:characters","metadata":{"campaign":"campaign_fixture:characters"},
                 "localization_prefix":"campaign_fixture.quest.characters","provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"visit","stages":[{"id":"visit","objectives":[{"id":"resolve","type":"campaign_outcome",
                 "story":{"milestone":"decision","outcome":"help","scope":"world"}}]}]}
                """).getAsJsonObject();
        raw.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("objectives").get(0).getAsJsonObject()
                .getAsJsonObject("story").addProperty("scope", scope.name().toLowerCase(java.util.Locale.ROOT));
        var envelope = com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope.read(fixture.base.campaignId, raw).orElseThrow();
        var quest = com.jvn.villagerretaliation.quest.QuestV2Compiler.compile(
                com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow().asQuestDefinition();
        var context = new QuestObjectiveEvaluationContext(player, null, player.serverLevel(), quest,
                new VillagerQuestSavedData.QuestProgress(), null, null, null, null, null, null);
        return QuestObjectiveRegistry.evaluate(context, quest.objectives().getFirst()).orElseThrow().complete();
    }

    static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, String objective) throws IOException {
        return new CampaignConversationGameTests.Fixture(helper, false, true, false, campaign -> { }, quest -> {
            var objectives = new com.google.gson.JsonArray();
            objectives.add(JsonParser.parseString(objective));
            quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("objectives", objectives);
        });
    }

    static void accept(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) throws IOException {
        CampaignConversationConfirmationGameTests.acceptQuest(fixture, player);
    }

    static QuestObjectiveEvaluationContext context(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var quest = fixture.base.catalog.quest(fixture.base.campaignId).orElseThrow();
        return new QuestObjectiveEvaluationContext(player, null, player.serverLevel(), quest,
                VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), quest.id()), null, null, null, null, null, null);
    }

    private static boolean complete(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var context = context(fixture, player);
        return QuestObjectiveRegistry.evaluate(context, context.definition().objectives().getFirst()).orElseThrow().complete();
    }
}
