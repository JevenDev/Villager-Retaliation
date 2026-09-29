package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

public final class WorkCraftingPlan {
    private final ServerLevel level;
    private final List<RecipeHolder<CraftingRecipe>> recipes;
    private final boolean hasCraftingTable;
    private int attempts;
    private int selections;

    private WorkCraftingPlan(ServerLevel level, boolean hasCraftingTable) {
        this.level = level;
        this.hasCraftingTable = hasCraftingTable;
        this.recipes = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
    }

    public static Result prepare(ServerLevel level, CraftingRecipe recipe,
                                 List<ItemStack> supplies, Map<Integer, Item> narrowed, boolean hasCraftingTable) {
        if (recipe == null || recipe.getResultItem(level.registryAccess()).isEmpty()) return null;
        var planner = new WorkCraftingPlan(level, hasCraftingTable);
        var state = new State(supplies.stream().map(ItemStack::copy).toList(), 0);
        State completed = planner.craft(recipe, state, narrowed, new HashSet<>(), 0);
        if (completed == null) {
            return null;
        }
        List<Integer> consumed = new ArrayList<>(supplies.size());
        for (int index = 0; index < supplies.size(); index++) {
            consumed.add(supplies.get(index).getCount() - completed.items.get(index).getCount());
        }
        return new Result(List.copyOf(consumed), supplies.stream().map(ItemStack::copy).toList(), completed.items.subList(supplies.size(), completed.productStart)
                .stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList(),
                completed.items.subList(completed.productStart, completed.items.size())
                        .stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList(), completed.crafts);
    }

    private State craft(CraftingRecipe recipe, State original,
                        Map<Integer, Item> narrowed, Set<CraftingRecipe> visiting, int depth) {
        if (depth >= 8 || ++this.attempts > 256 || original.crafts >= 64 || recipe.isSpecial()
                || !recipe.canCraftInDimensions(3, 3) || recipe.getIngredients().isEmpty()
                || !this.hasCraftingTable && HiredSupplyCrafting.requiresCraftingTable(recipe)
                || !visiting.add(recipe)) {
            return null;
        }
        try {
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 3;
            if (width <= 0 || height <= 0 || width * height > 9 || recipe.getIngredients().size() > width * height) {
                return null;
            }
            NonNullList<ItemStack> grid = NonNullList.withSize(width * height, ItemStack.EMPTY);
            return select(recipe, original.copy(), narrowed, visiting, depth, grid, width, height, 0);
        } finally {
            visiting.remove(recipe);
        }
    }

    private State select(CraftingRecipe recipe, State trial, Map<Integer, Item> narrowed,
                         Set<CraftingRecipe> visiting, int depth, NonNullList<ItemStack> grid,
                         int width, int height, int slot) {
        if (++this.selections > 4096) return null;
        if (slot == recipe.getIngredients().size()) {
            CraftingInput input = CraftingInput.of(width, height, grid);
            if (trial.crafts >= 64 || !recipe.matches(input, this.level)) return null;
            ItemStack output = recipe.assemble(input, this.level.registryAccess());
            if (output.isEmpty()) return null;
            trial.productStart = trial.items.size();
            for (ItemStack remainder : recipe.getRemainingItems(input)) {
                if (!remainder.isEmpty()) trial.items.add(remainder.copy());
            }
            trial.items.add(output.copy());
            trial.crafts++;
            return trial;
        }
        Ingredient ingredient = recipe.getIngredients().get(slot);
        if (ingredient.isEmpty()) {
            grid.set(slot, ItemStack.EMPTY);
            return select(recipe, trial, narrowed, visiting, depth, grid, width, height, slot + 1);
        }
        Item required = narrowed.get(slot);
        List<ItemStack> tried = new ArrayList<>();
        for (int index = 0; index < trial.items.size(); index++) {
            ItemStack candidate = trial.items.get(index);
            if (!matches(candidate, ingredient, required)
                    || tried.stream().anyMatch(previous -> ItemStack.isSameItemSameComponents(previous, candidate))) continue;
            tried.add(candidate);
            State completed = selectStack(recipe, trial, narrowed, visiting, depth, grid, width, height, slot, index);
            if (completed != null) return completed;
        }
        for (RecipeHolder<CraftingRecipe> candidate : this.recipes) {
            ItemStack advertised = candidate.value().getResultItem(this.level.registryAccess());
            if (!matches(advertised, ingredient, required)) continue;
            State produced = craft(candidate.value(), trial, Map.of(), visiting, depth + 1);
            if (produced == null) continue;
            for (int index = trial.items.size(); index < produced.items.size(); index++) {
                if (!matches(produced.items.get(index), ingredient, required)) continue;
                State completed = selectStack(recipe, produced, narrowed, visiting, depth, grid, width, height, slot, index);
                if (completed != null) return completed;
            }
        }
        return null;
    }

    private State selectStack(CraftingRecipe recipe, State trial, Map<Integer, Item> narrowed,
                              Set<CraftingRecipe> visiting, int depth, NonNullList<ItemStack> grid,
                              int width, int height, int slot, int index) {
        State selected = trial.copy();
        ItemStack stack = selected.items.get(index);
        grid.set(slot, stack.copyWithCount(1));
        selected.items.set(index, stack.copyWithCount(stack.getCount() - 1));
        return select(recipe, selected, narrowed, visiting, depth, grid, width, height, slot + 1);
    }

    private static boolean matches(ItemStack candidate, Ingredient ingredient, Item narrowed) {
        return !candidate.isEmpty() && (narrowed == null || candidate.is(narrowed)) && ingredient.test(candidate);
    }

    public record Result(List<Integer> consumed, List<ItemStack> inputs, List<ItemStack> surplus, List<ItemStack> products, int crafts) {
        public List<ItemStack> outputs() {
            return java.util.stream.Stream.concat(surplus.stream(), products.stream()).toList();
        }

        public ItemStack result() {
            return products.getLast().copy();
        }

        public List<ItemStack> consumedStacks() {
            List<ItemStack> stacks = new ArrayList<>();
            for (int index = 0; index < inputs.size(); index++) {
                if (consumed.get(index) > 0) stacks.add(inputs.get(index).copyWithCount(consumed.get(index)));
            }
            return List.copyOf(stacks);
        }
    }

    private static final class State {
        private final List<ItemStack> items;
        private int crafts;
        private int productStart;

        private State(List<ItemStack> items, int crafts) {
            this.items = new ArrayList<>(items);
            this.crafts = crafts;
        }

        private State copy() {
            return new State(this.items, this.crafts);
        }
    }
}
