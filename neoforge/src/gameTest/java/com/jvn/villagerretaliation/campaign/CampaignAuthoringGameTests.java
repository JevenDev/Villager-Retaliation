package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalog;
import com.jvn.villagerretaliation.quest.content.bundle.QuestBundleTransactions;
import com.jvn.villagerretaliation.quest.content.reward.QuestRewardCatalog;
import com.jvn.villagerretaliation.quest.schema.QuestJsonSchema;
import com.jvn.villagerretaliation.scene.compiler.SceneParser;
import com.jvn.villagerretaliation.scene.schema.SceneSchema;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignAuthoringGameTests {
    private CampaignAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_authoring")
    public static void sharedCampaignAuthoringCasesMatchRuntime(GameTestHelper helper) throws IOException {
        var catalog = quests(helper);
        for (var raw : read("campaign_authoring_cases.json").getAsJsonArray()) {
            var test = raw.getAsJsonObject();
            String name = test.get("fixture").getAsString();
            String source = "campaigns/" + name + "/campaign.json";
            var root = resource(source);
            for (var patch : test.getAsJsonArray("patches")) patch(root, patch.getAsJsonObject());
            var parsed = CampaignDefinitionParser.parse(id(source), root);
            var issues = new ArrayList<>(parsed.diagnostics());
            if (parsed.valid() && test.has("catalog")) issues.addAll(CampaignDefinitionParser.validateQuestReferences(parsed.definition(), catalog));
            helper.assertValueEqual(issues.isEmpty(), test.get("valid").getAsBoolean(), test.get("name").getAsString() + ": " + issues);
            if (test.has("pointer")) helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().equals(test.get("pointer").getAsString())),
                    test.get("name").getAsString() + " retains its authored pointer: " + issues);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_authoring")
    public static void campaignAndSceneSchemaArtifactsMatchRuntime(GameTestHelper helper) throws IOException {
        helper.assertValueEqual(JsonParser.parseString(Files.readString(projectFile("campaign-v1.schema.json"))), CampaignSchema.export(), "campaign schema artifact matches runtime");
        helper.assertValueEqual(JsonParser.parseString(Files.readString(projectFile("scene-v1.schema.json"))), SceneSchema.sceneV1(), "scene schema artifact matches runtime");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_authoring")
    public static void sceneActorDefaultsRemainAcceptedByTheExportedSchema(GameTestHelper helper) throws IOException {
        var root = resource("quests/campaign_fixture/characters/scenes/character_greeting.json");
        helper.assertTrue(QuestJsonSchema.validate(root, SceneSchema.sceneV1()).isEmpty(), "campaign actors can use the runtime replacement default");
        for (var raw : root.getAsJsonArray("actors")) {
            var actor = raw.getAsJsonObject();
            actor.add("source", actor.remove("binding"));
        }
        helper.assertTrue(QuestJsonSchema.validate(root, SceneSchema.sceneV1()).isEmpty(), "existing source alias remains accepted");
        var aliased = SceneParser.parse(id("actor_aliases"), root);
        helper.assertValueEqual(aliased.resource().actors().getFirst().bindingReference(), "mentor", "source alias keeps the authored binding");
        for (var raw : root.getAsJsonArray("actors")) raw.getAsJsonObject().remove("binding_source");
        helper.assertTrue(QuestJsonSchema.validate(root, SceneSchema.sceneV1()).isEmpty(), "actor binding source can use its runtime default");
        var parsed = SceneParser.parse(id("actor_defaults"), root);
        helper.assertTrue(parsed.diagnostics().isEmpty(), "parser accepts the same defaults: " + parsed.diagnostics());
        helper.succeed();
    }

    static QuestContentCatalog quests(GameTestHelper helper) throws IOException {
        var resources = new ArrayList<QuestBundleTransactions.RawResource>();
        for (String quest : List.of("decision", "dragon", "decision_catch_up", "dragon_catch_up")) {
            for (String file : List.of("quest.json", "locales/en_us.json")) {
                String source = "quests/campaign_fixture/" + quest + "/" + file;
                resources.add(QuestBundleTransactions.RawResource.valid(0, "campaign_fixture", id(source), resource(source)));
            }
        }
        var bundles = QuestBundleTransactions.compile(resources, QuestBundleTransactions.CompatibilityRules.empty());
        helper.assertTrue(bundles.diagnostics().isEmpty(), "authoring fixture bundles compile");
        var quests = VillagerQuestResources.snapshotForCatalog(helper.getLevel().getServer(), bundles);
        return new QuestContentCatalog(1, quests.compiledCatalog(), quests.dialogueCatalog(), quests.quests(),
                quests.objectiveEventQuestIds(), quests.factQuestIds(), quests.memoryEventQuestIds(), quests.exclusiveGroupQuestIds(),
                quests.triggerEventQuestIds(), Map.of(), Map.of(), List.of(), bundles.bundles(), bundles.localization(), QuestRewardCatalog.fromBundles(bundles.bundles()));
    }

    private static void patch(JsonObject root, JsonObject patch) {
        String[] tokens = patch.get("path").getAsString().substring(1).split("/");
        JsonElement target = root;
        for (int index = 0; index < tokens.length - 1; index++) target = target.isJsonArray()
                ? target.getAsJsonArray().get(Integer.parseInt(tokens[index])) : target.getAsJsonObject().get(tokens[index].replace("~1", "/").replace("~0", "~"));
        String key = tokens[tokens.length - 1].replace("~1", "/").replace("~0", "~");
        if (patch.has("remove")) target.getAsJsonObject().remove(key);
        else target.getAsJsonObject().add(key, patch.get("value").deepCopy());
    }

    private static Path projectFile(String name) throws IOException {
        var relative = Path.of("tools", "datapack-builder", name);
        for (int depth = 0; depth <= 3; depth++) {
            if (Files.isRegularFile(relative)) return relative;
            relative = Path.of("..").resolve(relative);
        }
        throw new IOException("Could not find schema artifact " + name);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("campaign_fixture", path); }
    private static JsonObject resource(String path) throws IOException { return read("data/campaign_fixture/" + path).getAsJsonObject(); }
    private static JsonElement read(String path) throws IOException {
        try (var stream = CampaignAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/" + path)) {
            if (stream == null) throw new IOException("Missing campaign authoring fixture " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
