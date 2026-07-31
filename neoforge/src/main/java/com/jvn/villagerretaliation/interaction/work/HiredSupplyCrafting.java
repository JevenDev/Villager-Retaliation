package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;

public final class HiredSupplyCrafting {
    private static final int MAX_CRAFTING_DEPTH = 8;

    private HiredSupplyCrafting() {
    }

    public static int countAvailable(Villager villager, HiredWorkContext context, Item item) {
        return countAvailable(villager, context, stack -> stack.is(item));
    }

    public static int countAvailable(Villager villager, HiredWorkContext context, Predicate<ItemStack> predicate) {
        int count = countCarried(context, predicate);
        if (context.useAssignedStorageForSupplies()) {
            count += AssignedStorageService.countItems(villager, predicate);
        }
        return count;
    }

    public static int countCarried(HiredWorkContext context, Item item) {
        return countCarried(context, stack -> stack.is(item));
    }

    public static int countCarried(HiredWorkContext context, Predicate<ItemStack> predicate) {
        int count = 0;
        for (int slot : context.inventory().supplySlots()) {
            ItemStack stack = context.inventory().getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean craftCarriedSupplyItem(ServerLevel level, HiredWorkContext context, Item item) {
        return craftCarriedSupplyItem(level, context, item, new HashSet<>(), true);
    }

    public static boolean craftCarriedSupplyItemWithStations(
            ServerLevel level,
            HiredWorkContext context,
            Item item) {
        return craftCarriedSupplyItem(level, context, item, new HashSet<>(), hasCraftingTable(level, context));
    }

    /** Crafts one exact recipe into supply slots while honoring configured ingredient alternatives. */
    public static boolean craftCarriedRecipeWithStations(
            ServerLevel level,
            HiredWorkContext context,
            CraftingRecipe recipe,
            Map<Integer, Item> narrowedIngredients) {
        boolean hasCraftingTable = hasCraftingTable(level, context);
        if (recipe == null || recipe.isSpecial() || !canUseRecipe(recipe, hasCraftingTable)) {
            return false;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        Map<Item, Integer> ingredients = new LinkedHashMap<>();
        if (!prepareRecipeIngredients(
                level, context, recipe, ingredients, narrowedIngredients, new HashSet<>(), hasCraftingTable)) {
            return false;
        }
        List<ItemStack> produced = craftingRemainders(ingredients);
        produced.add(0, result.copy());
        return context.inventory().tryTransformSupplies(ingredients, produced);
    }

    /** Crafts one exact recipe, placing its result in job-output slots. */
    public static boolean craftCarriedRecipeToOutputsWithStations(
            ServerLevel level,
            HiredWorkContext context,
            CraftingRecipe recipe) {
        boolean hasCraftingTable = hasCraftingTable(level, context);
        if (recipe == null || recipe.isSpecial() || !canUseRecipe(recipe, hasCraftingTable)) {
            return false;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return false;
        }
        Map<Item, Integer> ingredients = new LinkedHashMap<>();
        if (!prepareRecipeIngredients(level, context, recipe, ingredients, new HashSet<>(), hasCraftingTable)) {
            return false;
        }
        List<ItemStack> produced = craftingRemainders(ingredients);
        produced.add(0, result.copy());
        return context.inventory().tryTransformSuppliesToOutputs(ingredients, produced);

    }
    private static boolean craftCarriedSupplyItem(
            ServerLevel level,
            HiredWorkContext context,
            Item item,
            Set<Item> visiting,
            boolean hasCraftingTable) {
        if (!visiting.add(item)) {
            return false;
        }
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial() || !canUseRecipe(recipe, hasCraftingTable)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !result.is(item)) {
                continue;
            }
            Map<Item, Integer> ingredients = new HashMap<>();
            if (!prepareRecipeIngredients(level, context, recipe, ingredients, visiting, hasCraftingTable)) {
                continue;
            }
            List<ItemStack> produced = craftingRemainders(ingredients);
            produced.add(0, result.copy());
            boolean crafted = context.inventory().tryTransformSupplies(ingredients, produced);
            visiting.remove(item);
            return crafted;
        }
        visiting.remove(item);
        return false;
    }

    private static boolean prepareRecipeIngredients(
            ServerLevel level,
            HiredWorkContext context,
            CraftingRecipe recipe,
            Map<Item, Integer> ingredients,
            Set<Item> visiting,
            boolean hasCraftingTable) {
        return prepareRecipeIngredients(
                level, context, recipe, ingredients, Map.of(), visiting, hasCraftingTable);
    }

    private static boolean prepareRecipeIngredients(
            ServerLevel level,
            HiredWorkContext context,
            CraftingRecipe recipe,
            Map<Item, Integer> ingredients,
            Map<Integer, Item> narrowedIngredients,
            Set<Item> visiting,
            boolean hasCraftingTable) {
        List<Ingredient> recipeIngredients = recipe.getIngredients();
        for (int slot = 0; slot < recipeIngredients.size(); slot++) {
            Ingredient ingredient = recipeIngredients.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            Item selected = null;
            Item narrowed = narrowedIngredients.get(slot);
            ItemStack[] options = narrowed == null
                    ? ingredient.getItems()
                    : new ItemStack[]{new ItemStack(narrowed)};
            for (ItemStack option : options) {
                if (option.isEmpty()) {
                    continue;
                }
                Item optionItem = option.getItem();
                int needed = ingredients.getOrDefault(optionItem, 0) + 1;
                while (countCarried(context, optionItem) < needed) {
                    if (!craftCarriedSupplyItem(level, context, optionItem, visiting, hasCraftingTable)) {
                        break;
                    }
                }
                if (countCarried(context, optionItem) >= needed) {
                    selected = optionItem;
                    break;
                }
            }
            if (selected == null) {
                return false;
            }
            ingredients.merge(selected, 1, Integer::sum);
        }
        return true;
    }

    private static List<ItemStack> craftingRemainders(Map<Item, Integer> ingredients) {
        List<ItemStack> remainders = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
            ItemStack ingredient = new ItemStack(entry.getKey());
            if (!ingredient.hasCraftingRemainingItem()) {
                continue;
            }
            for (int count = 0; count < entry.getValue(); count++) {
                ItemStack craftingRemainder = ingredient.getCraftingRemainingItem();
                if (!craftingRemainder.isEmpty()) {
                    remainders.add(craftingRemainder.copy());
                }
            }
        }
        return remainders;
    }

    public static boolean requiresCraftingTable(CraftingRecipe recipe) {
        return recipe != null && !recipe.canCraftInDimensions(2, 2);
    }

    public static boolean canUseRecipe(ServerLevel level, HiredWorkContext context, CraftingRecipe recipe) {
        return canUseRecipe(recipe, hasCraftingTable(level, context));
    }

    private static boolean canUseRecipe(CraftingRecipe recipe, boolean hasCraftingTable) {
        return recipe != null
                && recipe.canCraftInDimensions(3, 3)
                && (!requiresCraftingTable(recipe) || hasCraftingTable);
    }

    public static boolean hasCraftingTable(ServerLevel level, HiredWorkContext context) {
        if (level == null || context == null || !context.hasWorkArea()) {
            return false;
        }
        for (BlockPos pos : context.workAreaPositions()) {
            if (context.isLoaded(level, pos) && level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                return true;
            }
        }
        return false;
    }

    public static final class MaterialPlanner {
        private final ServerLevel level;
        private final Villager villager;
        private final HiredWorkContext context;
        private final boolean respectStorageFilter;
        private final boolean hasCraftingTable;
        private final Map<Item, Integer> surplus = new HashMap<>();

        public MaterialPlanner(ServerLevel level, Villager villager, HiredWorkContext context) {
            this(level, villager, context, true, false);
        }

        public MaterialPlanner(
                ServerLevel level,
                Villager villager,
                HiredWorkContext context,
                boolean respectStorageFilter) {
            this(level, villager, context, respectStorageFilter, false);
        }

        public MaterialPlanner(
                ServerLevel level,
                Villager villager,
                HiredWorkContext context,
                boolean respectStorageFilter,
                boolean requireCraftingStations) {
            this.level = level;
            this.villager = villager;
            this.context = context;
            this.respectStorageFilter = respectStorageFilter;
            this.hasCraftingTable = !requireCraftingStations || hasCraftingTable(level, context);
        }

        public boolean plan(Item item, int count, Map<Item, Integer> planned) {
            Map<Item, Integer> trial = new LinkedHashMap<>(planned);
            Map<Item, Integer> trialSurplus = new LinkedHashMap<>(this.surplus);
            if (!plan(item, count, trial, trialSurplus, new HashSet<>(), 0)) {
                return false;
            }
            planned.clear();
            planned.putAll(trial);
            this.surplus.clear();
            this.surplus.putAll(trialSurplus);
            return true;
        }

        public int directAvailable(Item item, Map<Item, Integer> planned) {
            int available = countCarried(this.context, item);
            if (this.context.useAssignedStorageForSupplies()) {
                available += this.respectStorageFilter
                        ? AssignedStorageService.countItems(this.villager, stack -> stack.is(item))
                        : AssignedStorageService.countItemsIgnoringFilter(this.villager, stack -> stack.is(item));
            }
            return Math.max(0, available - planned.getOrDefault(item, 0));
        }

        public boolean planRecipe(
                CraftingRecipe recipe,
                int desiredCount,
                Map<Item, Integer> planned,
                Map<Integer, Item> narrowedIngredients) {
            return planRecipe(recipe, desiredCount, planned, narrowedIngredients, true);
        }

        public boolean planRecipe(CraftingRecipe recipe, int desiredCount, Map<Item, Integer> planned) {
            return planRecipe(recipe, desiredCount, planned, Map.of(), true);
        }

        private boolean planRecipe(
                CraftingRecipe recipe, int desiredCount, Map<Item, Integer> planned,
                Map<Integer, Item> narrowedIngredients, boolean topLevel) {
            if (recipe == null || desiredCount <= 0 || recipe.isSpecial() || !canUseRecipe(recipe, this.hasCraftingTable)) {
                return false;
            }
            ItemStack result = recipe.getResultItem(this.level.registryAccess());
            if (result.isEmpty()) {
                return false;
            }
            Map<Item, Integer> trial = new LinkedHashMap<>(planned);
            Map<Item, Integer> trialSurplus = new LinkedHashMap<>(this.surplus);
            if (!planRecipe(
                    recipe,
                    result.getItem(),
                    result.getCount(),
                    desiredCount,
                    trial,
                    trialSurplus,
                    topLevel ? narrowedIngredients : Map.of(),
                    new HashSet<>(),
                    0)) {
                return false;
            }
            planned.clear();
            planned.putAll(trial);
            this.surplus.clear();
            this.surplus.putAll(trialSurplus);
            return true;
        }

        public int surplusAvailable(Item item) {
            return Math.max(0, this.surplus.getOrDefault(item, 0));
        }

        private boolean plan(
                Item item,
                int count,
                Map<Item, Integer> planned,
                Map<Item, Integer> surplus,
                Set<Item> visiting,
                int depth) {
            int surplusAvailable = Math.max(0, surplus.getOrDefault(item, 0));
            int surplusUsed = Math.min(count, surplusAvailable);
            if (surplusUsed > 0) {
                int remainingSurplus = surplusAvailable - surplusUsed;
                if (remainingSurplus > 0) {
                    surplus.put(item, remainingSurplus);
                } else {
                    surplus.remove(item);
                }
            }
            int countAfterSurplus = count - surplusUsed;
            if (countAfterSurplus <= 0) {
                return true;
            }
            int directAvailable = directAvailable(item, planned);
            int directUsed = Math.min(countAfterSurplus, directAvailable);
            if (directUsed > 0) {
                planned.merge(item, directUsed, Integer::sum);
            }
            int remaining = countAfterSurplus - directUsed;
            if (remaining <= 0) {
                return true;
            }
            if (depth >= MAX_CRAFTING_DEPTH || !visiting.add(item)) {
                return false;
            }
            for (RecipeHolder<CraftingRecipe> holder : this.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial() || !canUseRecipe(recipe, this.hasCraftingTable)) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (result.isEmpty() || !result.is(item)) {
                    continue;
                }
                Map<Item, Integer> recipePlan = new LinkedHashMap<>(planned);
                Map<Item, Integer> recipeSurplus = new LinkedHashMap<>(surplus);
                if (planRecipe(recipe, item, result.getCount(), remaining, recipePlan, recipeSurplus,
                        Map.of(), visiting, depth + 1)) {
                    planned.clear();
                    planned.putAll(recipePlan);
                    surplus.clear();
                    surplus.putAll(recipeSurplus);
                    visiting.remove(item);
                    return true;
                }
            }
            visiting.remove(item);
            return false;
        }

        private boolean planRecipe(
                CraftingRecipe recipe,
                Item resultItem,
                int resultCount,
                int neededCount,
                Map<Item, Integer> planned,
                Map<Item, Integer> surplus,
                Map<Integer, Item> narrowedIngredients,
                Set<Item> visiting,
                int depth) {
            if (resultCount <= 0 || !recipe.canCraftInDimensions(3, 3)) {
                return false;
            }
            int crafts = (neededCount + resultCount - 1) / resultCount;
            List<Ingredient> recipeIngredients = recipe.getIngredients();
            for (int slot = 0; slot < recipeIngredients.size(); slot++) {
                Ingredient ingredient = recipeIngredients.get(slot);
                if (ingredient.isEmpty()) {
                    continue;
                }
                Item narrowed = narrowedIngredients.get(slot);
                boolean plannedIngredient = narrowed == null
                        ? planIngredient(ingredient, crafts, planned, surplus, visiting, depth)
                        : ingredient.test(new ItemStack(narrowed))
                                && plan(narrowed, crafts, planned, surplus, visiting, depth);
                if (!plannedIngredient) {
                    return false;
                }
            }
            int extra = crafts * resultCount - neededCount;
            if (extra > 0) {
                surplus.merge(resultItem, extra, Integer::sum);
            }
            return true;
        }

        private boolean planIngredient(
                Ingredient ingredient,
                int count,
                Map<Item, Integer> planned,
                Map<Item, Integer> surplus,
                Set<Item> visiting,
                int depth) {
            for (ItemStack option : ingredient.getItems()) {
                if (option.isEmpty()) {
                    continue;
                }
                Map<Item, Integer> optionPlan = new LinkedHashMap<>(planned);
                Map<Item, Integer> optionSurplus = new LinkedHashMap<>(surplus);
                if (plan(option.getItem(), count, optionPlan, optionSurplus, visiting, depth)) {
                    planned.clear();
                    planned.putAll(optionPlan);
                    surplus.clear();
                    surplus.putAll(optionSurplus);
                    return true;
                }
            }
            return false;
        }
    }
}
