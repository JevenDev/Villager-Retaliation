package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring_preview")
@PrefixGameTestTemplate(false)
public final class QuestPlaytestGameTests {
    private QuestPlaytestGameTests() { }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void navigationRestoresBranchesWithoutRepeatingEffects(GameTestHelper helper) {
        var source = JsonParser.parseString("""
                {"nodes":{"start":{"exchanges":[{"text":"First"},{"text":"Second"}],
                  "actions":[{"type":"experience","amount":7}],"responses":[{"id":"go","label":"Go","next":"finish"}]},
                  "finish":{"lines":["Finished"],"end":true}}}
                """).getAsJsonObject();
        try (var session = new DialoguePreviewSession(source, ResourceLocation.parse("test:playtest"), new PreviewConditions(), key -> null)) {
            session.start("start");
            session.advance();
            session.choose(0);
            int effects = session.log().size();
            session.previous();
            helper.assertValueEqual(session.nodeId(), "start", "previous restores the response source");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Second", "previous restores the exact exchange");
            helper.assertValueEqual(session.choices().size(), 1, "previous restores response choices");
            session.exchange(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "First", "exchange selection seeks directly");
            helper.assertValueEqual(session.log().size(), effects, "seeking does not execute effects");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void manualSteppingAndBypassesStayScoped(GameTestHelper helper) {
        var controls = new QuestPlaytestSession.Controls();
        helper.assertTrue(controls.held("start"), "quests wait for Next by default");
        controls.advance("start", () -> {
            helper.assertFalse(controls.held("start"), "Next releases the current stage");
            helper.assertTrue(controls.held("finish"), "Next does not run through another stage");
        });
        helper.assertTrue(controls.held("start"), "the hold is restored after a step");
        controls.force("start", "supplies");
        helper.assertTrue(controls.forced("finish", "supplies"), "item bypasses remain available for later turn-in");
        helper.assertFalse(controls.forced("finish", "other"), "bypasses leave unrelated requirements alone");
        controls.forceStage("start");
        helper.assertFalse(controls.stageForced("finish"), "stage conditions remain scoped");
        controls.clear();
        helper.assertFalse(controls.forced("start", "supplies"), "restart clears bypasses");
        try {
            controls.advance("start", () -> { throw new IllegalStateException("test failure"); });
        } catch (IllegalStateException expected) {
            helper.assertTrue(controls.held("start"), "a failed step restores the hold");
        }
        helper.succeed();
    }
}
