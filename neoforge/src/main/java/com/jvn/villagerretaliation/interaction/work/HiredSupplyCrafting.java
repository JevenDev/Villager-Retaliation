package com.jvn.villagerretaliation.interaction.work;

import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

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
        return craftCarriedSupplyItem(level, context, item, new HashSet<>());
    }

    private static boolean craftCarriedSupplyItem(
            ServerLevel level,
            HiredWorkContext context,
            Item item,
            Set<Item> visiting) {
        if (!visiting.add(item)) {
            return false;
        }
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            CraftingRecipe recipe = holder.value();
            if (recipe.isSpecial() || !recipe.canCraftInDimensions(3, 3)) {
                continue;
            }
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (result.isEmpty() || !result.is(item)) {
                continue;
            }
            Map<Item, Integer> ingredients = new HashMap<>();
            if (!prepareRecipeIngredients(level, context, recipe, ingredients, visiting)) {
                continue;
            }
            if (!canInsertSupply(context, result) && !willConsumeAnyOnlyCarriedSupplyStack(context, ingredients)) {
                visiting.remove(item);
                return false;
            }
            for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
                context.inventory().consumeSupply(stack -> stack.is(entry.getKey()), entry.getValue());
            }
            ItemStack remainder = context.inventory().insertSupply(result.copy());
            visiting.remove(item);
            return remainder.isEmpty();
        }
        visiting.remove(item);
        return false;
    }

    private static boolean prepareRecipeIngredients(
            ServerLevel level,
            HiredWorkContext context,
            CraftingRecipe recipe,
            Map<Item, Integer> ingredients,
            Set<Item> visiting) {
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }
            Item selected = null;
            for (ItemStack option : ingredient.getItems()) {
                if (option.isEmpty()) {
                    continue;
                }
                Item optionItem = option.getItem();
                int needed = ingredients.getOrDefault(optionItem, 0) + 1;
                while (countCarried(context, optionItem) < needed) {
                    if (!craftCarriedSupplyItem(level, context, optionItem, visiting)) {
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

    public static boolean canInsertSupply(HiredWorkContext context, ItemStack stack) {
        for (int slot : context.inventory().supplySlots()) {
            ItemStack current = context.inventory().getItem(slot);
            if (current.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, stack) && current.getCount() < current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public static boolean willConsumeOnlyCarriedSupplyStack(HiredWorkContext context, Item item) {
        int matchingSlots = 0;
        for (int slot : context.inventory().supplySlots()) {
            ItemStack stack = context.inventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                matchingSlots++;
                if (matchingSlots > 1 || stack.getCount() > 1) {
                    return false;
                }
            }
        }
        return matchingSlots == 1;
    }

    private static boolean willConsumeAnyOnlyCarriedSupplyStack(HiredWorkContext context, Map<Item, Integer> ingredients) {
        for (Map.Entry<Item, Integer> entry : ingredients.entrySet()) {
            if (countCarried(context, entry.getKey()) == entry.getValue()) {
                return true;
            }
        }
        return false;
    }

    public static final class MaterialPlanner {
        private final ServerLevel level;
        private final Villager villager;
        private final HiredWorkContext context;
        private final Map<Item, Integer> surplus = new HashMap<>();

        public MaterialPlanner(ServerLevel level, Villager villager, HiredWorkContext context) {
            this.level = level;
            this.villager = villager;
            this.context = context;
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
            return Math.max(0, countAvailable(this.villager, this.context, item) - planned.getOrDefault(item, 0));
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
                if (recipe.isSpecial()) {
                    continue;
                }
                ItemStack result = recipe.getResultItem(this.level.registryAccess());
                if (result.isEmpty() || !result.is(item)) {
                    continue;
                }
                Map<Item, Integer> recipePlan = new LinkedHashMap<>(planned);
                Map<Item, Integer> recipeSurplus = new LinkedHashMap<>(surplus);
                if (planRecipe(recipe, item, result.getCount(), remaining, recipePlan, recipeSurplus, visiting, depth + 1)) {
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
                Set<Item> visiting,
                int depth) {
            if (resultCount <= 0 || !recipe.canCraftInDimensions(3, 3)) {
                return false;
            }
            int crafts = (neededCount + resultCount - 1) / resultCount;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (!planIngredient(ingredient, crafts, planned, surplus, visiting, depth)) {
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
