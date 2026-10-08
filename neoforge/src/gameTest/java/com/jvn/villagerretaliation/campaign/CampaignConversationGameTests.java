package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationCompiler;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.party.PartySavedData;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundlePath;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("campaign_fixture:mentor_conversation");
    private CampaignConversationGameTests() { }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void exchangesChoicesAndRollsResumeWithoutReplay(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = fixture.player();
            var result = fixture.start(player);
            UUID id = result.session().identity().id();
            helper.assertValueEqual(result.session().transcript().getFirst().speaker(), fixture.mentor.getUUID(), "mentor speaks first");
            var advance = UUID.randomUUID();
            long revision = result.session().revision();
            result = ConversationService.request(player, id, revision, advance, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), player.getUUID(), "player exchange retains its speaker");
            fixture.storage.reload();
            var duplicate = ConversationService.request(player, id, revision, advance, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(duplicate.session(), result.session(), "duplicate advance returns saved presentation");
            var stale = ConversationService.request(player, id, revision, UUID.randomUUID(), ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(stale.diagnostic().isBlank(), "old node intent is rejected");
            helper.assertValueEqual(stale.session(), result.session(), "old intent does not advance");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().transcript().getLast().kind(), ConversationDefinition.SpeakerKind.NARRATOR, "narration follows in authored order");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.CHECK, "selected response waits for a roll");
            UUID roll = UUID.randomUUID();
            revision = result.session().revision();
            result = ConversationService.request(player, id, revision, roll, ConversationSession.Intent.ROLL, "");
            var check = result.session().checks().get("convince");
            helper.assertTrue(check != null && check.die() >= 1 && check.die() <= 20, "server commits a d20 result");
            fixture.storage.reload();
            var replay = ConversationService.request(player, id, revision, roll, ConversationSession.Intent.ROLL, "");
            helper.assertValueEqual(replay.session().checks().get("convince"), check, "reconnect cannot reroll");
            var reroll = fixture.intent(player, replay, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(reroll.diagnostic().isBlank(), "fresh roll request cannot reroll the check");
            result = fixture.intent(player, replay, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().node(), check.destination(), "saved result chooses the branch");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "fixture reaches an authored outcome");
            fixture.storage.reload();
            helper.assertValueEqual(ConversationService.view(player, id).session(), result.session(), "completed history survives reload");
            helper.assertTrue(VillagerConversationService.start(player, fixture.mentor), "ordinary interaction resumes after completion");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void listenersCannotAdvanceAndExplicitHandoverPreservesSpeakers(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var leader = fixture.player();
            var listener = fixture.player();
            var outsider = fixture.player();
            fixture.party(leader, listener);
            PartyService.setAdminPrivileges(leader, listener.getUUID(), false);
            var result = fixture.start(leader);
            UUID id = result.session().identity().id();
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            helper.assertTrue(result.session().participants().contains(listener.getUUID()), "listener joins the session");
            var denied = fixture.intent(listener, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "unchecked party member cannot advance");
            helper.assertValueEqual(denied.session().revision(), result.session().revision(), "denied intent cannot consume a revision");
            helper.assertTrue(ConversationService.view(outsider, id).session() == null, "another party cannot inspect the session");
            helper.assertTrue(ConversationService.start(outsider, ID, "guidance", UUID.randomUUID()).session() == null, "character cannot start a competing session");
            helper.assertFalse(VillagerConversationService.start(leader, fixture.mentor), "ordinary dialogue respects campaign character lock");
            PartyService.setAdminPrivileges(leader, listener.getUUID(), true);
            denied = fixture.intent(listener, result, ConversationSession.Intent.TAKE_CONTROL, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "checked member cannot steal a live controller's turn");
            result = fixture.intent(leader, result, ConversationSession.Intent.HAND_OVER, listener.getUUID().toString());
            helper.assertValueEqual(result.session().controller(), listener.getUUID(), "leader explicitly transfers control");
            result = fixture.intent(listener, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().transcript().getLast().speaker(), listener.getUUID(), "new player exchange records the new speaker");
            helper.assertValueEqual(result.session().transcript().getFirst().speaker(), fixture.mentor.getUUID(), "earlier speakers remain unchanged");
            fixture.storage.reload();
            PartyService.setAdminPrivileges(leader, listener.getUUID(), false);
            ConversationTestStorage.maintain(fixture.base.server);
            result = ConversationService.view(leader, id);
            helper.assertTrue(result.session().controller() == null, "revoked permission pauses session");
            result = fixture.intent(leader, result, ConversationSession.Intent.TAKE_CONTROL, "");
            helper.assertValueEqual(result.session().controller(), leader.getUUID(), "authorized leader resumes same node");
            ConversationService.onLogout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(leader));
            fixture.storage.reload();
            helper.assertTrue(ConversationService.view(listener, id).session().controller() == null, "disconnect pause is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void completedEffectsAreNotRepeatedAndUncertainEffectsStayBlocked(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.effects("[{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":7,\"required\":true}}]");
            var player = fixture.player();
            int experience = player.totalExperience;
            var result = fixture.start(player);
            UUID id = result.session().identity().id();
            helper.assertValueEqual(player.totalExperience, experience + 7, "effect applies once");
            fixture.storage.reload();
            result = ConversationService.start(player, ID, "guidance", id);
            helper.assertValueEqual(player.totalExperience, experience + 7, "duplicate start does not award twice");
            helper.assertValueEqual(result.session().receipts().size(), 1, "effect has a saved receipt");
            fixture.storage.leavePrepared(id);
            fixture.storage.reload();
            result = ConversationService.view(player, id);
            result = fixture.intent(player, result, ConversationSession.Intent.RESUME, "");
            helper.assertTrue(result.diagnostic().contains("completion receipt"), "uncertain non-idempotent effect requires inspection");
            helper.assertValueEqual(player.totalExperience, experience + 7, "uncertain execution never blindly replays");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.NODE_EFFECTS, "required uncertainty blocks transitions");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void requiredFailuresStopLaterEffectsAndOptionalFailuresContinue(GameTestHelper helper) throws IOException {
        for (boolean required : List.of(true, false)) try (var fixture = new Fixture(helper)) {
            fixture.effects("[{\"id\":\"missing\",\"action\":{\"type\":\"start_scene\",\"scene_id\":\"campaign_fixture:missing\",\"operation_id\":\"missing\",\"required\":" + required
                    + "}},{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":9}}]");
            var player = fixture.player();
            int before = player.totalExperience;
            var result = fixture.start(player);
            helper.assertTrue(result.session() != null, "failed start still returns its durable session identity");
            helper.assertValueEqual(player.totalExperience, before + (required ? 0 : 9), "required failure controls subsequent effects");
            helper.assertValueEqual(result.session().phase(), required ? ConversationSession.Phase.NODE_EFFECTS : ConversationSession.Phase.EXCHANGES,
                    "required failure controls transition");
            fixture.storage.reload();
            if (required) {
                long revision = result.session().revision();
                ConversationTestStorage.maintain(fixture.base.server);
                ConversationTestStorage.maintain(fixture.base.server);
                helper.assertValueEqual(ConversationService.view(player, result.session().identity().id()).session().revision(), revision,
                        "unavailable effects wait for an explicit retry without rewriting storage");
                result = fixture.intent(player, ConversationService.view(player, result.session().identity().id()), ConversationSession.Intent.RESUME, "");
                helper.assertValueEqual(player.totalExperience, before, "retry does not bypass required failure");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void suspendedScenesResumeAutomaticallyWithTheOriginalEffectRecipient(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.effects("[{\"id\":\"greeting\",\"action\":{\"type\":\"start_scene\",\"scene_id\":\"campaign_fixture:character_greeting\",\"operation_id\":\"greet\",\"wait_for_result\":true,\"required\":true}},"
                    + "{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":11}}]");
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            var progress = VillagerQuestSavedData.get(helper.getLevel()).getOrCreate(leader.getUUID(), fixture.base.campaignId);
            progress.start(fixture.mentor.getUUID(), helper.getLevel().dimension(), fixture.mentor.blockPosition(), helper.getLevel().getGameTime());
            var result = fixture.start(leader);
            helper.assertTrue(result.session() != null, "scene suspension retains session identity: " + result.diagnostic());
            UUID id = result.session().identity().id();
            var receipt = result.session().receipts().values().iterator().next();
            helper.assertTrue(receipt.result().suspended(), "scene suspends before the reward: " + receipt.result());
            int before = leader.totalExperience;
            int memberBefore = member.totalExperience;
            fixture.storage.reload();
            result = fixture.intent(leader, ConversationService.view(leader, id), ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            var sceneData = SceneSavedData.get(helper.getLevel());
            var scene = sceneData.get(receipt.result().suspensionId()).orElseThrow();
            scene.complete(helper.getLevel().getGameTime());
            sceneData.setDirty();
            ConversationTestStorage.maintain(fixture.base.server);
            result = ConversationService.view(member, id);
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.EXCHANGES, "completed scene resumes presentation automatically: " + result.diagnostic());
            helper.assertValueEqual(leader.totalExperience, before + 11, "pending reward belongs to the original actor");
            helper.assertValueEqual(member.totalExperience, memberBefore, "handover does not redirect the reward");
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(leader.totalExperience, before + 11, "recovery does not repeat the reward");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void missingContentAndSandboxPreserveDormantSessions(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = fixture.player();
            fixture.base.mode("sandbox");
            helper.assertTrue(ConversationService.start(player, ID, "guidance", UUID.randomUUID()).session() == null, "sandbox does not create campaign sessions");
            fixture.base.mode("campaign");
            var result = fixture.start(player);
            UUID id = result.session().identity().id();
            var saved = result.session();
            fixture.install(null);
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().contains("missing"), "missing graph explains the blocked path");
            helper.assertValueEqual(result.session(), saved, "missing content preserves readable history");
            fixture.storage.reload();
            fixture.install(fixture.graph);
            result = fixture.intent(player, ConversationService.view(player, id), ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "restored compatible content resumes: " + result.diagnostic());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_runtime", timeoutTicks = 200)
    public static void forcedInterruptionsPauseAdvancementUntilTheyFinish(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            var player = fixture.player();
            helper.assertTrue(VillagerConversationService.start(player, fixture.mentor), "ordinary conversation opens first");
            helper.assertTrue(ConversationService.start(player, ID, "guidance", UUID.randomUUID()).session() == null, "campaign start respects ordinary lock");
            VillagerConversationService.endForPlayer(player, false);
            var result = fixture.start(player);
            helper.assertTrue(VillagerConversationService.startForced(player, fixture.mentor), "forced interruption can run");
            var denied = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "campaign advancement pauses during interruption");
            helper.assertValueEqual(denied.session(), result.session(), "interruption preserves the current node");
            VillagerConversationService.endForPlayer(player, false);
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertTrue(result.diagnostic().isBlank(), "conversation resumes after interruption");
        }
        helper.succeed();
    }

    static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final CampaignCharacterTestFixture base;
        final ConversationTestStorage storage;
        final PartySavedData priorParties;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final Villager mentor;
        JsonObject graph;

        Fixture(GameTestHelper helper) throws IOException { this(helper, false); }

        Fixture(GameTestHelper helper, boolean replacement) throws IOException { this(helper, replacement, false); }

        Fixture(GameTestHelper helper, boolean replacement, boolean impact) throws IOException { this(helper, replacement, impact, false); }

        Fixture(GameTestHelper helper, boolean replacement, boolean impact, boolean fixedOutcome) throws IOException {
            this.helper = helper;
            base = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(), quest -> {
                if (impact) {
                    var stage = quest.getAsJsonArray("stages").get(0).getAsJsonObject();
                    stage.remove("on_enter");
                    stage.add("objectives", JsonParser.parseString("[{\"id\":\"decide\",\"type\":\"choice\"}]"));
                }
            }, campaign -> {
                if (impact) campaign.add("milestones", JsonParser.parseString("""
                        [{"id":"decision","chapter":"arrival","quest":"campaign_fixture:characters","stage":"visit","irreversible":true,
                          "outcomes":[{"id":"help","title_key":"campaign_fixture.campaign.characters.mentor"},
                                      {"id":"leave","title_key":"campaign_fixture.campaign.characters.home"}],
                          "catch_up":{"quest":"campaign_fixture:characters","stage":"report"}}]
                        """));
                if (impact && fixedOutcome) campaign.getAsJsonArray("milestones").get(0).getAsJsonObject().getAsJsonArray("outcomes").remove(1);
            });
            storage = new ConversationTestStorage(helper.getLevel());
            priorParties = PartySavedData.get(helper.getLevel());
            base.server.overworld().getDataStorage().set("villagerretaliation_parties", new PartySavedData());
            if (replacement) {
                var campaign = base.raw();
                var role = campaign.getAsJsonArray("characters").get(0).getAsJsonObject();
                var successor = role.deepCopy();
                successor.addProperty("id", "successor");
                campaign.getAsJsonArray("characters").add(successor);
                role.addProperty("loss_policy", "replacement");
                role.addProperty("replacement_role", "successor");
                base.install(campaign);
            }
            mentor = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
            mentor.setNoAi(true);
            helper.assertValueEqual(base.resolve().entity(), mentor, "fixture binds the mentor");
            graph = CampaignCharacterTestFixture.resource("quests/campaign_fixture/characters/conversations/mentor.json");
            install(graph);
        }

        void effects(String effects) {
            graph.getAsJsonArray("nodes").get(0).getAsJsonObject().add("effects", JsonParser.parseString(effects));
            install(graph);
        }

        void install(JsonObject graph) {
            var old = base.catalog;
            var original = old.bundles().values().iterator().next();
            var bundle = new QuestBundleTransactions.EffectiveBundle(original.owner(), original.questId(), original.localizationPrefix(),
                    graph == null ? Map.of() : Map.of(QuestBundlePath.Kind.CONVERSATION, Map.of(ID, graph)), Map.of(), original.locales(), java.util.Set.of());
            Map<ResourceLocation, ConversationDefinition> conversations = Map.of();
            if (graph != null) {
                var result = ConversationCompiler.compile(bundle, ID);
                helper.assertTrue(result.valid(), "conversation fixture compiles: " + result.diagnostics());
                conversations = Map.of(ID, result.definition());
            }
            var catalog = new QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                    old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                    old.scenes(), old.encounters(), old.pools(), old.bundles(), old.localization(), old.rewards(), conversations);
            var campaigns = CampaignResources.current(base.server).campaigns();
            QuestContentCatalogs.installForTests(base.server, catalog, base.report);
            CampaignResources.installForTests(base.server, new CampaignContentCatalog(catalog, campaigns, List.of()));
        }

        void party(ServerPlayer leader, ServerPlayer member) {
            helper.assertTrue(PartyService.createParty(leader).success(), "fixture party created");
            var invitation = PartyService.sendInvitation(leader, member);
            helper.assertTrue(PartyService.acceptInvitation(member, invitation.invitationId()).success(), "fixture member joins");
        }

        ServerPlayer player() {
            return player(new GameProfile(UUID.randomUUID(), "conv_" + UUID.randomUUID().toString().substring(0, 8)));
        }

        ServerPlayer reconnect(ServerPlayer previous) { return player(previous.getGameProfile()); }

        private ServerPlayer player(GameProfile profile) {
            var cookie = CommonListenerCookie.createInitial(profile, false);
            var player = new ServerPlayer(base.server, helper.getLevel(), profile, cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channels.add(new EmbeddedChannel(connection));
            base.server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setPos(mentor.getX() + 1, mentor.getY(), mentor.getZ());
            players.add(player);
            return player;
        }

        ConversationService.Result start(ServerPlayer player) {
            var result = ConversationService.start(player, ID, "guidance", UUID.randomUUID());
            helper.assertTrue(result.session() != null, "conversation begins: " + result.diagnostic());
            return result;
        }

        ConversationService.Result intent(ServerPlayer player, ConversationService.Result result, ConversationSession.Intent intent, String argument) {
            return ConversationService.request(player, result.session().identity().id(), result.session().revision(), UUID.randomUUID(), intent, argument);
        }

        @Override public void close() throws IOException {
            for (var player : players) {
                com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService.onLogout(
                        new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
                VillagerConversationService.endForPlayer(player, false);
                PartyService.leaveParty(player);
                base.server.getPlayerList().remove(player);
            }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
            mentor.discard();
            base.server.overworld().getDataStorage().set("villagerretaliation_parties", priorParties);
            storage.close();
            base.close();
        }
    }
}
