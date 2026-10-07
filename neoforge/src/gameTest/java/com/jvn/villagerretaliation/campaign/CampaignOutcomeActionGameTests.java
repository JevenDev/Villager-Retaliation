package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionStatus;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.quest.schema.QuestResourceEnvelope;
import com.jvn.villagerretaliation.quest.schema.v2.QuestV2Parser;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignOutcomeActionGameTests {
    private CampaignOutcomeActionGameTests() {
    }

    @GameTest(template = "empty", batch = "campaign_outcome_actions")
    public static void campaignOutcomeFieldsRejectInvalidAuthoredActions(GameTestHelper helper) {
        for (String invalid : new String[] {"{\"operation_id\":false}", "{\"operation_id\":\"has spaces\"}",
                "{\"milestone\":null}", "{\"outcome\":12}", "{\"required\":false}"}) {
            var root = JsonParser.parseString("""
                    {"schema":"villagerretaliation:quest/v2","id":"test:outcome","localization_prefix":"quest.outcome.",
                     "metadata":{"campaign":"test:campaign"},"provider":{"type":"villagerretaliation:villager"},
                     "entry_stage":"choose","stages":[{"id":"choose","objectives":[],"on_enter":[
                      {"type":"campaign_outcome","milestone":"dragon","outcome":"spared","operation_id":"decide"}]}]}
                    """).getAsJsonObject();
            var action = root.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonArray("on_enter").get(0).getAsJsonObject();
            JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> action.add(entry.getKey(), entry.getValue()));
            var envelope = QuestResourceEnvelope.read(ResourceLocation.parse("test:outcome"), root).orElseThrow();
            helper.assertTrue(QuestV2Parser.parse(envelope).isEmpty(), "invalid action cannot compile with its decision silently removed: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_outcome_actions")
    public static void malformedLegacyOutcomeRetainsItsRequiredFailureBoundary(GameTestHelper helper) {
        var raw = JsonParser.parseString("""
                {"actions":[{"type":"campaign_outcome","milestone":"dragon","outcome":"spared"},
                 {"type":"experience","amount":13}]}
                """).getAsJsonObject();
        var actions = VillagerActionDefinition.readList(ResourceLocation.parse("test:outcome"), "test", raw);
        helper.assertValueEqual(actions.size(), 2, "legacy boundary keeps the malformed action instead of silently dropping it");
        helper.assertTrue(actions.getFirst().required(), "campaign outcomes always stop following effects on failure");
        helper.assertValueEqual(VillagerActionRegistry.execute(null, actions.getFirst(), Map.of()).status(), ActionStatus.FAILED,
                "missing context cannot apply a decision");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "campaign_outcome_actions")
    public static void legacyQuestConsentLoadsWithoutInventingDecisionApproval(GameTestHelper helper) {
        var acceptance = new CampaignQuestAcceptance(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "fingerprint", 0L, 1L, Map.of());
        var quest = ResourceLocation.parse("test:original");
        var original = new CampaignQuestConsent(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                Map.of(quest, new CampaignQuestConsent.Entry(acceptance, new net.minecraft.nbt.CompoundTag())));
        var saved = original.save();
        saved.putInt("Version", 1);
        saved.remove("Decisions");
        var restored = CampaignQuestConsent.load(saved);
        helper.assertValueEqual(restored, original, "version one quest approvals survive the decision extension");
        helper.assertTrue(restored.decisions().isEmpty(), "legacy approvals cannot authorize a later choice");
        helper.succeed();
    }

}
