package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import com.jvn.villagerretaliation.skill.player.SocialProgressionCompiler;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestData;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationSocialGameTests {
    private CampaignConversationSocialGameTests() { }

    @GameTest(template = "empty", batch = "conversation_social")
    public static void savedSocialChecksKeepTheirOriginalProgressionAfterReload(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            var profile = SocialProgressionTestData.definition();
            social.data().grant(new PlayerSocialAward(UUID.randomUUID(), "prior", UUID.randomUUID(), player.getUUID(),
                    SocialCheckBreakdown.capture(profile, PlayerSocialSkill.PERSUASION, 0, 10)));
            var result = ready(fixture, player);
            UUID request = UUID.randomUUID();
            long revision = result.session().revision();
            UUID session = result.session().identity().id();
            result = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ROLL, "");
            var check = result.session().checks().get("convince");
            helper.assertTrue(result.diagnostic().isBlank(), "roll and award succeed: " + result.diagnostic());
            helper.assertValueEqual(check.modifier(), 2, "roll uses the player's authored level before awarding experience");
            helper.assertValueEqual(check.total(), check.die() + 2, "server persists the full total");
            helper.assertValueEqual(check.social().experience(), 10L, "breakdown freezes prior experience");
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 20L, "roller earns the authored experience");
            helper.assertTrue(result.session().socialAwards().contains("convince"), "award acknowledgment is durable");
            fixture.storage.reload();
            social.reload();
            var replay = ConversationService.request(player, session, revision, request, ConversationSession.Intent.ROLL, "");
            helper.assertValueEqual(replay.session().checks().get("convince"), check, "duplicate roll preserves its complete breakdown");
            helper.assertValueEqual(social.data().receipts().size(), 2, "duplicate roll grants no more experience");
            var changed = SocialProgressionTestData.profile();
            changed.addProperty("revision", 2);
            changed.getAsJsonArray("skills").get(0).getAsJsonObject().getAsJsonArray("levels").get(1).getAsJsonObject().addProperty("bonus", 3);
            var next = SocialProgressionCompiler.compile(SocialProgressionTestData.bundle(changed), profile.id()).definition();
            fixture.install(fixture.graph, Map.of(next.id(), next));
            helper.assertValueEqual(ConversationService.view(player, session).session().checks().get("convince"), check, "new balancing cannot alter a saved check");
            fixture.install(null);
            helper.assertValueEqual(ConversationService.view(player, session).session().checks().get("convince"), check, "missing content keeps readable check history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_social")
    public static void listenersCannotRollAndHandoverAwardsTheAuthorizedRoller(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            install(fixture);
            var leader = fixture.player(); var member = fixture.player();
            fixture.party(leader, member);
            var result = ready(fixture, leader);
            result = fixture.intent(member, result, ConversationSession.Intent.JOIN, "");
            var denied = fixture.intent(member, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "listener has no roll authority");
            helper.assertTrue(social.data().receipts().isEmpty(), "listener earns no experience");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(leader, result, ConversationSession.Intent.HAND_OVER, member.getUUID().toString());
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            denied = fixture.intent(member, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(denied.diagnostic().isBlank(), "revoked controller cannot roll");
            helper.assertTrue(social.data().receipts().isEmpty(), "revoked authority grants no experience");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            result = fixture.intent(member, denied, ConversationSession.Intent.TAKE_CONTROL, "");
            result = fixture.intent(member, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.diagnostic().isBlank(), "authorized member rolls: " + result.diagnostic());
            helper.assertValueEqual(result.session().checks().get("convince").player(), member.getUUID(), "saved check identifies the roller");
            helper.assertValueEqual(social.data().experience(member.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "roller receives personal experience");
            helper.assertValueEqual(social.data().experience(leader.getUUID(), PlayerSocialSkill.PERSUASION), 0L, "listening leader receives no experience");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_social")
    public static void completedOfflineConversationStillDeliversItsPendingExperience(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            var result = ready(fixture, player);
            social.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertFalse(result.diagnostic().isBlank(), "failed experience save exposes a recoverable diagnostic");
            helper.assertTrue(result.session().checks().containsKey("convince"), "saved roll remains available");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "pending experience does not repeat or block the story outcome");
            var id = result.session().identity().id();
            ConversationService.onLogout(new PlayerEvent.PlayerLoggedOutEvent(player));
            fixture.base.server.getPlayerList().remove(player);
            fixture.players.remove(player);
            fixture.storage.reload();
            social.restoreWrites();
            fixture.install(null);
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "offline roller receives the durable award");
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).snapshots().get(id).socialAwards().contains("convince"),
                    "completed session acknowledges delivery without available content or a controller");
            social.reload();
            fixture.storage.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().receipts().size(), 1, "completed session cannot duplicate its award after reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_social")
    public static void unsavedRollGrantsNoExperienceUntilItsOriginalResultCommits(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            var result = ready(fixture, player);
            fixture.storage.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            helper.assertTrue(result.session().checks().isEmpty(), "unsaved roll is hidden");
            helper.assertTrue(social.data().receipts().isEmpty(), "unsaved roll earns no experience");
            fixture.storage.restoreWrites();
            ConversationTestStorage.maintain(fixture.base.server);
            var saved = ConversationService.view(player, result.session().identity().id());
            helper.assertTrue(saved.session().checks().containsKey("convince"), "recovery saves the original roll");
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "saved roll earns experience once");
            fixture.storage.reload();
            social.reload();
            helper.assertValueEqual(ConversationService.view(player, saved.session().identity().id()).session().checks(), saved.session().checks(),
                    "storage recovery cannot reroll the saved result");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_social")
    public static void failedAcknowledgmentRetriesTheExistingExperienceReceipt(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper);
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            install(fixture);
            var player = fixture.player();
            var result = ready(fixture, player);
            social.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            social.restoreWrites();
            fixture.storage.blockWrites();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "award can commit before its conversation acknowledgment");
            var id = result.session().identity().id();
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).snapshots().get(id).socialAwards().isEmpty(), "failed acknowledgment is not published");
            fixture.storage.restoreWrites();
            fixture.storage.reload();
            social.reload();
            ConversationTestStorage.maintain(fixture.base.server);
            helper.assertValueEqual(social.data().experience(player.getUUID(), PlayerSocialSkill.PERSUASION), 10L, "recovery reuses the same receipt");
            helper.assertValueEqual(social.data().receipts().size(), 1, "recovery does not duplicate the award");
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).snapshots().get(id).socialAwards().contains("convince"), "retry acknowledges the existing award");
        }
        helper.succeed();
    }

    private static void install(CampaignConversationGameTests.Fixture fixture) throws IOException {
        var graph = SocialProgressionTestData.resource("quests/campaign_fixture/characters/conversations/social_mentor.json");
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
}
