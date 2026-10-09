package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTestStorage;
import com.jvn.villagerretaliation.party.PartyService;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_character_repairs")
@PrefixGameTestTemplate(false)
public final class CampaignConversationRepairGameTests {
    private CampaignConversationRepairGameTests() { }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void repairedConversationsKeepRollsRewardsAndPastSpeakersAcrossStorageFailureAndReload(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.effects("[{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":7,\"required\":true}}]");
            var leader = fixture.player();
            var listener = fixture.player();
            var outsider = fixture.player();
            fixture.party(leader, listener);
            PartyService.setAdminPrivileges(leader, listener.getUUID(), false);
            var result = fixture.start(leader);
            result = fixture.intent(listener, result, ConversationSession.Intent.JOIN, "");
            for (int i = 0; i < 3; i++) result = fixture.intent(leader, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(leader, result, ConversationSession.Intent.CHOOSE, "appeal");
            result = fixture.intent(leader, result, ConversationSession.Intent.ROLL, "");
            var before = result.session();
            var target = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
            target.setNoAi(true);
            try {
                repair(fixture, target);
                helper.assertTrue(ConversationService.isCharacterReserved(target), "repaired actor is locked before session reconciliation");
                helper.assertTrue(ConversationService.start(outsider, before.identity().conversation(), "guidance", UUID.randomUUID()).session() == null,
                        "another player cannot take the repaired role");
                fixture.storage.blockWrites();
                try {
                    var waiting = ConversationService.view(leader, before.identity().id());
                    helper.assertFalse(waiting.diagnostic().isBlank(), "failed repair save pauses the conversation");
                    helper.assertValueEqual(waiting.session(), before, "unsaved speaker change is never published");
                    helper.assertTrue(ConversationService.isCharacterReserved(target), "pending repair retains target reservation");
                } finally { fixture.storage.restoreWrites(); }
                ConversationTestStorage.maintain(fixture.base.server);
                fixture.storage.reload();
                var stale = ConversationService.request(leader, before.identity().id(), before.revision(), UUID.randomUUID(), ConversationSession.Intent.ADVANCE, "");
                helper.assertFalse(stale.diagnostic().isBlank(), "pre-repair intent cannot advance the updated session");
                var resumed = ConversationService.start(leader, before.identity().conversation(), "guidance", UUID.randomUUID());
                helper.assertValueEqual(resumed.session().identity(), before.identity(), "reopening resumes the same original session");
                helper.assertValueEqual(resumed.session().speakers().get("mentor").entity(), target.getUUID(), "future exchanges use repaired actor");
                helper.assertValueEqual(resumed.session().transcript(), before.transcript(), "past lines retain original speakers");
                helper.assertValueEqual(resumed.session().checks(), before.checks(), "saved die result cannot change");
                helper.assertValueEqual(resumed.session().choices(), before.choices(), "repair invents no choice or participation");
                helper.assertValueEqual(resumed.session().receipts(), before.receipts(), "repair preserves effect receipts");
                helper.assertValueEqual(leader.totalExperience, 7, "repair and reload cannot duplicate rewards");
                var denied = fixture.intent(listener, resumed, ConversationSession.Intent.ADVANCE, "");
                helper.assertValueEqual(denied.session().revision(), resumed.session().revision(), "listener still cannot advance");
                resumed = fixture.intent(leader, resumed, ConversationSession.Intent.ADVANCE, "");
                helper.assertValueEqual(resumed.session().transcript().getLast().speaker(), target.getUUID(), "new branch speaks through repaired role");
                resumed = fixture.intent(leader, resumed, ConversationSession.Intent.ADVANCE, "");
                helper.assertValueEqual(resumed.session().phase(), ConversationSession.Phase.COMPLETE, "saved branch completes");
                fixture.storage.reload();
                helper.assertValueEqual(ConversationService.view(leader, before.identity().id()).session(), resumed.session(), "complete repaired history survives reload");
            } finally { target.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_character_repairs")
    public static void repairedCharactersRequireFreshImpactConfirmation(GameTestHelper helper) throws Exception {
        try (var fixture = CampaignConversationConfirmationGameTests.fixture(helper, """
                [{"id":"accept","action":{"type":"quest","quest":"campaign_fixture:characters","action":"start","required":true}},
                 {"id":"gift","action":{"type":"experience","amount":7}}]
                """, false)) {
            var player = fixture.player();
            var result = fixture.intent(player, fixture.start(player), ConversationSession.Intent.CHOOSE, "appeal");
            var pending = result.session().confirmation();
            helper.assertTrue(pending != null, "original choice awaits acceptance");
            var target = helper.spawn(EntityType.VILLAGER, 2, 1, 1);
            target.setNoAi(true);
            try {
                repair(fixture, target);
                result = ConversationService.view(player, result.session().identity().id());
                helper.assertTrue(result.session().confirmation() == null && result.session().choices().isEmpty(), "repair withdraws uncommitted approval");
                var stale = fixture.intent(player, result, ConversationSession.Intent.CONFIRM, pending.id().toString());
                helper.assertFalse(stale.diagnostic().isBlank(), "old confirmation cannot be accepted");
                helper.assertValueEqual(player.totalExperience, 0, "repair and stale approval grant no rewards");
                result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
                var current = result.session().confirmation();
                helper.assertTrue(current != null && !current.id().equals(pending.id()), "reselection prepares fresh consequences");
                helper.assertValueEqual(current.consent().providerId(), target.getUUID(), "new approval names repaired provider");
                fixture.storage.reload();
                result = fixture.intent(player, ConversationService.view(player, result.session().identity().id()), ConversationSession.Intent.CONFIRM, current.id().toString());
                helper.assertValueEqual(player.totalExperience, 7, "accepted current approval applies reward once");
                helper.assertTrue(ConversationSavedData.get(helper.getLevel()).diagnostic().isBlank(), "repaired approval remains readable");
                fixture.storage.reload();
                ConversationTestStorage.maintain(fixture.base.server);
                helper.assertValueEqual(player.totalExperience, 7, "accepted repaired approval cannot repeat");
            } finally { target.discard(); }
        }
        helper.succeed();
    }

    private static void repair(CampaignConversationGameTests.Fixture fixture, Villager target) {
        var data = CampaignCharacterSavedData.get(fixture.helper.getLevel());
        var key = new CampaignCharacterBinding.Key(fixture.base.campaignId, "mentor");
        var result = CampaignCharacterRepairs.repair(fixture.base.server, new CampaignCharacterRepairs.Request(fixture.base.campaignId,
                "mentor", target.getUUID(), data.token(key), UUID.randomUUID(), UUID.randomUUID(), "recover conversation character"));
        fixture.helper.assertTrue(result.accepted(), result.diagnostic());
    }
}
