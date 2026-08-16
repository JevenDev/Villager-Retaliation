package com.jvn.villagerretaliation.recipe;

import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class VillagerFilterResetRecipe extends CustomRecipe {
    public VillagerFilterResetRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return singleFilter(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack filter = singleFilter(input);
        return filter == null ? ItemStack.EMPTY : new ItemStack(filter.getItem());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VillagerRetaliationRecipes.FILTER_RESETTING.get();
    }

    private static ItemStack singleFilter(CraftingInput input) {
        ItemStack filter = null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (filter != null || !VillagerRetaliationItems.isFilter(stack)) {
                return null;
            }
            filter = stack;
        }
        return filter;
    }
}
