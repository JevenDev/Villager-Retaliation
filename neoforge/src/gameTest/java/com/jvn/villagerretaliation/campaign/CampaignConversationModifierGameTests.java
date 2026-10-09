package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestData;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationModifierGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("campaign_fixture:characters");

    private CampaignConversationModifierGameTests() { }

    @GameTest(template = "empty", batch = "conversation_modifiers")
    public static void standingChangesCannotRewriteAnEarlierCheck(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel());
                var standing = new CampaignStandingTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            social.data().grant(new PlayerSocialAward(UUID.randomUUID(), "training", UUID.randomUUID(), player.getUUID(),
                    SocialCheckBreakdown.capture(SocialProgressionTestData.definition(), PlayerSocialSkill.PERSUASION, 0, 10)));
            var result = ready(fixture, player);
            UUID request = UUID.randomUUID();
            long revision = result.session().revision();
            UUID session = result.session().identity().id();
            result = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "contextual roll saves: " + result.diagnostic());
            var check = result.session().checks().get("convince");
            helper.assertValueEqual(check.modifier(), -1, "skill and both signed situational contributions combine");
            helper.assertValueEqual(check.modifiers().stream().map(value -> value.applied()).toList(), List.of(true, true, false),
                    "applied and inactive authored rules retain their order");
            helper.assertValueEqual(check.modifiers().get(0).label(), "A familiar face", "bundle text is captured with the result");
            rewardFaction(fixture, standing, player);
            fixture.storage.reload();
            social.reload();
            standing.reload();
            var replay = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ROLL, "");
            helper.assertValueEqual(replay.session().checks().get("convince"), check, "later faction standing cannot change the saved roll");
            helper.assertValueEqual(social.data().receipts().size(), 2, "replay does not repeat skill experience");
            var references = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            helper.assertTrue(references.references().stream().anyMatch(reference -> reference.to().kind() == CampaignReferenceReport.Kind.REACTION
                    && reference.source().pointer().equals("/checks/0/modifiers/2/conditions/0/reaction")),
                    "modifier dependencies preserve their authored source field");
            fixture.install(null);
            helper.assertValueEqual(ConversationService.view(player, session).session().checks().get("convince"), check,
                    "missing conversation content preserves the complete readable breakdown");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_modifiers")
    public static void unavailableModifierStateBlocksBeforeDrawingAndCanRecover(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel());
                var standing = new CampaignStandingTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            var result = ready(fixture, player);
            var original = standing.data();
            var malformed = new CompoundTag(); malformed.putInt("DataVersion", 99);
            standing.install(CampaignStandingSavedData.load(malformed, standing.directory()));
            player.getRandom().setSeed(7813L);
            var denied = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(denied.session().checks().isEmpty(), "unavailable standing does not produce a check");
            helper.assertTrue(denied.diagnostic().contains("Situational modifier"), "unavailable rule explains the blocked check");
            helper.assertTrue(social.data().receipts().isEmpty(), "blocked check grants no experience");
            helper.assertValueEqual(player.getRandom().nextInt(), RandomSource.create(7813L).nextInt(), "blocked rules draw no random result");
            standing.install(original);
            player.getRandom().setSeed(412L);
            result = fixture.intent(player, denied, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "repaired state permits the pending roll: " + result.diagnostic());
            helper.assertValueEqual(result.session().checks().get("convince").die(), RandomSource.create(412L).nextInt(20) + 1,
                    "recovery draws the first uniform server result");
            helper.assertValueEqual(social.data().receipts().size(), 1, "only the resolved check awards experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_modifiers")
    public static void handoverUsesTheCurrentControllersPersonalStanding(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel());
                var standing = new CampaignStandingTestStorage(helper.getLevel())) {
            install(fixture);
            var leader = fixture.player(); var member = fixture.player();
            fixture.party(leader, member);
            rewardFaction(fixture, standing, leader);
            var result = ready(fixture, leader);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            var denied = fixture.intent(member, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(denied.session().checks().isEmpty(), "listener cannot capture or roll the leader's modifiers");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(leader, denied, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            result = fixture.intent(member, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "authorized member rolls: " + result.diagnostic());
            var check = result.session().checks().get("convince");
            helper.assertValueEqual(check.player(), member.getUUID(), "result belongs to the current controller");
            helper.assertValueEqual(check.modifier(), -3, "leader's faction support is not borrowed by the member");
            helper.assertFalse(check.modifiers().get(2).applied(), "member's own faction standing determines support");
        }
        helper.succeed();
    }

    private static void install(CampaignConversationGameTests.Fixture fixture) throws IOException {
        var graph = SocialProgressionTestData.resource("quests/campaign_fixture/characters/conversations/context_mentor.json");
        graph.addProperty("id", "campaign_fixture:mentor_conversation");
        fixture.graph = graph;
        var profile = SocialProgressionTestData.definition();
        fixture.install(graph, Map.of(profile.id(), profile));
    }

    private static ConversationService.Result ready(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var result = fixture.start(player);
        for (int index = 0; index < 3; index++) result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
        return fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
    }

    private static void rewardFaction(CampaignConversationGameTests.Fixture fixture, CampaignStandingTestStorage standing, ServerPlayer player) throws IOException {
        var reading = CampaignStandingService.read(fixture.base.server, CAMPAIGN, player.getUUID(), Scope.FACTION, "wardens");
        fixture.helper.assertTrue(reading.diagnostic().isBlank() && reading.subject() != null, "fixture standing is available");
        var key = reading.subject().key();
        standing.data().apply(new CampaignStanding.Change(CAMPAIGN, key.instance(), player.getUUID(), CAMPAIGN,
                UUID.randomUUID(), Scope.FACTION, "wardens", 20, "wardens_support"), reading.subject(), fixture.helper.getLevel().getGameTime());
    }
}
