package com.jvn.villagerretaliation.dialogue.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.authoring.core.AuthoringDocument;
import com.jvn.villagerretaliation.authoring.core.AuthoringWorkspace;
import com.jvn.villagerretaliation.authoring.core.DefinitionKind;
import com.jvn.villagerretaliation.authoring.preview.PreviewConditions;
import com.jvn.villagerretaliation.authoring.preview.PreviewWordPools;
import com.jvn.villagerretaliation.authoring.preview.WordPoolPreview;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition.ConditionOutcome;
import com.jvn.villagerretaliation.dialogue.DialogueWordPool;
import com.jvn.villagerretaliation.mood.VillagerMood;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("dialogue_words")
@PrefixGameTestTemplate(false)
public final class DialogueWordPreviewGameTests {
    private static final ResourceLocation ID = ResourceLocation.parse("test:greeting");
    private static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void matrixCoversMoodsTiersAndStylesWithoutChangingInputs(GameTestHelper helper) {
        var pool = DialogueWordPool.read(json("""
                {"values":["Hello",{"text":"Friend","conditions":[{"type":"reputation","level":"trusted"}]},
                  {"text":"Upset friend","priority":20,"conditions":[{"type":"mood","mood":"angry"},{"type":"reputation","level":"trusted"}]}]}
                """));
        var conditions = new PreviewConditions();
        var saved = conditions.snapshot();
        var rows = WordPoolPreview.matrix("{word:test:greeting}", id -> pool, conditions, 42);
        helper.assertValueEqual(rows.size(), 12 * 9 * 4, "matrix covers every mood, reputation tier, and speaking style");
        var angry = rows.stream().filter(row -> row.mood() == VillagerMood.ANGRY && row.reputation() == VillagerReputationLevel.TRUSTED).findFirst().orElseThrow();
        helper.assertValueEqual(angry.result().text(), "Upset friend", "matrix applies combined requirements and priorities");
        helper.assertTrue(angry.result().entries().stream().filter(WordPoolPreview.EntryResult::selected).count() == 1, "matrix identifies the winning entry");
        helper.assertValueEqual(conditions.snapshot(), saved, "matrix does not change the author's simulated conditions");
        helper.assertValueEqual(rows, WordPoolPreview.matrix("{word:test:greeting}", id -> pool, conditions, 42), "same seed reproduces the matrix");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void unsavedNamedContextsControlMultiplePoolsAndRespectChanges(GameTestHelper helper) throws Exception {
        var root = Files.createTempDirectory("word-context-preview-");
        try {
            var workspace = AuthoringWorkspace.create(root);
            var owner = new AuthoringDocument(DefinitionKind.TREE, "data/test/dialogue_trees/en_us/hello.json", new JsonObject());
            var context = new AuthoringDocument(DefinitionKind.RESOURCE, "data/test/dialogue_contexts/friendly.json", json("""
                    {"id":"test:friendly","conditions":[{"type":"reputation","level":"trusted"}]}
                    """));
            workspace.add(context);
            for (String name : List.of("greeting", "thanks")) workspace.add(new AuthoringDocument(DefinitionKind.RESOURCE,
                    "data/test/dialogue_words/en_us/" + name + ".json", json("""
                    {"values":["neutral",{"text":"friendly","conditions":[{"type":"context","id":"test:friendly"}]}]}
                    """)));
            var conditions = new PreviewConditions();
            conditions.reputationLevel(VillagerReputationLevel.TRUSTED);
            conditions.contexts(PreviewWordPools.contexts(workspace, owner));
            var pools = PreviewWordPools.lookupDefinitions(workspace, owner, "en_us", (id, language) -> DialogueWordPool.EMPTY);
            helper.assertValueEqual(WordPoolPreview.inspect("{word:test:greeting} {word:test:thanks}", pools, conditions, 1, null, "").text(),
                    "friendly friendly", "unsaved named rule applies across multiple pools");
            context.edit("Change friendly rule", value -> value.add("conditions", JsonParser.parseString("[{\"type\":\"reputation\",\"level\":\"royalty\"}]")));
            conditions.contexts(PreviewWordPools.contexts(workspace, owner));
            helper.assertValueEqual(WordPoolPreview.inspect("{word:test:greeting} {word:test:thanks}", pools, conditions, 1, null, "").text(),
                    "neutral neutral", "editing one rule updates both pools without saving");
        } finally {
            try (var paths = Files.walk(root)) { for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path); }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void previewSnapshotsKeepMoodStyleAndLegacyCompatibility(GameTestHelper helper) {
        var conditions = new PreviewConditions();
        conditions.mood(VillagerMood.ANGRY, 80);
        conditions.speakingStyle("formal");
        var rules = DialogueCondition.readList(ID, "test", json("""
                {"conditions":[{"type":"mood","mood":"angry","min_intensity":70},{"type":"speaking_style","style":"formal"}]}
                """));
        var copy = conditions.copy();
        helper.assertValueEqual(copy.evaluate(rules), ConditionOutcome.MATCHED, "snapshot copy restores mood intensity and speaking style");
        var legacy = conditions.snapshot(); legacy.remove("mood"); legacy.remove("mood_intensity"); legacy.remove("speaking_style");
        copy.restore(legacy);
        helper.assertTrue(copy.mood() == VillagerMood.NEUTRAL && copy.speakingStyle().isEmpty(), "older snapshots use safe defaults");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "dialogue_words")
    public static void inspectorExplainsUnknownConditionsAndDetectsMissingPools(GameTestHelper helper) {
        var pool = DialogueWordPool.read(json("""
                {"values":["fallback",{"text":"burning","conditions":[{"type":"physical_state","subject":"speaker","state":"burning"}]}]}
                """));
        var result = WordPoolPreview.inspect("{word:test:greeting} {word:test:missing}", id -> id.equals(ID) ? pool : DialogueWordPool.EMPTY,
                new PreviewConditions(), 1, null, "");
        helper.assertValueEqual(result.text(), "fallback {word:test:missing}", "unknown requirements use fallback and missing text stays visible");
        helper.assertValueEqual(result.entries().get(1).conditions(), ConditionOutcome.UNKNOWN, "unknown requirements are distinguished from unmet requirements");
        helper.assertTrue(result.selections().getFirst().fallback() && result.diagnostics().size() == 1, "inspector explains fallback and missing references");
        var cyclic = new PreviewConditions();
        cyclic.contexts(id -> new DialogueContextResources.Rule(id, List.of(new DialogueCondition.ContextRule(id))));
        helper.assertValueEqual(cyclic.evaluate(List.of(new DialogueCondition.ContextRule(ID))), ConditionOutcome.UNKNOWN, "unfinished cyclic contexts cannot overflow preview evaluation");
        helper.succeed();
    }
}
