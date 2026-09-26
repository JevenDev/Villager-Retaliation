package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

final class SettlementCraftingPlan {
    private final ServerLevel level;
    private final List<RecipeHolder<CraftingRecipe>> recipes;
    private int attempts;

    private SettlementCraftingPlan(ServerLevel level) {
        this.level = level;
        this.recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
    }

    static Result prepare(ServerLevel level, RecipeHolder<CraftingRecipe> recipe,
                          List<ItemStack> supplies, Map<Integer, Item> narrowed) {
        var planner = new SettlementCraftingPlan(level);
        var state = new State(supplies.stream().map(ItemStack::copy).toList(), 0);
        State completed = planner.craft(recipe, state, narrowed, new HashSet<>(), 0);
        if (completed == null) {
            return null;
        }
        List<Integer> consumed = new ArrayList<>(supplies.size());
        for (int index = 0; index < supplies.size(); index++) {
            consumed.add(supplies.get(index).getCount() - completed.items.get(index).getCount());
        }
        return new Result(List.copyOf(consumed), completed.items.subList(supplies.size(), completed.items.size())
                .stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList(), completed.crafts);
    }

    private State craft(RecipeHolder<CraftingRecipe> holder, State original,
                        Map<Integer, Item> narrowed, Set<ResourceLocation> visiting, int depth) {
        CraftingRecipe recipe = holder.value();
        if (depth >= 8 || ++this.attempts > 256 || original.crafts >= 64 || recipe.isSpecial()
                || !recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().isEmpty()
                || !visiting.add(holder.id())) {
            return null;
        }
        try {
            State trial = original.copy();
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
            if (width <= 0 || height <= 0 || width * height > 9 || recipe.getIngredients().size() > width * height) {
                return null;
            }
            NonNullList<ItemStack> grid = NonNullList.withSize(width * height, ItemStack.EMPTY);
            for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
                Ingredient ingredient = recipe.getIngredients().get(slot);
                if (ingredient.isEmpty()) {
                    continue;
                }
                Item required = narrowed.get(slot);
                int available = find(trial, ingredient, required);
                if (available < 0) {
                    for (RecipeHolder<CraftingRecipe> candidate : this.recipes) {
                        ItemStack advertised = candidate.value().getResultItem(this.level.registryAccess());
                        if (advertised.isEmpty() || required != null && !advertised.is(required)
                                || !ingredient.test(advertised)) {
                            continue;
                        }
                        State produced = craft(candidate, trial, Map.of(), visiting, depth + 1);
                        if (produced != null) {
                            available = find(produced, ingredient, required);
                            if (available >= 0) {
                                trial = produced;
                                break;
                            }
                        }
                    }
                }
                if (available < 0) {
                    return null;
                }
                ItemStack selected = trial.items.get(available);
                grid.set(slot, selected.copyWithCount(1));
                trial.items.set(available, selected.copyWithCount(selected.getCount() - 1));
            }
            CraftingInput input = CraftingInput.of(width, height, grid);
            if (trial.crafts >= 64 || !recipe.matches(input, this.level)) {
                return null;
            }
            ItemStack output = recipe.assemble(input, this.level.registryAccess());
            if (output.isEmpty()) {
                return null;
            }
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty()) {
                    trial.items.add(remainder.copy());
                }
            }
            trial.items.add(output.copy());
            trial.crafts++;
            return trial;
        } finally {
            visiting.remove(holder.id());
        }
    }

    private static int find(State state, Ingredient ingredient, Item narrowed) {
        for (int index = 0; index < state.items.size(); index++) {
            ItemStack candidate = state.items.get(index);
            if (!candidate.isEmpty() && (narrowed == null || candidate.is(narrowed)) && ingredient.test(candidate)) {
                return index;
            }
        }
        return -1;
    }

    record Result(List<Integer> consumed, List<ItemStack> outputs, int crafts) {
    }

    private static final class State {
        private final List<ItemStack> items;
        private int crafts;

        private State(List<ItemStack> items, int crafts) {
            this.items = new ArrayList<>(items);
            this.crafts = crafts;
        }

        private State copy() {
            return new State(this.items, this.crafts);
        }
    }
}
