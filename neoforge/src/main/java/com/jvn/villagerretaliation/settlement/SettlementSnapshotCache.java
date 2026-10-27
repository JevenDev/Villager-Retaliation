package com.jvn.villagerretaliation.settlement;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

final class SettlementSnapshotCache {
    private final Map<SettlementNavigationSection, CompoundTag> navigation = new WeakHashMap<>();
    CompoundTag capture(SettlementSavedData data, HolderLookup.Provider provider) {
        var snapshot = data.save(new CompoundTag(), provider, town -> snapshot(town, provider));
        if (snapshot.contains("FoodAid")) snapshot.put("FoodAid", snapshot.get("FoodAid").copy());
        return snapshot;
    }
    private CompoundTag snapshot(SettlementRecord town, HolderLookup.Provider provider) {
        var serialized = town.snapshot(provider, new ListTag());
        var snapshot = new CompoundTag();
        for (var key : serialized.getAllKeys()) {
            var value = serialized.get(key);
            // resident and worker serializers already detach their mutable tags
            snapshot.put(key, key.equals("ResidentCare") || key.equals("Workers") ? value : value.copy());
        }
        var navigationTags = new ListTag();
        town.navigation().values().forEach(section -> navigationTags.add(this.navigation.computeIfAbsent(section, SettlementNavigationSection::save)));
        snapshot.put("Navigation", navigationTags);
        return snapshot;
    }
}
