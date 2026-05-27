package com.jvn.villagerretaliation.trade;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record SkillTradeResult(
        List<Item> items,
        int count,
        SkillTradeEnchantments enchantments) {
    public SkillTradeResult {
        items = items == null ? List.of() : List.copyOf(items);
        count = Math.clamp(count, 1, 64);
        enchantments = enchantments == null ? SkillTradeEnchantments.NONE : enchantments;
    }

    public ItemStack createBaseStack(RandomSource random) {
        return createBaseStack(random, item -> true);
    }

    public ItemStack createBaseStack(RandomSource random, Predicate<Item> itemFilter) {
        if (this.items.isEmpty()) {
            return ItemStack.EMPTY;
        }

        List<Item> candidates = this.items.stream().filter(itemFilter).toList();
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item item = candidates.get(random.nextInt(candidates.size()));
        return new ItemStack(item, this.count);
    }
}
