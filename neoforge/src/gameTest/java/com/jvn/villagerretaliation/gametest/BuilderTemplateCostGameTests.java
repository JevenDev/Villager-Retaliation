package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("builder_template_costs")
@PrefixGameTestTemplate(false)
public final class BuilderTemplateCostGameTests {
    private BuilderTemplateCostGameTests() { }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void exactCostsCountFurnitureStackedBlocksAndWater(GameTestHelper helper) {
        var requirements = BuilderStructureScanner.materialRequirements(List.of(
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER),
                Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER),
                Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT),
                Blocks.RED_BED.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD),
                Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE),
                Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true),
                Blocks.WHITE_CANDLE.defaultBlockState().setValue(BlockStateProperties.CANDLES, 4),
                Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 5),
                Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                Blocks.POTTED_POPPY.defaultBlockState()));
        helper.assertValueEqual(count(requirements, Items.OAK_DOOR), 1, "one door pays both halves");
        helper.assertValueEqual(count(requirements, Items.RED_BED), 1, "one bed pays both halves");
        helper.assertValueEqual(count(requirements, Items.OAK_SLAB), 3, "double slab costs two slabs");
        helper.assertValueEqual(count(requirements, Items.WATER_BUCKET), 2, "fluid and waterlogging are both funded");
        helper.assertValueEqual(count(requirements, Items.WHITE_CANDLE), 4, "stacked candles are not free");
        helper.assertValueEqual(count(requirements, Items.SNOW), 5, "snow costs every layer");
        helper.assertValueEqual(count(requirements, Items.FLOWER_POT), 1, "potted decoration funds the pot");
        helper.assertValueEqual(count(requirements, Items.POPPY), 1, "potted decoration funds the plant");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void simplifiedCostsAreExplicitAndMalformedBillsFailClosed(GameTestHelper helper) {
        var cost = BuilderConstructionCost.parse(JsonParser.parseString("""
                {"mode":"simplified","materials":[
                  {"item":"minecraft:oak_log","count":12},
                  {"tag":"minecraft:planks","count":24}]}
                """).getAsJsonObject());
        helper.assertValueEqual(cost.mode(), BuilderConstructionCost.Mode.SIMPLIFIED, "authored mode is retained");
        helper.assertTrue(cost.ingredients().getFirst().matches(new ItemStack(Items.OAK_LOG)), "explicit item matches");
        helper.assertTrue(cost.ingredients().get(1).matches(new ItemStack(Items.BIRCH_PLANKS)), "tag accepts alternatives");
        for (String invalid : List.of(
                "{\"mode\":\"simplified\",\"materials\":[]}",
                "{\"mode\":\"typo\"}",
                "{\"mode\":\"exact\",\"materials\":[]}",
                "{\"mode\":\"simplified\",\"materials\":[{\"item\":\"minecraft:stone\",\"count\":1.5}]}",
                "{\"mode\":\"simplified\",\"materials\":[{\"item\":\"minecraft:air\",\"count\":1}]}")) {
            boolean rejected = false;
            try { BuilderConstructionCost.parse(JsonParser.parseString(invalid).getAsJsonObject()); }
            catch (IllegalArgumentException expected) { rejected = true; }
            helper.assertTrue(rejected, "malformed construction bill is rejected: " + invalid);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void templateInventoryAndLootNeverEnterThePlan(GameTestHelper helper) throws Exception {
        CompoundTag treasure = TagParser.parseTag("""
                {Items:[{Slot:0b,id:"minecraft:diamond",count:64}],LootTable:"minecraft:chests/end_city_treasure",
                 Bees:[{entity_data:{id:"minecraft:bee"}}]}
                """);
        helper.assertTrue(BuilderStructureScanner.sanitizeBlockEntityTag(Blocks.CHEST.defaultBlockState(), treasure) == null,
                "container contents and loot are stripped");
        helper.assertTrue(BuilderStructureScanner.sanitizeBlockEntityTag(Blocks.BEEHIVE.defaultBlockState(), treasure) == null,
                "bees cannot be manufactured through block data");
        CompoundTag sign = TagParser.parseTag("{front_text:{messages:['\"Home\"']},Items:[{id:\"minecraft:diamond\",count:64}]}");
        var safeSign = BuilderStructureScanner.sanitizeBlockEntityTag(Blocks.OAK_SIGN.defaultBlockState(), sign);
        helper.assertTrue(safeSign != null && safeSign.contains("front_text") && !safeSign.contains("Items"),
                "cosmetic sign appearance remains while unrelated state is removed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void orphanFurnitureCannotBecomeFreeBlocks(GameTestHelper helper) throws Exception {
        var id = ResourceLocation.parse("villagerretaliation:test/orphan_bed_cost");
        var template = helper.getLevel().getStructureManager().getOrCreate(id);
        template.load(BuiltInRegistries.BLOCK.asLookup(), TagParser.parseTag("""
                {size:[1,1,1],palette:[{Name:"minecraft:red_bed",Properties:{part:"head",facing:"north"}}],
                 blocks:[{pos:[0,0,0],state:0}],entities:[]}
                """));
        BuilderStructureScanner.clearCache();
        helper.assertTrue(BuilderStructureScanner.scan(helper.getLevel(),
                new BuilderStructureCatalog.Entry(id, "Test", "Orphan", 0), Rotation.NONE).isEmpty(),
                "a secondary part without its paid primary part is rejected");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void scanRetainsAuthoredStatesAndRemovesContainerRewards(GameTestHelper helper) throws Exception {
        var id = ResourceLocation.parse("villagerretaliation:test/authored_state_cost");
        var template = helper.getLevel().getStructureManager().getOrCreate(id);
        template.load(BuiltInRegistries.BLOCK.asLookup(), TagParser.parseTag("""
                {size:[2,1,1],palette:[{Name:"minecraft:oak_slab",Properties:{type:"top",waterlogged:"true"}},
                 {Name:"minecraft:chest"}],blocks:[{pos:[0,0,0],state:0},{pos:[1,0,0],state:1,
                 nbt:{Items:[{Slot:0b,id:"minecraft:diamond",count:64}],LootTable:"minecraft:chests/end_city_treasure"}}],
                 entities:[]}
                """));
        BuilderStructureScanner.clearCache();
        var plan = BuilderStructureScanner.scan(helper.getLevel(),
                new BuilderStructureCatalog.Entry(id, "House", "Authored", 0), Rotation.CLOCKWISE_90).orElseThrow();
        var slab = plan.blocks().stream().filter(block -> block.state().is(Blocks.OAK_SLAB)).findFirst().orElseThrow();
        helper.assertValueEqual(slab.state().getValue(SlabBlock.TYPE), SlabType.TOP, "top slab appearance is retained");
        helper.assertTrue(slab.state().getValue(BlockStateProperties.WATERLOGGED), "authored waterlogging remains");
        helper.assertValueEqual(count(plan.materials(), Items.WATER_BUCKET), 1, "waterlogged output is funded");
        helper.assertTrue(plan.blocks().stream().allMatch(block -> block.blockEntityTag() == null),
                "stored diamonds and loot tables never become placement data");
        boolean rejected = false;
        try { BuilderStructureScanner.exactBlockMaterials(Blocks.END_PORTAL.defaultBlockState()); }
        catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "unsupported itemless output fails closed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void reloadIdentityRejectsSameSizeValuableSubstitutionAndChangedBill(GameTestHelper helper) throws Exception {
        var id = ResourceLocation.parse("villagerretaliation:test/reload_identity");
        var cost = new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED,
                List.of(new BuilderConstructionCost.Ingredient(ResourceLocation.parse("minecraft:dirt"), false, 4)));
        var entry = new BuilderStructureCatalog.Entry(id, "House", "Small", 0, cost, "small");
        var original = oneBlockPlan(entry, Blocks.STONE.defaultBlockState(), null);
        var valuableReload = oneBlockPlan(entry, Blocks.DIAMOND_BLOCK.defaultBlockState(), null);
        helper.assertValueEqual(original.blocks().size(), valuableReload.blocks().size(), "reload keeps the old superficial block count");
        helper.assertValueEqual(original.localMax(), valuableReload.localMax(), "reload keeps the old superficial bounds");
        String frozen = BuilderStructureScanner.fingerprint(original);
        helper.assertTrue(!frozen.equals(BuilderStructureScanner.fingerprint(valuableReload)),
                "a cheap frozen bill cannot authorize valuable replacement output");
        var increased = new BuilderStructureCatalog.Entry(id, "House", "Small", 0,
                new BuilderConstructionCost(BuilderConstructionCost.Mode.SIMPLIFIED,
                        List.of(new BuilderConstructionCost.Ingredient(ResourceLocation.parse("minecraft:dirt"), false, 5))), "small");
        helper.assertTrue(!frozen.equals(BuilderStructureScanner.fingerprint(oneBlockPlan(increased, Blocks.STONE.defaultBlockState(), null))),
                "a changed authored quote invalidates pending approval too");
        var signA = TagParser.parseTag("{front_text:{color:\"black\",has_glowing_text:0b}}");
        var signB = TagParser.parseTag("{front_text:{has_glowing_text:0b,color:\"black\"}}");
        helper.assertValueEqual(BuilderStructureScanner.fingerprint(oneBlockPlan(entry, Blocks.OAK_SIGN.defaultBlockState(), signA)),
                BuilderStructureScanner.fingerprint(oneBlockPlan(entry, Blocks.OAK_SIGN.defaultBlockState(), signB)),
                "equivalent NBT key ordering survives reload");
        signB.getCompound("front_text").putString("color", "red");
        helper.assertTrue(!BuilderStructureScanner.fingerprint(oneBlockPlan(entry, Blocks.OAK_SIGN.defaultBlockState(), signA)).equals(
                        BuilderStructureScanner.fingerprint(oneBlockPlan(entry, Blocks.OAK_SIGN.defaultBlockState(), signB))),
                "changed authored block-entity appearance changes identity");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "builder_template_costs")
    public static void cauldronContentsAndPowderSnowHaveExplicitCosts(GameTestHelper helper) {
        var requirements = BuilderStructureScanner.materialRequirements(List.of(
                Blocks.WATER_CAULDRON.defaultBlockState(), Blocks.LAVA_CAULDRON.defaultBlockState(),
                Blocks.POWDER_SNOW_CAULDRON.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState()));
        helper.assertValueEqual(count(requirements, Items.CAULDRON), 3, "each filled vessel pays its cauldron");
        helper.assertValueEqual(count(requirements, Items.WATER_BUCKET), 1, "water contents are funded even without a fluid state");
        helper.assertValueEqual(count(requirements, Items.LAVA_BUCKET), 1, "lava contents cannot become free lava");
        helper.assertValueEqual(count(requirements, Items.POWDER_SNOW_BUCKET), 2, "powder snow in a vessel and block are funded");
        boolean rejected = false;
        try {
            BuilderStructureScanner.exactBlockMaterials(Blocks.COMPOSTER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ComposterBlock.LEVEL, 8));
        } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "ready compost cannot grant free bone meal");
        helper.succeed();
    }

    private static BuilderStructureScanner.StructurePlan oneBlockPlan(BuilderStructureCatalog.Entry entry,
            net.minecraft.world.level.block.state.BlockState state, CompoundTag data) {
        var block = new BuilderStructureScanner.BuildBlock(BlockPos.ZERO, state, data, ItemStack.EMPTY,
                BuilderStructureScanner.BuilderToolAction.NONE);
        return new BuilderStructureScanner.StructurePlan(entry, Rotation.NONE, new Vec3i(1, 1, 1),
                BlockPos.ZERO, BlockPos.ZERO, List.of(block), BuilderStructureScanner.materialRequirements(List.of(state)), 1);
    }

    private static int count(List<BuilderStructureScanner.MaterialRequirement> requirements, Item item) {
        return requirements.stream().filter(value -> value.item().is(item))
                .mapToInt(BuilderStructureScanner.MaterialRequirement::count).sum();
    }
}
