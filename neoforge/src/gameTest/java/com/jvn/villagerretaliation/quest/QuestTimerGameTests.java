package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.runtime.QuestLifecycleService;
import com.jvn.villagerretaliation.quest.runtime.QuestTimerState;
import com.jvn.villagerretaliation.quest.runtime.QuestTimers;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.tracking.QuestStageReadiness;
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
public final class QuestTimerGameTests {
    @GameTest(template = "empty")
    public static void timersSurviveReloadPauseAndResetWithTheirStage(GameTestHelper helper) {
        var quest = fixture();
        var player = UUID.randomUUID();
        var data = new VillagerQuestSavedData();
        var progress = data.getOrCreate(player, quest.id());
        progress.start(UUID.randomUUID(), Level.OVERWORLD, null, 100);
        QuestLifecycleService.initializeStage(quest, progress, 100);
        QuestTimers.update(quest, progress, 119, true);
        helper.assertFalse(QuestTimers.allowsObjective(quest.objectives().getFirst(), progress), "duration gates early completion");
        data = VillagerQuestSavedData.load(data.save(new CompoundTag(), null), null);
        progress = data.get(player, quest.id());
        QuestTimers.update(quest, progress, 120, true);
        helper.assertTrue(QuestTimers.allowsObjective(quest.objectives().getFirst(), progress), "duration ends at its exact boundary");
        QuestTimers.update(quest, progress, 121, false);
        QuestTimers.update(quest, progress, 500, false);
        helper.assertValueEqual(progress.timer("stage/work").elapsed(), 20L, "inactive time does not count");
        QuestTimers.update(quest, progress, 501, true);
        QuestTimers.update(quest, progress, 521, true);
        helper.assertTrue(QuestStageReadiness.forCurrentStage(quest, progress, objective -> true, conditions -> true).ready(),
                "duration stage advances after its own timer");
        QuestLifecycleService.transitionStage(quest, progress, "done", 521);
        QuestLifecycleService.transitionStage(quest, progress, "work", 522);
        helper.assertValueEqual(progress.timer("stage/work").elapsed(), 0L, "reentry resets stage time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deadlineWarningsAndReceiptsAreDurable(GameTestHelper helper) {
        var timer = new QuestTimer(40, QuestTimer.Mode.DEADLINE, true, true, 10, "");
        var state = new QuestTimerState(100);
        state.update(130, true);
        helper.assertTrue(state.warn(timer), "warning is emitted at its boundary");
        state = QuestTimerState.load(state.save());
        helper.assertFalse(state.warn(timer), "reload does not replay a warning");
        state.update(140, true);
        helper.assertTrue(state.elapsed(timer), "deadline is inclusive");
        state.handle();
        state = QuestTimerState.load(state.save());
        helper.assertTrue(state.handled(), "timeout receipt survives reload");
        state.resume(1000);
        state.update(1001, true);
        helper.assertValueEqual(state.elapsed(), 41L, "offline resume excludes disconnected time");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidTimersFailAuthoringValidation(GameTestHelper helper) {
        for (String invalid : new String[] {
                "{\"duration_ticks\":0}", "{\"duration_ticks\":10,\"warning_ticks\":10}",
                "{\"duration_ticks\":10,\"mode\":\"duration\",\"timeout_stage\":\"late\"}",
                "{\"duration_ticks\":10,\"unknown\":true}" }) {
            boolean rejected = false;
            try { QuestTimer.read(JsonParser.parseString(invalid)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid timer rejected: " + invalid);
        }
        helper.succeed();
    }

    private static QuestDefinition fixture() {
        var json = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:timers",
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"work","stages":[
                 {"id":"work","timer":{"duration_ticks":40,"mode":"duration"},
                  "objectives":[{"id":"wait","type":"condition","timer":{"duration_ticks":20,"mode":"duration"}}],"next":"done"},
                 {"id":"done","objectives":[]}]}
                """).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:quests/timers.json"), json).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow().asQuestDefinition();
    }
}
