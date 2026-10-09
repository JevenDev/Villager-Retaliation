package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignStandingDefinition.Scope;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationService;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.party.PartyService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignStandingRuntimeGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");
    private CampaignStandingRuntimeGameTests() { }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void personalStandingRequiresCurrentAuthorityAndQuestOwnership(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var leader = fixture.player(); var member = fixture.player();
            var action = action("character", "mentor", "help");
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), leader, fixture.mentor);
            helper.assertFalse(CampaignStandingService.execute(context, action).success(), "unaccepted quest cannot change standing");
            startQuest(fixture, leader);
            fixture.party(leader, member);
            var other = VillagerInteractionService.createDialogueContext(helper.getLevel(), member, fixture.mentor);
            helper.assertFalse(CampaignStandingService.execute(other, action).success(), "listener cannot change standing");
            PartyService.setAdminPrivileges(leader, member.getUUID(), true);
            startQuest(fixture, member);
            PartyService.setAdminPrivileges(leader, member.getUUID(), false);
            helper.assertFalse(CampaignStandingService.execute(other, action).success(), "revoked member cannot apply an effect");
            helper.assertTrue(CampaignStandingService.execute(context, action).success(), "leader applies their personal change");
            helper.assertTrue(CampaignStandingService.execute(context, action).success(), "duplicate effect is already satisfied");
            helper.assertValueEqual(disk.data().receipts().size(), 1, "only the original operation is stored");
            helper.assertValueEqual(read(fixture, leader, Scope.CHARACTER, "mentor").value(), 20, "leader owns the relationship");
            helper.assertValueEqual(read(fixture, member, Scope.CHARACTER, "mentor").value(), 0, "member does not inherit it");
            fixture.base.mode("sandbox");
            helper.assertFalse(CampaignStandingService.execute(context, action).success(), "Sandbox does not execute campaign standing");
            helper.assertFalse(read(fixture, leader, Scope.CHARACTER, "mentor").diagnostic().isBlank(), "Sandbox condition is unavailable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void failedStandingEffectBlocksLaterEffectsAndConversationPresentation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            fixture.effects("""
                    [{"id":"standing","action":{"type":"campaign_standing","scope":"character","subject":"mentor","amount":20,"operation_id":"help"}},
                     {"id":"later","action":{"type":"experience","amount":3}}]
                    """);
            disk.blockWrites();
            var result = fixture.start(player);
            helper.assertFalse(result.diagnostic().isBlank(), "failed save exposes the recovery reason");
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.NODE_EFFECTS, "dialogue remains before presentation");
            helper.assertValueEqual(player.totalExperience, 0, "later effects have not run");
            helper.assertTrue(disk.data().receipts().isEmpty(), "failed write publishes no relationship");
            disk.restoreWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.RESUME, "");
            helper.assertTrue(result.diagnostic().isBlank(), "restored storage resumes the conversation: " + result.diagnostic());
            helper.assertValueEqual(result.session().phase(), ConversationSession.Phase.EXCHANGES, "presentation begins after effects commit");
            helper.assertValueEqual(player.totalExperience, 3, "following effect runs once");
            helper.assertValueEqual(read(fixture, player, Scope.CHARACTER, "mentor").value(), 20, "relationship commits once");
            fixture.storage.reload(); disk.reload();
            ConversationService.view(player, result.session().identity().id());
            helper.assertValueEqual(player.totalExperience, 3, "reload does not repeat the next effect");
            helper.assertValueEqual(disk.data().receipts().size(), 1, "reload preserves one standing receipt");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void unloadedCharactersKeepReadableRelationshipsAndMissingContentKeepsHistory(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            helper.assertTrue(CampaignStandingService.execute(context, action("character", "mentor", "help")).success(), "standing changes");
            fixture.mentor.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            var reading = read(fixture, player, Scope.CHARACTER, "mentor");
            helper.assertTrue(reading.diagnostic().isBlank(), "unloaded entity does not block a saved relationship");
            helper.assertValueEqual(reading.reaction().id(), "trusted", "authored reaction resolves without loading the character");
            disk.reload();
            var catalog = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(catalog.quests(), Map.of(), List.of()));
            helper.assertFalse(read(fixture, player, Scope.CHARACTER, "mentor").diagnostic().isBlank(), "missing campaign blocks new work");
            helper.assertValueEqual(disk.data().values().values().iterator().next().amount(), 20, "missing content preserves readable history");
            CampaignResources.installForTests(fixture.base.server, catalog);
            helper.assertValueEqual(read(fixture, player, Scope.CHARACTER, "mentor").value(), 20, "restoring content restores reading");
            var changed = fixture.base.raw();
            addStandings(changed);
            changed.addProperty("revision", 2);
            var parsed = com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser.parse(QUEST, changed);
            helper.assertTrue(parsed.valid(), "changed campaign remains valid");
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(catalog.quests(),
                    Map.of(QUEST, new CampaignContentCatalog.Entry(parsed.definition(), catalog.campaigns().get(QUEST).localization())), List.of()));
            helper.assertFalse(read(fixture, player, Scope.CHARACTER, "mentor").diagnostic().isBlank(), "incompatible content requires explicit repair");
            helper.assertValueEqual(disk.data().receipts().size(), 1, "incompatible content preserves receipts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void referenceReportNamesDeclarationsAndNestedConditions(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper)) {
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().add("conditions", JsonParser.parseString("""
                    [{"type":"all_of","conditions":[{"type":"not","condition":{"type":"campaign_standing","campaign":"campaign_fixture:characters",
                      "scope":"character","subject":"mentor","reaction":"hostile"}}]}]
                    """));
            fixture.install(fixture.graph);
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            var key = CampaignReferenceReport.Key.of(CampaignReferenceReport.Kind.RELATIONSHIP, QUEST, "mentor");
            helper.assertFalse(report.nodes().get(key).missing(), "authored relationship is declared");
            helper.assertTrue(report.references().stream().anyMatch(reference -> reference.to().equals(key)
                    && reference.source().pointer().equals("/topics/0/conditions/0/conditions/0/condition/subject")), "nested condition retains its exact pointer");
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().getAsJsonArray("conditions").get(0).getAsJsonObject()
                    .getAsJsonArray("conditions").get(0).getAsJsonObject().getAsJsonObject("condition").addProperty("reaction", "missing");
            fixture.install(fixture.graph);
            var catalog = CampaignResources.current(fixture.base.server);
            var campaign = fixture.base.raw(); addStandings(campaign);
            var checked = CampaignContentCatalog.compile(catalog.quests(), List.of(new CampaignContentCatalog.Source(
                    ResourceLocation.parse("campaign_fixture:campaigns/characters/campaign.json"), "test", campaign,
                    catalog.campaigns().get(QUEST).localization(), List.of())));
            helper.assertTrue(checked.diagnostics().stream().anyMatch(issue ->
                    issue.jsonPointer().equals("/topics/0/conditions/0/conditions/0/condition/reaction")), "unknown reaction reports the authored field");
            var condition = catalog.quests().conversations().values().iterator().next().topics().values().iterator().next().conditions().getFirst();
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), fixture.player(), fixture.mentor);
            helper.assertFalse(condition.unavailableReason(context).isBlank(), "unknown reaction remains unavailable");
            helper.assertFalse(condition.matches(context), "inversion cannot make an unavailable reaction true");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void aReplacementHasItsOwnRelationshipAndCannotReceiveAnOldOperation(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper, true); var disk = new CampaignStandingTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            var original = action("character", "mentor", "help");
            helper.assertTrue(CampaignStandingService.execute(context, original).success(), "original character receives the change");
            var originalKey = disk.data().values().keySet().iterator().next();
            var successor = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 1);
            successor.setNoAi(true);
            try {
                fixture.mentor.discard();
                helper.assertValueEqual(fixture.base.resolve().entity(), successor, "authored successor takes over");
                helper.assertValueEqual(read(fixture, player, Scope.CHARACTER, "mentor").value(), 0, "successor starts with an independent relationship");
                var nextContext = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, successor);
                helper.assertFalse(CampaignStandingService.execute(nextContext, original).success(), "actions wait for the saved quest provider to reconcile");
                var quest = com.jvn.villagerretaliation.quest.VillagerQuestResources.quest(fixture.base.server, QUEST).orElseThrow();
                var progress = com.jvn.villagerretaliation.quest.VillagerQuestSavedData.get(helper.getLevel()).get(player.getUUID(), QUEST);
                helper.assertTrue(CampaignQuestReconciliation.reconcile(player, quest, progress), "quest records its authored successor");
                var replay = CampaignStandingService.execute(nextContext, original);
                helper.assertTrue(replay.success(), "retry recognizes the original operation: " + replay.message());
                helper.assertValueEqual(read(fixture, player, Scope.CHARACTER, "mentor").value(), 0, "retry cannot award a replacement");
                helper.assertTrue(CampaignStandingService.execute(nextContext, action("character", "mentor", "new_help")).success(), "new event can affect the successor");
                disk.reload(); fixture.base.reload();
                helper.assertValueEqual(disk.data().values().get(originalKey).amount(), 20, "original relationship remains readable");
                var reading = read(fixture, player, Scope.CHARACTER, "mentor");
                helper.assertValueEqual(reading.subject().key().character(), successor.getUUID(), "current standing belongs to the successor UUID");
                helper.assertValueEqual(reading.value(), 20, "successor relationship survives reload");
                helper.assertValueEqual(disk.data().receipts().size(), 2, "only distinct personal events apply");
            } finally { successor.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_standing_runtime")
    public static void questStandingReferencesKeepResolvableSourceLocations(GameTestHelper helper) throws IOException {
        var authored = new com.google.gson.JsonObject[1];
        try (var fixture = new CampaignConversationGameTests.Fixture(helper, false, false, false,
                CampaignStandingRuntimeGameTests::addStandings, quest -> {
                    quest.getAsJsonObject("availability").add("conditions", JsonParser.parseString("""
                            [{"type":"campaign_standing","campaign":"campaign_fixture:characters","scope":"faction","subject":"wardens","value":0}]
                            """));
                    quest.getAsJsonArray("stages").get(0).getAsJsonObject().add("on_enter", JsonParser.parseString("""
                            [{"type":"campaign_standing","scope":"faction","subject":"wardens","amount":1,"operation_id":"first_meeting"}]
                            """));
                    authored[0] = quest.deepCopy();
                })) {
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            var references = report.references().stream().filter(reference -> reference.from().kind() == CampaignReferenceReport.Kind.QUEST
                    && reference.to().kind() == CampaignReferenceReport.Kind.FACTION).toList();
            helper.assertTrue(references.size() >= 2, "quest standing reads and writes are both reported");
            for (var reference : references) {
                com.google.gson.JsonElement cursor = authored[0];
                String pointer = reference.source().pointer();
                if (!pointer.isEmpty()) for (String token : pointer.substring(1).split("/")) {
                    String key = token.replace("~1", "/").replace("~0", "~");
                    cursor = cursor.isJsonArray() ? cursor.getAsJsonArray().get(Integer.parseInt(key)) : cursor.getAsJsonObject().get(key);
                    helper.assertTrue(cursor != null, "standing source resolves: " + pointer);
                }
            }
        }
        helper.succeed();
    }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper) throws IOException { return fixture(helper, false); }

    private static CampaignConversationGameTests.Fixture fixture(GameTestHelper helper, boolean replacement) throws IOException {
        return new CampaignConversationGameTests.Fixture(helper, false, false, false, campaign -> {
            addStandings(campaign);
            if (replacement) {
                var mentor = campaign.getAsJsonArray("characters").get(0).getAsJsonObject();
                var successor = mentor.deepCopy(); successor.addProperty("id", "successor");
                campaign.getAsJsonArray("characters").add(successor);
                mentor.addProperty("loss_policy", "replacement"); mentor.addProperty("replacement_role", "successor");
            }
        }, quest -> {
            var stage = quest.getAsJsonArray("stages").get(0).getAsJsonObject();
            stage.remove("on_enter");
            stage.add("objectives", JsonParser.parseString("[{\"id\":\"decision\",\"type\":\"choice\"}]"));
        });
    }

    private static void addStandings(com.google.gson.JsonObject campaign) {
        var standing = JsonParser.parseString("""
                {"id":"mentor","title_key":"campaign_fixture.campaign.characters.mentor","minimum":-100,"maximum":100,"initial":0,
                 "reactions":[{"id":"hostile","title_key":"campaign_fixture.campaign.characters.mentor","minimum":-100},
                              {"id":"neutral","title_key":"campaign_fixture.campaign.characters.mentor","minimum":0},
                              {"id":"trusted","title_key":"campaign_fixture.campaign.characters.mentor","minimum":10}]}
                """).getAsJsonObject();
        var relationships = new com.google.gson.JsonArray(); relationships.add(standing);
        campaign.add("relationships", relationships);
        var faction = standing.deepCopy(); faction.addProperty("id", "wardens");
        var factions = new com.google.gson.JsonArray(); factions.add(faction); campaign.add("factions", factions);
    }

    private static void startQuest(CampaignConversationGameTests.Fixture fixture, ServerPlayer player) {
        var context = VillagerInteractionService.createDialogueContext(fixture.helper.getLevel(), player, fixture.mentor);
        fixture.base.select(context, "visit", "offer");
        VillagerConversationService.endForPlayer(player, false);
    }

    private static VillagerActionDefinition action(String scope, String subject, String operation) {
        var raw = JsonParser.parseString("{\"actions\":[{\"type\":\"campaign_standing\",\"scope\":\"" + scope
                + "\",\"subject\":\"" + subject + "\",\"amount\":20,\"operation_id\":\"" + operation + "\"}]}").getAsJsonObject();
        return VillagerActionDefinition.readList(QUEST, "test", raw, QUEST).getFirst();
    }

    private static CampaignStandingService.Reading read(CampaignConversationGameTests.Fixture fixture, ServerPlayer player, Scope scope, String subject) {
        return CampaignStandingService.read(fixture.base.server, QUEST, player.getUUID(), scope, subject);
    }
}
