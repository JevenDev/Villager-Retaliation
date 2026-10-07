package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestConditionStructureGameTests {
    private QuestConditionStructureGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void nestedAliasesCompileToTheirTypedConditions(GameTestHelper helper) {
        var envelope = fixture("{\"type\":\"all_of\",\"conditions\":[{\"type\":\"not\",\"condition\":{\"type\":\"reputation\",\"min\":0}}]}");
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var compiled = QuestV2Compiler.compile(resource, envelope).orElseThrow();
        var condition = compiled.provider().offer().conditions().getFirst();
        helper.assertTrue(condition instanceof DialogueCondition.AllOf all
                        && all.conditions().getFirst() instanceof DialogueCondition.Not not
                        && not.condition() instanceof DialogueCondition.Reputation,
                "nested aliases retain their logical structure through typed compilation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_condition_structure")
    public static void malformedChildrenFailBeforeCompilationWithExactPointers(GameTestHelper helper) {
        String[][] cases = {
                {"{\"type\":\"and\",\"conditions\":[{\"type\":\"unsupported_nested_condition\"}]}", "/conditions/0/type"},
                {"{\"type\":\"not\",\"condition\":{\"type\":\"all\",\"conditions\":[{\"type\":\"unsupported_nested_condition\"}]}}", "/condition/conditions/0/type"},
                {"{\"type\":\"not\"}", "/condition"},
                {"{\"type\":\"or\",\"conditions\":[]}", "/conditions"},
                {"{\"type\":\"any_of\",\"conditions\":[false]}", "/conditions/0"}
        };
        for (int index = 0; index < cases.length; index++) {
            var location = VillagerRetaliation.id("quests/condition_structure_" + index + ".json");
            var envelope = fixture(cases[index][0]);
            helper.assertTrue(QuestV2Parser.parse(location, envelope.root()).isEmpty(),
                    "malformed nested condition is rejected before compilation: " + cases[index][0]);
            String pointer = "/availability/conditions/0" + cases[index][1];
            helper.assertTrue(DatapackDiagnostics.recent().stream().anyMatch(entry ->
                            location.equals(entry.diagnostic().resourceId()) && pointer.equals(entry.diagnostic().jsonPointer())),
                    "nested condition diagnostic identifies " + pointer);
        }
        helper.succeed();
    }

    private static QuestResourceEnvelope fixture(String condition) {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:condition_structure",
                 "provider":{"type":"villagerretaliation:villager"},"availability":{"conditions":[]},
                 "entry_stage":"start","stages":[{"id":"start","objectives":[{"id":"choice","type":"choice"}]}]}
                """).getAsJsonObject();
        root.getAsJsonObject("availability").getAsJsonArray("conditions").add(JsonParser.parseString(condition));
        return QuestResourceEnvelope.read(VillagerRetaliation.id("quests/condition_structure.json"), root).orElseThrow();
    }
}
