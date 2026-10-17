package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport;
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
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignEvidenceAuthoringGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("campaign_fixture:investigation");

    private CampaignEvidenceAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_evidence")
    public static void sharedCasesMatchConditionsQuestsConversationsAndScenes(GameTestHelper helper) throws IOException {
        try (var stream = CampaignEvidenceAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/evidence_condition_cases.json")) {
            if (stream == null) throw new IOException("Missing evidence condition cases.");
            for (var raw : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var test = raw.getAsJsonObject(); var condition = test.getAsJsonObject("condition");
                String name = test.get("name").getAsString();
                boolean valid = test.has("valid") && test.get("valid").getAsBoolean();
                var conditions = new JsonArray(); conditions.add(condition);
                var data = new JsonObject(); data.add("conditions", conditions);
                var parsed = DialogueCondition.readList(ID, name, data);
                helper.assertValueEqual(parsed.size() == 1 && parsed.getFirst() instanceof DialogueCondition.CampaignEvidence, valid, name + " runtime condition");
                helper.assertValueEqual(CampaignEvidenceFields.issues(data).isEmpty(), valid, name + " scene fields");
                var quest = com.jvn.villagerretaliation.quest.schema.v3.QuestV3Format.runtime(CampaignCharacterTestFixture.resource("quests/campaign_fixture/investigation/quest.json"));
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
                if (!valid) helper.assertTrue(CampaignEvidenceFields.issues(data).stream().anyMatch(issue ->
                        issue.pointer().equals("/conditions/0" + test.get("pointer").getAsString())), name + " keeps its field pointer");
            }
        }
        helper.succeed();
    }

    private static JsonObject conditionSchema() {
        var schema = new JsonObject(); schema.addProperty("$ref", "#/$defs/condition");
        var definitions = new JsonObject(); definitions.add("condition", QuestV2Schema.export().getAsJsonObject("$defs").get("condition"));
        schema.add("$defs", definitions);
        return schema;
    }

    @GameTest(template = "empty", batch = "campaign_evidence")
    public static void referenceReportsAndMissingEvidenceKeepAuthoredSourcePointers(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var report = CampaignReferenceReport.build(fixture.base.campaigns);
            var references = report.references().stream().filter(reference -> reference.from().kind() == CampaignReferenceReport.Kind.CONVERSATION
                    && reference.to().kind() == CampaignReferenceReport.Kind.EVIDENCE).toList();
            helper.assertValueEqual(references.size(), 3, "topic, proof, and missing-proof conditions reference the evidence");
            helper.assertTrue(references.stream().anyMatch(reference -> reference.source().pointer().equals("/nodes/0/responses/1/conditions/0/condition/evidence")),
                    "nested negation retains its authored condition pointer");
            for (var reference : references) {
                helper.assertValueEqual(reference.source().pack(), "campaign_fixture", "condition retains its source pack");
                helper.assertValueEqual(reference.source().resource(), ResourceLocation.parse(
                        "campaign_fixture:quests/campaign_fixture/investigation/conversations/ledger.json"), "condition points to the owned conversation");
                helper.assertFalse(report.nodes().get(reference.to()).missing(), "authored evidence is declared");
            }
            var root = fixture.base.raw(); root.remove("evidence");
            var campaign = fixture.base.campaigns.campaigns().get(ID);
            var missing = CampaignContentCatalog.compile(fixture.base.catalog, List.of(new CampaignContentCatalog.Source(
                    campaign.definition().source(), "campaign_fixture", root, campaign.localization(), List.of())));
            helper.assertTrue(missing.diagnostics().stream().anyMatch(issue ->
                    issue.jsonPointer().equals("/nodes/0/responses/1/conditions/0/condition/evidence")
                            && issue.message().contains("unknown campaign evidence")), "missing evidence diagnoses the authored condition");
            helper.assertTrue(fixture.data().receipts().isEmpty(), "reference inspection cannot invent a discovery");
        }
        helper.succeed();
    }
}
