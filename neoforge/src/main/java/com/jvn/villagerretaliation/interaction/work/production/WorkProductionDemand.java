package com.jvn.villagerretaliation.interaction.work.production;

import com.jvn.villagerretaliation.interaction.work.WorkCraftingPlan;
import com.jvn.villagerretaliation.interaction.work.brewing.HiredBrewingRecipeCatalog;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;

public final class WorkProductionDemand {
    private WorkProductionDemand() { }

    public record Plan(List<WorkSupplyRequirement> requirements, String blocker) {
        public Plan { requirements = List.copyOf(requirements); }
    }

    public static Plan describe(ServerLevel level, HiredProductionRecipe definition, long outputs, List<ItemStack> available) {
        return describe(level, definition, outputs, available, false);
    }

    public static Plan describe(ServerLevel level, HiredProductionRecipe definition, long outputs, List<ItemStack> available, boolean waterSource) {
        if (outputs <= 0) return new Plan(List.of(), "");
        if (definition.recipe() == null) return brewing(level, definition, outputs, available, waterSource);
        var resolution = VillagerRecipeFilterData.resolve(level, definition.filter(level));
        if (!resolution.valid()) return new Plan(List.of(), "recipe_unavailable");
        var recipe = resolution.recipe().value();
        var output = recipe.getResultItem(level.registryAccess());
        if (output.isEmpty() || recipe.isSpecial()) return new Plan(List.of(), "recipe_unavailable");
        long batches = (outputs + output.getCount() - 1) / output.getCount();
        var result = new ArrayList<WorkSupplyRequirement>();
        var supplies = new ArrayList<>(available.stream().map(ItemStack::copy).toList());
        if (recipe instanceof CraftingRecipe crafting) {
            var narrowed = new java.util.LinkedHashMap<Integer, net.minecraft.world.item.Item>();
            definition.alternatives().forEach((slot, item) -> narrowed.put(slot, BuiltInRegistries.ITEM.get(item)));
            for (; batches > 0; batches--) {
                var plan = WorkCraftingPlan.prepare(level, crafting, supplies, narrowed, true);
                if (plan == null) break;
                for (int index = 0; index < supplies.size(); index++) supplies.get(index).shrink(plan.consumed().get(index));
                supplies.addAll(plan.surplus().stream().map(ItemStack::copy).toList());
                supplies.addAll(plan.products().subList(0, plan.products().size() - 1).stream().map(ItemStack::copy).toList());
                supplies.subList(available.size(), supplies.size()).removeIf(ItemStack::isEmpty);
            }
        }
        for (int slot = 0; slot < recipe.getIngredients().size() && batches > 0; slot++) {
            var ingredient = recipe.getIngredients().get(slot);
            if (ingredient.isEmpty()) continue;
            var need = ingredient(definition, ingredient, slot, batches);
            if (need.alternatives().isEmpty()) return new Plan(List.of(), "ingredient_unavailable");
            long needed = batches;
            if (recipe instanceof CraftingRecipe) needed = fulfill(level, supplies, need.accepts(), needed);
            if (needed > 0) merge(result, need.withQuantity(needed));
        }
        if (recipe instanceof CraftingRecipe) {
            var inputs = new ArrayList<WorkSupplyRequirement>();
            for (int index = 0; index < available.size(); index++) {
                int consumed = available.get(index).getCount() - supplies.get(index).getCount();
                if (consumed <= 0) continue;
                WorkSupplyRequirement direct = null;
                for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
                    var candidate = ingredient(definition, recipe.getIngredients().get(slot), slot, consumed);
                    if (candidate.accepts().test(available.get(index))) {
                        direct = candidate;
                        break;
                    }
                }
                merge(inputs, direct == null ? WorkSupplyRequirement.exact("material", available.get(index), consumed) : direct);
            }
            result.forEach(need -> merge(inputs, need));
            result = inputs;
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            var type = cooking.getType();
            var fuels = BuiltInRegistries.ITEM.stream().map(ItemStack::new).filter(stack -> stack.getBurnTime(type) > 0).toList();
            result.add(new WorkSupplyRequirement("fuel", fuels, batches * cooking.getCookingTime() * (type == net.minecraft.world.item.crafting.RecipeType.SMELTING ? 1 : 2),
                    WorkSupplyRequirement.Unit.BURN_TICKS, stack -> stack.getBurnTime(type) > 0, type));
        }
        return new Plan(result, "");
    }

    private static WorkSupplyRequirement ingredient(HiredProductionRecipe definition, net.minecraft.world.item.crafting.Ingredient ingredient,
                                                    int slot, long quantity) {
        var narrowed = definition.alternatives().get(slot);
        java.util.function.Predicate<ItemStack> accepts = stack -> ingredient.test(stack)
                && (narrowed == null || BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(narrowed));
        return new WorkSupplyRequirement("ingredient", java.util.Arrays.stream(ingredient.getItems()).filter(accepts).toList(),
                quantity, WorkSupplyRequirement.Unit.ITEMS, accepts, null);
    }

    private static long fulfill(ServerLevel level, List<ItemStack> supplies, java.util.function.Predicate<ItemStack> accepts, long needed) {
        var recipes = level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                .filter(holder -> accepts.test(holder.value().getResultItem(level.registryAccess()))).toList();
        for (int attempt = 0; needed > 0 && attempt < HiredProductionOrder.MAX_AMOUNT; attempt++) {
            for (var stack : supplies) {
                if (!accepts.test(stack)) continue;
                int used = (int) Math.min(needed, stack.getCount());
                stack.shrink(used);
                needed -= used;
            }
            if (needed == 0) break;
            WorkCraftingPlan.Result prepared = null;
            for (var holder : recipes) {
                prepared = WorkCraftingPlan.prepare(level, holder.value(), supplies, java.util.Map.of(), true);
                if (prepared != null) break;
            }
            if (prepared == null) break;
            for (int index = 0; index < supplies.size(); index++) supplies.get(index).shrink(prepared.consumed().get(index));
            supplies.addAll(prepared.outputs().stream().map(ItemStack::copy).toList());
        }
        return needed;
    }

    private static Plan brewing(ServerLevel level, HiredProductionRecipe definition, long outputs, List<ItemStack> available, boolean waterSource) {
        var route = HiredBrewingRecipeCatalog.find(level, definition.brewingItem(), definition.potion()).orElse(null);
        if (route == null) return new Plan(List.of(), "recipe_unavailable");
        var result = new ArrayList<WorkSupplyRequirement>();
        long batches = (outputs + 2) / 3;
        for (var item : route.ingredients()) merge(result, WorkSupplyRequirement.exact("ingredient", new ItemStack(item), batches));
        var water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
        long filled = Math.min(outputs, available.stream().filter(HiredBrewingRecipeCatalog::isWaterPotion).mapToLong(ItemStack::getCount).sum());
        if (!waterSource) filled = outputs;
        if (filled > 0) result.add(WorkSupplyRequirement.exact("water_bottles", water, filled));
        long empty = outputs - filled;
        if (empty > 0) {
            long bottles = Math.min(empty, available.stream().filter(stack -> stack.is(Items.GLASS_BOTTLE)).mapToLong(ItemStack::getCount).sum());
            if (bottles > 0) result.add(WorkSupplyRequirement.exact("bottles", new ItemStack(Items.GLASS_BOTTLE), bottles));
            if (empty > bottles) {
                var recipe = level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                        .filter(holder -> holder.value().getResultItem(level.registryAccess()).is(Items.GLASS_BOTTLE)).findFirst().orElse(null);
                if (recipe == null) result.add(WorkSupplyRequirement.exact("bottles", new ItemStack(Items.GLASS_BOTTLE), empty - bottles));
                else result.addAll(describe(level, HiredProductionRecipe.exact(recipe.id(), java.util.Map.of()), empty - bottles, available).requirements());
            }
            result.add(new WorkSupplyRequirement("water", List.of(), empty, WorkSupplyRequirement.Unit.WATER_FILLS, stack -> false, null));
        }
        result.add(new WorkSupplyRequirement("brewing_fuel", List.of(new ItemStack(Items.BLAZE_POWDER)),
                batches * route.ingredients().size(), WorkSupplyRequirement.Unit.BREW_USES, stack -> stack.is(Items.BLAZE_POWDER), null));
        return new Plan(result, "");
    }

    private static void merge(List<WorkSupplyRequirement> result, WorkSupplyRequirement next) {
        for (int index = 0; index < result.size(); index++) {
            var previous = result.get(index);
            if (previous.sameGroup(next)) {
                result.set(index, previous.withQuantity(previous.quantity() + next.quantity()));
                return;
            }
        }
        result.add(next);
    }
}
