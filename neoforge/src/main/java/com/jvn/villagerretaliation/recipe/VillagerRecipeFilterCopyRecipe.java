package com.jvn.villagerretaliation.recipe;

import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Copies a configured Recipe Filter onto one default Recipe Filter. */
public final class VillagerRecipeFilterCopyRecipe extends CustomRecipe {
    public VillagerRecipeFilterCopyRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return configuredSource(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = configuredSource(input);
        if (source == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = new ItemStack(VillagerRetaliationItems.RECIPE_FILTER.get(), 2);
        VillagerRecipeFilterData.copyConfiguration(source, result);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VillagerRetaliationRecipes.RECIPE_FILTER_COPYING.get();
    }

    private static ItemStack configuredSource(CraftingInput input) {
        ItemStack configured = null;
        ItemStack empty = null;
        int ingredients = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ingredients++;
            if (ingredients > 2 || !VillagerRetaliationItems.isRecipeFilter(stack)) {
                return null;
            }
            if (VillagerRecipeFilterData.isDefault(stack)) {
                if (empty != null) {
                    return null;
                }
                empty = stack;
            } else {
                if (configured != null
                        || VillagerRecipeFilterData.read(stack).state()
                                != VillagerRecipeFilterData.StoredState.CONFIGURED) {
                    return null;
                }
                configured = stack;
            }
        }
        return ingredients == 2 && configured != null && empty != null ? configured : null;
    }
}
