package com.jvn.villagerretaliation.storage;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class KeySmeltingRecipe extends SmeltingRecipe {
    public KeySmeltingRecipe(String group,CookingBookCategory category,Ingredient ingredient,ItemStack result,float experience,int time) {
        super(group,category,ingredient,result,experience,time);
    }
    @Override public boolean matches(SingleRecipeInput input,Level level) {
        return input.item().is(StorageRegistry.CAST.get()) && StorageKeys.identity(input.item())!=null;
    }
    @Override public ItemStack assemble(SingleRecipeInput input,HolderLookup.Provider provider) {
        return input.item().is(StorageRegistry.CAST.get()) ? StorageKeys.create(StorageRegistry.KEY.get(),StorageKeys.identity(input.item())) : ItemStack.EMPTY;
    }
    @Override public RecipeSerializer<?> getSerializer() { return StorageRegistry.SMELTING.get(); }
}
