package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerIndex;
import com.jvn.villagerretaliation.quest.QuestV2Compiler;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuest;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestMetadata;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestObjective;
import com.jvn.villagerretaliation.quest.compiled.CompiledQuestRewards;
import com.jvn.villagerretaliation.quest.compiler.QuestV1Compiler;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.QuestSchemaVersion;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class CompiledQuestAuthorityGameTests {
    private CompiledQuestAuthorityGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_compiled_views")
    public static void typedDefinitionsOwnCompatibilityViewsAndIndexes(GameTestHelper helper) {
        CompiledQuest original = fixture();
        var metadata = original.metadata();
        var typedMetadata = new CompiledQuestMetadata("Typed title", metadata.description(), metadata.titleKey(),
                metadata.descriptionKey(), metadata.questline(), metadata.tags(), metadata.parent(), metadata.dialogue());
        var objective = original.objectives().getFirst();
        var updated = new CompiledQuestObjective(objective.id(), objective.index(), objective.definition(),
                objective.source().child("expanded"));
        var rewards = new CompiledQuestRewards(new QuestDefinition.Rewards(17, 0, 0, null, null, null));
        var revision = new QuestDefinition.Revision(9, QuestDefinition.RevisionPolicy.KEEP,
                Map.of("old", "offer"), Map.of("old.goal", "offer.goal"));
        CompiledQuest compiled = new CompiledQuest(original.id(), original.source(), original.asQuestDefinition(),
                typedMetadata, original.provider(), original.target(), original.entryStage(), original.prerequisites(),
                original.rules(), original.ui(), List.of(updated), original.objectivesById(), original.stages(),
                original.stagesById(), original.triggers(), Map.of(), QuestTriggerIndex.EMPTY, rewards, false, revision);
        QuestDefinition view = compiled.asQuestDefinition();
        helper.assertValueEqual(view.title(), "Typed title", "typed metadata owns presentation");
        helper.assertValueEqual(view.rewards().experience(), 17, "typed rewards own execution");
        helper.assertValueEqual(view.revision(), revision, "typed revision owns migration");
        helper.assertFalse(view.showLockedAdventureHint(), "typed availability metadata owns its compatibility view");
        helper.assertTrue(compiled.objectivesById().get(updated.id()) == updated,
                "objective lookups cannot retain a stale caller-provided index");
        helper.assertTrue(compiled.stagesById().get("offer").objectives().getFirst() == updated,
                "stage objective bindings resolve the authoritative objective");
        helper.assertValueEqual(compiled.triggerIndex().candidates(QuestDefinition.TriggerEvent.STARTED, "offer").size(), 1,
                "trigger dispatch cannot retain a stale caller-provided index");
        helper.assertValueEqual(compiled.triggersByEvent().get(QuestDefinition.TriggerEvent.STARTED).size(), 1,
                "event lookup shares the dispatch index");
        helper.assertTrue(compiled.asQuestDefinition() == view, "boundary adapters reuse their immutable view");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_compiled_views")
    public static void legacyCompiledConstructorPreservesRevisionAndHints(GameTestHelper helper) {
        CompiledQuest original = fixture();
        CompiledQuest legacy = new CompiledQuest(original.id(), original.source(), original.asQuestDefinition(),
                original.metadata(), original.provider(), original.target(), original.entryStage(), original.prerequisites(),
                original.rules(), original.ui(), original.objectives(), Map.of(), original.stages(), Map.of(),
                original.triggers(), Map.of(), null, original.rewards());
        helper.assertValueEqual(legacy.asQuestDefinition(), original.asQuestDefinition(),
                "existing callers retain their complete definition through the boundary adapter");
        helper.assertValueEqual(legacy.revision().number(), 3, "legacy revision remains explicit in the compiled model");
        helper.assertFalse(legacy.showLockedAdventureHint(), "legacy locked hint policy is preserved");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_compiled_views")
    public static void legacyCompilerRetainsAuthoredEntryStage(GameTestHelper helper) {
        CompiledQuest original = fixture();
        var envelope = new QuestResourceEnvelope(original.source().resource(), new JsonObject(),
                QuestSchemaVersion.V1, null);
        CompiledQuest legacy = QuestV1Compiler.compile(original.asQuestDefinition(), envelope);
        helper.assertValueEqual(legacy.entryStage(), "offer", "v1 compilation preserves its authored entry stage");
        helper.assertValueEqual(legacy.asQuestDefinition(), original.asQuestDefinition(),
                "compiled legacy definitions preserve all gameplay fields");
        helper.succeed();
    }

    private static CompiledQuest fixture() {
        JsonObject raw = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:compiled_authority",
                 "metadata":{"title":"Original title","revision":3,"show_locked_adventure_hint":false},
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "lifecycle":{"on_start":{"actions":[{"type":"experience","amount":2}]}},
                 "stages":[{"id":"offer","objectives":[{"id":"goal","type":"choice"}],
                   "complete_when":["goal"]}]}
                """).getAsJsonObject();
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/compiled_authority.json"), raw).orElseThrow();
        return QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
    }
}
