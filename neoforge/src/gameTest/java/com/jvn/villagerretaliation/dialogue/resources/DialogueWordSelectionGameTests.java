package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueWordHistory;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
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
public final class DialogueWordSelectionGameTests {
    private static DialogueWordPool pool(String json) { return DialogueWordPool.read(JsonParser.parseString(json).getAsJsonObject()); }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void priorityWinsBeforeWeightsAndFallbacksStaySeparate(GameTestHelper helper) {
        var pool = pool("""
                {"values":[{"text":"fallback","priority":1000},
                  {"text":"friendly","weight":1000,"conditions":[{"type":"reputation","level":"trusted"}]},
                  {"text":"angry","priority":10,"conditions":[{"type":"mood","mood":"angry"}]},
                  {"text":"trusted but angry","priority":20,"conditions":[{"type":"mood","mood":"angry"},{"type":"reputation","level":"trusted"}]}]}
                """);
        for (int seed = 0; seed < 20; seed++)
            helper.assertValueEqual(pool.selectMatching(rules -> true, RandomSource.create(seed)), "trusted but angry", "priority overrides weight among matches");
        helper.assertValueEqual(pool.selectMatching(rules -> false, RandomSource.create(1)), "fallback", "unconditional fallback is separate from conditional priorities");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void sharedVariantsSurviveFormattingAndUseNeutralFallbacks(GameTestHelper helper) {
        var greeting = pool("""
                {"selection_group":"test:tone","values":["Hello",{"text":"Welcome","variant":"warm","conditions":[{"type":"mood","mood":"content"}]}]}
                """);
        var address = pool("""
                {"selection_group":"test:tone","values":["traveller",{"text":"friend","variant":"warm","conditions":[{"type":"mood","mood":"content"}]},
                  {"text":"stranger","variant":"guarded","weight":1000,"conditions":[{"type":"mood","mood":"content"}]}]}
                """);
        var farewell = pool("""
                {"selection_group":"test:tone","values":["Goodbye",{"text":"Watch yourself","variant":"guarded","conditions":[{"type":"mood","mood":"content"}]}]}
                """);
        var definitions = Map.of(ResourceLocation.parse("test:greeting"), greeting, ResourceLocation.parse("test:address"), address, ResourceLocation.parse("test:farewell"), farewell);
        var trace = new ArrayList<DialogueWordSelection.Selection>();
        var resolver = new DialogueWordSelection(definitions::get, rules -> true, RandomSource.create(1), null, "", helper::fail, trace::add);
        helper.assertValueEqual(resolver.apply("<aqua>{word:test:greeting}</aqua>"), "<aqua>Welcome</aqua>", "first fragment establishes the family");
        helper.assertValueEqual(resolver.apply("{word:test:address} {word:test:address}"), "friend friend", "other fragments share the family and repeated pools share the roll");
        helper.assertValueEqual(resolver.apply("{word:test:farewell}"), "Goodbye", "missing family uses neutral fallback");
        helper.assertTrue(trace.size() == 3 && trace.getLast().fallback(), "trace records one selection per pool and explains fallback");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void recentWordsNeverEscapeConditionsPriorityOrPlayerHistory(GameTestHelper helper) {
        var pool = pool("""
                {"avoid_recent":2,"values":["fallback",{"text":"one","priority":10,"conditions":[{"type":"mood","mood":"angry"}]},
                  {"text":"two","priority":10,"conditions":[{"type":"mood","mood":"angry"}]},
                  {"text":"lower priority","conditions":[{"type":"mood","mood":"angry"}]}]}
                """);
        var id = ResourceLocation.parse("test:history");
        var history = new DialogueWordHistory();
        history.remember("player-a/" + id, "one");
        var resolver = new DialogueWordSelection(key -> pool, rules -> true, RandomSource.create(1), history, "player-a", helper::fail, selection -> { });
        helper.assertValueEqual(resolver.apply("{word:test:history}"), "two", "fresh eligible phrase replaces recent wording");
        helper.assertTrue(history.recent("player-b/" + id, 2).isEmpty(), "another player has separate history");
        helper.assertValueEqual(history.recent("player-a/" + id, 2), List.of("two", "one"), "one roll records one use");
        resolver.apply("{word:test:history}");
        helper.assertValueEqual(history.recent("player-a/" + id, 2), List.of("two", "one"), "redisplay does not record another use");
        var exhausted = new DialogueWordSelection(key -> pool, rules -> true, RandomSource.create(2), history, "player-a", helper::fail, selection -> { });
        helper.assertTrue(List.of("one", "two").contains(exhausted.apply("{word:test:history}")), "exhausted history relaxes without lowering priority");
        var noMatch = new DialogueWordSelection(key -> pool, rules -> false, RandomSource.create(2), history, "player-a", helper::fail, selection -> { });
        helper.assertValueEqual(noMatch.apply("{word:test:history}"), "fallback", "history never bypasses conditions");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void weightedPriorityTiesAndLegacyDefaultsKeepTheirMeaning(GameTestHelper helper) {
        var pool = pool("""
                {"values":["fallback",{"text":"common","priority":20,"weight":9,"conditions":[{"type":"mood","mood":"content"}]},
                  {"text":"rare","priority":20,"conditions":[{"type":"mood","mood":"content"}]}]}
                """);
        var random = RandomSource.create(12);
        int common = 0;
        for (int i = 0; i < 1000; i++) if (pool.selectMatching(rules -> true, random).equals("common")) common++;
        helper.assertTrue(common > 850 && common < 950, "weights still apply within the highest priority");
        var old = pool("{\"values\":[\"one\",\"two\"]}");
        helper.assertTrue(old.avoidRecent() == 0 && old.selectionGroup().isEmpty() && old.entries().getFirst().priority() == 0, "legacy pools opt out of new behavior");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void malformedSelectionFieldsAreRejected(GameTestHelper helper) {
        for (String json : List.of(
                "{\"values\":[{\"text\":\"x\",\"priority\":1.5}]}",
                "{\"values\":[{\"text\":\"x\",\"priority\":2147483648}]}",
                "{\"values\":[\"x\"],\"avoid_recent\":17}",
                "{\"values\":[\"x\"],\"selection_group\":\"invalid\"}",
                "{\"values\":[{\"text\":\"x\",\"variant\":\"warm\"}]}",
                "{\"selection_group\":\"test:tone\",\"values\":[{\"text\":\"x\",\"variant\":\"warm\"}]}")) {
            boolean rejected = false;
            try { pool(json); } catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid selection fields rejected: " + json);
        }
        helper.succeed();
    }
}
