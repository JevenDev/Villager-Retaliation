package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.scene.compiler.SceneCompiler;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.model.CampaignFollowDefinition;
import com.jvn.villagerretaliation.scene.schema.SceneSchema;
import java.io.IOException;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignFollowAuthoringGameTests {
    private static final ResourceLocation SCENE = ResourceLocation.parse("campaign_fixture:mentor_walk");

    private CampaignFollowAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_follow_authoring")
    public static void followDataUsesTheSameDescriptorCompilerAndExportedSchema(GameTestHelper helper) throws IOException {
        var original = CampaignCharacterTestFixture.resource("quests/campaign_fixture/following/scenes/mentor_walk.json");
        verify(helper, original, true);
        var compiled = SceneCompiler.compile(SceneParser.parse(SCENE, original).resource()).scene();
        helper.assertValueEqual(compiled.steps().get("walk").follow().followDistance(), 4.0, "authored follow radius is compiled");
        helper.assertValueEqual(compiled.steps().get("walk").follow().pollTicks(), 5, "omitted polling interval uses bounded default");
        helper.assertFalse(compiled.steps().get("walk").immediate(), "following is a suspending scene step");
        for (String fields : List.of("{\"actor\":null}", "{\"actor\":true}", "{\"actor\":\"bad alias\"}",
                "{\"follow_distance\":1}", "{\"follow_distance\":33}", "{\"follow_distance\":true}",
                "{\"arrival_distance\":0}", "{\"arrival_distance\":5}", "{\"speed\":0}", "{\"speed\":2}",
                "{\"poll_ticks\":0}", "{\"poll_ticks\":6}", "{\"poll_ticks\":1.5}", "{\"timeout_ticks\":0}", "{\"timeout_ticks\":72001}",
                "{\"allow_teleport\":true}", "{\"path_failure_policy\":\"skip\"}", "{\"x\":1}", "{\"unexpected\":true}")) {
            var changed = original.deepCopy();
            var data = changed.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data");
            JsonParser.parseString(fields).getAsJsonObject().entrySet().forEach(entry -> data.add(entry.getKey(), entry.getValue()));
            verify(helper, changed, false);
        }
        for (String required : List.of("actor", "target_actor")) {
            var changed = original.deepCopy();
            changed.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data").remove(required);
            verify(helper, changed, false);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_follow_authoring")
    public static void followObjectivesRequireTheOwnedSceneRoleDestinationAndStep(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignFollowTestFixture(helper)) {
            var campaign = fixture.base.campaigns.campaigns().get(fixture.base.campaignId).definition();
            var quest = fixture.base.catalog.quest(fixture.base.campaignId).orElseThrow();
            var follow = (CampaignStoryObjective.Follow) quest.objectives().getFirst().story();
            helper.assertTrue(CampaignStoryObjective.referenceIssues(follow, campaign, fixture.base.catalog, quest.id()).isEmpty(),
                    "authored follow references compile");
            for (var invalid : List.of(
                    new CampaignStoryObjective.Follow("missing", "destination", SCENE, "walk"),
                    new CampaignStoryObjective.Follow("mentor", "missing", SCENE, "walk"),
                    new CampaignStoryObjective.Follow("mentor", "destination", ResourceLocation.parse("test:missing"), "walk"),
                    new CampaignStoryObjective.Follow("mentor", "destination", SCENE, "finish"))) {
                helper.assertFalse(CampaignStoryObjective.referenceIssues(invalid, campaign, fixture.base.catalog, quest.id()).isEmpty(),
                        "invalid follow reference is diagnosed");
            }
            helper.assertTrue(CampaignStoryObjective.referenceIssues(follow, campaign, fixture.base.catalog, ResourceLocation.parse("test:other"))
                    .containsKey("scene"), "private follow scenes cannot be borrowed by another quest");
            var raw = fixture.base.raw();
            raw.getAsJsonArray("characters").get(0).getAsJsonObject().addProperty("id", "renamed");
            var changed = CampaignDefinitionParser.parse(quest.id(), raw).definition();
            helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(changed, fixture.base.catalog).stream()
                    .anyMatch(value -> value.toString().contains("/stages/0/objectives/0/story/role")), "reference diagnostic retains the objective source pointer");
        }
        var original = CampaignCharacterTestFixture.resource("quests/campaign_fixture/following/scenes/mentor_walk.json");
        for (int index = 0; index < 2; index++) {
            var changed = original.deepCopy();
            changed.getAsJsonArray("actors").get(index).getAsJsonObject().addProperty("binding_source", "unbound");
            helper.assertTrue(SceneCompiler.compile(SceneParser.parse(SCENE, changed).resource()).scene() == null,
                    "following requires explicit campaign actor and location bindings");
        }
        var unused = original.deepCopy();
        unused.getAsJsonArray("steps").get(0).getAsJsonObject().add("actors", JsonParser.parseString("[\"guide\"]"));
        helper.assertTrue(SceneCompiler.compile(SceneParser.parse(SCENE, unused).resource()).scene() == null, "destination alias must be declared by the step");
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, JsonObject scene, boolean valid) {
        var data = scene.getAsJsonArray("steps").get(0).getAsJsonObject().getAsJsonObject("data");
        helper.assertValueEqual(QuestJsonSchema.validate(data, CampaignFollowDefinition.schema()).isEmpty(), valid, "follow data schema: " + data);
        var schemaIssues = QuestJsonSchema.validate(scene, SceneSchema.sceneV1());
        helper.assertValueEqual(schemaIssues.isEmpty(), valid, "exported scene schema: " + schemaIssues);
        boolean parsed = true;
        try { CampaignFollowDefinition.descriptor().parser().parse(data); }
        catch (IllegalArgumentException exception) { parsed = false; }
        helper.assertValueEqual(parsed, valid, "registered parser: " + data);
        var result = SceneCompiler.compile(SceneParser.parse(SCENE, scene).resource());
        helper.assertValueEqual(result.scene() != null, valid, "scene compiler: " + result.diagnostics());
    }
}
