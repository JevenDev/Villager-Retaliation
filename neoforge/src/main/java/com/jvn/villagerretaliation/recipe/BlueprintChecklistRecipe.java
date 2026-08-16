package com.jvn.villagerretaliation.recipe;

import com.jvn.villagerretaliation.item.BlueprintChecklistItem;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class BlueprintChecklistRecipe extends CustomRecipe {
    public BlueprintChecklistRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return blueprint(input) != null && hasSingleBook(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack blueprint = blueprint(input);
        return blueprint == null ? ItemStack.EMPTY : BlueprintChecklistItem.createFromBlueprint(blueprint);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (VillagerRetaliationItems.isConstructionBlueprint(stack)) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return VillagerRetaliationRecipes.BLUEPRINT_CHECKLIST.get();
    }

    private static ItemStack blueprint(CraftingInput input) {
        ItemStack blueprint = null;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty() || stack.is(Items.BOOK)) {
                continue;
            }
            if (blueprint != null || !VillagerRetaliationItems.isConstructionBlueprint(stack)) {
                return null;
            }
            blueprint = stack;
        }
        return blueprint;
    }

    private static boolean hasSingleBook(CraftingInput input) {
        int books = 0;
        int ingredients = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ingredients++;
            if (stack.is(Items.BOOK)) {
                books++;
            }
        }
        return ingredients == 2 && books == 1;
    }
}
