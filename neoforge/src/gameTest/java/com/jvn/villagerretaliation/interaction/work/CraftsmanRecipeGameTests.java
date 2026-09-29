package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("villagerretaliation")
@PrefixGameTestTemplate(false)
public final class CraftsmanRecipeGameTests {
    private CraftsmanRecipeGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void craftsmanPreservesNarrowedIngredientsThroughNativeExecution(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var level = helper.getLevel();
            var recipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.FIRE_CHARGE))
                    .findFirst().orElseThrow();
            int slot = -1;
            for (int index = 0; index < recipe.value().getIngredients().size(); index++) {
                if (recipe.value().getIngredients().get(index).test(new ItemStack(Items.CHARCOAL))) slot = index;
            }
            helper.assertTrue(slot >= 0, "the recipe has a charcoal alternative");
            var filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, level, recipe.id()), "the exact recipe is selected");
            helper.assertTrue(VillagerRecipeFilterData.setIngredient(filter, level, slot, BuiltInRegistries.ITEM.getKey(Items.CHARCOAL)),
                    "the ingredient is narrowed to charcoal");
            fixture.context().inventory().setItem(HiredJobInventory.FILTER_SLOT, filter);
            fixture.context().inventory().insertSupply(new ItemStack(Items.BLAZE_POWDER));
            fixture.context().inventory().insertSupply(new ItemStack(Items.GUNPOWDER));
            fixture.context().inventory().insertSupply(new ItemStack(Items.COAL));
            tick(helper, fixture, 20);
            helper.assertValueEqual(count(fixture, Items.FIRE_CHARGE), 0, "a different valid ingredient cannot satisfy the narrowed recipe");
            helper.assertValueEqual(count(fixture, Items.COAL), 1, "rejected ingredients remain untouched");
            fixture.context().inventory().insertSupply(new ItemStack(Items.CHARCOAL));
            tick(helper, fixture, 19);
            helper.assertValueEqual(count(fixture, Items.FIRE_CHARGE), 0, "the native crafting interval is retained");
            tick(helper, fixture, 1);
            helper.assertValueEqual(count(fixture, Items.FIRE_CHARGE), 3, "the whole indivisible recipe batch is produced");
            helper.assertValueEqual(count(fixture, Items.COAL), 1, "execution preserves the unselected alternative");
            helper.assertValueEqual(count(fixture, Items.CHARCOAL), 0, "execution consumes the selected alternative");
            helper.assertValueEqual(fixture.context().inventory().outputSlots().stream()
                    .map(fixture.context().inventory()::getItem).filter(stack -> stack.is(Items.FIRE_CHARGE)).mapToInt(ItemStack::getCount).sum(),
                    3, "the crafted batch is classified as output");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void craftsmanCannotSubstituteCraftingForAnExactProcessingRecipe(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, helper.getLevel(),
                    ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron")), "the smelting recipe is selected");
            fixture.context().inventory().setItem(HiredJobInventory.FILTER_SLOT, filter);
            fixture.context().inventory().insertSupply(new ItemStack(Items.IRON_BLOCK));
            tick(helper, fixture, 20);
            helper.assertValueEqual(count(fixture, Items.IRON_INGOT), 0, "a same-output crafting recipe cannot replace the exact recipe");
            helper.assertValueEqual(count(fixture, Items.IRON_BLOCK), 1, "unrelated crafting materials remain untouched");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void narrowedOutputCraftPreservesMaterialsWhenOutputIsFull(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var level = helper.getLevel();
            var recipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.FIRE_CHARGE))
                    .findFirst().orElseThrow();
            int slot = -1;
            for (int index = 0; index < recipe.value().getIngredients().size(); index++) {
                if (recipe.value().getIngredients().get(index).test(new ItemStack(Items.CHARCOAL))) slot = index;
            }
            var inventory = fixture.context().inventory();
            inventory.insertSupply(new ItemStack(Items.BLAZE_POWDER, 64));
            inventory.insertSupply(new ItemStack(Items.GUNPOWDER, 64));
            inventory.insertSupply(new ItemStack(Items.CHARCOAL, 64));
            for (int index = HiredJobInventory.MAIN_GRID_START; index < HiredJobInventory.FILTER_SLOT; index++) {
                if (inventory.getItem(index).isEmpty()) inventory.setItem(index, new ItemStack(Items.COBBLESTONE, 64));
            }
            var before = inventory.captureSnapshot();
            helper.assertFalse(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(level, fixture.context(),
                    recipe.value(), java.util.Map.of(slot, Items.CHARCOAL)), "a full output rejects the entire craft");
            helper.assertValueEqual(inventory.captureSnapshot(), before, "failure preserves every inventory slot and classification");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void finiteQueueSkipsBlockedRecipesAndPreservesWholeBatches(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            hire(helper, fixture);
            var blocked = order(helper, "diamond_block", 1);
            var sticks = order(helper, "stick", 5);
            helper.assertValueEqual(HiredProductionService.appendPersonal(fixture.owner(), fixture.worker(), 0L, blocked), "", "first order is accepted");
            helper.assertValueEqual(HiredProductionService.appendPersonal(fixture.owner(), fixture.worker(), 1L, sticks), "", "second order is accepted");
            var inventory = fixture.context().inventory();
            var legacy = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
            VillagerRecipeFilterData.setRecipe(legacy, helper.getLevel(), ResourceLocation.withDefaultNamespace("oak_button"));
            inventory.setItem(HiredJobInventory.FILTER_SLOT, legacy.copy());
            inventory.insertSupply(new ItemStack(Items.OAK_PLANKS, 12));
            tick(helper, fixture, 19);
            helper.assertValueEqual(count(fixture, Items.STICK), 0, "queued work retains the native crafting interval");
            tick(helper, fixture, 21);
            helper.assertValueEqual(count(fixture, Items.STICK), 8, "a target of five needs two indivisible four-stick batches");
            tick(helper, fixture, 40);
            helper.assertValueEqual(count(fixture, Items.STICK), 8, "completed finite production stops");
            helper.assertValueEqual(count(fixture, Items.OAK_PLANKS), 8, "only the two completed batches consume materials");
            helper.assertValueEqual(queue(helper, fixture).order(sticks.id()).produced(), 8L, "persisted progress counts real outputs once");
            helper.assertValueEqual(queue(helper, fixture).order(blocked.id()).blocker(), "missing_materials", "blocked orders report the reason");
            helper.assertTrue(ItemStack.matches(inventory.getItem(HiredJobInventory.FILTER_SLOT), legacy), "queue recipes preserve the personal legacy filter");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void pausingQueuedCraftReleasesMaterialsAndResetsItsWorkInterval(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            hire(helper, fixture);
            var sticks = order(helper, "stick", 4);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.worker(), 0L, sticks);
            fixture.context().inventory().insertSupply(new ItemStack(Items.OAK_PLANKS, 2));
            tick(helper, fixture, 19);
            var before = queue(helper, fixture);
            helper.assertValueEqual(before.pending(sticks.id()), 4, "work reserves one native batch before producing it");
            helper.assertValueEqual(HiredProductionService.editPersonal(fixture.owner(), fixture.worker(), before.configurationRevision(),
                    sticks.id(), HiredProductionService.Edit.PAUSE, 0), "", "the unfinished craft can be paused");
            tick(helper, fixture, 1);
            helper.assertValueEqual(queue(helper, fixture).pending(sticks.id()), 0, "pausing frees the unconsumed quantity");
            helper.assertValueEqual(count(fixture, Items.OAK_PLANKS), 2, "pausing consumes no ingredients");
            HiredProductionService.editPersonal(fixture.owner(), fixture.worker(), queue(helper, fixture).configurationRevision(),
                    sticks.id(), HiredProductionService.Edit.RESUME, 0);
            tick(helper, fixture, 19);
            helper.assertValueEqual(count(fixture, Items.STICK), 0, "a released reservation cannot retain partial work");
            tick(helper, fixture, 1);
            helper.assertValueEqual(count(fixture, Items.STICK), 4, "resumed production completes one new interval");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void pausingTheWorkerReleasesItsUnstartedProduction(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            hire(helper, fixture);
            var sticks = order(helper, "stick", 4);
            HiredProductionService.appendPersonal(fixture.owner(), fixture.worker(), 0L, sticks);
            fixture.context().inventory().insertSupply(new ItemStack(Items.OAK_PLANKS, 2));
            tick(helper, fixture, 10);
            helper.assertValueEqual(queue(helper, fixture).pending(sticks.id()), 4, "the active worker reserves production");
            new CraftsmanWorker().pause(helper.getLevel(), fixture.worker(), fixture.context());
            helper.assertValueEqual(queue(helper, fixture).pending(sticks.id()), 0, "pausing or stopping work releases an unconsumed batch");
            helper.assertValueEqual(count(fixture, Items.OAK_PLANKS), 2, "worker pause leaves materials intact");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void recursivePlanningAndFullOutputFailuresLeaveEveryInputUntouched(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var inventory = fixture.context().inventory();
            inventory.insertSupply(new ItemStack(Items.OAK_LOG, 64));
            var impossible = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.PAPER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS), Ingredient.of(Items.DIAMOND)));
            var before = inventory.captureSnapshot();
            helper.assertFalse(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), impossible), "a missing final ingredient rejects recursive planning");
            helper.assertValueEqual(inventory.captureSnapshot(), before, "failed planning never crafts intermediate planks");
            for (int slot = HiredJobInventory.MAIN_GRID_START; slot < HiredJobInventory.FILTER_SLOT; slot++) {
                if (inventory.getItem(slot).isEmpty()) inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            }
            var sticks = helper.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElseThrow();
            before = inventory.captureSnapshot();
            helper.assertFalse(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), (CraftingRecipe) sticks.value()), "full output rejects the recursive craft");
            helper.assertValueEqual(inventory.captureSnapshot(), before, "capacity failure preserves logs and slot classifications");
            inventory.setItem(HiredJobInventory.MAIN_GRID_START + 1, ItemStack.EMPTY);
            inventory.setItem(HiredJobInventory.MAIN_GRID_START + 2, ItemStack.EMPTY);
            helper.assertTrue(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), (CraftingRecipe) sticks.value()), "available space permits one recursive batch");
            helper.assertValueEqual(count(fixture, Items.OAK_LOG), 63, "one log funds the recursive craft");
            helper.assertValueEqual(count(fixture, Items.OAK_PLANKS), 2, "unused intermediate planks survive");
            helper.assertValueEqual(count(fixture, Items.STICK), 4, "the exact stick recipe produces one batch");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void craftingUsesSelectedComponentsAndRecipeDefinedRemainders(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var input = new ItemStack(Items.PAPER);
            input.set(DataComponents.CUSTOM_NAME, Component.literal("Selected survey"));
            var recipe = componentRecipe();
            fixture.context().inventory().insertSupply(input.copy());
            var planned = WorkCraftingPlan.prepare(helper.getLevel(), recipe, java.util.List.of(input),
                    java.util.Map.of(), true);
            helper.assertTrue(planned != null, "the common planner evaluates the same supported recipe for civic inventories");
            helper.assertTrue(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), recipe), "personal execution assembles the actual selected input");
            helper.assertValueEqual(count(fixture, Items.PAPER), 0, "the selected input is consumed once");
            helper.assertValueEqual(count(fixture, Items.MAP), 2, "assembly determines the output count rather than the advertised result");
            helper.assertValueEqual(count(fixture, Items.GLASS_BOTTLE), 1, "the recipe defines its own nonstandard remainder");
            for (ItemStack expected : planned.outputs()) {
                helper.assertTrue(fixture.context().inventory().outputSlots().stream()
                                .map(fixture.context().inventory()::getItem).anyMatch(stack -> ItemStack.matches(stack, expected)),
                        "personal execution agrees with civic planning on output counts and components");
            }
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void craftingRejectsMismatchedInputsAndStaleComponentPlans(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            var inventory = fixture.context().inventory();
            inventory.insertSupply(new ItemStack(Items.PAPER));
            var before = inventory.captureSnapshot();
            helper.assertFalse(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), componentRecipe()), "advertised ingredients do not bypass recipe matching");
            helper.assertValueEqual(inventory.captureSnapshot(), before, "a mismatched input consumes nothing");
            int slot = inventory.supplySlots().stream().filter(index -> inventory.getItem(index).is(Items.PAPER)).findFirst().orElseThrow();
            var named = new ItemStack(Items.PAPER);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Selected survey"));
            inventory.setItem(slot, named);
            var plan = HiredSupplyCrafting.prepareCarriedRecipe(helper.getLevel(), fixture.context(), componentRecipe(), java.util.Map.of());
            helper.assertTrue(plan != null, "the matching component input produces a plan");
            inventory.setItem(slot, new ItemStack(Items.PAPER));
            before = inventory.captureSnapshot();
            helper.assertFalse(HiredSupplyCrafting.applyCarriedRecipe(fixture.context(), plan, true, true),
                    "a different component variant cannot pay for a stale plan");
            helper.assertValueEqual(inventory.captureSnapshot(), before, "stale planning preserves inventory exactly");
            helper.assertFalse(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), componentRecipe(), java.util.Map.of(0, Items.DIRT)),
                    "narrowing cannot introduce an ingredient outside the recipe");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100, batch = "craftsman_recipe")
    public static void craftingReconsidersOverlappingIngredientAlternatives(GameTestHelper helper) {
        var fixture = fixture(helper);
        try {
            fixture.context().inventory().insertSupply(new ItemStack(Items.OAK_PLANKS));
            fixture.context().inventory().insertSupply(new ItemStack(Items.BIRCH_PLANKS));
            var recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.PAPER),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS), Ingredient.of(Items.OAK_PLANKS)));
            var materials = new java.util.LinkedHashMap<Item, Integer>();
            helper.assertTrue(new HiredSupplyCrafting.MaterialPlanner(helper.getLevel(), fixture.worker(), fixture.context())
                    .planRecipe(recipe, 1, materials), "material selection can reserve oak for the constrained ingredient");
            helper.assertTrue(HiredSupplyCrafting.craftCarriedRecipeToOutputsWithStations(
                    helper.getLevel(), fixture.context(), recipe), "execution retries the alternative after the first choice fails");
            helper.assertValueEqual(count(fixture, Items.PAPER), 1, "one complete recipe is assembled");
            helper.assertValueEqual(count(fixture, Items.OAK_PLANKS) + count(fixture, Items.BIRCH_PLANKS), 0,
                    "only the selected two planks are consumed");
        } finally {
            fixture.worker().discard();
        }
        helper.succeed();
    }

    private static CraftingRecipe componentRecipe() {
        return new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.MAP),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.PAPER))) {
            @Override
            public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
                return super.matches(input, level) && input.getItem(0).has(DataComponents.CUSTOM_NAME);
            }

            @Override
            public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
                ItemStack output = new ItemStack(Items.MAP, 2);
                output.set(DataComponents.CUSTOM_NAME, input.getItem(0).get(DataComponents.CUSTOM_NAME));
                return output;
            }

            @Override
            public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                var remainder = new ItemStack(Items.GLASS_BOTTLE);
                remainder.set(DataComponents.CUSTOM_NAME, input.getItem(0).get(DataComponents.CUSTOM_NAME));
                return NonNullList.of(ItemStack.EMPTY, remainder);
            }
        };
    }

    private static HiredProductionOrder order(GameTestHelper helper, String recipe, int amount) {
        return HiredProductionOrder.create(helper.getLevel(), HiredProductionRecipe.exact(ResourceLocation.withDefaultNamespace(recipe), java.util.Map.of()),
                HiredProductionOrder.Mode.BATCH, amount, null);
    }

    private static HiredProductionQueue queue(GameTestHelper helper, Fixture fixture) {
        return HiredProductionService.access(helper.getLevel(), fixture.worker(), HiredVillagerRole.CRAFTSMAN).queue();
    }

    private static void hire(GameTestHelper helper, Fixture fixture) {
        HireContractStore.save(fixture.worker(), HireContract.regular(fixture.owner().getUUID(), helper.getLevel().getGameTime(),
                1, 1, 1, HiredVillagerRole.CRAFTSMAN));
    }

    private static void tick(GameTestHelper helper, Fixture fixture, int ticks) {
        var worker = new CraftsmanWorker();
        for (int tick = 0; tick < ticks; tick++) worker.tick(helper.getLevel(), fixture.worker(), fixture.owner(), fixture.context());
    }

    private static int count(Fixture fixture, Item item) {
        return java.util.stream.IntStream.range(0, HiredJobInventory.SLOT_COUNT).mapToObj(fixture.context().inventory()::getItem)
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static Fixture fixture(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(helper.absolutePos(new BlockPos(x, 3, z)), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(helper.absolutePos(new BlockPos(3, 2, 3)), Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
        var villager = EntityType.VILLAGER.create(level);
        villager.setPos(helper.absolutePos(new BlockPos(2, 2, 2)).getBottomCenter());
        villager.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(villager), "the worker is loaded without settlement authority");
        var area = HiredWorkArea.fromBounds(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(5, 4, 5)), true);
        var state = HiredWorkStateStore.state(villager);
        HiredWorkerBrain.initialize(state);
        var context = new HiredWorkContext(HiredJobInventory.getJobInventory(villager), state, area.center(), area.min(), area.max(),
                area.horizontalRadius(), area.verticalRadius(), true, 100, false, false);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RecipeOwner"));
        return new Fixture(villager, owner, context);
    }

    private record Fixture(Villager worker, ServerPlayer owner, HiredWorkContext context) {
    }
}
