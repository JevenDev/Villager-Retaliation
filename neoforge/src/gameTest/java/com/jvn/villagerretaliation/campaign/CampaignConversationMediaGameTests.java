package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationPresentationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload;
import com.jvn.villagerretaliation.network.CampaignConversationRequestPayload.Action;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignConversationMediaGameTests {
    private CampaignConversationMediaGameTests() { }

    @GameTest(template = "empty", batch = "conversation_media")
    public static void authoredPresentationFollowsSavedSpeakersAcrossReloadAndDuplicateRequests(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            var started = fixture.start(player);
            UUID view = UUID.randomUUID();
            var first = send(player, fixture, view, Action.TOPICS, null);
            helper.assertValueEqual(first.session().line().camera(), new ConversationLinePresentation.Camera(
                    ConversationLinePresentation.Target.SPEAKER, 0.2D), "server presents the authored camera cue");
            helper.assertValueEqual(first.session().line().voice(), ResourceLocation.parse("campaign_fixture:mentor/welcome"),
                    "server selects the viewer's locale fallback");
            var transcript = started.session().transcript();
            fixture.base.reload();
            fixture.storage.reload();
            var restored = send(player, fixture, view, Action.VIEW, first);
            helper.assertValueEqual(restored.session().line(), first.session().line(), "reload keeps presentation on the same saved exchange");
            helper.assertValueEqual(ConversationService.view(player, first.session().id()).session().transcript(), transcript,
                    "presentation never appends another exchange");
            var advance = request(fixture, view, Action.ADVANCE, restored);
            var next = ConversationPresentationService.respond(player, advance);
            var replay = ConversationPresentationService.respond(player, advance);
            helper.assertValueEqual(replay.session().line(), next.session().line(), "duplicate advance retains the selected presentation");
            helper.assertValueEqual(next.session().line().speaker(), player.getUUID(), "speaker still comes from saved dialogue");
            helper.assertValueEqual(next.session().line().camera().target(), ConversationLinePresentation.Target.CHARACTER,
                    "a player line can focus the other character");
            var narrator = send(player, fixture, view, Action.ADVANCE, next);
            helper.assertTrue(narrator.session().line().speaker() == null, "narrator has no invented actor");
            helper.assertValueEqual(narrator.session().line().camera().target(), ConversationLinePresentation.Target.NONE,
                    "narration can leave the camera free");
            var responses = send(player, fixture, view, Action.ADVANCE, narrator);
            helper.assertTrue(responses.session().line().camera() == null && responses.session().line().voice() == null,
                    "finished speech does not keep playing while choices are shown");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "conversation_media")
    public static void unavailableOrChangedContentKeepsTextWithoutSubstitutingPresentation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            var player = fixture.player();
            fixture.start(player);
            UUID view = UUID.randomUUID();
            var original = send(player, fixture, view, Action.TOPICS, null);
            fixture.install(null);
            var missing = send(player, fixture, view, Action.VIEW, original);
            helper.assertFalse(missing.diagnostic().isBlank(), "missing content remains recoverable");
            helper.assertValueEqual(missing.session().line().text(), original.session().line().text(), "saved text stays readable");
            helper.assertTrue(missing.session().line().camera() == null && missing.session().line().voice() == null,
                    "missing content cannot start optional presentation");
            var changed = fixture.graph.deepCopy();
            changed.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(0).getAsJsonObject()
                    .getAsJsonObject("voices").addProperty("en_us", "campaign_fixture:replacement");
            fixture.install(changed);
            var incompatible = send(player, fixture, view, Action.VIEW, missing);
            helper.assertTrue(incompatible.session().line().voice() == null && !incompatible.session().canControl(),
                    "a changed definition cannot substitute a recording for saved speech");
            fixture.install(fixture.graph);
            var recovered = send(player, fixture, view, Action.VIEW, incompatible);
            helper.assertValueEqual(recovered.session().line(), original.session().line(), "matching content restores the saved line's presentation");
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException {
        var fixture = new CampaignConversationGameTests.Fixture(helper);
        var exchanges = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges");
        exchanges.get(0).getAsJsonObject().add("camera", JsonParser.parseString("{\"target\":\"speaker\",\"zoom\":0.2}"));
        exchanges.get(0).getAsJsonObject().add("voices", JsonParser.parseString("{\"en_us\":\"campaign_fixture:mentor/welcome\",\"fr_fr\":\"campaign_fixture:mentor/bienvenue\"}"));
        exchanges.get(1).getAsJsonObject().add("camera", JsonParser.parseString("{\"target\":\"character\"}"));
        exchanges.get(2).getAsJsonObject().add("camera", JsonParser.parseString("{\"target\":\"none\"}"));
        fixture.install(fixture.graph);
        return fixture;
    }

    private static CampaignConversationViewPayload send(ServerPlayer player, CampaignConversationGameTests.Fixture fixture,
            UUID view, Action action, CampaignConversationViewPayload prior) {
        return ConversationPresentationService.respond(player, request(fixture, view, action, prior));
    }

    private static CampaignConversationRequestPayload request(CampaignConversationGameTests.Fixture fixture,
            UUID view, Action action, CampaignConversationViewPayload prior) {
        var session = prior == null ? null : prior.session();
        return new CampaignConversationRequestPayload(view, UUID.randomUUID(), fixture.mentor.getId(), fixture.mentor.getUUID(), action,
                session == null ? null : session.id(), session == null ? 0 : session.revision(), "", "", 0, 0);
    }
}
