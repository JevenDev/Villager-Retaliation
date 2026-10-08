package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTopicInspection;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignTopicInspectionGameTests {
    private static final ResourceLocation CONVERSATION = ResourceLocation.parse("campaign_fixture:mentor_conversation");
    private CampaignTopicInspectionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void topicTracesExplainNestedConditionsWithoutStartingAnything(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var player = fixture.player();
            var quests = VillagerQuestSavedData.get(helper.getLevel());
            var before = quests.save(new CompoundTag(), helper.getLevel().registryAccess());
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().add("conditions", JsonParser.parseString("""
                    [{"type":"all","conditions":[{"type":"time","value":"night"},{"type":"not","condition":{"type":"time","value":"night"}}]}]
                    """));
            fixture.install(fixture.graph);
            var report = inspect(fixture, player);
            helper.assertValueEqual(report.status(), ConversationTopicInspection.Status.BLOCKED, "contradictory authored condition blocks topic");
            helper.assertValueEqual(report.conditions().getFirst().pointer(), "/topics/0/conditions/0", "diagnostic keeps authored pointer");
            var trace = report.conditions().getFirst().trace();
            helper.assertValueEqual(trace.outcome(), DialogueCondition.ConditionOutcome.UNMET, "nested trace reports unmet condition");
            helper.assertValueEqual(trace.children().size(), 2, "nested children remain inspectable");
            helper.assertTrue(ConversationService.start(player, CONVERSATION, "guidance", UUID.randomUUID()).session() == null,
                    "runtime agrees with inspection");
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).snapshots().isEmpty(), "inspection creates no conversation");
            helper.assertValueEqual(quests.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "inspection does not advance quests");
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().remove("conditions");
            fixture.install(fixture.graph);
            helper.assertValueEqual(inspect(fixture, player).status(), ConversationTopicInspection.Status.AVAILABLE, "unrestricted topic is available");
            fixture.start(player);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void topicInspectionUsesPartyAuthorityPresenceAndOrdinaryConversationGates(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            blocked(helper, inspect(fixture, member), "authority");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            helper.assertValueEqual(inspect(fixture, member).status(), ConversationTopicInspection.Status.AVAILABLE, "checked member may control");
            member.setPos(fixture.mentor.getX() + 32, fixture.mentor.getY(), fixture.mentor.getZ());
            blocked(helper, inspect(fixture, member), "presence");
            helper.assertTrue(VillagerConversationService.start(leader, fixture.mentor), "ordinary dialogue starts");
            blocked(helper, inspect(fixture, leader), "ordinary_conversation");
            VillagerConversationService.endForPlayer(leader, false);
            fixture.base.mode("sandbox");
            blocked(helper, inspect(fixture, leader), "mode");
            helper.assertTrue(ConversationSavedData.get(helper.getLevel()).snapshots().isEmpty(), "gate inspection starts no campaign conversation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void offlineAndUnloadedTopicsDoNotInventLiveContext(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().add("conditions",
                    JsonParser.parseString("[{\"type\":\"time\",\"value\":\"night\"}]"));
            fixture.install(fixture.graph);
            var unknown = ConversationTopicInspection.inspect(fixture.base.server, CONVERSATION, "guidance", UUID.randomUUID());
            helper.assertValueEqual(unknown.status(), ConversationTopicInspection.Status.UNKNOWN, "offline player's live gates are unknown");
            helper.assertValueEqual(unknown.conditions().getFirst().trace().outcome(), DialogueCondition.ConditionOutcome.UNKNOWN, "offline conditions are not fabricated");
            var player = fixture.player();
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.base.reload();
            var unloaded = inspect(fixture, player);
            blocked(helper, unloaded, "character");
            helper.assertTrue(unloaded.gates().stream().anyMatch(gate -> gate.message().contains("unloaded")), "diagnostic identifies unloaded actor");
            helper.assertFalse(CampaignCharacterSavedData.get(helper.getLevel()).forEntity(fixture.mentor.getUUID()).lost(), "inspection does not mark unloaded actor dead");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void inspectingSavedRollsNeverAdvancesOrReplaysEffects(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.effects("[{\"id\":\"gift\",\"action\":{\"type\":\"experience\",\"amount\":7,\"required\":true}}]");
            var player = fixture.player();
            var result = fixture.start(player);
            for (int i = 0; i < 3; i++) result = fixture.intent(player, result, ConversationSession.Intent.ADVANCE, "");
            result = fixture.intent(player, result, ConversationSession.Intent.CHOOSE, "appeal");
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var saved = result.session();
            int experience = player.totalExperience;
            fixture.storage.reload();
            var report = inspect(fixture, player);
            helper.assertValueEqual(report.status(), ConversationTopicInspection.Status.EXISTING_SESSION, "saved session is offered for continuation");
            helper.assertValueEqual(report.session(), saved, "inspection preserves speakers, choices, checks and receipts");
            helper.assertValueEqual(CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId).conversations().getFirst(),
                    saved, "campaign inspection exposes the same durable snapshot");
            helper.assertValueEqual(player.totalExperience, experience, "inspection does not replay rewards");
            helper.assertValueEqual(ConversationSavedData.get(helper.getLevel()).snapshots().get(saved.identity().id()), saved, "inspection consumes no revision");
            var stranger = fixture.player();
            blocked(helper, inspect(fixture, stranger), "session");
            fixture.graph.addProperty("revision", 2);
            fixture.install(fixture.graph);
            blocked(helper, inspect(fixture, player), "session");
            helper.assertValueEqual(ConversationSavedData.get(helper.getLevel()).snapshots().get(saved.identity().id()), saved, "incompatible content does not rewrite history");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void pausedControllersAndUnreadableStorageRemainUnchanged(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var leader = fixture.player();
            var member = fixture.player();
            fixture.party(leader, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            var result = fixture.start(leader);
            result = fixture.intent(leader, result, ConversationSession.Intent.RELEASE_CONTROL, "");
            var paused = result.session();
            var report = inspect(fixture, member);
            helper.assertValueEqual(report.status(), ConversationTopicInspection.Status.EXISTING_SESSION, "authorized member can find paused session");
            helper.assertTrue(report.session().controller() == null, "inspection never takes control");
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            blocked(helper, inspect(fixture, member), "authority");
            helper.assertValueEqual(ConversationSavedData.get(helper.getLevel()).snapshots().get(paused.identity().id()), paused, "permission inspection does not rewrite session");
            var future = new CompoundTag();
            future.putInt("DataVersion", ConversationSavedData.CURRENT_DATA_VERSION + 1);
            fixture.storage.reload(future);
            blocked(helper, inspect(fixture, leader), "storage");
        }
        helper.succeed();
    }

    private static ConversationTopicInspection.Snapshot inspect(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        return ConversationTopicInspection.inspect(fixture.base.server, CONVERSATION, "guidance", player.getUUID());
    }

    private static void blocked(GameTestHelper helper, ConversationTopicInspection.Snapshot report, String gate) {
        helper.assertValueEqual(report.status(), ConversationTopicInspection.Status.BLOCKED, "topic is blocked: " + report.gates());
        helper.assertTrue(report.gates().stream().anyMatch(value -> value.id().equals(gate) && value.state() == ConversationTopicInspection.GateState.BLOCKED),
                "named blocker explains topic: " + gate);
    }
}
