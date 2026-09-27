package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

public final class SettlementSavedData extends SavedData {
    private static final String DATA_NAME = "villagerretaliation_settlements";
    private static final int FORMAT_VERSION = 39;
    private final Map<VillageAllegianceId, SettlementRecord> settlements = new LinkedHashMap<>();

    public static SettlementSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementSavedData::new, SettlementSavedData::load, DataFixTypes.LEVEL),
                DATA_NAME);
    }

    public Optional<SettlementRecord> get(VillageAllegianceId id) {
        return Optional.ofNullable(this.settlements.get(id));
    }

    public List<SettlementRecord> settlements() {
        return List.copyOf(this.settlements.values());
    }

    public Optional<SettlementRecord> at(ResourceLocation dimension, BlockPos position) {
        return this.settlements.values().stream()
                .filter(town -> town.dimension().equals(dimension))
                .filter(town -> town.center().equals(position)
                        || town.areas().values().stream().anyMatch(area -> area.contains(position)))
                .findFirst();
    }

    public List<SettlementRecord> accessibleTo(UUID player) {
        return this.settlements.values().stream()
                .filter(town -> town.permits(player, SettlementPermission.VIEW))
                .toList();
    }

    public boolean canEstablish(ResourceLocation dimension, BlockPos center) {
        return dimension != null && center != null && this.settlements.values().stream().noneMatch(town ->
                town.dimension().equals(dimension) && (town.center().distSqr(center) < 256.0D
                        || town.areas().values().stream().anyMatch(area -> area.contains(center))));
    }

    public Optional<SettlementRecord> establish(VillageAllegianceRegistrySavedData registry,
                                                VillageAllegianceId villageId, UUID leader,
                                                BlockPos center, long gameTime) {
        var village = registry.canonicalRecord(villageId).orElse(null);
        if (village == null || village.archived() || village.originDimension() == null || leader == null
                || center == null || this.settlements.containsKey(village.id())
                || registry.isManaged(village.id()) || !canEstablish(village.originDimension(), center)) {
            return Optional.empty();
        }
        SettlementRecord town = new SettlementRecord(village.id(), village.originDimension(),
                center, leader, gameTime, this::setDirty);
        this.settlements.put(town.id(), town);
        updateFootprint(registry, town);
        setDirty();
        return Optional.of(town);
    }

    public boolean putArea(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id,
                           UUID actor, SettlementArea area) {
        SettlementRecord town = this.settlements.get(id);
        if (town == null || area == null
                || this.settlements.values().stream()
                        .filter(other -> !other.id().equals(id) && other.dimension().equals(town.dimension()))
                        .anyMatch(other -> area.contains(other.center())
                                || other.areas().values().stream().anyMatch(area::intersects))
                || !town.putArea(actor, area)) {
            return false;
        }
        updateFootprint(registry, town);
        return true;
    }

    public boolean removeArea(VillageAllegianceRegistrySavedData registry, VillageAllegianceId id,
                              UUID actor, UUID areaId) {
        SettlementRecord town = this.settlements.get(id);
        if (town == null || !town.removeArea(actor, areaId)) {
            return false;
        }
        updateFootprint(registry, town);
        return true;
    }

    public void restoreRegistry(VillageAllegianceRegistrySavedData registry) {
        for (SettlementRecord town : this.settlements.values()) {
            registry.ensureRecord(town.id(), town.foundedAt(), town.dimension(), town.center());
            updateFootprint(registry, town);
        }
    }

    private static void updateFootprint(VillageAllegianceRegistrySavedData registry, SettlementRecord town) {
        Set<Long> footprint = new LinkedHashSet<>();
        footprint.add(SectionPos.asLong(town.center()));
        for (SettlementArea area : town.areas().values()) {
            for (int x = SectionPos.blockToSectionCoord(area.min().getX());
                 x <= SectionPos.blockToSectionCoord(area.max().getX()); x++) {
                for (int y = SectionPos.blockToSectionCoord(area.min().getY());
                     y <= SectionPos.blockToSectionCoord(area.max().getY()); y++) {
                    for (int z = SectionPos.blockToSectionCoord(area.min().getZ());
                         z <= SectionPos.blockToSectionCoord(area.max().getZ()); z++) {
                        footprint.add(SectionPos.asLong(x, y, z));
                    }
                }
            }
        }
        registry.manage(town.id(), town.center(), footprint);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("FormatVersion", FORMAT_VERSION);
        ListTag entries = new ListTag();
        this.settlements.values().forEach(town -> entries.add(town.save(provider)));
        tag.put("Settlements", entries);
        return tag;
    }

    public static SettlementSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        int version = tag.getInt("FormatVersion");
        if (version > FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported settlement format " + version);
        }
        SettlementSavedData data = new SettlementSavedData();
        for (Tag raw : tag.getList("Settlements", Tag.TAG_COMPOUND)) {
            SettlementRecord town = SettlementRecord.load((CompoundTag) raw, provider, data::setDirty);
            if (data.settlements.putIfAbsent(town.id(), town) != null) {
                throw new IllegalArgumentException("Duplicate settlement identity " + town.id());
            }
        }
        return data;
    }
}
