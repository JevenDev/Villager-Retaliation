package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.item.VillagerRecipeSemantics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

public final class WorkCraftingRecipes {
    private static final com.google.common.cache.Cache<RecipeManager, Index> INDEXES =
            com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumSize(8).build();

    private WorkCraftingRecipes() { }

    public static List<RecipeHolder<CraftingRecipe>> producing(ServerLevel level, Item item) {
        var manager = level.getRecipeManager();
        var index = INDEXES.getIfPresent(manager);
        long revision = VillagerRecipeSemantics.revision();
        if (index == null || index.revision != revision) {
            index = new Index(level, revision);
            INDEXES.put(manager, index);
        }
        return index.producing(item);
    }

    private static final class Index {
        final long revision;
        final Map<Item, List<Entry>> outputs = new HashMap<>();
        final List<Entry> dynamic = new ArrayList<>();
        final Map<Item, List<RecipeHolder<CraftingRecipe>>> candidates = new HashMap<>();

        Index(ServerLevel level, long revision) {
            this.revision = revision;
            int ordinal = 0;
            for (var holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                var entry = new Entry(ordinal++, holder);
                var recipe = holder.value();
                // custom recipes may derive their output from mutable state
                if (recipe.getClass() != ShapedRecipe.class && recipe.getClass() != ShapelessRecipe.class) {
                    this.dynamic.add(entry);
                } else {
                    var output = recipe.getResultItem(level.registryAccess());
                    if (!output.isEmpty()) this.outputs.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>()).add(entry);
                }
            }
        }

        List<RecipeHolder<CraftingRecipe>> producing(Item item) {
            return this.candidates.computeIfAbsent(item, ignored -> {
                var entries = new ArrayList<>(this.outputs.getOrDefault(item, List.of()));
                entries.addAll(this.dynamic);
                entries.sort(Comparator.comparingInt(Entry::ordinal));
                return entries.stream().map(Entry::recipe).toList();
            });
        }
    }

    private record Entry(int ordinal, RecipeHolder<CraftingRecipe> recipe) { }
}
