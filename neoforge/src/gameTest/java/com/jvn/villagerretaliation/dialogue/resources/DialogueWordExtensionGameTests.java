package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.dialogue.DialogueWordPoolLibrary;
import com.jvn.villagerretaliation.dialogue.DialogueWordSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_words")
@PrefixGameTestTemplate(false)
public final class DialogueWordExtensionGameTests {
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
    private static ResourceLocation id(String value) { return ResourceLocation.parse("test:" + value); }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void groupsCombineConditionsAndKeepIndividualWeights(GameTestHelper helper) {
        var pool = DialogueWordPool.read(json("""
                {"values":["Hello"],"groups":[{"priority":20,"conditions":[{"type":"mood","mood":"angry"}],
                 "values":[{"id":"brief","text":"Keep it brief","weight":3},{"text":"I will listen","conditions":[{"type":"reputation","level":"trusted"}]}]}]}
                """));
        helper.assertValueEqual(pool.entries().get(1).priority(), 20, "shared priority applies to every phrase");
        helper.assertValueEqual(pool.entries().get(1).weight(), 3, "individual weights are retained");
        helper.assertValueEqual(pool.entries().get(2).conditions().size(), 2, "individual requirements are added to group requirements");
        helper.assertValueEqual(pool.selectMatching(rules -> false, RandomSource.create(1)), "Hello", "group conditions cannot bypass fallback");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void inheritanceOverridesRemovesAndAppendsWithoutChangingParents(GameTestHelper helper) {
        var definitions = Map.of(id("base"), json("""
                {"role":"opening_clause","avoid_recent":2,"values":[{"id":"fallback","text":"Hello"},{"id":"old","text":"Old wording"}]}
                """), id("farmer"), json("""
                {"extends":"test:base","remove":["old"],"values":[{"id":"fallback","text":"Good day"},"Fresh from the fields"]}
                """));
        var library = new DialogueWordPoolLibrary(definitions::get, key -> DialogueWordPool.EMPTY);
        var child = library.apply(id("farmer"));
        helper.assertValueEqual(child.values(), List.of("Good day", "Fresh from the fields"), "targeted replacement, removal, and addition preserve order");
        helper.assertValueEqual(child.role(), "opening_clause", "metadata inherits by default");
        helper.assertValueEqual(child.avoidRecent(), 2, "history settings inherit");
        helper.assertValueEqual(library.apply(id("base")).values(), List.of("Hello", "Old wording"), "parent remains unchanged");
        helper.assertTrue(child.entries().getFirst().source().contains("test:farmer"), "effective entries retain their authoring origin");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void inheritanceRejectsCyclesMissingIdsAndInvalidEffectivePools(GameTestHelper helper) {
        for (var definitions : List.of(
                Map.of(id("child"), json("{\"extends\":\"test:missing\"}")),
                Map.of(id("child"), json("{\"extends\":\"test:child\"}")),
                Map.of(id("child"), json("{\"extends\":\"test:base\",\"remove\":[\"missing\"]}"), id("base"), json("{\"values\":[\"Hello\"]}")),
                Map.of(id("child"), json("{\"extends\":\"test:base\",\"remove\":[\"fallback\"]}"), id("base"), json("{\"values\":[{\"id\":\"fallback\",\"text\":\"Hello\"}]}")))) {
            boolean rejected = false;
            try { new DialogueWordPoolLibrary(definitions::get, key -> DialogueWordPool.EMPTY).apply(id("child")); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid inheritance must be rejected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void wholeLineUsesACommonVariantRegardlessOfReferenceOrder(GameTestHelper helper) {
        var a = DialogueWordPool.read(json("""
                {"selection_group":"test:tone","values":["neutral a",{"text":"warm a","variant":"warm","conditions":[{"type":"mood","mood":"content"}]},
                  {"text":"guarded a","variant":"guarded","weight":1000,"conditions":[{"type":"mood","mood":"content"}]}]}
                """));
        var b = DialogueWordPool.read(json("""
                {"selection_group":"test:tone","values":["neutral b",{"text":"warm b","variant":"warm","conditions":[{"type":"mood","mood":"content"}]}]}
                """));
        var definitions = Map.of(id("a"), a, id("b"), b);
        for (String template : List.of("{word:test:a} {word:test:b}", "{word:test:b} {word:test:a}")) {
            var trace = new ArrayList<DialogueWordSelection.Selection>();
            var resolver = new DialogueWordSelection(definitions::get, rules -> true, RandomSource.create(1), null, "", helper::fail, trace::add);
            String result = resolver.apply(template);
            helper.assertTrue(result.contains("warm a") && result.contains("warm b"), "whole-line planning avoids unnecessary neutral fallbacks");
            helper.assertTrue(trace.stream().allMatch(selection -> selection.variant().equals("warm")), "reference order does not choose the family");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void optionalClausesRespectChanceConditionsAndWhitespace(GameTestHelper helper) {
        var omitted = DialogueWordPool.read(json("{\"optional\":true,\"chance\":0,\"role\":\"sentence\",\"values\":[\"you helped\"]}"));
        var included = DialogueWordPool.read(json("{\"optional\":true,\"chance\":1,\"role\":\"sentence\",\"values\":[\"you helped\"]}"));
        for (var pool : List.of(omitted, included)) {
            var resolver = new DialogueWordSelection(key -> pool, rules -> true, RandomSource.create(1), null, "", helper::fail, selection -> { });
            helper.assertValueEqual(resolver.apply("Thanks. {word:test:clause} Goodbye."), pool == omitted ? "Thanks. Goodbye." : "Thanks. You helped. Goodbye.", "optional sentences join without extra spaces");
        }
        var conditional = DialogueWordPool.read(json("""
                {"optional":true,"values":[{"text":"You are a friend.","conditions":[{"type":"reputation","level":"trusted"}]}]}
                """));
        var resolver = new DialogueWordSelection(key -> conditional, rules -> false, RandomSource.create(1), null, "", helper::fail, selection -> { });
        helper.assertValueEqual(resolver.apply("Thanks. {word:test:clause}"), "Thanks.", "unmatched optional requirements omit the complete clause");
        helper.assertTrue(conditional.values().contains(""), "paired text certification includes the omitted alternative");
        helper.assertTrue(com.jvn.villagerretaliation.dialogue.DialogueWordPools.matches("Thanks. {word:test:clause} Goodbye.", "Thanks. Goodbye.", key -> omitted.values()), "paired certification accepts correctly joined optional omissions");
        helper.assertFalse(com.jvn.villagerretaliation.dialogue.DialogueWordPools.matches("Thanks. {word:test:clause} Goodbye.", "Thanks.Goodbye.", key -> omitted.values()), "paired certification rejects incorrect spacing");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "dialogue_words")
    public static void coverageFindsShadowedEntriesAndSamplesExactBoundaries(GameTestHelper helper) {
        var pool = DialogueWordPool.read(json("""
                {"values":["Hello",{"text":"low","conditions":[{"type":"mood","mood":"angry","min_intensity":70}]},
                 {"text":"high","priority":20,"conditions":[{"type":"mood","mood":"angry","min_intensity":70}]},
                 {"text":"score","priority":30,"conditions":[{"type":"reputation","min":42}]}]}
                """));
        var conditions = new com.jvn.villagerretaliation.authoring.preview.PreviewConditions();
        var report = com.jvn.villagerretaliation.authoring.preview.WordPoolPreview.coverage("{word:test:a}", key -> pool, conditions, 1);
        helper.assertTrue(report.rows().stream().anyMatch(row -> row.intensity() == 69) && report.rows().stream().anyMatch(row -> row.intensity() == 70)
                && report.rows().stream().anyMatch(row -> row.intensity() == 71), "coverage samples just below, at, and above mood cutoffs");
        helper.assertTrue(report.rows().stream().anyMatch(row -> row.score() == 41) && report.rows().stream().anyMatch(row -> row.score() == 42)
                && report.rows().stream().anyMatch(row -> row.score() == 43), "coverage samples reputation cutoffs");
        helper.assertTrue(report.findings().stream().anyMatch(finding -> finding.kind().equals("shadowed") && finding.message().contains("/values/1")), "coverage distinguishes priority shadowing");
        helper.assertTrue(report.findings().stream().anyMatch(finding -> finding.kind().equals("duplicate_output")), "duplicate rendered dialogue is reported");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void grammarRolesFlagUnsafeSlotsAndPreserveLiteralText(GameTestHelper helper) {
        var sentence = DialogueWordPool.read(json("{\"role\":\"sentence\",\"values\":[\"things are going well\"]}"));
        helper.assertValueEqual(sentence.selectMatching(rules -> true, RandomSource.create(1)), "Things are going well.", "sentence slots supply capitalization and punctuation");
        helper.assertTrue(!com.jvn.villagerretaliation.dialogue.DialogueWordGrammar.warnings("I am feeling {word:test:a}.", key -> sentence).isEmpty(), "complete sentences inside adjective slots are flagged");
        helper.assertTrue(com.jvn.villagerretaliation.dialogue.DialogueWordGrammar.warnings(
                "<gray>{word:test:a}</gray> {word:test:b}", key -> sentence).isEmpty(), "adjacent sentence pools form separate sentences");
        var optional = new DialogueWordPool(sentence.entries(), "", 0, "sentence", true, 1);
        helper.assertTrue(!com.jvn.villagerretaliation.dialogue.DialogueWordGrammar.warnings(
                "I feel {word:test:a} {word:test:b}", key -> key.getPath().equals("a") ? optional : sentence).isEmpty(),
                "optional sentences cannot guarantee a boundary");
        var address = DialogueWordPool.read(json("{\"role\":\"address\",\"values\":[\"my friend.\"]}"));
        helper.assertTrue(!com.jvn.villagerretaliation.dialogue.DialogueWordGrammar.warnings(address).isEmpty(), "fragment punctuation is flagged");
        var literal = DialogueWordPool.read(json("{\"values\":[\"hello\"]}"));
        helper.assertValueEqual(literal.selectMatching(rules -> true, RandomSource.create(1)), "hello", "legacy literal pools keep their exact wording");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void authoringRejectsUnresolvedInheritanceBeforeExport(GameTestHelper helper) {
        var child = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(com.jvn.villagerretaliation.authoring.core.DefinitionKind.RESOURCE,
                "data/test/dialogue_words/en_us/child.json", json("{\"extends\":\"test:missing\"}"));
        var issues = com.jvn.villagerretaliation.authoring.core.AuthoringValidation.validate(List.of(child));
        helper.assertTrue(issues.stream().anyMatch(issue -> issue.message().contains("missing parent word pool")), "missing inherited dependencies block export");
        var parent = new com.jvn.villagerretaliation.authoring.core.AuthoringDocument(com.jvn.villagerretaliation.authoring.core.DefinitionKind.RESOURCE,
                "data/test/dialogue_words/en_us/missing.json", json("{\"values\":[\"Hello\"]}"));
        helper.assertTrue(com.jvn.villagerretaliation.authoring.core.AuthoringValidation.validate(List.of(child, parent)).isEmpty(), "unsaved inherited definitions validate together");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void optionalVariantsCannotDisplaceRequiredWording(GameTestHelper helper) {
        var required = DialogueWordPool.read(json("""
                {"selection_group":"test:tone","values":["neutral",{"text":"Welcome, friend.","variant":"warm","conditions":[{"type":"mood","mood":"content"}]}]}
                """));
        var optional = DialogueWordPool.read(json("""
                {"selection_group":"test:tone","optional":true,"values":[{"text":"Keep your distance.","variant":"guarded","conditions":[{"type":"mood","mood":"content"}]}]}
                """));
        var definitions = Map.of(id("a"), required, id("b"), optional);
        var trace = new ArrayList<DialogueWordSelection.Selection>();
        var resolver = new DialogueWordSelection(definitions::get, rules -> true, RandomSource.create(1), null, "", helper::fail, trace::add);
        helper.assertValueEqual(resolver.apply("{word:test:a} {word:test:b}"), "Welcome, friend.", "incompatible optional wording is omitted without changing required tone");
        helper.assertTrue(trace.getLast().omitted(), "omitted incompatible clauses remain visible to the inspector");
        helper.succeed();
    }

}
