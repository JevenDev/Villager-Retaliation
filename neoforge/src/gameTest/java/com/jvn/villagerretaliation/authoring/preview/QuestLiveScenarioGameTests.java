package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring_live")
@PrefixGameTestTemplate(false)
public final class QuestLiveScenarioGameTests {
    private QuestLiveScenarioGameTests() { }

    @GameTest(template = "empty", batch = "authoring_live", timeoutTicks = 500)
    public static void savedLiveTestsExerciseRewardsFailuresExpirationRepeatRunsAndRestoration(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var server = level.getServer();
        var player = helper.makeMockServerPlayerInLevel();
        var provider = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, 2, 1, 2);
        provider.setNoAi(true);
        player.teleportTo(provider.getX() - 1, provider.getY(), provider.getZ());
        var quest = fixture();
        var selected = List.copyOf(server.getPackRepository().getSelectedIds());
        var override = VillagerQuestResources.preserveTestOverride();
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) { }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original, net.minecraft.gametest.framework.GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup(); }
            private void cleanup() {
                QuestPlaytestSession.close(server, player.getUUID());
                VillagerQuestSavedData.get(level).remove(player.getUUID(), quest.id());
                VillagerQuestService.setClientEffectsSuppressedForTests(player, false);
                VillagerQuestService.clearRuntimeState(player);
                server.getPackRepository().setSelected(selected);
                provider.discard();
                try { override.close(); } catch (Exception exception) { throw new IllegalStateException(exception); }
            }
        });
        var pack = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("datapacks/test_content");
        java.nio.file.Files.createDirectories(pack);
        java.nio.file.Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"Live quest test fixture\"}}");
        server.getPackRepository().reload();
        var enabled = new ArrayList<>(server.getPackRepository().getSelectedIds());
        if (!enabled.contains("file/test_content")) enabled.add("file/test_content");
        server.getPackRepository().setSelected(enabled);
        VillagerQuestResources.installCompiledTestCatalog(server, List.of(quest));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        QuestPlaytestSession.bind(player, quest.id(), provider, true);
        QuestPlaytestBaseline.capture(player, quest.id(), "before");
        for (int i = 1; i < 8; i++) QuestPlaytestBaseline.capture(player, quest.id(), "baseline_" + i);
        int originalExperience = player.totalExperience;
        var suite = new JsonObject();
        var complete = List.of(step("start", "", "started"), step("prepare", "work.supplies", ""), step("next", "", ""), step("turn_in", "", "completed"));
        suite.add("completion", scenario(complete, List.of(check("state", "completed"), check("experience_delta", "11"), check("completion_delta", "1"), check("performance_tier", "swift"),
                new QuestLiveTest.Expectation("item_count", "minecraft:bread", "0"))));
        var repeated = new ArrayList<>(complete); repeated.addAll(complete);
        suite.add("repeat", scenario(repeated, List.of(check("state", "completed"), check("experience_delta", "22"), check("completion_delta", "2"), check("start_delta", "2"))));
        suite.add("failure", scenario(List.of(step("start", "", "started"), step("fail", "", "")), List.of(check("state", "failed"), check("experience_delta", "0"))));
        suite.add("expiration", scenario(List.of(step("start", "", "started"), step("wait", "", "50")), List.of(check("state", "expired"), check("experience_delta", "0"))));
        suite.add("mismatch", scenario(complete, List.of(check("experience_delta", "999"))));
        suite.add("after mismatch", scenario(complete, List.of(check("experience_delta", "11"), check("completion_delta", "1"))));
        QuestPlaytestScenarios.start(player, quest.id(), suite);
        boolean[] checkingCancellation = {false};
        helper.succeedWhen(() -> {
            VillagerQuestService.onPlayerTick(player);
            QuestPlaytestScenarios.tick(server);
            var view = QuestPlaytestScenarios.view(player, quest.id());
            if (!checkingCancellation[0]) {
                helper.assertFalse(view.running(), "live suite is still running: " + view);
                helper.assertValueEqual(view.results().size(), 6, "every saved test runs");
                helper.assertValueEqual(view.results().stream().filter(QuestPlaytestScenarios.Result::passed).count(), 5L, "only the intentional mismatch fails: " + view.results());
                helper.assertTrue(view.results().stream().filter(result -> !result.passed()).allMatch(result -> result.name().equals("mismatch")), "mismatched assertions are reported against the correct test");
                helper.assertValueEqual(player.totalExperience, originalExperience, "suite restores pre-run experience");
                helper.assertTrue(VillagerQuestSavedData.get(level).get(player.getUUID(), quest.id()) == null, "suite restores pre-run quest progress");
                helper.assertValueEqual(QuestPlaytestBaseline.names(player, quest.id()).size(), 8, "transient restoration does not consume a named baseline");
                var cancellation = new JsonObject();
                cancellation.add("cancel", scenario(List.of(step("start", "", "started"), step("wait", "", "12000")), List.of(check("state", "active"))));
                QuestPlaytestScenarios.start(player, quest.id(), cancellation);
                checkingCancellation[0] = true;
                helper.assertTrue(false, "waiting for cancellation setup");
            }
            view = QuestPlaytestScenarios.view(player, quest.id());
            helper.assertTrue(view.running() && view.step() >= 1, "waiting for the cancellable live run to start");
            QuestPlaytestScenarios.cancel(server, player.getUUID());
            helper.assertFalse(QuestPlaytestScenarios.view(player, quest.id()).running(), "cancellation stops scheduled actions");
            helper.assertTrue(VillagerQuestSavedData.get(level).get(player.getUUID(), quest.id()) == null, "cancellation restores original progress");
            helper.assertValueEqual(player.totalExperience, originalExperience, "cancellation preserves original rewards");
        });
    }

    private static QuestLiveTest.Step step(String action, String target, String value) { return new QuestLiveTest.Step(action, target, value); }
    private static QuestLiveTest.Expectation check(String kind, String expected) { return new QuestLiveTest.Expectation(kind, "", expected); }
    private static JsonObject scenario(List<QuestLiveTest.Step> steps, List<QuestLiveTest.Expectation> checks) {
        return new QuestLiveTest("before", true, steps, checks).json();
    }

    private static com.jvn.villagerretaliation.quest.compiled.CompiledQuest fixture() {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"live_fixture:flow",
                 "metadata":{"title":"Live test fixture"},"provider":{"type":"villagerretaliation:villager"},
                 "availability":{"repeatable":true,"max_starts":0,"max_completions":0,"consume_on_completion":false,
                     "expiration":{"after_ticks":40,"consume":false,"allow_repickup":true}},
                 "entry_stage":"work","rewards":{"experience":7},
                 "performance":{"measurements":{"time":{"metric":"elapsed_ticks"}},"tiers":[{"id":"swift","requirements":{"time":{"max":1000}},"rewards":{"experience":11}}]},
                 "stages":[{"id":"work","objectives":[{"id":"supplies","type":"item_check","item":"minecraft:bread","count":2,"consume":true}],"complete_when":["supplies"],"next":"return"},
                           {"id":"return","objectives":[]}]}
                """).getAsJsonObject();
        var envelope = com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope.read(ResourceLocation.parse("live_fixture:quests/flow.json"), root).orElseThrow();
        return com.jvn.villagerretaliation.quest.QuestV2Compiler.compile(
                com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }
}
