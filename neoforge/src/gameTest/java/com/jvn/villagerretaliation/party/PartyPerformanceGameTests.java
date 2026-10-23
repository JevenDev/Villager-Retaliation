package com.jvn.villagerretaliation.party;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.PartyQuestService;
import com.jvn.villagerretaliation.quest.QuestPerformance;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class PartyPerformanceGameTests {
    @GameTest(template = "empty")
    public static void teamMeasurementsUseOneClockAndPersistIdenticalPendingRewards(GameTestHelper helper) {
        var raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:team_performance",
                  "provider":{"type":"villagerretaliation:villager"},"entry_stage":"work",
                  "stages":[{"id":"work","objectives":[{"id":"logs","type":"item_check","item":"minecraft:oak_log","count":16}]}],
                  "rewards":{"experience":10},"performance":{"measurements":{
                    "time":{"metric":"elapsed_ticks"},"deaths":{"metric":"deaths"},"kills":{"metric":"kills"}},
                    "tiers":[{"id":"gold","requirements":{"deaths":{"max":0},"kills":{"min":2}},"rewards":{"experience":100}},
                      {"id":"copper","requirements":{"time":{"min":0}},"rewards":{"experience":20}}]}}
                """).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/team_performance.json"), raw).orElseThrow();
        var quest = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow().asQuestDefinition();
        var first = new VillagerQuestSavedData.QuestProgress();
        var second = new VillagerQuestSavedData.QuestProgress();
        first.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        second.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, first, 100);
        QuestLifecycleService.initializeStage(quest, second, 100);
        var shared = new PartySharedQuestRecord(quest.id(), null, 100);
        PartyQuestService.syncPersonalProgress(shared, quest, first);
        PartyQuestService.syncPersonalProgress(shared, quest, second);
        helper.assertTrue(first.performance() == second.performance(), "members use one team measurement state");
        first.performance().update(quest, first, 100, true, true, actor -> null);
        first.performance().update(quest, first, 110, true, true, actor -> null);
        second.performance().update(quest, second, 110, true, true, actor -> null);
        helper.assertValueEqual(first.performance().values().get("time"), 10.0, "member updates do not multiply elapsed time");
        first.performance().event(quest.rewards().performance(), QuestPerformance.Metric.KILLS, 1, metric -> true);
        second.performance().event(quest.rewards().performance(), QuestPerformance.Metric.KILLS, 1, metric -> true);
        helper.assertValueEqual(first.performance().values().get("kills"), 2.0, "participating members contribute to the same total");
        first.performance().freeze(quest.rewards());
        shared = PartySharedQuestRecord.load(shared.save());
        PartyQuestService.syncPersonalProgress(shared, quest, second);
        helper.assertValueEqual(second.performance().tier(), "gold", "offline claim retains the team's grade after reload");
        helper.assertValueEqual(second.performance().resolvedRewards().experience(), 100, "offline claim retains resolved rewards");
        second.performance().event(quest.rewards().performance(), QuestPerformance.Metric.DEATHS, 1, metric -> true);
        helper.assertValueEqual(second.performance().tier(), "gold", "events after completion cannot change the team grade");
        helper.succeed();
    }
}
