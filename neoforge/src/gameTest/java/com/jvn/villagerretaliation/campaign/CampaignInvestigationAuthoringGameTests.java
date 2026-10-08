package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import com.jvn.villagerretaliation.quest.content.bundle.QuestLocaleCatalog;
import java.io.IOException;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignInvestigationAuthoringGameTests {
    private CampaignInvestigationAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_investigation_authoring")
    public static void evidenceSchemaRequiresOwnedLocalizedAndBoundedSources(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var raw = fixture.base.raw();
            var id = fixture.base.campaignId;
            helper.assertTrue(CampaignDefinitionParser.parse(id, raw).valid(), "authored evidence compiles");
            for (String required : List.of("id", "title_key", "text_key", "source", "location", "entity_tag")) {
                var missing = raw.deepCopy();
                missing.getAsJsonArray("evidence").get(0).getAsJsonObject().remove(required);
                helper.assertFalse(CampaignSchema.validate(id, missing).isEmpty(), "schema requires " + required);
                helper.assertFalse(CampaignDefinitionParser.parse(id, missing).valid(), "runtime requires " + required);
            }
            for (String invalid : List.of("{\"source\":\"block\"}", "{\"entity_tag\":true}", "{\"entity_tag\":\"bad tag\"}",
                    "{\"search_radius\":0}", "{\"search_radius\":33}", "{\"search_radius\":1.5}", "{\"unexpected\":true}")) {
                var changed = raw.deepCopy();
                var evidence = changed.getAsJsonArray("evidence").get(0).getAsJsonObject();
                JsonParser.parseString(invalid).getAsJsonObject().entrySet().forEach(entry -> evidence.add(entry.getKey(), entry.getValue()));
                helper.assertFalse(CampaignSchema.validate(id, changed).isEmpty(), "exported schema rejects " + invalid);
                helper.assertFalse(CampaignDefinitionParser.parse(id, changed).valid(), "runtime rejects " + invalid);
            }
            for (String field : List.of("location", "title_key", "text_key")) {
                var changed = raw.deepCopy();
                changed.getAsJsonArray("evidence").get(0).getAsJsonObject().addProperty(field, "other.missing");
                var parsed = CampaignDefinitionParser.parse(id, changed);
                helper.assertFalse(parsed.valid(), "unowned or unresolved reference rejected");
                helper.assertTrue(parsed.diagnostics().stream().anyMatch(value -> value.toString().contains("/evidence/0/" + field)),
                        "diagnostic retains evidence source pointer");
            }
            var duplicate = raw.deepCopy();
            duplicate.getAsJsonArray("evidence").add(duplicate.getAsJsonArray("evidence").get(0).deepCopy());
            helper.assertFalse(CampaignDefinitionParser.parse(id, duplicate).valid(), "duplicate source IDs rejected");
            var defaults = raw.deepCopy();
            defaults.getAsJsonArray("evidence").get(0).getAsJsonObject().remove("search_radius");
            helper.assertValueEqual(CampaignDefinitionParser.parse(id, defaults).definition().evidence().get("ledger").searchRadius(),
                    8, "omitted radius uses bounded default");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_investigation_authoring")
    public static void investigationReferencesAndReadableEvidenceAreValidated(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignInvestigationTestFixture(helper)) {
            var campaign = fixture.base.campaigns.campaigns().get(fixture.base.campaignId).definition();
            var quest = fixture.base.catalog.quest(fixture.base.campaignId).orElseThrow();
            var story = (CampaignStoryObjective.Investigate) quest.objectives().getFirst().story();
            helper.assertValueEqual(story.evidence(), "ledger", "compiled objective retains authored evidence identity");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(story, campaign, fixture.base.catalog, quest.id()).isEmpty(),
                    "matching evidence reference compiles");
            helper.assertTrue(CampaignStoryObjective.referenceIssues(new CampaignStoryObjective.Investigate("missing"),
                    campaign, fixture.base.catalog, quest.id()).containsKey("evidence"), "missing evidence is diagnosed");
            var raw = fixture.base.raw();
            raw.getAsJsonArray("evidence").get(0).getAsJsonObject().addProperty("id", "renamed");
            var changed = CampaignDefinitionParser.parse(fixture.base.campaignId, raw).definition();
            helper.assertTrue(CampaignDefinitionParser.validateQuestReferences(changed, fixture.base.catalog).stream()
                    .anyMatch(value -> value.toString().contains("/stages/0/objectives/0/story/evidence")), "quest diagnostic retains authored pointer");
            for (String field : List.of("title_key", "text_key")) {
                var locales = CampaignCharacterTestFixture.resource("campaigns/investigation/locales/en_us.json");
                String key = fixture.base.raw().getAsJsonArray("evidence").get(0).getAsJsonObject().get(field).getAsString();
                locales.getAsJsonObject("messages").remove(key);
                var messages = QuestLocaleCatalog.read(locales);
                var result = CampaignContentCatalog.compile(fixture.base.catalog, List.of(new CampaignContentCatalog.Source(
                        campaign.source(), "campaign_fixture", fixture.base.raw(),
                        QuestLocaleCatalog.empty().overlay("en_us", messages.messages()), List.of())));
                helper.assertTrue(result.diagnostics().stream().anyMatch(value -> value.toString().contains("/evidence/0/" + field)),
                        "missing readable evidence has a source pointer");
            }
        }
        helper.succeed();
    }
}
