package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignScenarioParser;
import com.jvn.villagerretaliation.campaign.content.CampaignScenarioSchema;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.scene.SceneResources;
import com.jvn.villagerretaliation.scene.encounter.EncounterResources;
import java.util.List;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignScenarioAuthoringGameTests {
    private CampaignScenarioAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_scenario_authoring", timeoutTicks = 400)
    public static void sharedScenarioCasesMatchRuntime(GameTestHelper helper) throws IOException {
        var pack = CampaignScenarioPack.read(projectFile("neoforge/src/gameTest/resources/campaign_fixture"));
        var catalog = pack.compile(helper.getLevel().getServer(), 1);
        try (var stream = CampaignScenarioAuthoringGameTests.class.getResourceAsStream("/campaign_fixture/campaign_scenario_cases.json")) {
            if (stream == null) throw new IOException("Missing campaign scenario authoring cases");
            for (var raw : JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray()) {
                var test = raw.getAsJsonObject();
                var source = ResourceLocation.parse("campaign_fixture:campaign_scenarios/" + test.get("fixture").getAsString() + ".json");
                var root = pack.resources.get(source).deepCopy();
                for (var patch : test.getAsJsonArray("patches")) patch(root, patch.getAsJsonObject());
                var parsed = CampaignScenarioParser.parse(source, root);
                var issues = new ArrayList<>(parsed.diagnostics());
                if (parsed.valid() && test.has("catalog") && test.get("catalog").getAsBoolean())
                    issues.addAll(CampaignScenarioParser.validateContent(parsed.scenario(), catalog));
                String name = test.get("name").getAsString();
                helper.assertValueEqual(issues.isEmpty(), test.get("valid").getAsBoolean(), name + ": " + issues);
                if (test.has("pointer")) helper.assertTrue(issues.stream().anyMatch(issue -> issue.jsonPointer().equals(test.get("pointer").getAsString())),
                        name + " retains the authored source pointer: " + issues);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scenario_authoring")
    public static void exportedScenarioSchemaMatchesRuntime(GameTestHelper helper) throws IOException {
        var artifact = JsonParser.parseString(Files.readString(projectFile("tools/datapack-builder/campaign-scenario-v1.schema.json")));
        helper.assertValueEqual(artifact, CampaignScenarioSchema.export(), "scenario schema artifact matches runtime descriptors");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_scenario_override_isolation", timeoutTicks = 400)
    public static void scenarioCompilationIgnoresAndPreservesOtherTestOverrides(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var prior = QuestContentCatalogs.current(server);
        var report = QuestContentCatalogs.loadReport(server);
        try (var quests = VillagerQuestResources.preserveTestOverride();
                var scenes = SceneResources.preserveTestOverride();
                var encounters = EncounterResources.preserveTestOverride()) {
            VillagerQuestResources.installCompiledTestCatalog(server, List.of());
            SceneResources.installTestScenes(server, List.of());
            var sentinel = ResourceLocation.parse("campaign_fixture:unrelated_test_override");
            var errors = new ArrayList<String>();
            var template = EncounterResources.parse(sentinel, JsonParser.parseString("""
                    {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:unrelated_test_override","members":[{"entity":"minecraft:cow"}]}
                    """).getAsJsonObject(), errors);
            helper.assertTrue(template != null && errors.isEmpty(), "unrelated encounter fixture compiles");
            EncounterResources.installTestTemplates(server, List.of(template));
            var pack = CampaignScenarioPack.read(projectFile("neoforge/src/gameTest/resources/campaign_fixture"));
            var compiled = pack.compile(server, 1);
            helper.assertTrue(compiled.quests().compiledQuest(ResourceLocation.parse("campaign_fixture:decision")).isPresent(), "scenario quest comes from its authored bundle");
            helper.assertTrue(compiled.quests().scene(ResourceLocation.parse("campaign_fixture:mentor_walk")).isPresent(), "scenario scene comes from its authored bundle");
            helper.assertTrue(compiled.quests().encounter(sentinel).isEmpty(), "unrelated encounter overrides do not enter the scenario catalog");
            helper.assertTrue(VillagerQuestResources.snapshotForCatalog(server).quests().isEmpty(), "quest override is preserved");
            helper.assertTrue(SceneResources.snapshotForCatalog(server).scenes().isEmpty(), "scene override is preserved");
            helper.assertTrue(EncounterResources.snapshotForCatalog(server).templates().containsKey(sentinel), "encounter override is preserved");
        } finally {
            QuestContentCatalogs.installForTests(server, prior, report);
        }
        helper.succeed();
    }

    private static void patch(JsonObject root, JsonObject patch) {
        String[] tokens = patch.get("path").getAsString().substring(1).split("/");
        JsonElement target = root;
        for (int index = 0; index < tokens.length - 1; index++) target = target.isJsonArray()
                ? target.getAsJsonArray().get(Integer.parseInt(tokens[index])) : target.getAsJsonObject().get(tokens[index].replace("~1", "/").replace("~0", "~"));
        String key = tokens[tokens.length - 1].replace("~1", "/").replace("~0", "~");
        if (target.isJsonArray()) {
            int index = Integer.parseInt(key);
            if (patch.has("remove")) target.getAsJsonArray().remove(index);
            else target.getAsJsonArray().set(index, patch.get("value").deepCopy());
        } else if (patch.has("remove")) target.getAsJsonObject().remove(key);
        else target.getAsJsonObject().add(key, patch.get("value").deepCopy());
    }

    static Path projectFile(String name) throws IOException {
        var relative = Path.of(name);
        for (int depth = 0; depth <= 3; depth++) {
            if (Files.exists(relative)) return relative;
            relative = Path.of("..").resolve(relative);
        }
        throw new IOException("Could not find scenario authoring resource " + name);
    }
}
