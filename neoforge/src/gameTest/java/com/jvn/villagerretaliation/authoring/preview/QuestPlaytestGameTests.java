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
    public static void visualEditsKeepCursorHistoryAndAppliedLogic(GameTestHelper helper) {
        var source = JsonParser.parseString("""
                {"nodes":{"start":{"exchanges":[{"text":"First"},{"text":"Second"}],
                  "actions":[{"type":"experience","amount":7}],"responses":[{"id":"go","label":"Go","next":"finish"}]},
                  "finish":{"lines":["Finished"],"end":true}}}
                """).getAsJsonObject();
        try (var session = new DialoguePreviewSession(source, ResourceLocation.parse("test:visuals"), new PreviewConditions(), key -> null)) {
            session.start("start"); session.advance();
            var draft = source.deepCopy();
            var node = draft.getAsJsonObject("nodes").getAsJsonObject("start");
            node.getAsJsonArray("exchanges").get(1).getAsJsonObject().addProperty("text", "Updated");
            node.getAsJsonArray("exchanges").get(0).getAsJsonObject().addProperty("text", "New first");
            node.getAsJsonArray("actions").get(0).getAsJsonObject().addProperty("amount", 99);
            int logSize = session.log().size();
            session.refreshPresentation(draft);
            helper.assertValueEqual(session.cursor().exchange(), 1, "visual edits preserve the selected exchange");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Updated", "visible text refreshes");
            helper.assertValueEqual(session.definition().getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("actions")
                    .get(0).getAsJsonObject().get("amount").getAsInt(), 7, "logic edits stay pending");
            session.previous();
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "New first", "history uses the latest presentation");
            helper.assertValueEqual(session.log().size(), logSize, "visual refresh never executes effects");
        }
        var changed = source.deepCopy();
        changed.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("exchanges").get(0).getAsJsonObject().addProperty("text", "New text");
        helper.assertTrue(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", changed)), "text changes are presentation only");
        changed.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("responses").get(0).getAsJsonObject().addProperty("next", "start");
        helper.assertFalse(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", changed)), "response destinations require apply");
        var display = source.deepCopy();
        var title = new com.google.gson.JsonObject(); title.addProperty("title", "New title");
        display.add("display", title);
        helper.assertTrue(PlaytestDrafts.sameLogic(java.util.Map.of("tree.json", source), java.util.Map.of("tree.json", display)), "adding a display title is presentation only");
        var locale = JsonParser.parseString("""
                {"messages":{"line":[{"text":"Hello","conditions":[{"type":"quest_fact","value":"before"}]}]}}
                """).getAsJsonObject();
        var changedLocale = locale.deepCopy();
        changedLocale.getAsJsonObject("messages").getAsJsonArray("line").get(0).getAsJsonObject()
                .getAsJsonArray("conditions").get(0).getAsJsonObject().addProperty("value", "after");
        helper.assertFalse(PlaytestDrafts.sameLogic(java.util.Map.of("data/test/quests/demo/locales/en_us.json", locale), java.util.Map.of("data/test/quests/demo/locales/en_us.json", changedLocale)), "locale condition edits require apply");
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
