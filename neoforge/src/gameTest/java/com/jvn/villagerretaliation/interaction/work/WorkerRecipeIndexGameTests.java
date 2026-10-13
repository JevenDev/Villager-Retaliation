package com.jvn.villagerretaliation.interaction.work;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("worker_performance")
@PrefixGameTestTemplate(false)
public final class WorkerRecipeIndexGameTests {
    @GameTest(template = "empty", batch = "worker_recipes")
    public static void replacementAndDynamicOutputsKeepNativeRecipeOrder(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var output = new AtomicReference<Item>(Items.COAL);
        var dynamic = new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:dynamic_index_test"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.COAL), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK))) {
                    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return new ItemStack(output.get()); }
                });
        var iron = new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:iron_index_test"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.IRON_INGOT), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK))));
        var coal = new RecipeHolder<>(ResourceLocation.parse("villagerretaliation:coal_index_test"),
                new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(Items.COAL), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STICK))));
        try {
            manager.replaceRecipes(List.of(dynamic, iron, coal));
            var candidates = WorkCraftingRecipes.producing(level, Items.IRON_INGOT);
            helper.assertValueEqual(candidates.stream().map(RecipeHolder::id).toList(), List.of(dynamic.id(), iron.id()),
                    "static unrelated outputs are excluded while custom recipes keep their native position");
            output.set(Items.IRON_INGOT);
            helper.assertTrue(candidates.getFirst().value().getResultItem(level.registryAccess()).is(Items.IRON_INGOT),
                    "dynamic output is read at execution rather than frozen by the index");
            manager.replaceRecipes(List.of(coal));
            helper.assertTrue(WorkCraftingRecipes.producing(level, Items.IRON_INGOT).isEmpty(), "replacement invalidates cached candidates");
            helper.assertValueEqual(WorkCraftingRecipes.producing(level, Items.COAL).size(), 1, "replacement recipes are indexed");
        } finally {
            manager.replaceRecipes(original);
        }
        helper.succeed();
    }
}
