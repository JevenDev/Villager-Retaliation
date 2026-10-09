package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignScenario;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignPromiseCompanionGameTests {
    private CampaignPromiseCompanionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_promises")
    public static void authoredPromiseKeepsCompanionSpeechAndListenersPersonalHistorySeparate(GameTestHelper helper) throws IOException {
        var pack = CampaignScenarioPack.read(CampaignScenarioAuthoringGameTests.projectFile("neoforge/src/gameTest/resources/campaign_fixture"));
        var authored = pack.scenarios.stream().filter(value -> value.id().equals(ResourceLocation.parse("campaign_fixture:promise_broken")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Promise scenario is missing."));
        var scenario = new CampaignScenario(authored.id(), authored.campaign(), List.of("first", "listener"),
                authored.actors(), authored.locations(), authored.steps(), authored.source());
        try (var fixture = CampaignScenarioFixture.open(helper, scenario, pack)) {
            var execution = new CampaignScenarioExecution(fixture);
            for (var step : scenario.steps()) {
                execution.execute(step);
                switch (step.id()) {
                    case "connect" -> {
                        var first = fixture.player("first");
                        fixture.connect("listener");
                        var companion = fixture.actors.get("traveller");
                        VillagerReputationManager.setReputation(helper.getLevel(), companion, first.getUUID(), 0);
                        first.getInventory().add(new ItemStack(Items.EMERALD, 64));
                        helper.assertTrue(PartyVillagerContractService.recruit(first, companion).success(), "authored character joins the player's party");
                        var invitation = PartyService.sendInvitation(first, fixture.player("listener"));
                        helper.assertTrue(PartyService.acceptInvitation(fixture.player("listener"), invitation.invitationId()).success(), "listener joins party");
                    }
                    case "promise" -> {
                        var state = execution.session("pledge");
                        var joined = ConversationService.request(fixture.player("listener"), state.identity().id(), state.revision(),
                                UUID.randomUUID(), ConversationSession.Intent.JOIN, "");
                        helper.assertTrue(joined.diagnostic().isBlank(), "party listener joins the promise");
                    }
                    case "finish_pledge" -> finishInterjection(helper, fixture, execution, "pledge", "support", "companion.accepted");
                    case "finish_resolution" -> finishInterjection(helper, fixture, execution, "resolution", "objection", "companion.broken");
                    case "finish_memory" -> helper.assertValueEqual(execution.session("memory").transcript().getLast().exchange(),
                            "broken_memory", "later authored dialogue recalls the broken promise after reconnect and reload");
                    default -> { }
                }
            }
            var first = fixture.player("first");
            var listener = fixture.player("listener");
            var definition = fixture.content.definition();
            helper.assertTrue(new CampaignFactPredicate(definition.id(), "broken", CampaignFactPredicate.Comparison.EQUALS, "true")
                    .evaluate(helper.getLevel(), first.getUUID()).matches(), "controller retains the broken promise");
            helper.assertFalse(new CampaignFactPredicate(definition.id(), "offered", CampaignFactPredicate.Comparison.EQUALS, "true")
                    .evaluate(helper.getLevel(), listener.getUUID()).matches(), "listening never makes a personal promise");
            var standing = CampaignStandingService.read(fixture.server, definition.id(), listener.getUUID(),
                    CampaignStandingDefinition.Scope.CHARACTER, "mentor");
            helper.assertTrue(standing.diagnostic().isBlank(), "listener's standing is readable");
            helper.assertValueEqual(standing.value(), 0, "listener does not inherit another player's relationship penalty");
            helper.assertValueEqual(execution.session("resolution").transcript().getLast().speaker(), fixture.actors.get("traveller").getUUID(),
                    "saved companion speech keeps its identity after reload");
            helper.assertTrue(PartyService.setAdminPrivileges(first, listener.getUUID(), true).success(), "leader grants dialogue control");
            var own = ConversationService.start(listener, ResourceLocation.parse("campaign_fixture:promise_conversation"), "promise", UUID.randomUUID());
            helper.assertTrue(own.diagnostic().isBlank() && own.session() != null, "listener can begin their own promise");
            helper.assertValueEqual(own.session().node(), "offer", "listener starts from their own story position");
        }
        helper.succeed();
    }

    private static void finishInterjection(GameTestHelper helper, CampaignScenarioFixture fixture, CampaignScenarioExecution execution,
            String session, String exchange, String key) {
        var state = execution.session(session);
        helper.assertValueEqual(state.phase(), ConversationSession.Phase.EXCHANGES, "present companion has an authored line");
        var line = state.transcript().getLast();
        helper.assertValueEqual(line.exchange(), exchange, "expected companion interjection is selected");
        helper.assertValueEqual(line.speaker(), fixture.actors.get("traveller").getUUID(), "line uses the campaign's bound companion");
        helper.assertValueEqual(line.textKey(), "campaign_fixture.quest.promises." + key, "speech belongs to the quest bundle locale");
        var result = ConversationService.request(fixture.player("first"), state.identity().id(), state.revision(), UUID.randomUUID(),
                ConversationSession.Intent.ADVANCE, "");
        helper.assertTrue(result.diagnostic().isBlank(), "companion finishes the exchange");
        helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.COMPLETE, "authored outcome follows the companion line");
    }
}
