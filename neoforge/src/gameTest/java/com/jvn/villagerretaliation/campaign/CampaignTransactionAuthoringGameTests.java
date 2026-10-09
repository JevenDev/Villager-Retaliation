package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
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
public final class CampaignTransactionAuthoringGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("campaign_fixture:payment_scene");

    private CampaignTransactionAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void sharedSceneCasesMatchRuntimeAndExportedSchema(GameTestHelper helper) throws IOException {
        try (var stream = CampaignTransactionAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/transaction_scene_cases.json")) {
            if (stream == null) throw new IOException("Missing transaction scene authoring cases.");
            for (var entry : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var test = entry.getAsJsonObject();
                var data = test.getAsJsonObject("data");
                var scene = scene(data);
                boolean valid = test.has("valid") && test.get("valid").getAsBoolean();
                String name = test.get("name").getAsString();
                var structural = CampaignTransactionSceneFields.issues(data);
                var exported = QuestJsonSchema.validate(scene, SceneSchema.sceneV1());
                var compiled = SceneCompiler.compile(SceneParser.parse(ID, scene).resource());
                helper.assertValueEqual(structural.isEmpty(), valid, name + ": " + structural);
                helper.assertValueEqual(exported.isEmpty(), valid, name + ": " + exported);
                helper.assertValueEqual(compiled.valid(), valid, name + ": " + compiled.diagnostics());
                if (!valid) {
                    String pointer = test.get("pointer").getAsString();
                    helper.assertTrue(structural.stream().anyMatch(issue -> issue.pointer().equals(pointer)), name + " keeps its data pointer: " + structural);
                    helper.assertTrue(exported.stream().anyMatch(issue -> issue.pointer().equals("/steps/0/data" + pointer)), name + " keeps its scene pointer: " + exported);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void sceneReferencesUseRuntimeQuestPrecedenceAndNestedSourcePointers(GameTestHelper helper) {
        var data = JsonParser.parseString("""
                {"actions":[{"id":"buy","type":"campaign_payment","quest":"campaign_fixture:characters",
                             "quest_id":"campaign_fixture:other","transaction":"supplies"}],
                 "conditions":[{"type":"all","conditions":[{"type":"not","condition":{
                   "type":"campaign_transaction","campaign":"campaign_fixture:characters","quest":"campaign_fixture:characters",
                   "transaction":"supplies","state":"unpaid"}}]}]}
                """).getAsJsonObject();
        var references = CampaignTransactionSceneFields.references(data);
        var actions = VillagerActionDefinition.readList(ID, "test", data);
        helper.assertValueEqual(references.size(), 2, "both payment and nested condition are reported");
        helper.assertValueEqual(references.getFirst().quest(), actions.getFirst().questId(), "reference resolution uses the runtime owner");
        helper.assertValueEqual(references.getFirst().pointer(), "/actions/0/transaction", "payment keeps its authored position");
        helper.assertValueEqual(references.getLast().pointer(), "/conditions/0/conditions/0/condition/transaction", "nested condition keeps its authored position");
        helper.assertFalse(references.getLast().payment(), "a condition reads its transaction");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void dependencyReportsAndCatalogDiagnosticsKeepScenePaymentSources(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignCharacterTestFixture(helper, "characters", "character_greeting", java.util.List.of(),
                quest -> { }, campaign -> campaign.add("transactions", JsonParser.parseString("""
                        [{"id":"supplies","title_key":"campaign_fixture.campaign.characters.mentor","quest":"campaign_fixture:characters",
                          "character":"mentor","payment":{"item":"minecraft:emerald","count":8},"bargained_count":5}]
                        """)), java.util.List.of(), scene -> {
                    var first = scene.getAsJsonArray("steps").get(0).getAsJsonObject();
                    first.addProperty("type", "villagerretaliation:action_batch");
                    first.add("data", JsonParser.parseString("""
                            {"actions":[{"id":"buy","type":"campaign_payment","quest":"campaign_fixture:characters","transaction":"supplies"}]}
                            """));
                })) {
            var report = com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.build(fixture.campaigns);
            var reference = report.references().stream().filter(value ->
                    value.from().kind() == com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Kind.SCENE
                    && value.to().kind() == com.jvn.villagerretaliation.campaign.content.CampaignReferenceReport.Kind.TRANSACTION).findFirst().orElseThrow();
            helper.assertValueEqual(reference.source().pointer(), "/steps/0/data/actions/0/transaction", "scene payment keeps its original source pointer");
            helper.assertValueEqual(reference.source().pack(), "campaign_fixture", "scene payment retains its owning pack");
            helper.assertValueEqual(reference.source().resource(), ResourceLocation.parse(
                    "campaign_fixture:quests/campaign_fixture/characters/scenes/character_greeting.json"), "source is the authored scene file");
            helper.assertFalse(report.nodes().get(reference.to()).missing(), "authored transaction is declared in the dependency graph");
            var content = com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog.compile(fixture.catalog, java.util.List.of(
                    new com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog.Source(
                            fixture.campaigns.campaigns().get(fixture.campaignId).definition().source(), "campaign_fixture", fixture.raw(),
                            fixture.campaigns.campaigns().get(fixture.campaignId).localization(), java.util.List.of())));
            helper.assertTrue(content.diagnostics().stream().anyMatch(issue ->
                    issue.jsonPointer().equals(reference.source().pointer()) && issue.message().contains("unknown campaign transaction supplies")),
                    "missing declaration is reported at the payment's scene source");
            helper.assertTrue(CampaignSavedData.get(helper.getLevel()).instances().isEmpty(), "reporting never starts the campaign");
            helper.assertTrue(CampaignCharacterSavedData.get(helper.getLevel()).bindings().isEmpty(), "reporting never binds the seller");
        }
        helper.succeed();
    }

    private static JsonObject scene(JsonObject data) {
        var scene = JsonParser.parseString("""
                {"schema":"villagerretaliation:scene/v1","id":"campaign_fixture:payment_scene","ownership":"player",
                 "entry_step":"pay","actors":[],"steps":[
                   {"id":"pay","type":"villagerretaliation:action_batch","data":{},"next":"done"},
                   {"id":"done","type":"villagerretaliation:scene_complete"}]}
                """).getAsJsonObject();
        scene.getAsJsonArray("steps").get(0).getAsJsonObject().add("data", data.deepCopy());
        return scene;
    }
}
