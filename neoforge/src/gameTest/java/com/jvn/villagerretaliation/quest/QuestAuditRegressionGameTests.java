package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveDispatchService;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.inventory.QuestSubmissionRequirements;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressionService;
import com.jvn.villagerretaliation.quest.runtime.QuestStartRules;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_reliability")
@PrefixGameTestTemplate(false)
public final class QuestAuditRegressionGameTests {
    @GameTest(template = "empty")
    public static void mergingBranchesRequireOnlyCompletedSubmissionsAfterReload(GameTestHelper helper) {
        var quest = compile("""
                {"schema":"villagerretaliation:quest/v2","id":"test:branch_submission",
                 "provider":{"type":"villagerretaliation:discovery"},"entry_stage":"left","stages":[
                 {"id":"left","responses":[{"id":"alternate","text":"Other route","stage":"right"}],
                  "objectives":[{"id":"wheat","type":"item_check","item":"minecraft:wheat"}],"next":"return"},
                 {"id":"right","objectives":[{"id":"carrot","type":"item_check","item":"minecraft:carrot"}],"next":"return"},
                 {"id":"return","objectives":[]}]}
                """).asQuestDefinition();
        var player = UUID.randomUUID();
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(player, quest.id());
        progress.start(null, Level.OVERWORLD, null, 0);
        progress.setCurrentStage("left");
        helper.assertTrue(QuestProgressionService.advanceSaved(quest, progress, 1, objective -> true, conditions -> true), "left stage completes");
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        progress = data.get(player, quest.id());
        helper.assertValueEqual(QuestSubmissionRequirements.required(quest, progress).stream().map(QuestDefinition.Objective::id).toList(),
                List.of("left.wheat"), "the unchosen branch never adds a submission");
        progress.start(null, Level.OVERWORLD, null, 2);
        progress.setCurrentStage("right");
        helper.assertTrue(QuestProgressionService.advanceSaved(quest, progress, 3, objective -> true, conditions -> true), "right stage completes");
        helper.assertValueEqual(QuestSubmissionRequirements.required(quest, progress).stream().map(QuestDefinition.Objective::id).toList(),
                List.of("right.carrot"), "a new run cannot inherit the previous route");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void largeQuestHistoriesRetainRestrictionsAndPendingWork(GameTestHelper helper) {
        var data = new VillagerQuestSavedData();
        var player = UUID.randomUUID();
        var first = ResourceLocation.parse("test:history_0");
        for (int index = 0; index < 1030; index++) {
            var progress = data.getOrCreate(player, ResourceLocation.parse("test:history_" + index));
            progress.start(null, Level.OVERWORLD, null, index);
            progress.complete(index + 1L, index == 0);
        }
        data.get(player, first).deferLifecycleEvent(QuestDefinition.TriggerEvent.COMPLETED);
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        var progress = data.get(player, first);
        helper.assertTrue(progress != null, "the oldest authoritative record survives reload");
        helper.assertValueEqual(progress.completionCount(), 1, "completion count remains authoritative");
        helper.assertTrue(progress.hasPendingLifecycleEvents(), "pending completion actions survive");
        var definition = QuestDefinitionFixtures.read(first, JsonParser.parseString("{}").getAsJsonObject(), first);
        helper.assertFalse(QuestStartRules.canStart(definition, progress, null, 99999, () -> 0), "consumed quest cannot restart");
        helper.assertValueEqual(data.progress(player).size(), 1030, "unique quest records are not silently evicted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void discoveryPartyRewardsDeliverOnceWithoutAnIssuer(GameTestHelper helper) {
        var quest = fixture(false);
        var player = helper.makeMockServerPlayerInLevel();
        var data = VillagerQuestSavedData.get(helper.getLevel());
        VillagerQuestResources.installCompiledTestCatalog(helper.getLevel().getServer(), List.of(quest));
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            var progress = data.getOrCreate(player.getUUID(), quest.id());
            progress.start(null, helper.getLevel().dimension(), null, 0);
            progress.complete(1, false);
            progress.markPendingPartyReward();
            helper.assertTrue(PartyQuestRewardService.pendingPartyRewardContext(player, progress) != null, "issuer-free delivery has a player context");
            int before = player.totalExperience;
            PartyQuestRewardService.deliverPendingPartyReward(player, quest.id(), progress);
            PartyQuestRewardService.deliverPendingPartyReward(player, quest.id(), progress);
            helper.assertValueEqual(player.totalExperience - before, 7, "retry cannot duplicate rewards");
            helper.assertFalse(progress.pendingPartyReward(), "successful delivery releases the pending reward lock");
            helper.assertTrue(progress.partyRewardClaimed(), "delivery records its receipt");
        } finally {
            data.remove(player.getUUID(), quest.id());
            VillagerQuestService.clearRuntimeState(player);
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void eventAndMaintainedObjectivesRespectTheSamePausePolicy(GameTestHelper helper) {
        var player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        var data = VillagerQuestSavedData.get(level);
        VillagerQuestService.setClientEffectsSuppressedForTests(player, true);
        try {
            for (boolean paused : new boolean[] {true, false}) {
                var quest = fixture(paused);
                VillagerQuestResources.installCompiledTestCatalog(level.getServer(), List.of(quest));
                var progress = data.getOrCreate(player.getUUID(), quest.id());
                progress.start(null, level.dimension(), null, level.getGameTime());
                progress.setCurrentStage("work");
                helper.assertTrue(player.isAlive() && !player.isSpectator(), "player can progress quests");
                helper.assertTrue(player.serverLevel() == level, "player uses the test level");
                helper.assertValueEqual(quest.asQuestDefinition().objectives().get(1).rules().maintainTicks(), 20, "maintained rules compile");
                helper.assertValueEqual(QuestObjectiveQuery.activeObjectives(quest.asQuestDefinition(), progress).size(), 2, "both objectives are active");
                long now = level.getGameTime();
                progress.sampleCondition("work.maintain", true, now - 2, 20);
                progress.sampleCondition("work.maintain", true, now - 1, 20);
                VillagerQuestService.onCriterion(level, player, ResourceLocation.parse("test:observation"), Map.of());
                helper.assertValueEqual(progress.objectiveCounter("work.event"), paused ? 0 : 1, "event progress follows the pause policy");
                VillagerQuestService.onPlayerTick(player);
                helper.assertValueEqual(progress.maintainedTicks("work.maintain"), paused ? 0L : 2L, "maintained progress follows the pause policy");
                data.remove(player.getUUID(), quest.id());
            }
        } finally {
            data.remove(player.getUUID(), ResourceLocation.parse("test:pause_policy"));
            VillagerQuestService.clearRuntimeState(player);
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void objectiveIndexesRespectDefinitionIdentityAndOrder(GameTestHelper helper) {
        var first = fixture(true).asQuestDefinition();
        var second = fixture(false).asQuestDefinition();
        var objectives = QuestObjectiveQuery.stageObjectives(first, "work");
        helper.assertValueEqual(objectives.stream().map(QuestDefinition.Objective::id).toList(),
                List.of("work.event", "work.maintain"), "stage order is preserved");
        helper.assertTrue(objectives == QuestObjectiveQuery.stageObjectives(first, " work "), "repeated queries reuse resolved objectives");
        helper.assertTrue(QuestObjectiveQuery.stageObjectives(second, "work").getFirst() == second.objectives().getFirst(),
                "replacement content with the same quest id never uses the previous definition");
        helper.assertTrue(QuestObjectiveQuery.stageObjectives(first, "missing").isEmpty(), "unknown stages remain empty");
        QuestObjectiveQuery.clearCache();
        helper.assertValueEqual(QuestObjectiveQuery.stageObjectives(first, "work"), objectives, "clearing the cache preserves results");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "quest_reliability_native")
    public static void maintainedIndexFollowsCatalogReplacement(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        try {
            VillagerQuestResources.installCompiledTestCatalog(server, List.of(fixture(true)));
            var catalog = QuestContentCatalogs.current(server);
            var maintained = QuestObjectiveDispatchService.maintainedDefinitions(catalog);
            helper.assertValueEqual(maintained.size(), 1, "maintained content is selected");
            helper.assertTrue(maintained == QuestObjectiveDispatchService.maintainedDefinitions(catalog), "catalog queries reuse their index");
            VillagerQuestResources.installCompiledTestCatalog(server, List.of());
            helper.assertTrue(QuestObjectiveDispatchService.maintainedDefinitions(QuestContentCatalogs.current(server)).isEmpty(),
                    "removed maintained content does not survive reload");
        } finally {
            VillagerQuestResources.clearCache();
        }
        helper.succeed();
    }

    private static CompiledQuest fixture(boolean paused) {
        return compile("""
                {"schema":"villagerretaliation:quest/v2","id":"test:pause_policy",
                 "provider":{"type":"villagerretaliation:discovery"},"entry_stage":"work",
                 "availability":{"active":{"conditions":[{"type":"quest_fact","tag":"missing"}],"pause_progress_when_unmet":%s}},
                 "stages":[{"id":"work","objectives":[
                 {"id":"event","type":"criterion","criterion":"test:observation","count":3},
                 {"id":"maintain","type":"condition","maintain_ticks":20,"conditions":[{"type":"quest","quest":"test:pause_policy","states":["active"]}]}]}],"rewards":{"experience":7}}
                """.formatted(paused));
    }

    private static CompiledQuest compile(String source) {
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/audit.json"), JsonParser.parseString(source).getAsJsonObject()).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }
}
