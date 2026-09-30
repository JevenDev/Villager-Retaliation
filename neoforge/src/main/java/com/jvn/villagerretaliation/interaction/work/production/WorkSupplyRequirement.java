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
        };
    }

    public WorkSupplyRequirement withQuantity(long quantity) {
        return new WorkSupplyRequirement(key, alternatives, quantity, unit, accepts, recipeType);
    }

    public static WorkSupplyRequirement exact(String key, ItemStack sample, long quantity) {
        var product = HiredProductionStock.product(sample).copyWithCount(1);
        return new WorkSupplyRequirement(key, List.of(product), quantity, Unit.ITEMS,
                stack -> HiredProductionStock.matches(product, stack), null);
    }

    public enum Unit { ITEMS, BURN_TICKS, BREW_USES, WATER_FILLS }
}
