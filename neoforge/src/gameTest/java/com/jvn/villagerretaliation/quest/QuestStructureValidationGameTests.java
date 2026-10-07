package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.dialogue.resources.QuestDialogueCompiler;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.util.DatapackDiagnostics;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class QuestStructureValidationGameTests {
    private QuestStructureValidationGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_structure_contract")
    public static void schemaFieldsRetainProviderOfferTriggerAndDialogueBehavior(GameTestHelper helper) {
        var envelope = QuestResourceEnvelope.read(VillagerRetaliation.id("quests/schema_fields.json"), fixture()).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var compiled = QuestV2Compiler.compile(resource, envelope).orElseThrow();
        helper.assertTrue(compiled.provider().blocksHiring(), "provider hiring policy survives validation");
        helper.assertValueEqual(compiled.provider().offer().weight(), 7, "explicit offer weight retains alias precedence");
        helper.assertValueEqual(compiled.rules().maxActiveQuests(), 4, "active quest limit survives validation");
        helper.assertValueEqual(compiled.rules().maxActiveByTag().get("story"), 2, "tag limit survives validation");
        var trigger = compiled.triggers().getFirst().definition();
        helper.assertValueEqual(trigger.priority(), 3, "trigger priority survives validation");
        helper.assertValueEqual(trigger.chance(), 0.5D, "trigger chance survives validation");
        helper.assertValueEqual(trigger.weight(), 2, "trigger weight survives validation");
        helper.assertTrue(trigger.exclusive(), "exclusive trigger policy survives validation");
        var tree = QuestDialogueCompiler.compile(resource, envelope).trees().values().iterator().next();
        helper.assertValueEqual(tree.entries().getFirst().priority(), 3, "dialogue priority survives validation");
        helper.assertTrue(tree.nodes().values().iterator().next().end(), "explicit conversation ending survives validation");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_structure_contract")
    public static void unsupportedFieldsStillReportTheirAuthoredPointer(GameTestHelper helper) {
        var root = fixture();
        root.getAsJsonObject("availability").addProperty("unsupported_campaign_field", 1);
        var location = VillagerRetaliation.id("quests/schema_unknown.json");
        helper.assertTrue(QuestV2Parser.parse(location, root).isEmpty(), "unknown structural fields still reject the quest");
        helper.assertTrue(DatapackDiagnostics.recent().stream().anyMatch(entry ->
                        entry.diagnostic().jsonPointer().equals("/availability/unsupported_campaign_field")),
                "validation retains the original authored JSON pointer");
        helper.succeed();
    }

    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"villagerretaliation:schema_fields",
                 "provider":{"type":"villagerretaliation:villager","blocks_hiring":true},
                 "availability":{"weight":7,"selection_weight":2,"max_active_quests":4,"max_active_by_tag":{"story":2}},
                 "entry_stage":"started","stages":[{"id":"started","objectives":[{"id":"paper","type":"item_check","item":"minecraft:paper"}],
                   "dialogue":{"offer":{"lines":["Bring paper."],"priority":3,"end":true}}}],
                 "events":[{"id":"reminder","event":"player_tick","priority":3,"chance":0.5,"exclusive":true,"weight":2,
                   "actions":[{"type":"tracker"}]}]}
                """).getAsJsonObject();
    }
}
