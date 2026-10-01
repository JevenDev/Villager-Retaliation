package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record SettlementPurchasePlan(boolean enabled, CurrencyAmount dailyBudget, List<Approval> approvals) {
    public static final int MAX_APPROVALS = 16;
    public static final long SHORTAGE_TICKS = 1_200L;
    public static final SettlementPurchasePlan DISABLED = new SettlementPurchasePlan(false, CurrencyAmount.ZERO, List.of());

    public SettlementPurchasePlan {
        Objects.requireNonNull(dailyBudget);
        approvals = List.copyOf(approvals);
        if (dailyBudget.compareTo(CurrencyAmount.of(1_000_000L, 1L)) > 0 || approvals.size() > MAX_APPROVALS
                || approvals.stream().map(Approval::id).distinct().count() != approvals.size()
                || approvals.stream().map(Approval::choice).distinct().count() != approvals.size()
                || enabled && (dailyBudget.isZero() || approvals.isEmpty()
                        || approvals.stream().anyMatch(approval -> approval.unitPriceLimit().isZero())))
            throw new IllegalArgumentException("Invalid policy purchase approvals or budget");
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("Version", 1);
        tag.putBoolean("Enabled", this.enabled);
        tag.put("DailyBudget", this.dailyBudget.save());
        var approvals = new ListTag();
        for (var approval : this.approvals) {
            var entry = new CompoundTag();
            entry.putUUID("Id", approval.id());
            if (approval.consumer() != null) entry.putUUID("Consumer", approval.consumer());
            entry.putUUID("Requirement", approval.requirement());
            entry.putString("Item", approval.item().toString());
            entry.putLong("Destination", approval.destination().asLong());
            entry.put("UnitPriceLimit", approval.unitPriceLimit().save());
            approvals.add(entry);
        }
        tag.put("Approvals", approvals);
        return tag;
    }

    public static SettlementPurchasePlan load(CompoundTag tag) {
        if (!tag.contains("Version", Tag.TAG_INT) || tag.getInt("Version") != 1 || !tag.contains("Enabled", Tag.TAG_BYTE)
                || !tag.contains("DailyBudget", Tag.TAG_COMPOUND) || !(tag.get("Approvals") instanceof ListTag list)
                || list.size() > MAX_APPROVALS || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
            throw new IllegalArgumentException("Invalid saved policy purchase plan");
        var approvals = new java.util.ArrayList<Approval>();
        for (var raw : list) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || !entry.hasUUID("Requirement")
                    || entry.contains("Consumer") && !entry.hasUUID("Consumer")
                    || !entry.contains("Item", Tag.TAG_STRING) || !entry.contains("Destination", Tag.TAG_LONG)
                    || !entry.contains("UnitPriceLimit", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid saved policy purchase approval");
            approvals.add(new Approval(entry.getUUID("Id"), entry.contains("Consumer") ? entry.getUUID("Consumer") : null,
                    entry.getUUID("Requirement"), ResourceLocation.parse(entry.getString("Item")),
                    BlockPos.of(entry.getLong("Destination")), currency(entry.getCompound("UnitPriceLimit"))));
        }
        return new SettlementPurchasePlan(tag.getBoolean("Enabled"), currency(tag.getCompound("DailyBudget")), approvals);
    }

    static CurrencyAmount currency(CompoundTag tag) {
        if (!tag.contains("Numerator", Tag.TAG_STRING) || !tag.contains("Denominator", Tag.TAG_STRING))
            throw new IllegalArgumentException("Invalid saved policy purchase currency");
        var amount = CurrencyAmount.load(tag);
        if (!amount.numerator().toString().equals(tag.getString("Numerator"))
                || !amount.denominator().toString().equals(tag.getString("Denominator")))
            throw new IllegalArgumentException("Invalid saved policy purchase currency");
        return amount;
    }

    public record Approval(UUID id, UUID consumer, UUID requirement, ResourceLocation item, BlockPos destination,
                           CurrencyAmount unitPriceLimit) {
        public Approval {
            Objects.requireNonNull(id);
            Objects.requireNonNull(requirement);
            Objects.requireNonNull(item);
            destination = Objects.requireNonNull(destination).immutable();
            Objects.requireNonNull(unitPriceLimit);
            if (!BuiltInRegistries.ITEM.containsKey(item) || BuiltInRegistries.ITEM.get(item) == Items.AIR
                    || unitPriceLimit.compareTo(CurrencyAmount.of(1_000_000L, 1L)) > 0)
                throw new IllegalArgumentException("Invalid policy purchase commodity or price limit");
        }

        public ItemStack sample() { return new ItemStack(BuiltInRegistries.ITEM.get(this.item)); }
        private Choice choice() { return new Choice(this.consumer, this.requirement, this.item, this.destination); }
    }

    private record Choice(UUID consumer, UUID requirement, ResourceLocation item, BlockPos destination) { }
}
