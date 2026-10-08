package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationTopicInspection;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignInspectionGameTests {
    private CampaignInspectionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_inspection")
    public static void unstartedInspectionNeverBindsSpawnsOrEnrollsPlayers(GameTestHelper helper) throws IOException {
        for (String source : List.of("existing_villager", "owned_spawn")) {
            try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(),
                    quest -> { }, campaign -> campaign.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("source", source),
                    List.of("conversations/mentor.json"))) {
                var report = CampaignStateInspection.inspect(fixture.server, fixture.campaignId);
                helper.assertValueEqual(report.status(), CampaignStateInspection.Status.UNSTARTED, "loaded campaign has no instance");
                helper.assertValueEqual(report.characters().getFirst().status(), CampaignStateInspection.CharacterStatus.UNBOUND, "actor is unbound");
                UUID unknown = UUID.randomUUID();
                helper.assertFalse(report.player(unknown).recorded(), "inspection does not invent a personal record");
                helper.assertFalse(report.player(unknown).chapters().get("arrival").completed(), "unstarted chapter remains incomplete");
                var topic = ConversationTopicInspection.inspect(fixture.server, id("mentor_conversation"), "guidance", unknown);
                helper.assertValueEqual(topic.status(), ConversationTopicInspection.Status.NEEDS_BINDING, "casting is reported without attempting it");
                helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "no instance is created");
                helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "no actor is cast");
                helper.assertTrue(CampaignLocationSavedData.get(helper.getLevel()).bindings().isEmpty(), "no location search is started");
                helper.assertTrue(CampaignSpawnSavedData.get(helper.getLevel()).receipts().isEmpty(), "no spawn is prepared");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection", timeoutTicks = 200)
    public static void offlineProgressKeepsParticipationCatchUpAndFactsSeparate(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, false, true, false, campaign ->
                campaign.add("facts", JsonParser.parseString("""
                        [{"id":"shared","scope":"world","kind":"tag"},{"id":"learned","scope":"player","kind":"tag"}]
                        """)))) {
            var data = CampaignSavedData.get(helper.getLevel());
            var content = CampaignResources.current(fixture.base.server).campaigns().get(fixture.base.campaignId);
            var participant = UUID.randomUUID();
            var absent = UUID.randomUUID();
            var late = UUID.randomUUID();
            data.enterMilestone(content.definition(), participant, "decision", 1L);
            data.enterMilestone(content.definition(), absent, "decision", 1L);
            helper.assertValueEqual(data.commitWorldOutcome(content, "decision", "help", UUID.randomUUID(), 0L,
                    CampaignInstance.Origin.PLAYER_DECISION, Set.of(participant), 2L).status(), CampaignSavedData.Status.APPLIED, "fixture commits outcome");
            helper.assertValueEqual(data.commitFact(content.definition(), participant, "shared", CampaignFacts.Mutation.SET, "true",
                    UUID.randomUUID(), 1L, 3L).status(), CampaignSavedData.Status.APPLIED, "fixture records world fact");
            helper.assertValueEqual(data.commitFact(content.definition(), absent, "learned", CampaignFacts.Mutation.SET, "true",
                    UUID.randomUUID(), data.instances().get(fixture.base.campaignId).players().get(absent).revision(), 4L).status(),
                    CampaignSavedData.Status.APPLIED, "fixture records personal fact");
            var before = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            var report = CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId);
            helper.assertTrue(report.player(participant).progress().participation().contains("decision"), "participant retains credit");
            var personal = report.player(absent);
            helper.assertTrue(personal.progress().participation().isEmpty(), "absent player has no participation credit");
            helper.assertValueEqual(personal.entries().get("decision").route(), CampaignInstance.Route.CATCH_UP, "offline player has catch-up entry");
            helper.assertValueEqual(personal.entries().get("decision").stage(), "report", "catch-up points to authored stage");
            helper.assertValueEqual(personal.facts().keySet(), Set.of("learned"), "world fact does not become personal knowledge");
            helper.assertValueEqual(report.instance().facts().world().keySet(), Set.of("shared"), "world facts remain shared");
            helper.assertFalse(report.player(late).recorded(), "querying a late player does not enroll them");
            helper.assertValueEqual(data.save(new CompoundTag(), helper.getLevel().registryAccess()), before, "inspection preserves save bytes");
            fixture.base.reload();
            helper.assertValueEqual(CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId).player(absent),
                    personal, "offline inspection survives reload");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection")
    public static void missingChangedAndUnreadableContentRetainsInspectionEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var server = fixture.base.server;
            var original = CampaignResources.current(server);
            var saved = CampaignSavedData.get(helper.getLevel()).instances().get(fixture.base.campaignId);
            CampaignResources.installForTests(server, new CampaignContentCatalog(QuestContentCatalogs.current(server), Map.of(), List.of()));
            var dormant = CampaignStateInspection.inspect(server, fixture.base.campaignId);
            helper.assertValueEqual(dormant.status(), CampaignStateInspection.Status.DORMANT, "missing content is dormant");
            helper.assertValueEqual(dormant.instance(), saved, "saved identity remains inspectable");
            helper.assertValueEqual(dormant.characters().getFirst().saved().entityId(), fixture.mentor.getUUID(), "missing content retains actor evidence");
            helper.assertTrue(dormant.player(UUID.randomUUID()).chapters().isEmpty(), "missing content cannot claim chapter availability");
            helper.assertValueEqual(CampaignStateInspection.inspect(server, id("missing")).status(), CampaignStateInspection.Status.MISSING, "unknown ID differs from dormant save");
            CampaignResources.installForTests(server, original);
            var changed = fixture.base.raw();
            changed.addProperty("revision", 2);
            QuestContentCatalogs.installForTests(server, fixture.base.catalog, fixture.base.report);
            fixture.base.install(changed);
            var incompatible = CampaignStateInspection.inspect(server, fixture.base.campaignId);
            helper.assertValueEqual(incompatible.status(), CampaignStateInspection.Status.INCOMPATIBLE, "revision change blocks interpretation");
            helper.assertValueEqual(incompatible.instance(), saved, "incompatible content retains saved data");
            helper.assertTrue(incompatible.player(UUID.randomUUID()).chapters().isEmpty(), "incompatible prerequisites are not evaluated");
            var future = new CompoundTag();
            future.putInt("DataVersion", CampaignSavedData.CURRENT_DATA_VERSION + 1);
            server.overworld().getDataStorage().set(CampaignSavedData.DATA_NAME, CampaignSavedData.load(future, fixture.base.disk.root));
            helper.assertValueEqual(CampaignStateInspection.inspect(server, fixture.base.campaignId).status(),
                    CampaignStateInspection.Status.UNAVAILABLE, "unsupported storage is diagnosed");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()),
                    future, "inspection preserves unsupported data");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection")
    public static void inspectionDistinguishesDownedAndUnloadedCharacters(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.mentor.hurt(fixture.mentor.damageSources().genericKill(), 1000);
            var downed = CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId).characters().getFirst();
            helper.assertValueEqual(downed.status(), CampaignStateInspection.CharacterStatus.DOWNED, "downed actor is not dead or unloaded");
            com.jvn.villagerretaliation.combat.downed.VillagerDownedService.recover(fixture.mentor);
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            fixture.base.reload();
            var unloaded = CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId).characters().getFirst();
            helper.assertValueEqual(unloaded.status(), CampaignStateInspection.CharacterStatus.UNLOADED, "unloaded actor remains bound");
            helper.assertFalse(unloaded.saved().lost(), "inspection never infers death from unloading");
            helper.assertValueEqual(unloaded.saved().entityId(), downed.saved().entityId(), "identity is preserved");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_inspection")
    public static void operatorCommandsRequirePermissionAndKeepLegacyWorldsUnchanged(GameTestHelper helper) throws Exception {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(),
                quest -> { }, campaign -> { }, List.of("conversations/mentor.json"))) {
            var dispatcher = fixture.server.getCommands().getDispatcher();
            var source = fixture.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            String prefix = "vr admin campaign ";
            for (String mode : List.of("legacy", "sandbox", "campaign")) {
                fixture.mode(mode);
                var report = CampaignStateInspection.inspect(fixture.server, fixture.campaignId);
                helper.assertValueEqual(report.mode().id(), mode, "inspection preserves world mode");
                for (String command : List.of("state campaign_fixture:characters", "player campaign_fixture:characters " + UUID.randomUUID(),
                        "topic campaign_fixture:mentor_conversation guidance " + UUID.randomUUID())) {
                    helper.assertValueEqual(dispatcher.execute(prefix + command, source), 1, "operator can inspect " + command);
                    boolean denied = false;
                    try { dispatcher.execute(prefix + command, source.withPermission(0)); }
                    catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
                    helper.assertTrue(denied, "ordinary player cannot use inspection");
                }
                helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "inspection does not activate campaign");
            }
            helper.assertValueEqual(dispatcher.execute(prefix + "state campaign_fixture:characters 999", source), 0, "invalid page is rejected");
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix + "state campaign_fixture:", source)).join();
            helper.assertTrue(suggestions.getList().stream().anyMatch(value -> value.getText().equals("campaign_fixture:characters")), "campaign IDs are suggested");
            suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix + "topic campaign_fixture:mentor_conversation ", source)).join();
            helper.assertTrue(suggestions.getList().stream().anyMatch(value -> value.getText().equals("guidance")), "authored topics are suggested");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }
}
