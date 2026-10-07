package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
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
public final class CampaignRewardAuthoringGameTests {
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:reward_authoring");

    private CampaignRewardAuthoringGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_reward_authoring")
    public static void malformedRewardActionsAreRequiredFailuresAndRejectQuestModules(GameTestHelper helper) {
        for (String invalid : List.of("{\"reward\":true}", "{\"reward\":null}", "{\"operation_id\":\"has spaces\"}", "{\"required\":false}")) {
            var root = quest();
            var action = root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("on_enter").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> action.add(entry.getKey(), entry.getValue()));
            helper.assertTrue(QuestV2Parser.parse(QUEST, root).isEmpty(), "malformed reward rejects the quest module: " + invalid);
            var legacy = new com.google.gson.JsonObject();
            var actions = new com.google.gson.JsonArray();
            actions.add(action);
            legacy.add("actions", actions);
            var retained = VillagerActionDefinition.readList(QUEST, "test", legacy, QUEST).getFirst();
            helper.assertTrue(retained.required() && retained.campaignReward() == null, "invalid legacy reward remains a required failure");
        }
        helper.assertTrue(QuestV2Parser.parse(QUEST, quest()).isPresent(), "valid reward fields compile");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_authoring")
    public static void savedRewardActionsRetainFieldsAndPreserveCorruptData(GameTestHelper helper) {
        var action = VillagerActionDefinition.readList(QUEST, "test", JsonParser.parseString("""
                {"actions":[{"type":"campaign_reward","reward":"catch_up","operation_id":"claim"}]}
                """).getAsJsonObject(), QUEST).getFirst();
        var original = new SceneContinuation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                QUEST, UUID.randomUUID(), "/fixture", List.of(action), 0, Map.of());
        var saved = original.save();
        helper.assertValueEqual(SceneContinuation.load(saved).actions().getFirst(), action, "typed reward and owning quest survive scene storage");
        saved.getList("Actions", Tag.TAG_COMPOUND).getCompound(0).getCompound("CampaignReward").putInt("Reward", 3);
        var restored = SceneContinuation.load(saved);
        helper.assertTrue(restored.hasUnreadableActions() && restored.actions().getFirst().required(), "malformed saved reward blocks continuation");
        helper.assertValueEqual(restored.save().getList("Actions", Tag.TAG_COMPOUND), saved.getList("Actions", Tag.TAG_COMPOUND), "raw malformed reward remains available for repair");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_reward_authoring")
    public static void rewardsRequireValidOwnershipScopesAmountsAndMilestones(GameTestHelper helper) {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:campaign/v1","id":"test:campaign","revision":1,
                 "title_key":"campaign.test.title","localization_prefix":"campaign.test.",
                 "quest_bundles":["test:reward_authoring","test:catch_up"],
                 "chapters":[{"id":"opening","title_key":"campaign.test.title","quests":["test:reward_authoring"]}],
                 "milestones":[{"id":"decision","chapter":"opening","quest":"test:reward_authoring",
                    "outcomes":[{"id":"chosen","title_key":"campaign.test.title"}],"catch_up":{"quest":"test:catch_up","stage":"learn"}}],
                 "rewards":[{"id":"reward","quest":"test:reward_authoring","scope":"participants","milestone":"decision","experience":10}]}
                """).getAsJsonObject();
        helper.assertTrue(CampaignDefinitionParser.parse(QUEST, root).valid(), "valid participation reward compiles");
        for (String invalid : List.of("{\"scope\":\"everyone\"}", "{\"scope\":\"personal\"}", "{\"experience\":-1}", "{\"experience\":0}",
                "{\"experience\":1.5}", "{\"experience\":2147483648}", "{\"quest\":\"test:missing\"}", "{\"milestone\":\"missing\"}", "{\"outcome\":\"missing\"}")) {
            var candidate = root.deepCopy();
            var reward = candidate.getAsJsonArray("rewards").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> reward.add(entry.getKey(), entry.getValue()));
            var parsed = CampaignDefinitionParser.parse(QUEST, candidate);
            helper.assertFalse(parsed.valid(), "invalid reward is rejected: " + invalid);
            helper.assertTrue(parsed.diagnostics().stream().anyMatch(value -> value.jsonPointer().startsWith("/rewards/0")), "reward diagnostic retains authored pointer");
        }
        helper.succeed();
    }

    private static com.google.gson.JsonObject quest() {
        return JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:reward_authoring","localization_prefix":"quest.reward_authoring.",
                 "metadata":{"campaign":"test:campaign"},"provider":{"type":"villagerretaliation:villager"},
                 "entry_stage":"begin","stages":[{"id":"begin","objectives":[],"on_enter":[
                  {"type":"campaign_reward","reward":"catch_up","operation_id":"claim"}]}]}
                """).getAsJsonObject();
    }
}
