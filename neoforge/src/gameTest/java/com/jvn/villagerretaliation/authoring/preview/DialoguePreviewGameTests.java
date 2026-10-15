package com.jvn.villagerretaliation.authoring.preview;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring_preview")
@PrefixGameTestTemplate(false)
public final class DialoguePreviewGameTests {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("test", "preview");

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void followsExchangesChoicesAndResponseLinesWithoutChangingSource(GameTestHelper helper) {
        JsonObject source = json("""
                {"entries":[{"id":"talk","label":"Talk","start":"start"}],"nodes":{
                  "start":{"actions":[{"type":"reputation","amount":10}],"exchanges":[
                    {"speaker":{"type":"player"},"text":"Hello"},
                    {"speaker":{"type":"role","role":"provider"},"text":"Welcome"}],
                    "responses":[{"id":"next","label":"Continue","lines":["Certainly"],"next":"finish"}]},
                  "finish":{"lines":["Goodbye"],"end":true}}}
                """);
        String before = source.toString();
        try (var session = new DialoguePreviewSession(source, ID, new PreviewConditions(), key -> null)) {
            session.start("");
            helper.assertValueEqual(session.choices().size(), 1, "entry topic");
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Hello", "first exchange");
            helper.assertTrue(session.choices().isEmpty(), "responses wait for exchanges");
            session.advance();
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Welcome", "second exchange");
            helper.assertValueEqual(session.choices().size(), 1, "response becomes available");
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Certainly", "response text is presented");
            session.advance();
            helper.assertValueEqual(session.nodeId(), "finish", "branch enters target");
            helper.assertTrue(session.choices().isEmpty(), "terminal has no choices");
            helper.assertTrue(session.log().stream().anyMatch(line -> line.startsWith("Simulated actions:")), "actions are recorded");
        }
        helper.assertValueEqual(source.toString(), before, "source snapshot is untouched");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void runtimeBooleanConditionsUseSimulatedLeavesAndPreserveUnknown(GameTestHelper helper) {
        var conditions = new PreviewConditions();
        var nested = json("""
                {"conditions":[{"type":"not","condition":{"type":"quest","quest":"test:quest","states":["completed"]}}]}
                """);
        helper.assertValueEqual(conditions.evaluate(ID, nested), ConditionOutcome.UNKNOWN, "unknown is not made available by negation");
        conditions.quest("test:quest", "active");
        helper.assertValueEqual(conditions.evaluate(ID, nested), ConditionOutcome.MATCHED, "simulated quest state uses runtime not");
        conditions.quest("test:quest", "completed");
        helper.assertValueEqual(conditions.evaluate(ID, nested), ConditionOutcome.UNMET, "completed quest negation fails");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void forcedFollowUpUsesLocalBranchAndLogsConsequences(GameTestHelper helper) {
        var root = json("""
                {"entries":[{"id":"warning","trigger":"container_opened","lines":["Stop"],
                  "options":[{"id":"ask","label":"Why?","reputation":-2,"response":"Because",
                    "follow_up":{"lines":["Private property"],"options":[
                      {"id":"leave","label":"Leave","end_conversation":true,"response":"Goodbye"}]}}]}]}
                """);
        try (var session = new DialoguePreviewSession(root, ID, new PreviewConditions(), key -> null)) {
            session.start("");
            session.choose(0);
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Because", "response precedes follow-up");
            session.advance();
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Private property", "nested follow-up");
            session.choose(0);
            session.advance();
            helper.assertTrue(session.choices().isEmpty(), "leave ends preview");
            helper.assertTrue(session.log().stream().anyMatch(line -> line.contains("Simulated reputation")), "legacy consequence logged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void conversationChecksLocalizationAndCancellationAreLocal(GameTestHelper helper) {
        var root = json("""
                {"topics":[{"id":"talk","label":{"key":"topic"},"start":"start"}],
                  "nodes":[{"id":"start","exchanges":[{"text":{"key":"greeting"},"speaker":{"type":"role","role":"provider"}}],
                    "responses":[{"id":"roll","label":{"key":"topic"},"check":"test"}]},
                    {"id":"win","exchanges":[{"text":{"key":"win"}}],"outcome":"done"},
                    {"id":"lose","exchanges":[{"text":{"key":"lose"}}],"outcome":"done"}],
                  "checks":[{"id":"test","difficulty":12,"success":"win","failure":"lose"}],
                  "outcomes":[{"id":"done","label":{"key":"done"}}]}
                """);
        var conditions = new PreviewConditions();
        conditions.checkSuccess(false);
        var session = new DialoguePreviewSession(root, ID, conditions, key -> "translated " + key);
        session.start("");
        helper.assertValueEqual(session.choices().getFirst().label(), "translated topic", "localized topic");
        session.choose(0);
        helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "translated greeting", "localized line");
        session.choose(0);
        helper.assertValueEqual(session.nodeId(), "lose", "simulated failed check");
        session.close();
        helper.assertTrue(session.currentLine() == null && session.choices().isEmpty(), "closing disposes presentation");
        try {
            session.advance();
            helper.fail("closed preview accepted input");
        } catch (IllegalStateException expected) {
            helper.assertTrue(session.closed(), "cancel is final");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void missingBranchesFailAndCyclesWaitForInput(GameTestHelper helper) {
        var root = json("""
                {"nodes":{"start":{"lines":["Again"],"next":"start"}}}
                """);
        try (var session = new DialoguePreviewSession(root, ID, new PreviewConditions(), key -> null)) {
            session.start("start");
            for (int index = 0; index < 10; index++) session.choose(0);
            helper.assertValueEqual(session.nodeId(), "start", "cycle advances only from input");
            try {
                session.start("missing");
                helper.fail("missing node accepted");
            } catch (IllegalArgumentException expected) {
                helper.assertTrue(expected.getMessage().contains("Missing"), "missing target reported");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void variantsLegacyFormattingAndRequirementsArePreserved(GameTestHelper helper) {
        var conditions = new PreviewConditions();
        conditions.variant(1);
        var source = json("""
                {"nodes":{"storage_key":{"id":"entry","variants":[{"text":"First"},{"text":"Second","bold":true}],
                  "responses":[{"id":"locked","label":"Locked","requires_high_guts":true,"next":"end"}]},
                  "end":{"lines":["Done"],"end":true}}}
                """);
        try (var session = new DialoguePreviewSession(source, ID, conditions, key -> null)) {
            session.start("entry");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Second", "variants alias and selection");
            helper.assertTrue(com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.read(session.currentLine()).bold(),
                    "top-level formatting retained");
            helper.assertTrue(!session.choices().getFirst().available(), "unsimulated legacy requirements stay unknown");
            conditions.override("requires_high_guts", ConditionOutcome.MATCHED);
            session.start("entry");
            helper.assertTrue(session.choices().getFirst().available(), "explicit legacy simulation enables choice");
        }
        helper.assertValueEqual(conditions.evaluate(ID, json("{\"min_reputation\":10}")), ConditionOutcome.UNMET,
                "legacy reputation uses runtime matcher");
        conditions.reputation(20);
        helper.assertValueEqual(conditions.evaluate(ID, json("{\"min_reputation\":10}")), ConditionOutcome.MATCHED,
                "simulated legacy reputation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void ordinaryPoolPreviewSelectsEntriesAndKeepsInlineFallback(GameTestHelper helper) {
        var source = json("""
                {"openings":[{"id":"hello","lines":["Welcome"]}],
                 "lines":[{"id":"story","request":"story","variants":[{"text":"Fallback","text_key":"missing"}]}]}
                """);
        try (var session = new DialoguePreviewSession(source, ID, new PreviewConditions(), key -> null)) {
            session.start("");
            helper.assertValueEqual(session.choices().size(), 2, "ordinary resource entry chooser");
            session.choose(1);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Fallback", "missing translation keeps runtime fallback");
            helper.assertTrue(session.log().stream().anyMatch(value -> value.contains("Unresolved localization")),
                    "missing translation remains diagnosed");
        }
        try (var session = new DialoguePreviewSession(json("""
                {"nodes":{"start":{"lines":[{"key":"greeting"}],"end":true}}}
                """), ID, new PreviewConditions(), key -> "Translated " + key)) {
            session.start("start");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Translated greeting",
                    "bare localized variant key");
        }
        helper.succeed();
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
