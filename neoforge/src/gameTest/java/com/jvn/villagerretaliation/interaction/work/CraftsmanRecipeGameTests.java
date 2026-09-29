package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
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
        var state = new CompoundTag();
        HiredWorkerBrain.initialize(state);
        var context = new HiredWorkContext(HiredJobInventory.getJobInventory(villager), state, area.center(), area.min(), area.max(),
                area.horizontalRadius(), area.verticalRadius(), true, 100, false, false);
        var owner = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "RecipeOwner"));
        return new Fixture(villager, owner, context);
    }

    private record Fixture(Villager worker, ServerPlayer owner, HiredWorkContext context) {
    }
}
