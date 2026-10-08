package com.jvn.villagerretaliation.campaign;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.campaign.content.CampaignDefinitionParser;
import com.jvn.villagerretaliation.campaign.content.CampaignSchema;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_content")
@PrefixGameTestTemplate(false)
public final class CampaignVariantAuthoringGameTests {
    private static final ResourceLocation SOURCE = ResourceLocation.parse("campaign_fixture:campaigns/variants/campaign.json");
    private CampaignVariantAuthoringGameTests() { }

    @GameTest(template = "empty", batch = "campaign_variant_authoring")
    public static void variantFixtureCompilesIntoOrderedTypedDefinitions(GameTestHelper helper) throws Exception {
        var root = CampaignCharacterTestFixture.resource("campaigns/variants/campaign.json");
        helper.assertTrue(CampaignSchema.validate(SOURCE, root).isEmpty(), "fixture matches the exported runtime schema");
        var parsed = CampaignDefinitionParser.parse(SOURCE, root);
        helper.assertTrue(parsed.valid(), "fixture compiles: " + parsed.diagnostics());
        var variant = parsed.definition().locationVariants().get("sealed");
        helper.assertValueEqual(variant.cells().stream().map(value -> value.id()).toList(), java.util.List.of("floor", "entry"), "authored cell ordering is preserved");
        helper.assertValueEqual(variant.cells().get(1).pointer(), "/location_variants/1/cells/1", "diagnostics retain the authored source pointer");
        helper.assertValueEqual(parsed.definition().milestones().get("gate").outcomes().get("sealed").locationVariants(), java.util.List.of("sealed"), "outcome keeps its explicit variant reference");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_authoring")
    public static void invalidStatesReferencesAndDuplicateCellsCannotCompile(GameTestHelper helper) throws Exception {
        var original = CampaignCharacterTestFixture.resource("campaigns/variants/campaign.json");
        for (int index = 0; index < 7; index++) {
            var root = original.deepCopy();
            var variant = root.getAsJsonArray("location_variants").get(0).getAsJsonObject();
            switch (index) {
                case 0 -> variant.addProperty("location", "missing");
                case 1 -> variant.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("placed", "missing:block");
                case 2 -> variant.getAsJsonArray("cells").get(0).getAsJsonObject().addProperty("placed", "minecraft:stone[missing=true]");
                case 3 -> variant.getAsJsonArray("cells").add(variant.getAsJsonArray("cells").get(0).deepCopy());
                case 4 -> root.getAsJsonArray("milestones").get(0).getAsJsonObject().getAsJsonArray("outcomes")
                        .get(0).getAsJsonObject().add("location_variants", JsonParser.parseString("[\"unknown\"]"));
                case 5 -> root.getAsJsonArray("milestones").get(0).getAsJsonObject().addProperty("irreversible", false);
                case 6 -> root.getAsJsonArray("milestones").get(0).getAsJsonObject().getAsJsonArray("outcomes")
                        .get(0).getAsJsonObject().add("location_variants", JsonParser.parseString("[\"open\",\"open\"]"));
                default -> throw new AssertionError();
            }
            var result = CampaignDefinitionParser.parse(SOURCE, root);
            helper.assertFalse(result.valid(), "invalid authored change cannot compile: " + index);
            helper.assertTrue(result.diagnostics().stream().anyMatch(issue -> issue.jsonPointer().contains("location_variants")),
                    "variant error retains its source location: " + index);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_authoring")
    public static void schemaAndRuntimeEnforceVariantBoundsAndKnownFields(GameTestHelper helper) throws Exception {
        var original = CampaignCharacterTestFixture.resource("campaigns/variants/campaign.json");
        for (String mutation : java.util.List.of("{\"layout_revision\":0}", "{\"unknown\":true}", "{\"cells\":[]}", "{\"location\":false}")) {
            var root = original.deepCopy();
            var variant = root.getAsJsonArray("location_variants").get(0).getAsJsonObject();
            JsonParser.parseString(mutation).getAsJsonObject().entrySet().forEach(entry -> variant.add(entry.getKey(), entry.getValue()));
            helper.assertFalse(CampaignSchema.validate(SOURCE, root).isEmpty(), "schema rejects " + mutation);
            helper.assertFalse(CampaignDefinitionParser.parse(SOURCE, root).valid(), "runtime rejects " + mutation);
        }
        var root = original.deepCopy();
        var cells = root.getAsJsonArray("location_variants").get(0).getAsJsonObject().getAsJsonArray("cells");
        while (cells.size() <= 256) cells.add(cells.get(0).deepCopy());
        helper.assertFalse(CampaignSchema.validate(SOURCE, root).isEmpty(), "variant footprint has a hard authoring bound");
        helper.succeed();
    }
}
