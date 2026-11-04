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
    public static void unknownBlocksAndPropertiesCannotCompile(GameTestHelper helper) throws Exception {
        var original = CampaignCharacterTestFixture.resource("campaigns/variants/campaign.json");
        for (String state : java.util.List.of("missing:block", "minecraft:stone[missing=true]")) {
            var root = original.deepCopy();
            root.getAsJsonArray("location_variants").get(0).getAsJsonObject().getAsJsonArray("cells")
                    .get(0).getAsJsonObject().addProperty("placed", state);
            var result = CampaignDefinitionParser.parse(SOURCE, root);
            helper.assertFalse(result.valid(), "unknown block state cannot compile: " + state);
            helper.assertTrue(result.diagnostics().stream().anyMatch(issue -> issue.resourceId().equals(SOURCE)
                            && issue.jsonPointer().equals("/location_variants/0/cells/0/placed")),
                    "block state error retains its source location: " + result.diagnostics());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_variant_authoring")
    public static void schemaEnforcesVariantBoundsAndKnownFields(GameTestHelper helper) throws Exception {
        var original = CampaignCharacterTestFixture.resource("campaigns/variants/campaign.json");
        for (String mutation : java.util.List.of("{\"layout_revision\":0}", "{\"unknown\":true}", "{\"cells\":[]}", "{\"location\":false}")) {
            var root = original.deepCopy();
            var variant = root.getAsJsonArray("location_variants").get(0).getAsJsonObject();
            JsonParser.parseString(mutation).getAsJsonObject().entrySet().forEach(entry -> variant.add(entry.getKey(), entry.getValue()));
            helper.assertFalse(CampaignSchema.validate(SOURCE, root).isEmpty(), "schema rejects " + mutation);
        }
        var root = original.deepCopy();
        var cells = root.getAsJsonArray("location_variants").get(0).getAsJsonObject().getAsJsonArray("cells");
        while (cells.size() <= 256) cells.add(cells.get(0).deepCopy());
        helper.assertFalse(CampaignSchema.validate(SOURCE, root).isEmpty(), "variant footprint has a hard authoring bound");
        helper.succeed();
    }
}
