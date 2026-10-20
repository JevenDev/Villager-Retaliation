package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.quest.query.QuestProgressSnapshot;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestParallelPath;
import com.jvn.villagerretaliation.quest.runtime.QuestParallelService;
import com.jvn.villagerretaliation.quest.runtime.QuestProgressionService;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("quest_mechanics")
@PrefixGameTestTemplate(false)
public final class QuestParallelGameTests {
    @GameTest(template = "empty")
    public static void branchesAdvanceIndependentlyAndJoinAfterReload(GameTestHelper helper) {
        var quest = definition(raw());
        var player = UUID.randomUUID();
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(player, quest.id());
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        QuestProgressionService.advanceSaved(quest, progress, 100, objective -> false, conditions -> true);
        helper.assertValueEqual(QuestObjectiveQuery.activeObjectives(quest, progress).size(), 2, "both paths receive objective events");
        helper.assertValueEqual(QuestProgressSnapshot.activeObjectives(quest, QuestProgressSnapshot.capture(progress, quest)).size(),
                2, "the journal displays both active paths");
        progress.markObjectiveComplete("left.one");
        QuestProgressionService.advanceSaved(quest, progress, 101, objective -> objective.id().equals("left.one"), conditions -> true);
        helper.assertValueEqual(progress.currentStage(), "fork", "one path cannot release the join");
        helper.assertTrue(progress.parallelPaths().get("left").phase() == QuestParallelPath.Phase.ARRIVED, "first path records its arrival");
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        progress = data.get(player, quest.id());
        helper.assertTrue(progress.stageActive("right"), "unfinished path survives reload");
        helper.assertFalse(progress.stageActive("left"), "completed path stays closed");
        progress.markObjectiveComplete("right.two");
        QuestProgressionService.advanceSaved(quest, progress, 102, objective -> objective.id().equals("right.two"), conditions -> true);
        helper.assertTrue(progress.stageActive("last"), "a path can contain several stages");
        helper.assertValueEqual(progress.currentStage(), "fork", "intermediate path stage retains its parent");
        progress.markObjectiveComplete("last.three");
        QuestProgressionService.advanceSaved(quest, progress, 103, objective -> objective.id().equals("last.three"), conditions -> true);
        helper.assertValueEqual(progress.currentStage(), "join", "all arrivals release exactly one join");
        helper.assertTrue(progress.parallelPaths().isEmpty(), "join retires old parallel state");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void suspendedPathKeepsItsExitAndDoesNotBlockItsSibling(GameTestHelper helper) {
        var quest = definition(raw());
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        var actions = new ArrayList<String>();
        QuestParallelService.advance(quest, progress, 100, objective -> false, conditions -> true,
                (key, values) -> { actions.add(key); return true; });
        QuestParallelService.advance(quest, progress, 101, objective -> true, conditions -> true,
                (key, values) -> !key.equals("parallel/left/left/exit"));
        helper.assertTrue(progress.parallelPaths().get("left").phase() == QuestParallelPath.Phase.EXITING,
                "unfinished exit retains its target before effects run");
        helper.assertTrue(progress.parallelPaths().get("right").phase() == QuestParallelPath.Phase.ARRIVED,
                "the sibling can finish independently");
        helper.assertTrue(QuestParallelService.request(quest, progress, "left", "join") == false, "pending exits reject stale choices");
        QuestParallelService.advance(quest, progress, 102, objective -> true, conditions -> true,
                (key, values) -> { actions.add(key); return true; });
        helper.assertValueEqual(actions.stream().filter(key -> key.equals("parallel/left/left/entry")).count(), 1L,
                "resuming an exit does not replay entry effects");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aForkNamedStartedInitializesWithoutLosingItsPaths(GameTestHelper helper) {
        var raw = raw();
        raw.addProperty("entry_stage", "started");
        raw.getAsJsonArray("stages").get(0).getAsJsonObject().addProperty("id", "started");
        var quest = definition(raw);
        var progress = new VillagerQuestSavedData.QuestProgress();
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        helper.assertValueEqual(progress.parallelPaths().size(), 2, "default stage name still initializes the fork");
        QuestParallelService.advance(quest, progress, 101, objective -> true, conditions -> true, (key, actions) -> true);
        QuestLifecycleService.initializeStage(quest, progress, 102);
        helper.assertTrue(progress.parallelPaths().values().stream().allMatch(path -> path.phase() == QuestParallelPath.Phase.ARRIVED),
                "reinitializing an existing stage cannot reopen its completed paths");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidParallelGraphsAreRejected(GameTestHelper helper) {
        for (String invalid : List.of(
                "[\"left\",\"left\"]", "[\"left\",\"missing\"]", "[\"fork\",\"right\"]", "[\"left\",\"join\"]")) {
            var raw = raw();
            raw.getAsJsonArray("stages").get(0).getAsJsonObject().add("parallel", JsonParser.parseString(invalid));
            var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/parallel.json"), raw).orElseThrow();
            helper.assertTrue(QuestV2Parser.parse(envelope).isEmpty(), "invalid fork must fail closed: " + invalid);
        }
        helper.succeed();
    }

    private static QuestDefinition definition(JsonObject raw) {
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/parallel.json"), raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow().asQuestDefinition();
    }

    private static JsonObject raw() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:parallel","provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"fork","stages":[
                  {"id":"fork","objectives":[],"parallel":["left","right"],"next":"join"},
                  {"id":"left","objectives":[{"id":"one","type":"criterion","criterion":"test:one"}],"next":"join"},
                  {"id":"right","objectives":[{"id":"two","type":"criterion","criterion":"test:two"}],"next":"last"},
                  {"id":"last","objectives":[{"id":"three","type":"criterion","criterion":"test:three"}],"next":"join"},
                  {"id":"join","objectives":[]}]}
                """).getAsJsonObject();
    }
}
