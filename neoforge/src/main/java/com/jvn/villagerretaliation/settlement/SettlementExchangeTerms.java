package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public record SettlementExchangeTerms(Bundle first, Bundle second, boolean firstSends) {
    public record Bundle(List<ItemStack> items, CurrencyAmount funds) {
        public Bundle {
            if (items == null || items.size() > 27 || funds == null || funds.compareTo(CurrencyAmount.of(1_000_000, 1)) > 0
                    || funds.numerator().bitLength() > 64 || funds.denominator().bitLength() > 32)
                throw new IllegalArgumentException("Invalid exchange bundle");
            for (var item : items) if (item.isEmpty() || item.getCount() > item.getMaxStackSize())
                throw new IllegalArgumentException("Invalid exchange stack");
            items = items.stream().map(ItemStack::copy).toList();
        }
        @Override public List<ItemStack> items() { return items.stream().map(ItemStack::copy).toList(); }
        public boolean empty() { return items.isEmpty() && funds.isZero(); }
        public CompoundTag save(HolderLookup.Provider provider) {
            var tag = new CompoundTag(); var list = new ListTag();
            items.forEach(item -> list.add(item.save(provider))); tag.put("Items", list); tag.put("Funds", funds.save());
            return tag;
        }
        public static Bundle load(CompoundTag tag, HolderLookup.Provider provider) {
            var items = tag.getList("Items", Tag.TAG_COMPOUND);
            if (!items.equals(tag.get("Items")) || items.size() > 27 || !tag.contains("Funds", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("Invalid exchange bundle");
            var fundsTag = tag.getCompound("Funds"); var funds = CurrencyAmount.load(fundsTag);
            if (!funds.save().equals(fundsTag)) throw new IllegalArgumentException("Invalid exchange funds");
            return new Bundle(items.stream().map(raw -> ItemStack.parse(provider, (CompoundTag) raw)
                    .orElseThrow(() -> new IllegalArgumentException("Unreadable exchange item"))).toList(), funds);
        }
    }
    public SettlementExchangeTerms {
        if (first == null || second == null || first.empty() && second.empty())
            throw new IllegalArgumentException("Empty exchange");
    }
    public CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.put("First", first.save(provider)); tag.put("Second", second.save(provider));
        tag.putBoolean("FirstSends", firstSends); return tag;
    }
    public static SettlementExchangeTerms load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("FirstSends", Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing exchange carrier");
        return new SettlementExchangeTerms(Bundle.load(tag.getCompound("First"), provider),
                Bundle.load(tag.getCompound("Second"), provider), tag.getBoolean("FirstSends"));
    }
}
