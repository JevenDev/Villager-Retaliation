package com.jvn.villagerretaliation.storage;

import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public final class KeyCopyRecipe extends CustomRecipe {
    public KeyCopyRecipe(CraftingBookCategory category) { super(category); }
    private ItemStack source(CraftingInput input) {
        ItemStack source=ItemStack.EMPTY, material=ItemStack.EMPTY;
        for(int i=0;i<input.size();i++) {
            var s=input.getItem(i); if(s.isEmpty()) continue;
            if((s.is(StorageRegistry.KEY.get()) || s.is(StorageRegistry.MOLD.get())) && StorageKeys.identity(s)!=null && source.isEmpty()) source=s;
            else if((s.is(Items.CLAY_BALL) || s.is(Items.GOLD_INGOT)) && material.isEmpty()) material=s;
            else return ItemStack.EMPTY;
        }
        return source.is(StorageRegistry.KEY.get()) && material.is(Items.CLAY_BALL)
                || source.is(StorageRegistry.MOLD.get()) && material.is(Items.GOLD_INGOT) ? source : ItemStack.EMPTY;
    }
    @Override public boolean matches(CraftingInput input,Level level) { return !source(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input,HolderLookup.Provider provider) {
        var s=source(input);
        return StorageKeys.create(s.is(StorageRegistry.KEY.get()) ? StorageRegistry.MOLD.get() : StorageRegistry.CAST.get(),StorageKeys.identity(s));
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var result=NonNullList.withSize(input.size(),ItemStack.EMPTY);
        if(!source(input).isEmpty()) for(int i=0;i<input.size();i++) if(input.getItem(i).is(StorageRegistry.KEY.get())) result.set(i,input.getItem(i).copyWithCount(1));
        return result;
    }
    @Override public boolean canCraftInDimensions(int w,int h) { return w*h>=2; }
    @Override public RecipeSerializer<?> getSerializer() { return StorageRegistry.COPYING.get(); }
}
