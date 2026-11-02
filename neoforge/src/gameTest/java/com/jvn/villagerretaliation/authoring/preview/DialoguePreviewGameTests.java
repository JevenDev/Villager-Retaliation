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
    public static void presentationTemplatesShareStylesAndKeepInstancesIndependent(GameTestHelper helper) {
        var styled = json("""
                {"parameters":["body"],"variants":[{"id":"ordinary","text":"{body}"},
                  {"id":"quiet","priority":20,"conditions":[{"type":"mood","mood":"stressed"}],"text":"<gray>{body}</gray>"}]}
                """);
        var greeting = json("""
                {"extends":"test:styled","parameters":["greeting"],"arguments":{"body":"Hello, {greeting}"}}
                """);
        var library = new com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates.Library(id ->
                id.getPath().equals("styled") ? styled : id.getPath().equals("greeting") ? greeting : null);
        var source = json("""
                {"id":"farmer","template":"test:greeting","arguments":{"greeting":"need a hand?"}}
                """);
        String before = source.toString();
        var expanded = com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates.expand(source, library);
        helper.assertValueEqual(expanded.getAsJsonArray("variants").get(0).getAsJsonObject().get("text").getAsString(),
                "Hello, need a hand?", "template arguments compose the full line");
        var quiet = expanded.getAsJsonArray("variants").get(1).getAsJsonObject();
        helper.assertValueEqual(quiet.get("text").getAsString(), "<gray>Hello, need a hand?</gray>", "shared presentation wraps the assembled line");
        helper.assertValueEqual(quiet.get("priority").getAsInt(), 20, "template priority survives");
        helper.assertTrue(quiet.getAsJsonArray("conditions").size() == 1, "template conditions survive");
        helper.assertValueEqual(source.toString(), before, "authored instance remains unchanged");
        var second = com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates.expand(json("""
                {"template":"test:greeting","arguments":{"greeting":"looking for a book?"}}
                """), library);
        helper.assertTrue(second.toString().contains("looking for a book?") && !second.toString().contains("need a hand?"),
                "one profession cannot change another template instance");
        for (String invalid : java.util.List.of(
                "{\"template\":\"test:missing\"}",
                "{\"template\":\"test:greeting\"}",
                "{\"template\":\"test:greeting\",\"arguments\":{\"greeting\":\"hello\",\"unknown\":\"x\"}}",
                "{\"template\":\"test:greeting\",\"arguments\":{\"greeting\":\"{body}\"}}",
                "{\"template\":\"test:greeting\",\"arguments\":{\"greeting\":\"hello\"},\"text\":\"override\"}")) {
            boolean rejected = false;
            try { com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates.expand(json(invalid), library); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid presentation template references and arguments are rejected");
        }
        var cyclic = new com.jvn.villagerretaliation.dialogue.DialoguePresentationTemplates.Library(id -> json("""
                {"extends":"test:cycle"}
                """));
        boolean rejected = false;
        try { cyclic.apply(ResourceLocation.parse("test:cycle")); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "template inheritance cycles are rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void wordingOverridesPreserveInheritedSelectionRules(GameTestHelper helper) {
        var base = json("""
                {"role":"sentence","selection_group":"test:tone","avoid_recent":2,"values":[{"id":"plain","text":"Hello."}],
                 "groups":[{"conditions":[{"type":"mood","mood":"stressed","min_intensity":25}],"priority":30,"variant":"quiet",
                   "values":[{"id":"stressed","text":"Tired.","weight":3}]}]}
                """);
        var child = json("""
                {"extends":"test:base","text_overrides":{"stressed":"A bit tired."}}
                """);
        var baseId = ResourceLocation.parse("test:base");
        var childId = ResourceLocation.parse("test:child");
        var library = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(
                id -> id.equals(childId) ? child : base, id -> com.jvn.villagerretaliation.dialogue.DialogueWordPool.EMPTY);
        var original = library.apply(baseId).entries().get(1);
        var replacement = library.apply(childId).entries().get(1);
        helper.assertValueEqual(replacement.text(), "A bit tired.", "wording changes");
        helper.assertValueEqual(replacement.conditions(), original.conditions(), "mood restrictions survive");
        helper.assertValueEqual(replacement.priority(), original.priority(), "priority survives");
        helper.assertValueEqual(replacement.weight(), original.weight(), "weight survives");
        helper.assertValueEqual(replacement.variant(), original.variant(), "shared tone survives");
        helper.assertTrue(replacement.source().endsWith("/text_overrides/stressed"), "trace points to the wording override");
        var conditions = new PreviewConditions();
        var neutral = WordPoolPreview.inspect("{word:test:child}", library, conditions, 1, null, "");
        helper.assertValueEqual(neutral.text(), "Hello.", "stress wording does not leak into neutral speech");
        conditions.mood(com.jvn.villagerretaliation.mood.VillagerMood.STRESSED, 50);
        helper.assertValueEqual(WordPoolPreview.inspect("{word:test:child}", library, conditions, 1, null, "").text(),
                "A bit tired.", "stress wording plays in its inherited context");
        for (String source : java.util.List.of(
                "{\"extends\":\"test:base\",\"text_overrides\":{\"missing\":\"No.\"}}",
                "{\"extends\":\"test:base\",\"text_overrides\":{\"plain\":\"\"}}",
                "{\"extends\":\"test:base\",\"remove\":[\"stressed\"],\"text_overrides\":{\"stressed\":\"No.\"}}")) {
            var invalid = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(
                    id -> id.equals(childId) ? json(source) : base, id -> com.jvn.villagerretaliation.dialogue.DialogueWordPool.EMPTY);
            boolean rejected = false;
            try { invalid.apply(childId); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid wording overrides fail validation");
        }
        var legacy = new com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary(
                id -> id.equals(childId) ? json("""
                        {"extends":"test:base","values":[{"id":"stressed","text":"Replacement."}]}
                        """) : base, id -> com.jvn.villagerretaliation.dialogue.DialogueWordPool.EMPTY);
        helper.assertTrue(legacy.apply(childId).entries().get(1).conditions().isEmpty(), "legacy full replacements keep their semantics");
        helper.succeed();
    }

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
        conditions.reputation(20, com.jvn.villagerretaliation.reputation.VillagerReputationLevel.NEUTRAL);
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

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void restartClearsPreviousNodeAndLineContext(GameTestHelper helper) {
        var source = json("""
                {"entries":[{"id":"talk","label":"Talk","lines":["Topic"],"start":"start"}],
                 "nodes":{"start":{"exchanges":[{"text":"First"},{"text":"Second"}],
                  "camera":{"preset":"behind"},"end":true}}}
                """);
        try (var session = new DialoguePreviewSession(source, ID, new PreviewConditions(), key -> null)) {
            session.start("start");
            session.advance();
            session.start("");
            helper.assertValueEqual(session.nodeId(), "", "entry chooser has no previous node");
            helper.assertTrue(session.currentLine() == null, "restart clears the previous exchange");
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Topic", "topic starts at first line");
            helper.assertTrue(!session.currentLine().has("camera"), "topic does not inherit the previous node camera");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "authoring_preview")
    public static void forcedLocalizationKeysAndOutcomeEffectsArePreviewed(GameTestHelper helper) {
        var conditions = new PreviewConditions();
        conditions.variant(1);
        var source = json("""
                {"line_keys":["first","second"],"lines":["Fallback"],
                 "options":[{"id":"leave","label":"Leave","response_key":"reply","response":"Reply fallback","end_conversation":true}]}
                """);
        try (var session = new DialoguePreviewSession(source, ID, conditions, key -> "Localized " + key)) {
            session.start("");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Localized second",
                    "line key variants are selectable without matching inline variants");
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Localized reply",
                    "forced response key is resolved");
        }
        try (var session = new DialoguePreviewSession(source, ID, conditions, key -> null)) {
            session.start("");
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Fallback", "missing key retains fallback");
            session.choose(0);
            helper.assertValueEqual(session.currentLine().get("resolved_text").getAsString(), "Reply fallback", "missing response key retains fallback");
        }
        var conversation = json("""
                {"nodes":[{"id":"done","exchanges":[{"text":"Finished"}],"outcome":"done"}],
                 "outcomes":[{"id":"done","label":{"key":"done"},"actions":[{"type":"test:effect"}]}]}
                """);
        try (var session = new DialoguePreviewSession(conversation, ID, conditions, key -> key)) {
            session.start("done");
            helper.assertTrue(session.log().stream().anyMatch(value -> value.contains("test:effect")), "outcome actions appear in simulation log");
            helper.assertValueEqual(conversation.getAsJsonArray("outcomes").get(0).getAsJsonObject().getAsJsonArray("actions").size(),
                    1, "outcome definitions remain untouched");
        }
        helper.succeed();
    }

    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
