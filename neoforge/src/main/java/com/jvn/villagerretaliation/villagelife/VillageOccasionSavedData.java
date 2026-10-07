package com.jvn.villagerretaliation.villagelife;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class VillageOccasionSavedData extends SavedData {
    private final Map<String, Occasions> villages = new HashMap<>();

    public static VillageOccasionSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new Factory<>(VillageOccasionSavedData::new, VillageOccasionSavedData::load), "villagerretaliation_village_life");
    }

    public boolean schedule(ServerLevel level, VillageAllegianceId village, Kind kind, String eventId) {
        if (!VillagerRetaliationConfig.ENABLE_VILLAGE_LIFE.get()) return false;
        long now = level.getGameTime();
        Occasions state = state(level, village);
        state.seen.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (state.seen.containsKey(eventId)) return false;
        if (state.seen.size() >= 64) state.seen.remove(state.seen.keySet().iterator().next());
        state.seen.put(eventId, now + 24_000L);
        if (state.expiresAt <= now) state.pending.clear();
        if (state.pending.isEmpty()) {
            long day = Math.floorDiv(level.getDayTime(), 24_000L);
            if (Math.floorMod(level.getDayTime(), 24_000L) >= 12_000L) day++;
            state.leisureAt = day * 24_000L + 9_000L;
            state.expiresAt = now + 24_000L;
        }
        state.pending.add(kind);
        setDirty();
        return true;
    }

    public void scheduleMarket(ServerLevel level, VillageAllegianceId village) {
        long day = Math.floorDiv(level.getDayTime(), 24_000L);
        if (Math.floorMod(day, 7L) != 0L) return;
        Occasions state = state(level, village);
        if (day <= state.lastMarketDay) return;
        if (schedule(level, village, Kind.MARKET, "market:" + day)) {
            state.lastMarketDay = day;
            setDirty();
        }
    }

    public EnumSet<Kind> due(ServerLevel level, VillageAllegianceId village) {
        Occasions state = this.villages.get(key(level, village));
        if (state == null || state.expiresAt <= level.getGameTime()
                || state.leisureAt > level.getDayTime() || Math.floorMod(level.getDayTime(), 24_000L) < 9_000L
                || Math.floorMod(level.getDayTime(), 24_000L) >= 12_000L) {
            return EnumSet.noneOf(Kind.class);
        }
        return state.pending.clone();
    }

    public void consume(ServerLevel level, VillageAllegianceId village) {
        Occasions state = this.villages.get(key(level, village));
        if (state != null && !state.pending.isEmpty()) {
            state.pending.clear();
            setDirty();
        }
    }

    private Occasions state(ServerLevel level, VillageAllegianceId village) {
        return this.villages.computeIfAbsent(key(level, village), ignored -> new Occasions());
    }

    private static String key(ServerLevel level, VillageAllegianceId village) {
        return level.dimension().location() + "/" + village;
    }

    public static VillageOccasionSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        var data = new VillageOccasionSavedData();
        for (Tag raw : tag.getList("Villages", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) raw;
            if (saved.getString("Key").isBlank()) continue;
            var state = new Occasions();
            state.expiresAt = saved.getLong("ExpiresAt");
            state.leisureAt = saved.getLong("LeisureAt");
            state.lastMarketDay = saved.contains("LastMarketDay", Tag.TAG_LONG) ? saved.getLong("LastMarketDay") : Long.MIN_VALUE;
            for (Kind kind : Kind.values()) if (saved.getBoolean(kind.name())) state.pending.add(kind);
            for (Tag event : saved.getList("Seen", Tag.TAG_COMPOUND)) {
                if (state.seen.size() >= 64) break;
                CompoundTag seen = (CompoundTag) event;
                state.seen.put(seen.getString("Id"), seen.getLong("Until"));
            }
            data.villages.put(saved.getString("Key"), state);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag entries = new ListTag();
        this.villages.forEach((key, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", key);
            entry.putLong("ExpiresAt", state.expiresAt);
            entry.putLong("LeisureAt", state.leisureAt);
            entry.putLong("LastMarketDay", state.lastMarketDay);
            state.pending.forEach(kind -> entry.putBoolean(kind.name(), true));
            ListTag seen = new ListTag();
            state.seen.forEach((id, until) -> {
                CompoundTag event = new CompoundTag();
                event.putString("Id", id);
                event.putLong("Until", until);
                seen.add(event);
            });
            entry.put("Seen", seen);
            entries.add(entry);
        });
        tag.put("Villages", entries);
        return tag;
    }

    public enum Kind {
        MARKET, BIRTH, RAID_VICTORY, FOUNDING
    }

    private static final class Occasions {
        private final EnumSet<Kind> pending = EnumSet.noneOf(Kind.class);
        private final Map<String, Long> seen = new LinkedHashMap<>();
        private long lastMarketDay = Long.MIN_VALUE;
        private long leisureAt;
        private long expiresAt;
    }
}
