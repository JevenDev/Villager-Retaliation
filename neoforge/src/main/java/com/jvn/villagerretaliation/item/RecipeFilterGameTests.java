package com.jvn.villagerretaliation.item;

import com.jvn.villagerretaliation.network.RecipeFilterIngredientPayload;
import com.jvn.villagerretaliation.network.RecipeFilterSelectPayload;
import com.jvn.villagerretaliation.recipe.VillagerFilterResetRecipe;
import com.jvn.villagerretaliation.recipe.VillagerRecipeFilterCopyRecipe;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class RecipeFilterGameTests {
    private static final String EMPTY_TEMPLATE = "empty";
    private static final String ROOT_TAG = "villagerretaliation:recipe_filter";

    private RecipeFilterGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void exactRecipeSelectionUsesSharedMatcher(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RecipeHolder<?> holder = recipeWithAlternative(level).holder();
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, level, holder.id()),
                "an exact registered worker recipe should be selected");
        helper.assertValueEqual(
                VillagerRecipeFilterData.resolve(level, filter).state(),
                VillagerRecipeFilterData.ResolutionState.VALID,
                "selected recipe resolution");
        helper.assertValueEqual(
                VillagerRecipeFilterData.read(filter).recipeId(),
                holder.id(),
                "recipe identity must remain authoritative");

        ItemStack result = holder.value().getResultItem(level.registryAccess());
        helper.assertTrue(!result.isEmpty(), "selected recipe should expose a result");
        helper.assertTrue(VillagerFilterMatcher.matches(level, filter, result),
                "the exact selected recipe result should match through the shared abstraction");
        ItemStack wrongResult = result.is(Items.BARRIER)
                ? new ItemStack(Items.APPLE)
                : new ItemStack(Items.BARRIER);
        helper.assertFalse(VillagerFilterMatcher.matches(level, filter, wrongResult),
                "a different result must not substitute for exact recipe identity");

        VillagerFilterPolicy.setPolicy(
                filter,
                VillagerFilterPolicy.TransferDirection.PROVIDE,
                VillagerFilterPolicy.ListMode.DENY_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                java.util.OptionalInt.of(64));
        helper.assertFalse(VillagerFilterMatcher.matches(level, filter, result),
                "Deny Matching should reject the selected result");
        helper.assertTrue(VillagerFilterMatcher.matches(level, filter, wrongResult),
                "Deny Matching should permit a different result");
        helper.assertTrue(VillagerFilterMatcher.rawMatches(level, filter, result),
                "raw matching must ignore nested mode, direction, and stock policy");

        ItemStack outer = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        helper.assertTrue(VillagerItemFilterData.setEntry(outer, 0, filter),
                "a configured Recipe Filter should be accepted as a nested matcher");
        helper.assertTrue(VillagerFilterMatcher.matches(level, outer, result),
                "the outer List Filter should own final policy for a nested Recipe Filter");
        helper.assertFalse(VillagerFilterMatcher.matches(level, outer, wrongResult),
                "the nested Recipe Filter should retain its exact-result predicate");

        ItemStack legacy = new ItemStack(VillagerRetaliationItems.ITEM_FILTER.get());
        VillagerItemFilterData.setEntry(legacy, 0, result);
        helper.assertTrue(VillagerFilterMatcher.matches(level, legacy, result),
                "legacy List Filters remain compatible with the shared matcher");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void ingredientAlternativeNarrowingCopiesAndPersists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AlternativeRecipe selected = recipeWithAlternative(level);
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        helper.assertTrue(VillagerRecipeFilterData.setRecipe(filter, level, selected.holder().id()),
                "recipe selection");

        List<ItemStack> choices = VillagerRecipeFilterData.ingredientChoices(selected.ingredient());
        ItemStack chosen = choices.getFirst();
        ItemStack rejected = choices.get(1);
        ResourceLocation chosenId = BuiltInRegistries.ITEM.getKey(chosen.getItem());
        helper.assertTrue(VillagerRecipeFilterData.setIngredient(
                        filter, level, selected.slot(), chosenId),
                "one valid alternative should narrow the ingredient");
        helper.assertTrue(VillagerRecipeFilterData.matchesIngredient(
                        level, filter, selected.slot(), chosen),
                "the exact narrowed alternative should match");
        helper.assertFalse(VillagerRecipeFilterData.matchesIngredient(
                        level, filter, selected.slot(), rejected),
                "another otherwise valid alternative should be rejected");
        VillagerFilterPolicy.setPolicy(
                filter,
                VillagerFilterPolicy.TransferDirection.BOTH,
                VillagerFilterPolicy.ListMode.ALLOW_MATCHING,
                VillagerFilterPolicy.CombinationMode.MATCH_ANY,
                java.util.OptionalInt.of(37));

        ItemStack restored = ItemStack.parseOptional(
                level.registryAccess(),
                (CompoundTag) filter.saveOptional(level.registryAccess()));
        helper.assertValueEqual(
                VillagerRecipeFilterData.read(restored),
                VillagerRecipeFilterData.read(filter),
                "recipe identity and exact alternatives must survive item persistence");
        helper.assertValueEqual(
                VillagerFilterPolicy.read(restored),
                VillagerFilterPolicy.read(filter),
                "shared policy must survive item persistence");

        ItemStack empty = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        filter.set(DataComponents.CUSTOM_NAME, Component.literal("Do not copy this"));
        CraftingInput copyInput = CraftingInput.of(2, 1, List.of(empty, filter));
        VillagerRecipeFilterCopyRecipe copyRecipe =
                new VillagerRecipeFilterCopyRecipe(CraftingBookCategory.MISC);
        helper.assertTrue(copyRecipe.matches(copyInput, level),
                "one configured and one default Recipe Filter should copy");
        ItemStack copied = copyRecipe.assemble(copyInput, level.registryAccess());
        helper.assertValueEqual(copied.getCount(), 2, "copy output count");
        helper.assertValueEqual(
                VillagerRecipeFilterData.read(copied),
                VillagerRecipeFilterData.read(filter),
                "copying must preserve only recipe configuration");
        helper.assertValueEqual(
                VillagerFilterPolicy.read(copied),
                VillagerFilterPolicy.read(filter),
                "copying must preserve the shared transfer policy");
        helper.assertFalse(copied.has(DataComponents.CUSTOM_NAME),
                "copying must not preserve unrelated components");

        VillagerFilterResetRecipe resetRecipe =
                new VillagerFilterResetRecipe(CraftingBookCategory.MISC);
        CraftingInput resetInput = CraftingInput.of(1, 1, List.of(filter));
        helper.assertTrue(resetRecipe.matches(resetInput, level),
                "the generic filter reset recipe should accept Recipe Filters");
        ItemStack reset = resetRecipe.assemble(resetInput, level.registryAccess());
        helper.assertTrue(reset.is(VillagerRetaliationItems.RECIPE_FILTER.get())
                        && VillagerRecipeFilterData.isDefault(reset),
                "resetting should return a default physical Recipe Filter");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void malformedAndReloadedRecipeDataFailsClosed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack malformed = rawFilter(VillagerRecipeFilterData.CURRENT_VERSION + 1, "minecraft:bread");
        helper.assertValueEqual(
                VillagerRecipeFilterData.read(malformed).state(),
                VillagerRecipeFilterData.StoredState.MALFORMED,
                "unknown data versions must be malformed");
        helper.assertValueEqual(
                VillagerRecipeFilterData.resolve(level, malformed).state(),
                VillagerRecipeFilterData.ResolutionState.MALFORMED,
                "malformed data must fail closed");

        ItemStack missing = rawFilter(
                VillagerRecipeFilterData.CURRENT_VERSION,
                "villagerretaliation:removed_recipe");
        helper.assertValueEqual(
                VillagerRecipeFilterData.resolve(level, missing).state(),
                VillagerRecipeFilterData.ResolutionState.MISSING_RECIPE,
                "a recipe removed by reload must remain explicit and unresolved");

        AlternativeRecipe selected = recipeWithAlternative(level);
        ItemStack invalidAlternative = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerRecipeFilterData.setRecipe(invalidAlternative, level, selected.holder().id());
        CompoundTag customData = invalidAlternative
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag();
        CompoundTag root = customData.getCompound(ROOT_TAG);
        CompoundTag ingredient = new CompoundTag();
        ingredient.putInt("Slot", selected.slot());
        ingredient.putString("Item", "minecraft:barrier");
        ListTag ingredients = new ListTag();
        ingredients.add(ingredient);
        root.put("Ingredients", ingredients);
        customData.put(ROOT_TAG, root);
        invalidAlternative.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        helper.assertValueEqual(
                VillagerRecipeFilterData.resolve(level, invalidAlternative).state(),
                VillagerRecipeFilterData.ResolutionState.INVALID_ALTERNATIVE,
                "alternatives invalidated by a recipe reload must fail closed");

        ItemStack valid = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        VillagerRecipeFilterData.setRecipe(valid, level, selected.holder().id());
        long before = VillagerRecipeSemantics.revision();
        long after = VillagerRecipeSemantics.markReloaded();
        helper.assertTrue(after > before, "recipe reload must advance its semantic revision");
        helper.assertValueEqual(
                VillagerRecipeFilterData.resolve(level, valid).state(),
                VillagerRecipeFilterData.ResolutionState.VALID,
                "still-registered recipes should revalidate after the revision changes");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void recipeFilterPayloadsRemainBoundedAndValidated(GameTestHelper helper) {
        AlternativeRecipe selected = recipeWithAlternative(helper.getLevel());
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(
                VillagerRecipeFilterData.ingredientChoices(selected.ingredient()).getFirst().getItem());
        helper.assertTrue(new RecipeFilterSelectPayload(selected.holder().id().toString()).valid(),
                "registered recipe id wire value");
        helper.assertFalse(new RecipeFilterSelectPayload("not a resource id").valid(),
                "malformed recipe ids should be rejected before menu mutation");
        helper.assertTrue(new RecipeFilterIngredientPayload(selected.slot(), itemId.toString()).valid(),
                "bounded ingredient selection wire value");
        helper.assertFalse(new RecipeFilterIngredientPayload(
                        VillagerRecipeFilterData.MAX_INGREDIENTS, itemId.toString()).valid(),
                "out-of-range ingredient slots should be rejected");
        helper.assertFalse(new RecipeFilterIngredientPayload(selected.slot(), "bad id").valid(),
                "malformed ingredient ids should be rejected");
        helper.assertTrue(
                VillagerRecipeFilterData.catalog(helper.getLevel()).size()
                        <= VillagerRecipeFilterData.MAX_CATALOG_RECIPES,
                "the UI recipe catalog must remain bounded");
        helper.succeed();
    }

    private static AlternativeRecipe recipeWithAlternative(ServerLevel level) {
        for (RecipeHolder<?> holder : VillagerRecipeFilterData.catalog(level)) {
            List<Ingredient> ingredients = holder.value().getIngredients();
            for (int slot = 0; slot < ingredients.size(); slot++) {
                Ingredient ingredient = ingredients.get(slot);
                if (VillagerRecipeFilterData.ingredientChoices(ingredient).size() > 1
                        && !holder.value().getResultItem(level.registryAccess()).isEmpty()) {
                    return new AlternativeRecipe(holder, slot, ingredient);
                }
            }
        }
        throw new IllegalStateException("No bounded worker recipe with alternative ingredients");
    }

    private static ItemStack rawFilter(int version, String recipeId) {
        ItemStack filter = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get());
        CompoundTag root = new CompoundTag();
        root.putInt("Version", version);
        root.putString("Recipe", recipeId);
        root.put("Ingredients", new ListTag());
        CompoundTag customData = new CompoundTag();
        customData.put(ROOT_TAG, root);
        filter.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        return filter;
    }

    private record AlternativeRecipe(RecipeHolder<?> holder, int slot, Ingredient ingredient) {
    }
}
