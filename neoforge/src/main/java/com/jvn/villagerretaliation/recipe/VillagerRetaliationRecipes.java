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

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VillagerAttributeFilterCopyRecipe>>
            ATTRIBUTE_FILTER_COPYING = SERIALIZERS.register(
                    "attribute_filter_copying",
                    () -> new SimpleCraftingRecipeSerializer<>(VillagerAttributeFilterCopyRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VillagerRecipeFilterCopyRecipe>>
            RECIPE_FILTER_COPYING = SERIALIZERS.register(
                    "recipe_filter_copying",
                    () -> new SimpleCraftingRecipeSerializer<>(VillagerRecipeFilterCopyRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<VillagerFilterResetRecipe>>
            FILTER_RESETTING = SERIALIZERS.register(
                    "filter_resetting",
                    () -> new SimpleCraftingRecipeSerializer<>(VillagerFilterResetRecipe::new));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BlueprintChecklistRecipe>>
            BLUEPRINT_CHECKLIST = SERIALIZERS.register(
                    "blueprint_checklist",
                    () -> new SimpleCraftingRecipeSerializer<>(BlueprintChecklistRecipe::new));

    private VillagerRetaliationRecipes() {
    }

    public static void register(IEventBus eventBus) {
        SERIALIZERS.register(eventBus);
    }
}
