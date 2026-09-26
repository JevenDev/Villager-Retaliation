package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SettlementTradeOrder(UUID id, Direction direction, ResourceLocation item, int stockTarget,
                                   CurrencyAmount unitPriceLimit, CurrencyAmount dailyBudget, boolean enabled) {
    public SettlementTradeOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(unitPriceLimit, "unitPriceLimit");
        Objects.requireNonNull(dailyBudget, "dailyBudget");
        if (!BuiltInRegistries.ITEM.containsKey(item) || BuiltInRegistries.ITEM.get(item) == Items.AIR
                || stockTarget < 0 || stockTarget > 1_000_000
                || unitPriceLimit.compareTo(CurrencyAmount.of(1_000_000L, 1L)) > 0
                || dailyBudget.compareTo(CurrencyAmount.of(1_000_000L, 1L)) > 0) {
            throw new IllegalArgumentException("Trade order item, stock target or currency limit is invalid");
        }
    }

    public static SettlementTradeOrder disabled(ResourceLocation item, Direction direction) {
        return new SettlementTradeOrder(UUID.randomUUID(), direction, item, 64,
                CurrencyAmount.ZERO, CurrencyAmount.ZERO, false);
    }

    public ItemStack sample() {
        return new ItemStack(BuiltInRegistries.ITEM.get(this.item));
    }

    public boolean matches(ItemStack stack) {
        return ItemStack.isSameItemSameComponents(sample(), stack);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Direction", this.direction.name());
        tag.putString("Item", this.item.toString());
        tag.putInt("StockTarget", this.stockTarget);
        tag.put("UnitPriceLimit", this.unitPriceLimit.save());
        tag.put("DailyBudget", this.dailyBudget.save());
        tag.putBoolean("Enabled", this.enabled);
        return tag;
    }

    static SettlementTradeOrder load(CompoundTag tag) {
        if (!tag.hasUUID("Id")) {
            throw new IllegalArgumentException("Trade order identity is missing");
        }
        return new SettlementTradeOrder(tag.getUUID("Id"), Direction.valueOf(tag.getString("Direction")),
                ResourceLocation.parse(tag.getString("Item")), tag.getInt("StockTarget"),
                CurrencyAmount.load(tag.getCompound("UnitPriceLimit")),
                CurrencyAmount.load(tag.getCompound("DailyBudget")), tag.getBoolean("Enabled"));
    }

    public enum Direction {
        BUY,
        SELL
    }
}
