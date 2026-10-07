package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignOutcomeConditionGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:outcome_conditions");

    private CampaignOutcomeConditionGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_outcome_conditions")
    public static void malformedOutcomeConditionsAreRejectedByQuestAndRuntimeParsing(GameTestHelper helper) {
        for (String invalid : List.of("{\"campaign\":\"missing namespace\"}", "{\"milestone\":true}", "{\"outcome\":null}",
                "{\"scope\":\"player\"}", "{\"scope\":null}", "{\"operation_id\":\"unexpected\"}")) {
            var condition = condition();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> condition.add(entry.getKey(), entry.getValue()));
            helper.assertTrue(CampaignOutcomePredicate.read(condition) == null, "runtime rejects malformed condition: " + invalid);
            helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(condition)).isEmpty(), "quest parser rejects malformed condition: " + invalid);
        }
        for (String required : List.of("campaign", "milestone", "outcome", "scope")) {
            var condition = condition();
            condition.remove(required);
            helper.assertTrue(CampaignOutcomePredicate.read(condition) == null, "scope and identities must be explicit: " + required);
            helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(condition)).isEmpty(), "quest parser requires " + required);
        }
        helper.assertTrue(QuestV2Parser.parse(CAMPAIGN, quest(condition())).isPresent(), "valid nested outcome condition compiles");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcome_conditions")
    public static void completedPersonalOutcomesDoNotRequireASharedWorldCommit(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        var definition = definition();
        var progress = new CampaignInstance.MilestoneProgress(CampaignInstance.Route.ORIGINAL, "refused", null, 1L, UUID.randomUUID(), 2L);
        var record = new CampaignInstance.PlayerRecord(2L, Set.of(), Map.of(), Map.of("pledge", progress), Set.of());
        var instance = new CampaignInstance(CAMPAIGN, UUID.randomUUID(), 1, "fixture", "test", 0L, 0L, Map.of(), Map.of(player, record));
        for (var scope : List.of(CampaignOutcomePredicate.Scope.COMPLETED)) {
            var predicate = new CampaignOutcomePredicate(CAMPAIGN, "pledge", "refused", scope);
            helper.assertTrue(predicate.evaluate(definition, instance, player).matches(), "personal outcome is available for " + scope);
            helper.assertFalse(predicate.evaluate(definition, instance, UUID.randomUUID()).matches(), "another player's personal outcome remains absent");
        }
        for (var scope : List.of(CampaignOutcomePredicate.Scope.WORLD, CampaignOutcomePredicate.Scope.DISCOVERED, CampaignOutcomePredicate.Scope.PARTICIPATED)) {
            var result = new CampaignOutcomePredicate(CAMPAIGN, "pledge", "refused", scope).evaluate(definition, instance, player);
            helper.assertFalse(result.matches(), "personal milestone has no shared outcome or participation");
            helper.assertFalse(result.diagnostic().isBlank(), "invalid authored scope is diagnosed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcome_conditions")
    public static void outcomeConditionsWithoutCampaignContextRemainUnavailable(GameTestHelper helper) {
        var predicate = CampaignOutcomePredicate.read(condition());
        var condition = new DialogueCondition.CampaignOutcome(predicate);
        helper.assertFalse(condition.matches(null), "missing context is unavailable");
        helper.assertFalse(new DialogueCondition.Not(condition).matches(null), "missing context cannot unlock negation");
        helper.assertFalse(predicate.evaluate(null).diagnostic().isBlank(), "missing campaign context explains unavailability");
        helper.succeed();
    }

    private static com.google.gson.JsonObject condition() {
        return JsonParser.parseString("""
                {"type":"campaign_outcome","campaign":"test:outcome_conditions","milestone":"pledge","outcome":"refused","scope":"completed"}
                """).getAsJsonObject();
    }

    private static com.google.gson.JsonObject quest(com.google.gson.JsonObject condition) {
        var root = JsonParser.parseString("""
                {"schema":"villagerretaliation:quest/v2","id":"test:outcome_conditions","localization_prefix":"quest.outcome_conditions.",
                 "provider":{"type":"villagerretaliation:villager"},"availability":{"conditions":[{"type":"not"}]},
                 "entry_stage":"begin","stages":[{"id":"begin","objectives":[]}]}
                """).getAsJsonObject();
        root.getAsJsonObject("availability").getAsJsonArray("conditions").get(0).getAsJsonObject().add("condition", condition);
        return root;
    }

    private static CampaignDefinition definition() {
        return new CampaignDefinition(CAMPAIGN, 1, "fixture", "campaign.outcome.title", "campaign.outcome.", List.of(CAMPAIGN),
                Map.of("opening", new CampaignDefinition.Chapter("opening", "campaign.outcome.opening", List.of(), List.of(CAMPAIGN), "")),
                Map.of("pledge", new CampaignDefinition.Milestone("pledge", "opening", CAMPAIGN, "", false,
                        Map.of("refused", new CampaignDefinition.Outcome("refused", "campaign.outcome.refused", "")), null, null, "")), Map.of(), CAMPAIGN);
    }
}
