package com.jvn.villagerretaliation.recipe;

import com.jvn.villagerretaliation.VillagerRetaliation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class VillagerRetaliationRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, VillagerRetaliation.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VillagerItemFilterCopyRecipe>>
            ITEM_FILTER_COPYING = SERIALIZERS.register(
                    "item_filter_copying",
                    () -> new SimpleCraftingRecipeSerializer<>(VillagerItemFilterCopyRecipe::new));

    private VillagerRetaliationRecipes() {
    }

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
