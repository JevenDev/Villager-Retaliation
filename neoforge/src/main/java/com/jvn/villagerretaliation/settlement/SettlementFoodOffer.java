package com.jvn.villagerretaliation.settlement;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public final class SettlementFoodOffer {
    public enum Arrangement { DONOR_DELIVERY, REQUESTER_COLLECTION }
    public enum Custody { RESERVED, COURIER, RETURNED, DELIVERED, RECOVERABLE }
    public record Parcel(UUID depot, ItemStack item) {
        public Parcel {
            if (depot == null || item.isEmpty() || !SettlementFoodService.isMeal(item)
                    || item.getCount() > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid aid parcel");
            item = item.copy();
        }
        @Override public ItemStack item() { return this.item.copy(); }
    }
    private final Arrangement arrangement;
    private final UUID courier;
    private final long nutrition;
    private final List<Parcel> parcels;
    private final List<Parcel> remaining;
    Custody custody = Custody.RESERVED;

    SettlementFoodOffer(Arrangement arrangement, UUID courier, List<Parcel> parcels) {
        if (arrangement == null || courier == null || parcels.isEmpty() || parcels.size() > 27)
            throw new IllegalArgumentException("Invalid food offer");
        this.arrangement = arrangement; this.courier = courier; this.parcels = List.copyOf(parcels);
        this.remaining = new ArrayList<>(parcels);
        this.nutrition = parcels.stream().mapToLong(parcel -> (long) parcel.item().getCount() * parcel.item().get(DataComponents.FOOD).nutrition()).sum();
    }
    public Arrangement arrangement() { return this.arrangement; }
    public UUID courier() { return this.courier; }
    public long nutrition() { return this.nutrition; }
    public List<Parcel> parcels() { return this.parcels; }
    public List<Parcel> remaining() { return List.copyOf(this.remaining); }
    public Custody custody() { return this.custody; }
    public boolean empty() { return this.remaining.isEmpty(); }
    void replaceRemaining(List<Parcel> remaining) { this.remaining.clear(); this.remaining.addAll(remaining); }
    CompoundTag save(HolderLookup.Provider provider) {
        var tag = new CompoundTag();
        tag.putString("Arrangement", this.arrangement.name()); tag.putUUID("Courier", this.courier);
        tag.putString("Custody", this.custody.name()); tag.putLong("Nutrition", this.nutrition);
        tag.put("Parcels", save(this.parcels, provider)); tag.put("Remaining", save(this.remaining, provider));
        return tag;
    }
    private static ListTag save(List<Parcel> parcels, HolderLookup.Provider provider) {
        var list = new ListTag();
        for (var parcel : parcels) {
            var tag = new CompoundTag(); tag.putUUID("Depot", parcel.depot()); tag.put("Item", parcel.item().save(provider)); list.add(tag);
        }
        return list;
    }
    private static List<Parcel> load(ListTag list, HolderLookup.Provider provider) {
        if (list.size() > 27) throw new IllegalArgumentException("Too much reserved food");
        var result = new ArrayList<Parcel>();
        for (var raw : list) {
            var entry = (CompoundTag) raw;
            if (!entry.hasUUID("Depot") || !entry.contains("Item", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Malformed aid parcel");
            result.add(new Parcel(entry.getUUID("Depot"), ItemStack.parse(provider, entry.getCompound("Item")).orElseThrow(() -> new IllegalArgumentException("Unreadable aid item"))));
        }
        return result;
    }
    static SettlementFoodOffer load(CompoundTag tag, HolderLookup.Provider provider) {
        if (!tag.contains("Arrangement", Tag.TAG_STRING) || !tag.hasUUID("Courier") || !tag.contains("Custody", Tag.TAG_STRING)
                || !tag.contains("Nutrition", Tag.TAG_LONG)) throw new IllegalArgumentException("Missing food offer fields");
        var original = tag.getList("Parcels", Tag.TAG_COMPOUND); var remaining = tag.getList("Remaining", Tag.TAG_COMPOUND);
        if (!original.equals(tag.get("Parcels")) || !remaining.equals(tag.get("Remaining"))) throw new IllegalArgumentException("Invalid offer parcels");
        var result = new SettlementFoodOffer(Arrangement.valueOf(tag.getString("Arrangement")), tag.getUUID("Courier"), load(original, provider));
        result.custody = Custody.valueOf(tag.getString("Custody"));
        result.replaceRemaining(load(remaining, provider));
        if (result.nutrition != tag.getLong("Nutrition")) throw new IllegalArgumentException("Offer nutrition does not match its food");
        var available = result.parcels.stream().map(parcel -> new Parcel(parcel.depot(), parcel.item())).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (var parcel : result.remaining) {
            int count = parcel.item().getCount();
            for (int index = 0; index < available.size() && count > 0; index++) {
                var candidate = available.get(index);
                if (!candidate.depot().equals(parcel.depot()) || !ItemStack.isSameItemSameComponents(candidate.item(), parcel.item())) continue;
                int consumed = Math.min(count, candidate.item().getCount()); count -= consumed;
                if (consumed == candidate.item().getCount()) available.remove(index--);
                else available.set(index, new Parcel(candidate.depot(), candidate.item().copyWithCount(candidate.item().getCount() - consumed)));
            }
            if (count != 0) throw new IllegalArgumentException("Offer custody exceeds its reserved food");
        }
        if ((result.custody == Custody.DELIVERED || result.custody == Custody.RETURNED) && !result.empty())
            throw new IllegalArgumentException("Completed custody still contains food");
        return result;
    }
}
