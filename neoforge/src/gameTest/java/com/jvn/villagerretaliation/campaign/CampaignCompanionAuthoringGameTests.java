package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSchema;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.schema.SceneSchema;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignCompanionAuthoringGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("campaign_fixture:investigation");

    private CampaignCompanionAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_companion")
    public static void sharedCasesMatchConditionsQuestsConversationsAndScenes(GameTestHelper helper) throws IOException {
        try (var stream = CampaignCompanionAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/companion_condition_cases.json")) {
            if (stream == null) throw new IOException("Missing companion condition cases.");
            for (var raw : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var test = raw.getAsJsonObject(); var condition = test.getAsJsonObject("condition");
                String name = test.get("name").getAsString();
                boolean valid = test.has("valid") && test.get("valid").getAsBoolean();
                var conditions = new JsonArray(); conditions.add(condition);
                var data = new JsonObject(); data.add("conditions", conditions);
                var parsed = DialogueCondition.readList(ID, name, data);
                helper.assertValueEqual(parsed.size() == 1 && parsed.getFirst() instanceof DialogueCondition.CampaignCompanion, valid, name + " runtime condition");
                helper.assertValueEqual(CampaignCompanionFields.issues(data).isEmpty(), valid, name + " scene fields");
                var quest = CampaignCharacterTestFixture.resource("quests/campaign_fixture/investigation/quest.json");
                quest.getAsJsonObject("availability").add("conditions", conditions);
                helper.assertValueEqual(QuestJsonSchema.validate(condition, conditionSchema()).isEmpty(), valid, name + " quest condition schema");
                helper.assertValueEqual(QuestV2Parser.parse(ID, quest).isPresent(), valid, name + " quest parser");
                var graph = CampaignCharacterTestFixture.resource("quests/campaign_fixture/investigation/conversations/ledger.json");
                graph.getAsJsonArray("topics").get(0).getAsJsonObject().add("conditions", conditions);
                helper.assertValueEqual(QuestJsonSchema.validate(graph, ConversationSchema.export()).isEmpty(), valid, name + " conversation schema");
                var scene = CampaignCharacterTestFixture.resource("quests/campaign_fixture/investigation/scenes/investigation_greeting.json");
                scene.getAsJsonArray("steps").get(0).getAsJsonObject().add("data", data);
                helper.assertValueEqual(QuestJsonSchema.validate(scene, SceneSchema.sceneV1()).isEmpty(), valid, name + " scene schema");
                helper.assertValueEqual(SceneCompiler.compile(SceneParser.parse(ResourceLocation.parse("campaign_fixture:investigation_greeting"), scene).resource()).valid(),
                        valid, name + " scene compiler");
                if (!valid) helper.assertTrue(CampaignCompanionFields.issues(data).stream().anyMatch(issue ->
                        issue.pointer().equals("/conditions/0" + test.get("pointer").getAsString())), name + " keeps its field pointer");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_companion")
    public static void missingCompanionReferencesRetainTheNestedConversationSource(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignConversationGameTests.Fixture(helper)) {
            var line = fixture.graph.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonArray("exchanges").get(0).getAsJsonObject();
            line.add("conditions", JsonParser.parseString("""
                    [{"type":"not","condition":{"type":"campaign_companion","campaign":"campaign_fixture:characters","role":"missing"}}]
                    """));
            fixture.install(fixture.graph);
            var catalog = com.jvn.villagerretaliation.campaign.content.CampaignResources.current(fixture.base.server);
            var campaign = catalog.campaigns().get(fixture.base.campaignId);
            var report = com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.build(catalog);
            String pointer = "/nodes/0/exchanges/0/conditions/0/condition/role";
            helper.assertTrue(report.references().stream().anyMatch(reference ->
                    reference.relation() == com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Relation.ACCOMPANIES
                    && report.nodes().get(reference.to()).missing() && reference.source().pointer().equals(pointer)),
                    "dependency report retains the missing role and nested source");
            var invalid = com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog.compile(catalog.quests(), java.util.List.of(
                    new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog.Source(campaign.definition().source(),
                            campaign.sourcePack(), fixture.base.raw(), campaign.localization(), java.util.List.of())));
            helper.assertTrue(invalid.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().equals(pointer)
                    && issue.message().contains("unknown campaign companion")), "catalog diagnoses the authored companion field");
        }
        helper.succeed();
    }

    private static JsonObject conditionSchema() {
        var schema = new JsonObject(); schema.addProperty("$ref", "#/$defs/condition");
        var definitions = new JsonObject(); definitions.add("condition", QuestV2Schema.export().getAsJsonObject("$defs").get("condition"));
        schema.add("$defs", definitions);
        return schema;
    }

}
