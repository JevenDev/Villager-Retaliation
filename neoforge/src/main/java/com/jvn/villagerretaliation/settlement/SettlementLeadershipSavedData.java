package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class SettlementLeadershipSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_settlement_leadership";
    private final Map<VillageAllegianceId, Observation> observations = new LinkedHashMap<>();

    public static SettlementLeadershipSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementLeadershipSavedData::new, SettlementLeadershipSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public Observation observation(VillageAllegianceId id) {
        return this.observations.getOrDefault(id, new Observation(-1L, 0L, 0, 0, 0L, false));
    }

    void observe(VillageAllegianceId id, long gameTime, int population, int housing, long nutrition,
                 boolean fullyLoaded, boolean supported) {
        Observation previous = observation(id);
        long elapsed = previous.checkedAt() >= 0L && previous.fullyLoaded() && fullyLoaded
                ? Math.clamp(gameTime - previous.checkedAt(), 0L, 200L) : 0L;
        long sustained = !fullyLoaded ? previous.sustainedTicks()
                : supported ? Math.min(720_000L, previous.sustainedTicks() + elapsed) : 0L;
        Observation current = new Observation(gameTime, sustained,
                fullyLoaded ? population : previous.population(),
                fullyLoaded ? housing : previous.housing(),
                fullyLoaded ? nutrition : previous.nutrition(), fullyLoaded);
        if (!current.equals(this.observations.put(id, current))) {
            setDirty();
        }
    }

    void remove(VillageAllegianceId id) {
        if (this.observations.remove(id) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", 1);
        ListTag entries = new ListTag();
        this.observations.forEach((id, observation) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Village", id.value());
            entry.putLong("CheckedAt", observation.checkedAt());
            entry.putLong("SustainedTicks", observation.sustainedTicks());
            entry.putInt("Population", observation.population());
            entry.putInt("Housing", observation.housing());
            entry.putLong("Nutrition", observation.nutrition());
            entry.putBoolean("FullyLoaded", observation.fullyLoaded());
            entries.add(entry);
        });
        tag.put("Observations", entries);
        return tag;
    }

    public static SettlementLeadershipSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.getInt("FormatVersion") > 1) {
            throw new IllegalArgumentException("Unsupported settlement leadership format");
        }
        var data = new SettlementLeadershipSavedData();
        for (Tag raw : tag.getList("Observations", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Village")) {
                throw new IllegalArgumentException("Leadership observation has no village identity");
            }
            var id = new VillageAllegianceId(entry.getUUID("Village"));
            var observation = new Observation(entry.getLong("CheckedAt"),
                    Math.clamp(entry.getLong("SustainedTicks"), 0L, 720_000L),
                    Math.max(0, entry.getInt("Population")), Math.max(0, entry.getInt("Housing")),
                    Math.max(0L, entry.getLong("Nutrition")), entry.getBoolean("FullyLoaded"));
            if (data.observations.putIfAbsent(id, observation) != null) {
                throw new IllegalArgumentException("Duplicate leadership observation");
            }
        }
        return data;
    }

    public record Observation(long checkedAt, long sustainedTicks, int population, int housing, long nutrition,
                              boolean fullyLoaded) {
    }
}
