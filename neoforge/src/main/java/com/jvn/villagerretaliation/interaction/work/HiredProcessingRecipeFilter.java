package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.item.VillagerFilterMatcher;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** Applies exact recipe-filter identity and ingredient choices to worker production decisions. */
final class HiredProcessingRecipeFilter {
    private HiredProcessingRecipeFilter() {
    }

    static boolean allows(
            ServerLevel level,
            ItemStack filter,
            RecipeHolder<?> recipe,
            ItemStack input,
            ItemStack result) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return VillagerFilterMatcher.matches(level, filter, result);
        }
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        VillagerRecipeFilterData.Resolution resolution = VillagerRecipeFilterData.resolve(level, filter);
        if (!policy.valid() || !resolution.valid()) {
            return false;
        }
        boolean exact = resolution.recipe().id().equals(recipe.id())
                && matchesNarrowedProcessingInput(resolution, input);
        return policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING ? exact : !exact;
    }

    static boolean allowsCraftingRecipe(
            ServerLevel level,
            ItemStack filter,
            RecipeHolder<?> recipe,
            ItemStack result) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return VillagerFilterMatcher.matches(level, filter, result);
        }
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        VillagerRecipeFilterData.Resolution resolution = VillagerRecipeFilterData.resolve(level, filter);
        if (!policy.valid() || !resolution.valid()) {
            return false;
        }
        boolean exact = resolution.recipe().id().equals(recipe.id());
        return policy.listMode() == VillagerFilterPolicy.ListMode.ALLOW_MATCHING ? exact : !exact;
    }

    static boolean supportsRecipeType(ServerLevel level, ItemStack filter, RecipeType<?> recipeType) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)) {
            return true;
        }
        VillagerFilterPolicy.Policy policy = VillagerFilterPolicy.read(filter);
        VillagerRecipeFilterData.Resolution resolution = VillagerRecipeFilterData.resolve(level, filter);
        if (!policy.valid() || !resolution.valid()) {
            return false;
        }
        return policy.listMode() != VillagerFilterPolicy.ListMode.ALLOW_MATCHING
                || resolution.recipe().value().getType() == recipeType;
    }

    static Map<Integer, Item> narrowedCraftingIngredients(
            ServerLevel level,
            ItemStack filter,
            RecipeHolder<?> recipe) {
        if (!VillagerRetaliationItems.isRecipeFilter(filter)
                || VillagerFilterPolicy.read(filter).listMode() != VillagerFilterPolicy.ListMode.ALLOW_MATCHING) {
            return Map.of();
        }
        VillagerRecipeFilterData.Resolution resolution = VillagerRecipeFilterData.resolve(level, filter);
        if (!resolution.valid() || !resolution.recipe().id().equals(recipe.id())) {
            return Map.of();
        }
        Map<Integer, Item> narrowed = new LinkedHashMap<>();
        resolution.configuration().narrowedIngredients().forEach(
                (slot, itemId) -> narrowed.put(slot, BuiltInRegistries.ITEM.get(itemId)));
        return Map.copyOf(narrowed);
    }

    private static boolean matchesNarrowedProcessingInput(
            VillagerRecipeFilterData.Resolution resolution,
            ItemStack input) {
        var narrowed = resolution.configuration().narrowedIngredients();
        if (narrowed.isEmpty()) {
            return true;
        }
        return narrowed.size() == 1
                && narrowed.containsKey(0)
                && BuiltInRegistries.ITEM.getKey(input.getItem()).equals(narrowed.get(0));
    }
}
