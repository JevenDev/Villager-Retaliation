package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import com.jvn.villagerretaliation.scene.runtime.SceneContinuation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignFactAuthoringGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:fact_authoring");

    private CampaignFactAuthoringGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_fact_authoring")
    public static void malformedFactActionFieldsAreRejectedDuringQuestParsing(GameTestHelper helper) {
        for (String invalid : List.of("{\"fact\":true}", "{\"value\":null}", "{\"value\":2}",
                "{\"mutation\":\"multiply\"}", "{\"operation_id\":\"has spaces\"}", "{\"required\":false}")) {
            var root = quest();
            var action = root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("on_enter").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> action.add(entry.getKey(), entry.getValue()));
            helper.assertTrue(QuestV2Parser.parse(QUEST, root).isEmpty(), "malformed fact action rejects the quest: " + invalid);
        }
        helper.assertTrue(QuestV2Parser.parse(QUEST, quest()).isPresent(), "valid fact fields compile");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fact_authoring")
    public static void malformedFactConditionsAreRejectedInsideCompoundConditions(GameTestHelper helper) {
        for (String invalid : List.of("{\"campaign\":\"missing namespace\"}", "{\"fact\":null}", "{\"value\":false}",
                "{\"comparison\":\"guess\"}", "{\"scope\":\"world\"}")) {
            var root = quest();
            var condition = JsonParser.parseString("""
                    {"type":"campaign_fact","campaign":"test:campaign","fact":"clue","value":"true"}
                    """).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> condition.add(entry.getKey(), entry.getValue()));
            var nested = new com.google.gson.JsonObject();
            nested.addProperty("type", "not");
            nested.add("condition", condition);
            var conditions = new com.google.gson.JsonArray();
            conditions.add(nested);
            var availability = new com.google.gson.JsonObject();
            availability.add("conditions", conditions);
            root.add("availability", availability);
            helper.assertTrue(QuestV2Parser.parse(QUEST, root).isEmpty(), "malformed nested condition rejects the quest: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fact_authoring")
    public static void savedFactActionsKeepTypedFieldsAndPreserveCorruption(GameTestHelper helper) {
        var action = VillagerActionDefinition.readList(QUEST, "test", JsonParser.parseString("""
                {"actions":[{"type":"campaign_fact","fact":"clue","value":"true","operation_id":"discover"}]}
                """).getAsJsonObject(), QUEST).getFirst();
        var original = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QUEST, UUID.randomUUID(), "/fixture", List.of(action), 0, Map.of());
        var saved = original.save();
        helper.assertValueEqual(SceneContinuation.load(saved).actions().getFirst().campaignFact(), action.campaignFact(), "typed fact action survives scene storage");
        var damaged = saved.copy();
        damaged.getList("Actions", Tag.TAG_COMPOUND).getCompound(0).getCompound("CampaignFact").putInt("Value", 3);
        var restored = SceneContinuation.load(damaged);
        helper.assertTrue(restored.hasUnreadableActions(), "malformed saved fact prevents continuation execution");
        helper.assertTrue(restored.actions().getFirst().required(), "malformed action remains required");
        helper.assertValueEqual(restored.save().getList("Actions", Tag.TAG_COMPOUND), damaged.getList("Actions", Tag.TAG_COMPOUND), "raw malformed action remains available for repair");
        var legacy = JsonParser.parseString("""
                {"actions":[{"type":"campaign_fact","fact":"clue","value":"true"}]}
                """).getAsJsonObject();
        var invalid = VillagerActionDefinition.readList(QUEST, "test", legacy, QUEST).getFirst();
        helper.assertTrue(invalid.required() && invalid.campaignFact() == null, "invalid legacy action is retained as a required failure");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_fact_authoring")
    public static void factOperationIdsSeparateAuthoredPathsAndPlayerScopes(GameTestHelper helper) {
        UUID instance = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        UUID run = UUID.randomUUID();
        var first = new CampaignFactAction("clue", CampaignFacts.Mutation.SET, "true", "write", "first/second");
        var second = new CampaignFactAction("clue", CampaignFacts.Mutation.SET, "true", "write", "second");
        var original = CampaignFactService.operationId(instance, player, ResourceLocation.parse("test:quest"), run, first, true);
        var different = CampaignFactService.operationId(instance, player, ResourceLocation.parse("test:quest/first"), run, second, true);
        helper.assertFalse(original.equals(different), "slashes inside quest and milestone identities cannot collapse different operations");
        helper.assertValueEqual(original, CampaignFactService.operationId(instance, UUID.randomUUID(), ResourceLocation.parse("test:quest"),
                UUID.randomUUID(), first, true), "all original players share one world operation");
        var personal = CampaignFactService.operationId(instance, player, QUEST, run, first, false);
        helper.assertFalse(personal.equals(CampaignFactService.operationId(instance, UUID.randomUUID(), QUEST, run, first, false)), "personal operations remain player-owned");
        helper.assertFalse(personal.equals(CampaignFactService.operationId(instance, player, QUEST, UUID.randomUUID(), first, false)), "a new quest run has its own personal operation");
        helper.succeed();
    }

    private static com.google.gson.JsonObject quest() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:fact_authoring","localization_prefix":"quest.fact_authoring.",
                 "metadata":{"campaign":"test:campaign"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"begin","stages":[{"id":"begin","objectives":[],"on_enter":[
                  {"type":"campaign_fact","fact":"clue","value":"true","operation_id":"discover"}]}]}
                """).getAsJsonObject();
    }
}
