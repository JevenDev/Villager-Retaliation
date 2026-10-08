package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Kind.*;
import static com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Relation.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.*;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignReferenceGameTests {
    private CampaignReferenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void campaignReportsTraceDecisionsCatchUpFactsAndRewards(GameTestHelper helper) throws IOException {
        var quests = CampaignAuthoringGameTests.quests(helper);
        var root = CampaignCharacterTestFixture.resource("campaigns/reconciliation/campaign.json");
        var messages = QuestLocaleCatalog.read(CampaignCharacterTestFixture.resource("campaigns/reconciliation/locales/en_us.json"));
        var content = CampaignContentCatalog.compile(quests, List.of(new CampaignContentCatalog.Source(
                id("campaigns/reconciliation/campaign.json"), "report_fixture", root,
                QuestLocaleCatalog.empty().overlay("en_us", messages.messages()), List.of())));
        helper.assertTrue(content.diagnostics().isEmpty(), "report fixture compiles");
        var report = CampaignReferenceReport.build(content);
        contains(helper, report, key(CHAPTER, "reconciliation", "dragon"), key(CHAPTER, "reconciliation", "decision"), REQUIRES);
        contains(helper, report, key(MILESTONE, "reconciliation", "dragon"), key(QUEST, "dragon_catch_up"), CATCH_UP);
        contains(helper, report, key(MILESTONE, "reconciliation", "dragon"), key(OUTCOME, "reconciliation", "dragon", "defeated"), OBSERVES);
        contains(helper, report, key(QUEST, "decision"), key(OUTCOME, "reconciliation", "decision", "admitted"), COMMITS);
        contains(helper, report, key(QUEST, "decision_catch_up"), key(FACT, "reconciliation", "learned_decision"), READS);
        contains(helper, report, key(QUEST, "decision_catch_up"), key(FACT, "reconciliation", "learned_decision"), WRITES);
        contains(helper, report, key(QUEST, "decision_catch_up"), key(REWARD, "reconciliation", "decision_catch_up_personal"), GRANTS);
        helper.assertTrue(report.nodes().values().stream().noneMatch(Node::missing), "fixture references resolve");
        for (var reference : report.references()) resolves(helper, reference.source());
        helper.assertValueEqual(report, CampaignReferenceReport.build(content), "report ordering is repeatable");
        helper.assertTrue(report.nodes().get(key(CAMPAIGN, "reconciliation")).declarations().getFirst().pack().equals("report_fixture"), "campaign pack ownership survives compilation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void characterReferencesIncludeConversationsScenesAndOriginalSourcePointers(GameTestHelper helper) throws IOException {
        try (var fixture = characters(helper)) {
            var report = CampaignReferenceReport.build(fixture.campaigns);
            var mentor = key(CHARACTER, "characters", "mentor");
            contains(helper, report, key(CONVERSATION, "mentor_conversation"), mentor, SPEAKS);
            contains(helper, report, key(QUEST, "characters"), mentor, PROVIDER);
            contains(helper, report, key(SCENE, "character_greeting"), mentor, REQUIRES);
            contains(helper, report, mentor, key(LOCATION, "characters", "home"), LOCATED_AT);
            var dependents = report.trace(mentor, Direction.DEPENDENTS, 1);
            helper.assertTrue(dependents.stream().anyMatch(match -> match.reference().from().kind() == CONVERSATION), "reverse query finds conversations");
            helper.assertTrue(dependents.stream().anyMatch(match -> match.reference().from().kind() == SCENE), "reverse query finds scenes");
            for (var reference : report.references()) resolves(helper, reference.source());
            helper.assertTrue(report.references().stream().anyMatch(reference -> reference.to().equals(mentor)
                    && reference.source().pointer().equals("/actors/0/binding") && reference.source().pack().equals("campaign_fixture")),
                    "actor reference retains its authored position and pack");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void nestedConditionsAndEffectsExposeMissingStoryTargetsWithoutApplyingThem(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            fixture.graph.getAsJsonArray("topics").get(0).getAsJsonObject().add("conditions", JsonParser.parseString("""
                    [{"type":"all","conditions":[
                      {"type":"any","conditions":[{"type":"campaign_fact","campaign":"campaign_fixture:characters","fact":"missing_fact","value":"known"}]},
                      {"type":"not","condition":{"type":"campaign_character","campaign":"campaign_fixture:characters","role":"absent","state":"bound"}},
                      {"type":"campaign_location","campaign":"campaign_fixture:characters","location":"elsewhere"},
                      {"type":"campaign_outcome","campaign":"campaign_fixture:other","milestone":"decision","outcome":"done","scope":"world"}
                    ]}]
                    """));
            fixture.effects("""
                    [
                      {"id":"remember","action":{"type":"campaign_fact","fact":"missing_fact","value":"known","operation_id":"remember"}},
                      {"id":"reward","action":{"type":"campaign_reward","reward":"missing_reward","operation_id":"reward"}},
                      {"id":"decide","action":{"type":"campaign_outcome","milestone":"missing_milestone","outcome":"done","operation_id":"decide"}}
                    ]
                    """);
            var before = Map.copyOf(CampaignSavedData.get(helper.getLevel()).instances());
            var report = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            var owner = key(CONVERSATION, "mentor_conversation");
            contains(helper, report, owner, key(FACT, "characters", "missing_fact"), READS);
            contains(helper, report, owner, key(FACT, "characters", "missing_fact"), WRITES);
            contains(helper, report, owner, key(CHARACTER, "characters", "absent"), READS);
            contains(helper, report, owner, key(LOCATION, "characters", "elsewhere"), READS);
            contains(helper, report, owner, key(OUTCOME, "other", "decision", "done"), READS);
            contains(helper, report, owner, key(REWARD, "characters", "missing_reward"), GRANTS);
            contains(helper, report, owner, key(OUTCOME, "characters", "missing_milestone", "done"), COMMITS);
            helper.assertTrue(report.nodes().get(key(FACT, "characters", "missing_fact")).missing(), "missing target stays inspectable");
            helper.assertValueEqual(CampaignSavedData.get(helper.getLevel()).instances(), before, "reporting applies no campaign effects");
            fixture.effects("""
                    [{"id":"unavailable","action":{"type":"campaign_fact","quest":"campaign_fixture:missing_quest","fact":"must_not_inherit","value":"known","operation_id":"unavailable"}}]
                    """);
            var unavailable = CampaignReferenceReport.build(CampaignResources.current(fixture.base.server));
            contains(helper, unavailable, owner, key(QUEST, "missing_quest"), REQUIRES);
            helper.assertTrue(unavailable.nodes().get(key(QUEST, "missing_quest")).missing(), "missing action quest remains visible");
            helper.assertFalse(unavailable.nodes().containsKey(key(FACT, "characters", "must_not_inherit")), "missing action quest cannot inherit the conversation campaign");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void reportsRefreshAfterContentChangesAndKeepOldSnapshotsReadable(GameTestHelper helper) throws IOException {
        try (var fixture = characters(helper)) {
            var original = CampaignReferenceReport.build(fixture.campaigns);
            var missing = CampaignReferenceReport.build(new CampaignContentCatalog(fixture.catalog, Map.of(), List.of()));
            var mentor = key(CHARACTER, "characters", "mentor");
            helper.assertTrue(missing.nodes().get(mentor).missing(), "missing campaign preserves incoming references");
            helper.assertFalse(original.nodes().get(mentor).missing(), "prior report remains immutable");
            helper.assertValueEqual(CampaignReferenceReport.build(fixture.campaigns), original, "restored content restores the same report");
            var old = fixture.catalog;
            var revised = new QuestContentCatalog(old.generation() + 1, old.compiledQuestCatalog(), old.dialogueCatalog(), old.quests(),
                    old.objectiveEventQuestIds(), old.factQuestIds(), old.memoryEventQuestIds(), old.exclusiveGroupQuestIds(), old.triggerEventQuestIds(),
                    old.scenes(), old.encounters(), old.pools(), Map.of(), old.localization(), old.rewards(), old.conversations());
            var shared = CampaignReferenceReport.build(new CampaignContentCatalog(revised, fixture.campaigns.campaigns(), List.of()));
            helper.assertValueEqual(shared.generation(), revised.generation(), "report records the actual content generation");
            contains(helper, shared, key(SCENE, "character_greeting"), mentor, REQUIRES);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void referenceTraversalBoundsCyclesAndKeepsScopedIdsDistinct(GameTestHelper helper) {
        var left = key(OUTCOME, "reconciliation", "a/b", "c");
        var right = key(OUTCOME, "reconciliation", "a", "b/c");
        helper.assertFalse(left.text().equals(right.text()), "escaped keys cannot alias different scoped IDs");
        var source = new Source(id("campaigns/reconciliation/campaign.json"), "test", "/milestones");
        var nodes = new LinkedHashMap<Key, Node>();
        nodes.put(left, new Node(left, List.of(source)));
        nodes.put(right, new Node(right, List.of(source)));
        var edges = new java.util.ArrayList<Reference>();
        edges.add(new Reference(left, right, REQUIRES, source));
        edges.add(new Reference(right, left, REQUIRES, source));
        var report = new CampaignReferenceReport(2, nodes, edges);
        nodes.clear();
        edges.clear();
        helper.assertValueEqual(report.trace(left, Direction.DEPENDENCIES, 1).size(), 1, "direct query stays at one hop");
        helper.assertValueEqual(report.trace(left, Direction.DEPENDENCIES, 16).size(), 2, "cycle reports each edge once");
        helper.assertValueEqual(report.trace(left, Direction.DEPENDENTS, 16).getFirst().reference().from(), right, "reverse query follows incoming edges");
        for (int depth : List.of(0, 17)) {
            boolean rejected = false;
            try { report.trace(left, Direction.DEPENDENCIES, depth); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "query rejects unbounded depth");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void reportsLinkAlternateOutcomesToLocationVariantsAndEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignVariantTestFixture(helper)) {
            var report = CampaignReferenceReport.build(fixture.base.campaigns);
            var outcome = key(OUTCOME, "variants", "gate", "open");
            contains(helper, report, outcome, key(VARIANT, "variants", "open"), CHANGES);
            contains(helper, report, key(VARIANT, "variants", "open"), key(LOCATION, "variants", "home"), CHANGES);
            helper.assertTrue(report.trace(outcome, Direction.DEPENDENCIES, 2).stream().anyMatch(match ->
                    match.reference().to().equals(key(LOCATION, "variants", "home"))), "transitive report reaches the affected location");
        }
        try (var fixture = new CampaignCharacterTestFixture(helper, "investigation", "investigation_greeting")) {
            var report = CampaignReferenceReport.build(fixture.campaigns);
            contains(helper, report, key(QUEST, "investigation"), key(EVIDENCE, "investigation", "ledger"), INVESTIGATES);
            contains(helper, report, key(EVIDENCE, "investigation", "ledger"), key(LOCATION, "investigation", "home"), LOCATED_AT);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void operatorReportsRequirePermissionAndValidatePagesWithoutChangingState(GameTestHelper helper) throws Exception {
        try (var fixture = characters(helper)) {
            var dispatcher = fixture.server.getCommands().getDispatcher();
            var source = fixture.server.createCommandSourceStack().withPermission(2).withSuppressedOutput();
            String prefix = "vr admin campaign references ";
            String mentor = com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(key(CHARACTER, "characters", "mentor").text());
            for (String query : List.of("list character", "inspect " + mentor, "dependencies " + mentor + " 2",
                    "dependents " + mentor + " 2 1")) helper.assertValueEqual(dispatcher.execute(prefix + query, source), 1, query);
            helper.assertValueEqual(dispatcher.execute(prefix + "list character 999", source), 0, "invalid page is rejected");
            helper.assertValueEqual(dispatcher.execute(prefix + "list unknown", source), 0, "unknown kind is rejected");
            helper.assertValueEqual(dispatcher.execute(prefix + "inspect missing", source), 0, "unknown identity is rejected");
            boolean denied = false;
            try { dispatcher.execute(prefix + "list", source.withPermission(0)); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
            helper.assertTrue(denied, "operator permission is required");
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix + "inspect \"character/", source)).join();
            helper.assertTrue(suggestions.getList().stream().anyMatch(value -> value.getText().equals(mentor)), "commands suggest stable reference identities");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "commands do not create campaign progress");
            helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "commands do not cast characters");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void followingReferencesKeepTheirRoleDestinationAndScene(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignCharacterTestFixture(helper, "following", "mentor_walk")) {
            var report = CampaignReferenceReport.build(fixture.campaigns);
            contains(helper, report, key(QUEST, "following"), key(CHARACTER, "following", "mentor"), FOLLOWS);
            contains(helper, report, key(QUEST, "following"), key(LOCATION, "following", "destination"), REQUIRES);
            contains(helper, report, key(QUEST, "following"), key(SCENE, "mentor_walk"), STARTS);
            for (var reference : report.references()) resolves(helper, reference.source());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_references")
    public static void explicitQuestLocksAppearInTheDependencyReport(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of("decision", "dragon"),
                quest -> quest.getAsJsonObject("availability").add("branch", JsonParser.parseString("""
                        {"blocks_on_start":["campaign_fixture:decision"],"blocks_on_completion":["campaign_fixture:dragon"]}
                        """)), campaign -> { })) {
            var report = CampaignReferenceReport.build(fixture.campaigns);
            contains(helper, report, key(QUEST, "characters"), key(QUEST, "decision"), BLOCKS_ON_START);
            contains(helper, report, key(QUEST, "characters"), key(QUEST, "dragon"), BLOCKS_ON_COMPLETION);
            helper.assertFalse(report.nodes().get(key(QUEST, "decision")).missing(), "locked quest retains its compiled declaration");
        }
        helper.succeed();
    }

    private static CampaignCharacterTestFixture characters(GameTestHelper helper) throws IOException {
        return new CampaignCharacterTestFixture(helper, "characters", "character_greeting", List.of(),
                quest -> { }, campaign -> { }, List.of("conversations/mentor.json"));
    }

    private static void contains(GameTestHelper helper, CampaignReferenceReport report, Key from, Key to, Relation relation) {
        helper.assertTrue(report.references().stream().anyMatch(reference -> reference.from().equals(from)
                && reference.to().equals(to) && reference.relation() == relation), from.text() + " " + relation + " " + to.text());
    }

    private static void resolves(GameTestHelper helper, Source source) throws IOException {
        JsonElement value = CampaignCharacterTestFixture.resource(source.resource().getPath());
        if (!source.pointer().isEmpty()) for (String token : source.pointer().substring(1).split("/")) {
            String key = token.replace("~1", "/").replace("~0", "~");
            value = value.isJsonArray() ? value.getAsJsonArray().get(Integer.parseInt(key)) : value.getAsJsonObject().get(key);
            helper.assertTrue(value != null, "source pointer resolves: " + source.text());
        }
    }

    private static ResourceLocation id(String value) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", value); }
    private static Key key(Kind kind, String owner, String... path) { return Key.of(kind, id(owner), path); }
}
