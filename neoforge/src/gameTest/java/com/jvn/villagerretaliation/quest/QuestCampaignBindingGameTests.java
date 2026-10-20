package com.jvn.villagerretaliation.quest;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.jvn.villagerretaliation.campaign.content.CampaignQuestReference;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Schema;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class QuestCampaignBindingGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:campaign_quest");
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:journey");

    private QuestCampaignBindingGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_quest_binding")
    public static void typedCompilationAndCompatibilityViewsRetainCampaignOwnership(GameTestHelper helper) {
        var root = fixture();
        root.getAsJsonObject("metadata").addProperty("campaign", CAMPAIGN.toString());
        var envelope = QuestResourceEnvelope.read(QUEST, root).orElseThrow();
        var resource = QuestV2Parser.parse(envelope).orElseThrow();
        var compiled = QuestV2Compiler.compile(resource, envelope).orElseThrow();
        root.getAsJsonObject("metadata").addProperty("campaign", "test:changed");
        helper.assertValueEqual(compiled.metadata().campaign(), CAMPAIGN, "compiled ownership is typed and detached from authoring JSON");
        helper.assertValueEqual(compiled.asQuestDefinition().campaign(), CAMPAIGN, "legacy boundary preserves campaign identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_binding")
    public static void omittedCampaignReferencesKeepOrdinaryQuestBehavior(GameTestHelper helper) {
        var root = fixture();
        var envelope = QuestResourceEnvelope.read(QUEST, root).orElseThrow();
        var compiled = QuestV2Compiler.compile(QuestV2Parser.parse(envelope).orElseThrow(), envelope).orElseThrow();
        helper.assertTrue(compiled.metadata().campaign() == null && compiled.asQuestDefinition().campaign() == null,
                "ordinary content is never silently adopted by a campaign");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_binding")
    public static void invalidCampaignReferencesAreRejectedByTheRuntimeDescriptor(GameTestHelper helper) {
        for (var invalid : List.of(JsonNull.INSTANCE, new JsonPrimitive(7), new JsonPrimitive(true),
                new JsonPrimitive(""), new JsonPrimitive("journey"), new JsonPrimitive("test:Bad"),
                new JsonPrimitive("test:" + "x".repeat(257)), new JsonObject())) {
            var root = fixture();
            root.getAsJsonObject("metadata").add("campaign", invalid);
            helper.assertTrue(QuestV2Parser.parse(QUEST, root).isEmpty(), "malformed reference cannot become an ordinary quest: " + invalid);
        }
        helper.assertValueEqual(QuestV2Schema.export().getAsJsonObject("$defs").getAsJsonObject("metadata")
                        .getAsJsonObject("properties").getAsJsonObject("campaign"), CampaignQuestReference.schema(),
                "exported schema uses the runtime descriptor");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_quest_binding")
    public static void malformedStoryObjectivesCannotDisappearFromLegacyQuests(GameTestHelper helper) {
        for (String type : List.of("campaign_speak", "campaign_outcome")) {
            String fields = type.equals("campaign_speak")
                    ? "{\"role\":\"mentor\",\"conversation\":\"test:mentor\",\"node\":\"welcome\"}"
                    : "{\"milestone\":\"decision\",\"outcome\":\"help\",\"scope\":\"world\"}";
            var objective = JsonParser.parseString("{\"id\":\"story\",\"type\":\"" + type + "\",\"story\":" + fields + "}").getAsJsonObject();
            var root = JsonParser.parseString("{\"id\":\"test:campaign_quest\",\"campaign\":\"test:journey\",\"objectives\":[]}").getAsJsonObject();
            root.getAsJsonArray("objectives").add(objective);
            helper.assertTrue(QuestDefinitionFixtures.read(QUEST, root, QUEST) != null, "valid legacy story objective loads");
            objective.addProperty("item", "minecraft:paper");
            helper.assertTrue(QuestDefinitionFixtures.read(QUEST, root, QUEST) == null, "invalid selector rejects the entire legacy quest");
            objective.remove("item");
            objective.remove("type");
            helper.assertTrue(QuestDefinitionFixtures.read(QUEST, root, QUEST) == null, "missing story type rejects the entire quest");
            objective.addProperty("type", "missing");
            helper.assertTrue(QuestDefinitionFixtures.read(QUEST, root, QUEST) == null, "unknown story type rejects the entire quest");
            objective.addProperty("type", type);
            objective.remove("story");
            helper.assertTrue(QuestDefinitionFixtures.read(QUEST, root, QUEST) == null, "missing story cannot silently disappear");
        }
        helper.succeed();
    }

    private static JsonObject fixture() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:campaign_quest",
                 "localization_prefix":"quest.campaign_fixture.","metadata":{},
                 "provider":{"type":"villagerretaliation:villager"},"entry_stage":"offer",
                 "stages":[{"id":"offer","objectives":[{"id":"choose","type":"choice"}]}]}
                """).getAsJsonObject();
    }
}
