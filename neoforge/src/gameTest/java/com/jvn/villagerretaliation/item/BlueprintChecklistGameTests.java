package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.recipe.BlueprintChecklistRecipe;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class BlueprintChecklistGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private BlueprintChecklistGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void inventoryCompletionIsStickyAndManualChecksAreReversible(GameTestHelper helper) {
        ItemStack checklist = BlueprintChecklistItem.create("Test Build", List.of(
                new BuilderStructureScanner.MaterialRequirement(new ItemStack(Items.OAK_PLANKS), 4),
                new BuilderStructureScanner.MaterialRequirement(new ItemStack(Items.COBBLESTONE), 2)));
        SimpleContainer inventory = new SimpleContainer(2);
        inventory.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
        inventory.setItem(1, new ItemStack(Items.COBBLESTONE, 1));

        helper.assertValueEqual(BlueprintChecklistItem.updateAgainst(checklist, inventory, true), 2,
                "both displayed counts should update");
        BlueprintChecklistItem.ChecklistData first = BlueprintChecklistItem.data(checklist);
        helper.assertTrue(first.entries().get(0).checked() && first.entries().get(0).autoSeen(),
                "a sufficient inventory count should complete the entry once");
        helper.assertFalse(first.entries().get(1).checked(), "an incomplete count should remain unchecked");

        inventory.clearContent();
        BlueprintChecklistItem.updateAgainst(checklist, inventory, true);
        BlueprintChecklistItem.ChecklistData removed = BlueprintChecklistItem.data(checklist);
        helper.assertTrue(removed.entries().get(0).checked(),
                "automatic completion must stay checked after items are removed");
        helper.assertValueEqual(removed.entries().get(0).observed(), 4,
                "later scans must not reconsider or rewrite completed entries");

        SimpleContainer container = new SimpleContainer(1);
        container.setItem(0, new ItemStack(Items.COBBLESTONE, 2));
        BlueprintChecklistItem.updateAgainst(checklist, container, true);
        BlueprintChecklistItem.ChecklistData containerScan = BlueprintChecklistItem.data(checklist);
        helper.assertValueEqual(containerScan.entries().get(1).observed(), 2,
                "a container scan should persist the current count for an incomplete entry");
        helper.assertTrue(containerScan.entries().get(1).checked(),
                "a sufficient container count should complete an unchecked entry");

        helper.assertTrue(BlueprintChecklistItem.toggle(checklist, 1),
                "a completed container entry should allow manual unchecking");
        helper.assertValueEqual(BlueprintChecklistItem.data(checklist).entries().get(1).observed(), 0,
                "manual unchecking should reset the displayed count");
        BlueprintChecklistItem.updateAgainst(checklist, container, true);
        helper.assertTrue(BlueprintChecklistItem.data(checklist).entries().get(1).checked(),
                "a qualifying scan must re-complete any currently unchecked entry");

        helper.assertTrue(BlueprintChecklistItem.toggle(checklist, 0), "manual uncheck should be accepted");
        helper.assertFalse(BlueprintChecklistItem.data(checklist).entries().get(0).checked(),
                "a manually unchecked automatic entry should remain unchecked without another qualifying scan");
        helper.assertValueEqual(BlueprintChecklistItem.data(checklist).entries().get(0).observed(), 0,
                "manual unchecking should clear a sticky completion count");
        BlueprintChecklistItem.updateAgainst(checklist, inventory, false);
        helper.assertFalse(BlueprintChecklistItem.data(checklist).entries().get(0).checked(),
                "an empty inventory must not undo the manual choice");
        helper.assertTrue(BlueprintChecklistItem.toggle(checklist, 0), "manual recheck should be accepted");
        helper.assertTrue(BlueprintChecklistItem.data(checklist).entries().get(0).checked(),
                "manual checks should be reversible in both directions");
        helper.assertValueEqual(BlueprintChecklistItem.data(checklist).entries().get(0).observed(), 4,
                "manual checking should set the displayed count to the required total");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void storedBlueprintStatesRebuildExactCarriedMaterials(GameTestHelper helper) {
        List<BuilderStructureScanner.MaterialRequirement> requirements =
                BuilderStructureScanner.materialRequirements(List.of(
                        Blocks.OAK_DOOR.defaultBlockState(),
                        Blocks.OAK_DOOR.defaultBlockState().setValue(
                                net.minecraft.world.level.block.DoorBlock.HALF,
                                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),
                        Blocks.FARMLAND.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()));

        int doors = count(requirements, Items.OAK_DOOR);
        int dirt = count(requirements, Items.DIRT);
        int stone = count(requirements, Items.STONE);
        helper.assertValueEqual(doors, 1, "a two-block door should require one door item");
        helper.assertValueEqual(dirt, 1, "farmland should require its carried dirt source");
        helper.assertValueEqual(stone, 1, "ordinary blocks should retain their item requirement");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void shapelessRecipeWritesMaterialsAndReturnsBlueprint(GameTestHelper helper) {
        BuilderStructureCatalog.Entry catalogEntry = new BuilderStructureCatalog.Entry(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("test_structure"),
                "Test",
                "Test Structure",
                0);
        BuilderStructureScanner.BuildBlock block = new BuilderStructureScanner.BuildBlock(
                BlockPos.ZERO,
                Blocks.OAK_PLANKS.defaultBlockState(),
                null,
                new ItemStack(Items.OAK_PLANKS),
                BuilderStructureScanner.BuilderToolAction.NONE);
        BuilderStructureScanner.StructurePlan plan = new BuilderStructureScanner.StructurePlan(
                catalogEntry,
                Rotation.NONE,
                new Vec3i(1, 1, 1),
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(block),
                List.of(new BuilderStructureScanner.MaterialRequirement(new ItemStack(Items.OAK_PLANKS), 1)),
                1);
        ItemStack blueprint = ConstructionBlueprintItem.create(
                helper.getLevel(), null, plan, BlockPos.ZERO, UUID.randomUUID(), 0, 1, "", 0L);
        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.BOOK), blueprint));
        BlueprintChecklistRecipe recipe = new BlueprintChecklistRecipe(CraftingBookCategory.MISC);

        helper.assertTrue(recipe.matches(input, helper.getLevel()),
                "one book and one configured construction blueprint should match in either position");
        ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(BlueprintChecklistItem.isChecklist(result), "the recipe should create a written checklist");
        BlueprintChecklistItem.ChecklistData data = BlueprintChecklistItem.data(result);
        helper.assertValueEqual(data.title(), catalogEntry.menuLabel(),
                "the blueprint's displayed structure name should become the book title");
        helper.assertValueEqual(data.entries().size(), 1, "all unique materials should be written to the book");
        helper.assertTrue(data.entries().getFirst().item().is(Items.OAK_PLANKS)
                        && data.entries().getFirst().required() == 1,
                "the written material entry should preserve item identity and count");
        helper.assertTrue(recipe.getRemainingItems(input).stream()
                        .anyMatch(VillagerRetaliationItems::isConstructionBlueprint),
                "crafting the checklist must return the active construction blueprint");
        helper.succeed();
    }

    private static int count(
            List<BuilderStructureScanner.MaterialRequirement> requirements,
            net.minecraft.world.item.Item item) {
        return requirements.stream()
                .filter(requirement -> requirement.item().is(item))
                .mapToInt(BuilderStructureScanner.MaterialRequirement::count)
                .sum();
    }
}
