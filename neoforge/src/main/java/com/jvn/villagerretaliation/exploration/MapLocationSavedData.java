package com.jvn.villagerretaliation.exploration;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class MapLocationSavedData extends SavedData {
    private final Map<UUID, Map<UUID, MapLocation>> locations = new LinkedHashMap<>();

    public static MapLocationSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MapLocationSavedData::new, MapLocationSavedData::load),
                "villagerretaliation_map_locations");
    }

    public List<MapLocation> locations(UUID player) {
        var entries = locations.get(player);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    public boolean put(UUID player, MapLocation location) {
        var entries = locations.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
        if (!entries.containsKey(location.id()) && entries.size() >= MapLocation.MAX_LOCATIONS) return false;
        if (!location.equals(entries.put(location.id(), location))) setDirty();
        return true;
    }

    public void remove(UUID player, UUID id) {
        var entries = locations.get(player);
        if (entries != null && entries.remove(id) != null) {
            if (entries.isEmpty()) locations.remove(player);
            setDirty();
        }
    }

    public int share(UUID sender, UUID recipient, UUID selected) {
        if (sender.equals(recipient)) return 0;
        int added = 0;
        for (var location : locations(sender)) {
            if (selected != null && !selected.equals(location.id())) continue;
            UUID copyId = UUID.nameUUIDFromBytes((sender + ":" + location.id() + ":" + recipient).getBytes(StandardCharsets.UTF_8));
            var entries = locations.get(recipient);
            if (entries != null && entries.containsKey(copyId)) continue;
            if (put(recipient, location.withId(copyId))) added++;
        }
        return added;
    }

    public static MapLocationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new MapLocationSavedData();
        for (var raw : tag.getList("Players", Tag.TAG_COMPOUND)) {
            var player = (CompoundTag) raw;
            if (!player.hasUUID("Player")) continue;
            for (var entry : player.getList("Locations", Tag.TAG_COMPOUND)) {
                try { data.put(player.getUUID("Player"), MapLocation.load((CompoundTag) entry)); }
                catch (IllegalArgumentException invalid) {
                    System.getLogger("VR map").log(System.Logger.Level.WARNING, "Skipping invalid saved map location", invalid);
                }
            }
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        var players = new ListTag();
        locations.forEach((id, entries) -> {
            var player = new CompoundTag();
            player.putUUID("Player", id);
            var markers = new ListTag();
            entries.values().forEach(location -> markers.add(location.save()));
            player.put("Locations", markers); players.add(player);
        });
        tag.put("Players", players);
        return tag;
    }
}
