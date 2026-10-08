package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.quest.QuestRegistryMetadata;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.io.IOException;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignStoryObjectiveAuthoringGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("campaign_fixture:characters");
    private static final String SPEAK = """
            {"id":"speak","type":"campaign_speak",
             "story":{"role":"mentor","conversation":"campaign_fixture:mentor_conversation","node":"welcome"}}
            """;
    private static final String OUTCOME = """
            {"id":"resolve","type":"campaign_outcome","story":{"milestone":"decision","outcome":"help","scope":"world"}}
            """;

    private CampaignStoryObjectiveAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_story_authoring")
    public static void storyFieldsUseTheSameRuntimeAndExportedSchema(GameTestHelper helper) throws IOException {
        var registries = QuestRegistryMetadata.export().getAsJsonObject("registries");
        var schemas = registries.getAsJsonArray("objectives");
        var publicTypes = new java.util.HashSet<String>();
        registries.getAsJsonArray("public_quest_objectives").forEach(entry -> publicTypes.add(entry.getAsJsonObject().get("id").getAsString()));
        for (var entry : schemas) helper.assertTrue(publicTypes.contains("villagerretaliation:" + entry.getAsJsonObject().get("id").getAsString()),
                "every built-in objective is present in the public registry");
        for (String raw : List.of(SPEAK, OUTCOME, "{\"id\":\"inspect\",\"type\":\"campaign_investigate\",\"story\":{\"evidence\":\"ledger\"}}",
                "{\"id\":\"follow\",\"type\":\"campaign_follow\",\"story\":{\"role\":\"mentor\",\"location\":\"home\",\"scene\":\"test:walk\",\"step\":\"walk\"}}")) {
            var objective = JsonParser.parseString(raw).getAsJsonObject();
            JsonObject schema = null;
            for (var entry : schemas) if (entry.getAsJsonObject().get("id").getAsString().equals(objective.get("type").getAsString()))
                schema = entry.getAsJsonObject().getAsJsonObject("schema");
            helper.assertTrue(schema != null, "registered objective exports its structural schema");
            verify(helper, objective, schema, true);
            var missingType = objective.deepCopy();
            missingType.remove("type");
            verify(helper, missingType, schema, false);
            for (String required : objective.getAsJsonObject("story").keySet()) {
                var missing = objective.deepCopy();
                missing.getAsJsonObject("story").remove(required);
                verify(helper, missing, schema, false);
            }
            for (String fields : List.of("{\"story\":null}", "{\"story\":true}", "{\"count\":1}",
                    "{\"item\":\"minecraft:paper\"}", "{\"criterion\":\"minecraft:tick\"}", "{\"campaign_location\":\"home\"}")) {
                var invalid = objective.deepCopy();
                JsonParser.parseString(fields).getAsJsonObject().entrySet().forEach(entry -> invalid.add(entry.getKey(), entry.getValue()));
                verify(helper, invalid, schema, false);
            }
            for (var entry : objective.getAsJsonObject("story").entrySet()) {
                for (String invalid : List.of("null", "true", "\"has spaces\"")) {
                    var changed = objective.deepCopy();
                    changed.getAsJsonObject("story").add(entry.getKey(), JsonParser.parseString(invalid));
                    verify(helper, changed, schema, false);
                }
            }
            var unknown = objective.deepCopy();
            unknown.getAsJsonObject("story").addProperty("unexpected", true);
            verify(helper, unknown, schema, false);
            var unowned = quest(objective);
            unowned.getAsJsonObject("metadata").remove("campaign");
            helper.assertTrue(QuestV2Parser.parse(QUEST, unowned).isEmpty(), "story objectives require campaign ownership");
            var unrelated = objective.deepCopy();
            unrelated.addProperty("type", "choice");
            helper.assertFalse(QuestObjectiveRegistry.structuralIssues(unrelated).isEmpty(), "ordinary objectives cannot hide story fields");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_authoring")
    public static void compiledStoryObjectivesValidateAuthoredCharacterAndConversationReferences(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignStoryObjectiveGameTests.fixture(helper, SPEAK)) {
            var objective = fixture.base.catalog.quest(QUEST).orElseThrow().objectives().getFirst();
            var story = (CampaignStoryObjective.Speak) objective.story();
            helper.assertValueEqual(story.role(), "mentor", "compiled objective retains typed role");
            helper.assertValueEqual(story.node(), "welcome", "compiled objective retains typed node");
            var campaign = fixture.base.campaigns.campaigns().get(QUEST).definition();
            var catalog = fixture.base.catalog;
            helper.assertTrue(CampaignStoryObjective.referenceIssues(story, campaign, catalog, QUEST).isEmpty(), "valid references compile");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Speak("missing", story.conversation(), story.node()),
                    campaign, catalog, QUEST).containsKey("role"), "missing character is diagnosed");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Speak("mentor", story.conversation(), "missing"),
                    campaign, catalog, QUEST).containsKey("node"), "missing node is diagnosed");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Speak("mentor", ResourceLocation.parse("test:missing"), "welcome"),
                    campaign, catalog, QUEST).containsKey("conversation"), "missing conversation is diagnosed");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(story, campaign, catalog, ResourceLocation.parse("test:other"))
                    .containsKey("conversation"), "private conversations cannot be borrowed by another quest");
            var raw = fixture.base.raw();
            raw.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("id", "renamed");
            var changed = CampaignDefinitionParser.parse(QUEST, raw).definition();
            var diagnostics = CampaignDefinitionParser.validateQuestReferences(changed, catalog);
            helper.assertTrue(diagnostics.stream().anyMatch(value -> value.toString().contains("/stages/0/objectives/0/story/role")),
                    "reference diagnostic retains the authored objective pointer: " + diagnostics);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_story_authoring")
    public static void outcomeObjectivesRequireNamedOutcomesAndCompatibleScopes(GameTestHelper helper) throws IOException {
        try (var fixture = CampaignStoryObjectiveGameTests.fixture(helper, OUTCOME)) {
            var objective = fixture.base.catalog.quest(QUEST).orElseThrow().objectives().getFirst();
            var story = (CampaignStoryObjective.Outcome) objective.story();
            helper.assertValueEqual(story.scope(), CampaignOutcomePredicate.Scope.WORLD, "compiled scope is typed");
            var campaign = fixture.base.campaigns.campaigns().get(QUEST).definition();
            for (var bad : List.of(new CampaignStoryObjective.Outcome("missing", "help", story.scope()),
                    new CampaignStoryObjective.Outcome("decision", "missing", story.scope()))) {
                helper.assertFalse(CampaignStoryObjective.referenceIssues(bad, campaign, fixture.base.catalog, QUEST).isEmpty(),
                        "unknown milestone or outcome is diagnosed");
            }
            var raw = fixture.base.raw();
            raw.add("milestones", JsonParser.parseString("""
                    [{"id":"personal","chapter":"arrival","quest":"campaign_fixture:characters","irreversible":false,
                      "outcomes":[{"id":"help","title_key":"campaign_fixture.campaign.characters.mentor"}]}]
                    """));
            var personal = CampaignDefinitionParser.parse(QUEST, raw).definition();
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Outcome("personal", "help", CampaignOutcomePredicate.Scope.WORLD),
                    personal, fixture.base.catalog, QUEST).containsKey("scope"), "personal outcome cannot claim world scope");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Outcome("personal", "help", CampaignOutcomePredicate.Scope.COMPLETED),
                    personal, fixture.base.catalog, QUEST).isEmpty(), "personal completion is valid");
        }
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, JsonObject objective, JsonObject descriptor, boolean valid) throws IOException {
        var quest = quest(objective);
        helper.assertValueEqual(QuestObjectiveRegistry.structuralIssues(objective).isEmpty(), valid, "runtime schema: " + objective);
        helper.assertValueEqual(QuestJsonSchema.validate(objective, descriptor).isEmpty(), valid, "descriptor schema: " + objective);
        var exported = new JsonObject();
        exported.add("allOf", QuestV2Schema.export().getAsJsonObject("$defs").getAsJsonObject("objective").get("allOf"));
        helper.assertValueEqual(QuestJsonSchema.validate(objective, exported).isEmpty(), valid, "exported objective rules: " + objective);
        helper.assertValueEqual(QuestV2Parser.parse(QUEST, quest).isPresent(), valid, "typed parser: " + objective);
    }

    private static JsonObject quest(JsonObject objective) throws IOException {
        var root = CampaignCharacterTestFixture.resource("quests/campaign_fixture/characters/quest.json");
        var objectives = new com.google.gson.JsonArray();
        objectives.add(objective);
        root.getAsJsonArray("stages").get(0).getAsJsonObject().add("objectives", objectives);
        return root;
    }
}
