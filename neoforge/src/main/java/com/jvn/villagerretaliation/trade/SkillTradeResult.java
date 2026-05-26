package com.jvn.villagerretaliation.trade;

import java.util.List;
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
        if (this.items.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Item item = this.items.get(random.nextInt(this.items.size()));
        return new ItemStack(item, this.count);
    }
}
