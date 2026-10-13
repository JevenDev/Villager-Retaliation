package com.jvn.villagerretaliation.interaction.work.mining;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ore_nodes")
@PrefixGameTestTemplate(false)
public final class OreNodeDefinitionGameTests {
    private OreNodeDefinitionGameTests() { }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void explicitBlocksAndModdedIdentifiersDoNotRequireAnOreName(GameTestHelper helper) {
        var definition = OreNodeDefinitions.parse(ResourceLocation.parse("fixture:custom"), GsonHelper.parse("""
                {"blocks":["minecraft:emerald_block","examplemod:tin_deposit"],"extractions":7,"priority":30}
                """));
        helper.assertTrue(definition.matches(Blocks.EMERALD_BLOCK.defaultBlockState()), "explicit block works without ore tag or suffix");
        helper.assertTrue(definition.blocks().contains(ResourceLocation.parse("examplemod:tin_deposit")), "custom namespace retained");
        helper.assertValueEqual(definition.extractions(), 7, "custom extraction count retained");
        helper.assertValueEqual(definition.priority(), 30, "custom priority retained");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "ore_nodes")
    public static void invalidExtractionCountsFailDefinitionLoading(GameTestHelper helper) {
        for (String value : java.util.List.of("0", "-1", "1.5", "100001", "4294967297")) {
            boolean rejected = false;
            try {
                OreNodeDefinitions.parse(ResourceLocation.parse("fixture:invalid"),
                        GsonHelper.parse("{\"tags\":[\"c:ores\"],\"extractions\":" + value + "}"));
            } catch (IllegalArgumentException exception) {
                rejected = true;
            }
            helper.assertTrue(rejected, "invalid count rejected: " + value);
        }
        helper.succeed();
    }
}
