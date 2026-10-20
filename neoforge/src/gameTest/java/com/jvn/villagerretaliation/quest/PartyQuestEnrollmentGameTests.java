package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.party.PartyVillagerContractService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySharedQuestRecord;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("narrative_boundary")
@PrefixGameTestTemplate(false)
public final class PartyQuestEnrollmentGameTests {
    private PartyQuestEnrollmentGameTests() { }

    @GameTest(template = "empty", batch = "campaign_party_enrollment")
    public static void existingPersonalRunCannotBeRedirectedIntoAnotherSharedRun(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var definition = fixture();
        helper.assertTrue(PartyService.createParty(player).success(), "the test leader creates its party");
        var party = PartyService.getPartyForPlayer(level, player.getUUID()).orElseThrow();
        var shared = new PartySharedQuestRecord(definition.id(), null, level.getGameTime());
        party.addSharedQuest(shared);
        var data = VillagerQuestSavedData.get(level);
        var progress = data.getOrCreate(player.getUUID(), definition.id());
        progress.start(null, level.dimension(), null, level.getGameTime());
        UUID personalRun = progress.beginRun(player.getUUID(), definition.id(), null);
        try {
            enrollment(helper).linkExistingActivePartyQuest(DialogueContext.forPlayer(player), definition, progress);
            helper.assertTrue(shared.enrollment(player.getUUID()) == null, "a conflicting run is not enrolled");
            helper.assertValueEqual(progress.questRunId(), personalRun, "the original operation identity survives rejected enrollment");
            helper.assertTrue(progress.partyQuestInstanceId() == null, "rejected enrollment does not bind a shared instance");
        } finally {
            data.remove(player.getUUID(), definition.id());
            PartyVillagerContractService.disband(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_party_enrollment")
    public static void sharedEnrollmentAndDeparturePreservePersonalProgressAndRunIdentity(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var definition = VillagerQuestResources.quest(level.getServer(), VillagerRetaliation.id("blank_map_promise")).orElseThrow();
        var objective = definition.objectives().getFirst();
        helper.assertTrue(PartyService.createParty(player).success(), "the test leader creates its party");
        var party = PartyService.getPartyForPlayer(level, player.getUUID()).orElseThrow();
        UUID issuer = UUID.randomUUID();
        var shared = new PartySharedQuestRecord(definition.id(), issuer, level.getGameTime());
        party.addSharedQuest(shared);
        shared.mergeObjectiveCounter(objective.id(), 4);
        var data = VillagerQuestSavedData.get(level);
        var progress = data.getOrCreate(player.getUUID(), definition.id());
        progress.start(issuer, level.dimension(), null, level.getGameTime());
        progress.beginRun(player.getUUID(), definition.id(), shared.instanceId());
        progress.addObjectiveCounter(objective.id(), 2);
        try {
            enrollment(helper).linkExistingActivePartyQuest(DialogueContext.forPlayer(player), definition, progress);
            helper.assertTrue(shared.linked(player.getUUID()), "the same definitive run can enroll");
            helper.assertValueEqual(progress.partyQuestInstanceId(), shared.instanceId(), "personal progress binds to its shared instance");
            helper.assertValueEqual(progress.objectiveCounter(objective.id()), 4, "enrollment synchronizes shared progress");
            shared.mergeObjectiveCounter(objective.id(), 5);
            PartyQuestService.detachQuest(level, party, player.getUUID(), definition.id());
            helper.assertTrue(shared.enrollment(player.getUUID()) == null, "departure removes only enrollment");
            helper.assertValueEqual(progress.objectiveCounter(objective.id()), 5, "departure retains progress credited before leaving");
            helper.assertValueEqual(progress.questRunId(), shared.instanceId(), "departure does not reassign the quest run");
            helper.assertValueEqual(progress.state(), VillagerQuestSavedData.QuestState.ACTIVE, "departure leaves personal quest progress active");
        } finally {
            data.remove(player.getUUID(), definition.id());
            PartyVillagerContractService.disband(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_party_enrollment")
    public static void pendingEnrollmentReadsTheExistingActiveProgressOwner(GameTestHelper helper) {
        var level = helper.getLevel();
        var definition = fixture();
        UUID completedPlayer = UUID.randomUUID();
        UUID activePlayer = UUID.randomUUID();
        var shared = new PartySharedQuestRecord(definition.id(), null, level.getGameTime());
        shared.enroll(completedPlayer, false);
        shared.enroll(activePlayer, false);
        var data = VillagerQuestSavedData.get(level);
        var completed = data.getOrCreate(completedPlayer, definition.id());
        completed.start(null, level.dimension(), null, 1L);
        completed.complete(2L, false);
        var active = data.getOrCreate(activePlayer, definition.id());
        active.start(null, level.dimension(), null, 1L);
        try {
            helper.assertTrue(PartyQuestEnrollmentService.canonicalSharedProgress(level, shared) == active,
                    "pending attachment reads the active owner instead of creating another progress copy");
            active.complete(3L, false);
            helper.assertTrue(PartyQuestEnrollmentService.canonicalSharedProgress(level, shared) == active,
                    "completed fallback keeps the existing enrollment order");
        } finally {
            data.remove(completedPlayer, definition.id());
            data.remove(activePlayer, definition.id());
        }
        helper.succeed();
    }

    private static PartyQuestEnrollmentService enrollment(GameTestHelper helper) {
        return new PartyQuestEnrollmentService((context, definition, progress) -> {
            helper.fail("linking existing progress must not run new-start eligibility");
            return false;
        }, (definition, progress) -> helper.fail("linking existing progress must not reroll quest state"),
                (context, definition, progress, target) -> helper.fail("linking existing progress must not replay start effects"),
                player -> { });
    }

    private static QuestDefinition fixture() {
        var id = VillagerRetaliation.id("party_enrollment_boundary");
        return QuestDefinitionFixtures.read(id, JsonParser.parseString("""
                {"objectives":[{"id":"supplies","type":"item_check","item":"minecraft:wheat","count":6}]}
                """).getAsJsonObject(), id);
    }
}
