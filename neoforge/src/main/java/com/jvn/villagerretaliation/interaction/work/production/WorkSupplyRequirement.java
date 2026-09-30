package com.jvn.villagerretaliation.interaction.work.production;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

public record WorkSupplyRequirement(String key, List<ItemStack> alternatives, long quantity, Unit unit,
                                    Predicate<ItemStack> accepts, RecipeType<?> recipeType) {
    public WorkSupplyRequirement {
        alternatives = alternatives.stream().map(stack -> stack.copyWithCount(1)).toList();
        if (quantity < 0) throw new IllegalArgumentException("Negative supply requirement");
    }

    @Override
    public List<ItemStack> alternatives() { return alternatives.stream().map(ItemStack::copy).toList(); }

    public long value(ItemStack stack) {
        if (stack.isEmpty() || !accepts.test(HiredProductionStock.product(stack))) return 0;
        return switch (unit) {
            case ITEMS -> 1;
            case BURN_TICKS -> Math.max(0, stack.getBurnTime(recipeType));
            case BREW_USES -> 20;
            case WATER_FILLS -> 0;
            case NUTRITION -> stack.has(net.minecraft.core.component.DataComponents.FOOD)
                    ? Math.max(0, stack.get(net.minecraft.core.component.DataComponents.FOOD).nutrition()) : 0;
        };
    }

    public String semanticKey(net.minecraft.core.HolderLookup.Provider registries) {
        var samples = alternatives.stream().map(stack -> HiredProductionStock.product(stack).copyWithCount(1).save(registries).toString())
                .distinct().sorted().toList();
        return key + ":" + unit + ":" + (recipeType == null ? "" : net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipeType))
                + ":" + String.join("|", samples);
    }

    public boolean sameGroup(WorkSupplyRequirement other) {
        return unit == other.unit && key.equals(other.key) && recipeType == other.recipeType
                && alternatives.stream().allMatch(first -> other.alternatives.stream().anyMatch(second -> ItemStack.isSameItemSameComponents(first, second)))
                && other.alternatives.stream().allMatch(first -> alternatives.stream().anyMatch(second -> ItemStack.isSameItemSameComponents(first, second)));
    }

    public WorkSupplyRequirement withQuantity(long quantity) {
        return new WorkSupplyRequirement(key, alternatives, quantity, unit, accepts, recipeType);
    }

    public static WorkSupplyRequirement exact(String key, ItemStack sample, long quantity) {
        var product = HiredProductionStock.product(sample).copyWithCount(1);
        return new WorkSupplyRequirement(key, List.of(product), quantity, Unit.ITEMS,
                stack -> HiredProductionStock.matches(product, stack), null);
    }

    public enum Unit { ITEMS, BURN_TICKS, BREW_USES, WATER_FILLS, NUTRITION }
}
