package com.jvn.villagerretaliation.quest.discovery;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class QuestDiscoverySavedData extends SavedData {
    private final Map<UUID, Map<ResourceLocation, Offer>> offers = new LinkedHashMap<>();

    public static QuestDiscoverySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(QuestDiscoverySavedData::new, QuestDiscoverySavedData::load), "villagerretaliation_discoveries");
    }

    public Map<ResourceLocation, Offer> offers(UUID player) {
        return Map.copyOf(offers.getOrDefault(player, Map.of()));
    }

    public boolean offer(UUID player, ResourceLocation quest, Offer offer) {
        var entries = offers.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        if (entries.size() >= 128 || entries.putIfAbsent(quest, offer) != null) return false;
        setDirty();
        return true;
    }

    public void remove(UUID player, ResourceLocation quest) {
        var entries = offers.get(player);
        if (entries != null && entries.remove(quest) != null) setDirty();
    }

    public static QuestDiscoverySavedData load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new QuestDiscoverySavedData();
        var entries = root.getList("Offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i);
            var quest = ResourceLocation.tryParse(entry.getString("Quest"));
            if (!entry.hasUUID("Player") || quest == null) continue;
            var payload = new LinkedHashMap<String, String>();
            var values = entry.getCompound("Payload");
            values.getAllKeys().forEach(key -> payload.put(key, values.getString(key)));
            data.offer(entry.getUUID("Player"), quest, new Offer(entry.getString("Discovery"),
                    entry.hasUUID("Provider") ? entry.getUUID("Provider") : null, payload));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        var entries = new ListTag();
        offers.forEach((player, quests) -> quests.forEach((quest, offer) -> {
            var entry = new CompoundTag();
            entry.putUUID("Player", player);
            entry.putString("Quest", quest.toString());
            entry.putString("Discovery", offer.discovery());
            if (offer.provider() != null) entry.putUUID("Provider", offer.provider());
            var payload = new CompoundTag();
            offer.payload().forEach(payload::putString);
            entry.put("Payload", payload);
            entries.add(entry);
        }));
        root.put("Offers", entries);
        return root;
    }

    public record Offer(String discovery, UUID provider, Map<String, String> payload) {
        public Offer { payload = Map.copyOf(payload); }
    }
}
